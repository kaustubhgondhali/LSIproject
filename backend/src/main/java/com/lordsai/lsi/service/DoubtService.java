package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.support.SupportDtos.DoubtDetail;
import com.lordsai.lsi.dto.support.SupportDtos.DoubtRequest;
import com.lordsai.lsi.dto.support.SupportDtos.DoubtSummary;
import com.lordsai.lsi.dto.support.SupportDtos.Reply;
import com.lordsai.lsi.entity.Doubt;
import com.lordsai.lsi.entity.DoubtReply;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.DoubtStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.DoubtReplyRepository;
import com.lordsai.lsi.repository.DoubtRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class DoubtService {

    private final DoubtRepository doubtRepository;
    private final DoubtReplyRepository replyRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserService userService;
    private final CourseService courseService;
    private final CurriculumService curriculumService;
    private final EnrollmentService enrollmentService;
    private final AuditService auditService;

    public DoubtService(DoubtRepository doubtRepository,
                        DoubtReplyRepository replyRepository,
                        StudentProfileRepository studentProfileRepository,
                        UserService userService,
                        CourseService courseService,
                        CurriculumService curriculumService,
                        EnrollmentService enrollmentService,
                        AuditService auditService) {
        this.doubtRepository = doubtRepository;
        this.replyRepository = replyRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.userService = userService;
        this.courseService = courseService;
        this.curriculumService = curriculumService;
        this.enrollmentService = enrollmentService;
        this.auditService = auditService;
    }

    // ---- Student ---------------------------------------------------------------------------

    @Transactional
    public DoubtSummary create(Long studentUserId, DoubtRequest req) {
        Doubt d = new Doubt();
        d.setStudent(userService.requireUser(studentUserId));
        d.setTitle(req.title().trim());
        d.setDescription(req.description().trim());
        if (req.courseId() != null) {
            // A doubt can only reference a course the student is actually enrolled in.
            enrollmentService.requireAccess(studentUserId, req.courseId());
            d.setCourse(courseService.requireCourse(req.courseId()));
        }
        if (req.lessonId() != null) {
            var lesson = curriculumService.requireLesson(req.lessonId());
            enrollmentService.requireAccess(studentUserId, lesson.getModule().getCourse().getId());
            d.setLesson(lesson);
            if (d.getCourse() == null) {
                d.setCourse(lesson.getModule().getCourse());
            }
        }
        return toSummary(doubtRepository.save(d));
    }

    @Transactional(readOnly = true)
    public Page<DoubtSummary> mine(Long studentUserId, Pageable pageable) {
        return doubtRepository.findByStudentIdOrderByCreatedAtDesc(studentUserId, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public DoubtDetail mineDetail(Long studentUserId, Long id) {
        return toDetail(doubtRepository.findByIdAndStudentId(id, studentUserId)
                .orElseThrow(() -> ResourceNotFoundException.of("Doubt", id)));
    }

    @Transactional
    public DoubtDetail reply(Long authorUserId, Long doubtId, String message) {
        User author = userService.requireUser(authorUserId);
        Doubt d = doubtRepository.findById(doubtId).orElseThrow(() -> ResourceNotFoundException.of("Doubt", doubtId));
        if (author.getRole() == Role.STUDENT && !d.getStudent().getId().equals(authorUserId)) {
            throw ResourceNotFoundException.of("Doubt", doubtId);
        }
        if (d.getStatus() == DoubtStatus.RESOLVED && author.getRole() == Role.STUDENT) {
            throw new ApiException(HttpStatus.CONFLICT, "This doubt is resolved. Please open a new doubt if you have a follow-up question.");
        }
        DoubtReply r = new DoubtReply();
        r.setDoubt(d);
        r.setAuthor(author);
        r.setMessage(message.trim());
        replyRepository.save(r);

        if (author.getRole() != Role.STUDENT && d.getStatus() == DoubtStatus.OPEN) {
            d.setStatus(DoubtStatus.IN_PROGRESS);
            doubtRepository.save(d);
        }
        return toDetail(d);
    }

    // ---- Mentor / admin --------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<DoubtSummary> queue(DoubtStatus status, Pageable pageable) {
        Page<Doubt> page = status == null ? doubtRepository.findAll(pageable)
                : doubtRepository.findByStatusOrderByCreatedAtAsc(status, pageable);
        return page.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public DoubtDetail detail(Long id) {
        return toDetail(doubtRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Doubt", id)));
    }

    @Transactional
    public DoubtDetail setStatus(Long id, DoubtStatus status, User actor, String ip) {
        Doubt d = doubtRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Doubt", id));
        d.setStatus(status);
        d.setResolvedAt(status == DoubtStatus.RESOLVED ? Instant.now() : null);
        doubtRepository.save(d);
        auditService.record(actor, "DOUBT_STATUS_CHANGED", "Doubt", id, "'" + d.getTitle() + "' -> " + status, ip);
        return toDetail(d);
    }

    // ---- Mapping ---------------------------------------------------------------------------

    private DoubtSummary toSummary(Doubt d) {
        User s = d.getStudent();
        return new DoubtSummary(d.getId(), s.getId(), s.getFullName(), studentIdOf(s),
                d.getCourse() == null ? null : d.getCourse().getId(),
                d.getCourse() == null ? null : d.getCourse().getCourseName(),
                d.getLesson() == null ? null : d.getLesson().getId(),
                d.getLesson() == null ? null : d.getLesson().getLessonTitle(),
                d.getTitle(), d.getDescription(), d.getStatus(),
                replyRepository.findByDoubtIdOrderByCreatedAtAsc(d.getId()).size(),
                d.getCreatedAt(), d.getUpdatedAt(), d.getResolvedAt());
    }

    private DoubtDetail toDetail(Doubt d) {
        User s = d.getStudent();
        List<Reply> replies = replyRepository.findByDoubtIdOrderByCreatedAtAsc(d.getId()).stream()
                .map(r -> new Reply(r.getId(), r.getAuthor().getId(), r.getAuthor().getFullName(),
                        r.getAuthor().getRole().name(), r.getMessage(), r.getCreatedAt()))
                .toList();
        return new DoubtDetail(d.getId(), s.getId(), s.getFullName(), studentIdOf(s),
                d.getCourse() == null ? null : d.getCourse().getId(),
                d.getCourse() == null ? null : d.getCourse().getCourseName(),
                d.getLesson() == null ? null : d.getLesson().getId(),
                d.getLesson() == null ? null : d.getLesson().getLessonTitle(),
                d.getTitle(), d.getDescription(), d.getAttachmentPath(), d.getStatus(),
                d.getCreatedAt(), d.getResolvedAt(), replies);
    }

    private String studentIdOf(User s) {
        return studentProfileRepository.findByUserId(s.getId()).map(StudentProfile::getStudentId).orElse(null);
    }
}
