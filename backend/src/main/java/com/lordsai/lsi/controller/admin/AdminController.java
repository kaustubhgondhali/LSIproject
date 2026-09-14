package com.lordsai.lsi.controller.admin;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.admin.AdminDtos.AuditLogResponse;
import com.lordsai.lsi.dto.admin.AdminDtos.DashboardStats;
import com.lordsai.lsi.dto.admin.AdminDtos.GlobalSearchResult;
import com.lordsai.lsi.dto.admin.AdminDtos.SessionInfo;
import com.lordsai.lsi.dto.payment.PaymentDtos.AdminPayment;
import com.lordsai.lsi.dto.payment.PaymentDtos.EnrollmentResponse;
import com.lordsai.lsi.dto.payment.PaymentDtos.EnrollmentStatusRequest;
import com.lordsai.lsi.dto.payment.PaymentDtos.ManualEnrollRequest;
import com.lordsai.lsi.dto.support.SupportDtos.DoubtDetail;
import com.lordsai.lsi.dto.support.SupportDtos.DoubtReplyRequest;
import com.lordsai.lsi.dto.support.SupportDtos.DoubtStatusRequest;
import com.lordsai.lsi.dto.support.SupportDtos.DoubtSummary;
import com.lordsai.lsi.dto.user.UserDtos.CreateStudentRequest;
import com.lordsai.lsi.dto.user.UserDtos.StudentResponse;
import com.lordsai.lsi.dto.user.UserDtos.UpdateStudentRequest;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.DoubtStatus;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.AdminService;
import com.lordsai.lsi.service.DoubtService;
import com.lordsai.lsi.service.EnrollmentService;
import com.lordsai.lsi.service.PaymentService;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final EnrollmentService enrollmentService;
    private final PaymentService paymentService;
    private final DoubtService doubtService;
    private final UserService userService;

    public AdminController(AdminService adminService,
                           EnrollmentService enrollmentService,
                           PaymentService paymentService,
                           DoubtService doubtService,
                           UserService userService) {
        this.adminService = adminService;
        this.enrollmentService = enrollmentService;
        this.paymentService = paymentService;
        this.doubtService = doubtService;
        this.userService = userService;
    }

    // ---- Dashboard & search ----------------------------------------------------------------

    @GetMapping("/dashboard")
    public ApiResponse<DashboardStats> dashboard() {
        return ApiResponse.ok(adminService.dashboard());
    }

    @GetMapping("/search")
    public ApiResponse<GlobalSearchResult> search(@RequestParam("q") String q) {
        return ApiResponse.ok(adminService.search(q));
    }

    // ---- Students --------------------------------------------------------------------------

    @GetMapping("/students")
    public ApiResponse<Page<StudentResponse>> students(@RequestParam(required = false) String search,
                                                       @RequestParam(required = false) AccountStatus status,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.students(search, status, PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending())));
    }

    @GetMapping("/students/{id}")
    public ApiResponse<StudentResponse> student(@PathVariable Long id) {
        return ApiResponse.ok(adminService.student(id));
    }

    @PostMapping("/students")
    public ApiResponse<StudentResponse> createStudent(@Valid @RequestBody CreateStudentRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Student created. A password setup email has been sent.",
                adminService.createStudent(body, actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/students/{id}")
    public ApiResponse<StudentResponse> updateStudent(@PathVariable Long id, @Valid @RequestBody UpdateStudentRequest body,
                                                      HttpServletRequest req) {
        return ApiResponse.ok("Student updated.", adminService.updateStudent(id, body, actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/students/{id}/status")
    public ApiResponse<StudentResponse> studentStatus(@PathVariable Long id, @RequestParam AccountStatus status,
                                                      HttpServletRequest req) {
        return ApiResponse.ok("Status updated.", adminService.setStudentStatus(id, status, actor(), RequestUtil.clientIp(req)));
    }

    @PostMapping("/students/{id}/force-logout")
    public ApiResponse<Void> forceLogout(@PathVariable Long id, HttpServletRequest req) {
        adminService.forceLogout(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("The student has been logged out of all devices.");
    }

    @PostMapping("/students/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, HttpServletRequest req) {
        adminService.sendPasswordReset(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("A password link has been emailed to the student.");
    }

    @DeleteMapping("/students/{id}")
    public ApiResponse<Void> deleteStudent(@PathVariable Long id, HttpServletRequest req) {
        adminService.deleteStudent(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Student deleted.");
    }

    @GetMapping("/students/{id}/enrollments")
    public ApiResponse<List<EnrollmentResponse>> studentEnrollments(@PathVariable Long id) {
        return ApiResponse.ok(enrollmentService.listForStudent(id));
    }

    @GetMapping("/students/{id}/payments")
    public ApiResponse<List<AdminPayment>> studentPayments(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.listForUser(id));
    }

    @GetMapping("/students/{id}/sessions")
    public ApiResponse<List<SessionInfo>> studentSessions(@PathVariable Long id) {
        return ApiResponse.ok(adminService.sessions(id));
    }


    // ---- Enrollments -----------------------------------------------------------------------

    @GetMapping("/enrollments")
    public ApiResponse<Page<EnrollmentResponse>> enrollments(@RequestParam(required = false) Long courseId,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("enrolledAt").descending());
        return ApiResponse.ok(courseId == null ? enrollmentService.listAll(pageable) : enrollmentService.listByCourse(courseId, pageable));
    }

    @PostMapping("/enrollments")
    public ApiResponse<EnrollmentResponse> manualEnroll(@Valid @RequestBody ManualEnrollRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Student enrolled (manual).",
                enrollmentService.adminEnroll(body.studentUserId(), body.courseId(), body.expiryDate(), actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/enrollments/{id}/status")
    public ApiResponse<EnrollmentResponse> enrollmentStatus(@PathVariable Long id, @Valid @RequestBody EnrollmentStatusRequest body,
                                                            HttpServletRequest req) {
        return ApiResponse.ok("Enrollment updated.", enrollmentService.setStatus(id, body.status(), actor(), RequestUtil.clientIp(req)));
    }

    // ---- Payments --------------------------------------------------------------------------

    @GetMapping("/payments")
    public ApiResponse<Page<AdminPayment>> payments(@RequestParam(required = false) PaymentStatus status,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(paymentService.listAdmin(status, PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending())));
    }

    @GetMapping("/payments/{id}")
    public ApiResponse<AdminPayment> payment(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.getAdmin(id));
    }

    // ---- Doubts (admin answers the doubt desk) ---------------------------------------------

    @GetMapping("/doubts")
    public ApiResponse<Page<DoubtSummary>> doubts(@RequestParam(required = false) DoubtStatus status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(doubtService.queue(status, PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending())));
    }

    @GetMapping("/doubts/{id}")
    public ApiResponse<DoubtDetail> doubt(@PathVariable Long id) {
        return ApiResponse.ok(doubtService.detail(id));
    }

    @PostMapping("/doubts/{id}/replies")
    public ApiResponse<DoubtDetail> replyDoubt(@PathVariable Long id, @Valid @RequestBody DoubtReplyRequest body) {
        return ApiResponse.ok(doubtService.reply(CurrentUser.require().id(), id, body.message()));
    }

    @PatchMapping("/doubts/{id}/status")
    public ApiResponse<DoubtDetail> doubtStatus(@PathVariable Long id, @Valid @RequestBody DoubtStatusRequest body,
                                                HttpServletRequest req) {
        return ApiResponse.ok(doubtService.setStatus(id, body.status(), actor(), RequestUtil.clientIp(req)));
    }

    // ---- Audit logs ------------------------------------------------------------------------

    @GetMapping("/audit-logs")
    public ApiResponse<Page<AuditLogResponse>> auditLogs(@RequestParam(required = false) Long actorId,
                                                         @RequestParam(required = false) String entityType,
                                                         @RequestParam(required = false) Long entityId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(adminService.auditLogs(actorId, entityType, entityId, PageRequest.of(page, Math.min(size, 200))));
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
