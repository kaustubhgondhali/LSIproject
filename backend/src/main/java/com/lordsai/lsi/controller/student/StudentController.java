package com.lordsai.lsi.controller.student;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.payment.PaymentDtos.AdminPayment;
import com.lordsai.lsi.dto.student.StudentDtos.ChangeUserIdRequest;
import com.lordsai.lsi.dto.student.StudentDtos.ChangeUserIdResponse;
import com.lordsai.lsi.dto.student.StudentDtos.CourseContent;
import com.lordsai.lsi.dto.student.StudentDtos.LessonDetail;
import com.lordsai.lsi.dto.student.StudentDtos.MyCourse;
import com.lordsai.lsi.dto.student.StudentDtos.Overview;
import com.lordsai.lsi.dto.student.StudentDtos.ProgressRequest;
import com.lordsai.lsi.dto.student.StudentDtos.ProgressResponse;
import com.lordsai.lsi.dto.student.StudentDtos.ProtectionEventRequest;
import com.lordsai.lsi.entity.Lesson;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.security.AuthUser;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.security.StreamTicketService;
import org.springframework.security.core.context.SecurityContextHolder;
import com.lordsai.lsi.service.AuthService;
import com.lordsai.lsi.service.FileStorageService;
import com.lordsai.lsi.service.PaymentService;
import com.lordsai.lsi.service.ProtectionEventService;
import com.lordsai.lsi.service.StudentLearningService;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import com.lordsai.lsi.util.VideoStreaming;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    /** 2 MB chunks keep memory flat while seeking through large lesson videos. */
    private final StudentLearningService learningService;
    private final PaymentService paymentService;
    private final FileStorageService storage;
    private final StreamTicketService streamTickets;
    private final ProtectionEventService protectionEvents;
    private final UserService userService;
    private final AuthService authService;

    public StudentController(StudentLearningService learningService,
                             PaymentService paymentService,
                             FileStorageService storage,
                             StreamTicketService streamTickets,
                             ProtectionEventService protectionEvents,
                             UserService userService,
                             AuthService authService) {
        this.learningService = learningService;
        this.paymentService = paymentService;
        this.storage = storage;
        this.streamTickets = streamTickets;
        this.protectionEvents = protectionEvents;
        this.userService = userService;
        this.authService = authService;
    }

    @GetMapping("/profile")
    public ApiResponse<Overview> profile() {
        return ApiResponse.ok(learningService.overview(me()));
    }

    /** Settings -> Change User ID. The account is always the authenticated student; the body never names one. */
    @PostMapping("/change-user-id")
    public ApiResponse<ChangeUserIdResponse> changeUserId(@Valid @RequestBody ChangeUserIdRequest body, HttpServletRequest request) {
        String userId = authService.changeStudentUserId(CurrentUser.require(), body.currentUserId(), body.newUserId(),
                body.confirmUserId(), body.currentPassword(), RequestUtil.clientIp(request));
        return ApiResponse.ok("Your User ID is now " + userId + ". Use it (or your email) the next time you log in.",
                new ChangeUserIdResponse(userId));
    }

    @GetMapping("/courses")
    public ApiResponse<List<MyCourse>> courses() {
        return ApiResponse.ok(learningService.myCourses(me()));
    }

    @GetMapping("/courses/{courseId}")
    public ApiResponse<CourseContent> course(@PathVariable Long courseId) {
        return ApiResponse.ok(learningService.courseContent(me(), courseId));
    }

    @GetMapping("/lessons/{lessonId}")
    public ApiResponse<LessonDetail> lesson(@PathVariable Long lessonId) {
        return ApiResponse.ok(learningService.lessonDetail(me(), lessonId));
    }

    @PostMapping("/lessons/{lessonId}/progress")
    public ApiResponse<ProgressResponse> progress(@PathVariable Long lessonId, @Valid @RequestBody ProgressRequest body) {
        return ApiResponse.ok(learningService.updateProgress(me(), lessonId, body.watchedPercentage(), body.completed()));
    }

    /** Issues a short-lived ticket the &lt;video&gt; tag can use; the enrollment is checked here and again on each stream request. */
    @GetMapping("/lessons/{lessonId}/stream-ticket")
    public ApiResponse<Map<String, String>> streamTicket(@PathVariable Long lessonId, HttpServletRequest request) {
        Lesson lesson = accessibleOrAudit(me(), lessonId, "stream-ticket", request);
        if (lesson.getVideoPath() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This lesson has no video yet.");
        }
        String ticket = streamTickets.issue(lessonId, me());
        return ApiResponse.ok(Map.of(
                "ticket", ticket,
                "url", "/api/student/lessons/" + lessonId + "/video?ticket=" + ticket));
    }

    /**
     * Streams the lesson video with HTTP Range support so the player can seek. This is the one
     * student endpoint that is not bearer-authenticated (video tags cannot send headers); the
     * signed ticket identifies the user and the enrollment check runs before a single byte is read.
     */
    @GetMapping("/lessons/{lessonId}/video")
    public ResponseEntity<ResourceRegion> video(@PathVariable Long lessonId,
                                                @RequestParam("ticket") String ticket,
                                                @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
                                                HttpServletRequest request)
            throws IOException {
        StreamTicketService.Ticket t;
        try {
            t = streamTickets.verify(ticket, lessonId);
        } catch (ApiException e) {
            protectionEvents.recordUnauthorizedMedia(null, lessonId, "Invalid or expired stream ticket", RequestUtil.clientIp(request));
            throw e;
        }
        // Cross-student verification: If the caller is authenticated as another student, forbid access
        var currentAuth = SecurityContextHolder.getContext().getAuthentication();
        if (currentAuth != null && currentAuth.getPrincipal() instanceof AuthUser authUser) {
            if (!authUser.id().equals(t.userId())) {
                User user = userService.requireUser(authUser.id());
                protectionEvents.recordUnauthorizedMedia(user, lessonId,
                        "Cross-student video access attempt: caller " + authUser.id() + " used ticket for user " + t.userId(),
                        RequestUtil.clientIp(request));
                throw new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this stream ticket.");
            }
        }
        Lesson lesson = accessibleOrAudit(t.userId(), lessonId, "video", request);
        if (lesson.getVideoPath() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This lesson has no video yet.");
        }
        return VideoStreaming.partial(storage.load(lesson.getVideoPath()), rangeHeader, "private, no-store");
    }

    /** Serves the handout to the in-portal viewer only; never linkable because it needs the bearer header. */
    @GetMapping("/lessons/{lessonId}/material")
    public ResponseEntity<Resource> material(@PathVariable Long lessonId, HttpServletRequest request) {
        Lesson lesson = accessibleOrAudit(me(), lessonId, "material", request);
        if (lesson.getMaterialPath() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This lesson has no material yet.");
        }
        Resource file = storage.load(lesson.getMaterialPath());
        String name = lesson.getMaterialOriginalName() == null ? "material.pdf" : lesson.getMaterialOriginalName();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name.replace("\"", "") + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "SAMEORIGIN")
                .body(file);
    }

    /** Browser-reported protection events (screen capture, print, copy…) for the audit trail. */
    @PostMapping("/protection-events")
    public ApiResponse<Void> protectionEvent(@Valid @RequestBody ProtectionEventRequest body, HttpServletRequest request) {
        protectionEvents.recordFromBrowser(me(), body, RequestUtil.clientIp(request));
        return ApiResponse.message("Recorded.");
    }

    /** The enrollment gate, with a denied attempt written to the audit log before the 403 goes out. */
    private Lesson accessibleOrAudit(Long userId, Long lessonId, String what, HttpServletRequest request) {
        try {
            return learningService.requireAccessibleLesson(userId, lessonId);
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.FORBIDDEN) {
                User user = userService.requireUser(userId);
                protectionEvents.recordUnauthorizedMedia(user, lessonId, "Denied " + what + " request: " + e.getMessage(),
                        RequestUtil.clientIp(request));
            }
            throw e;
        }
    }

    @GetMapping("/payments")
    public ApiResponse<List<AdminPayment>> payments() {
        return ApiResponse.ok(paymentService.listForUser(me()));
    }

    private Long me() {
        return CurrentUser.require().id();
    }
}
