package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.admin.AdminDtos.AuditLogResponse;
import com.lordsai.lsi.dto.admin.AdminDtos.CourseHit;
import com.lordsai.lsi.dto.admin.AdminDtos.DashboardStats;
import com.lordsai.lsi.dto.admin.AdminDtos.GlobalSearchResult;
import com.lordsai.lsi.dto.admin.AdminDtos.PaymentHit;
import com.lordsai.lsi.dto.admin.AdminDtos.SessionInfo;
import com.lordsai.lsi.dto.admin.AdminDtos.StudentHit;
import com.lordsai.lsi.dto.user.UserDtos.CreateStudentRequest;
import com.lordsai.lsi.dto.user.UserDtos.StudentResponse;
import com.lordsai.lsi.dto.user.UserDtos.UpdateStudentRequest;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.entity.AuditLog;
import com.lordsai.lsi.entity.Course;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.UserSession;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.CourseStatus;
import com.lordsai.lsi.entity.enums.DoubtStatus;
import com.lordsai.lsi.entity.enums.EnrollmentStatus;
import com.lordsai.lsi.entity.enums.PaymentMode;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.entity.enums.SessionRevokeReason;
import com.lordsai.lsi.entity.enums.TokenPurpose;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.repository.AuditLogRepository;
import com.lordsai.lsi.repository.CourseRepository;
import com.lordsai.lsi.repository.DoubtRepository;
import com.lordsai.lsi.repository.EnrollmentRepository;
import com.lordsai.lsi.repository.PaymentRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Student and dashboard operations behind /api/admin/**. */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PaymentRepository paymentRepository;
    private final DoubtRepository doubtRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserService userService;
    private final SessionService sessionService;
    private final AccountTokenService accountTokenService;
    private final EnrollmentService enrollmentService;
    private final PaymentService paymentService;
    private final PaymentGatewayConfigService gatewayConfigService;
    private final ReviewService reviewService;
    private final EmailService emailService;
    private final AuditService auditService;

    public AdminService(UserRepository userRepository,
                        StudentProfileRepository studentProfileRepository,
                        CourseRepository courseRepository,
                        EnrollmentRepository enrollmentRepository,
                        PaymentRepository paymentRepository,
                        DoubtRepository doubtRepository,
                        AuditLogRepository auditLogRepository,
                        UserService userService,
                        SessionService sessionService,
                        AccountTokenService accountTokenService,
                        EnrollmentService enrollmentService,
                        PaymentService paymentService,
                        PaymentGatewayConfigService gatewayConfigService,
                        ReviewService reviewService,
                        EmailService emailService,
                        AuditService auditService) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.paymentRepository = paymentRepository;
        this.doubtRepository = doubtRepository;
        this.auditLogRepository = auditLogRepository;
        this.userService = userService;
        this.sessionService = sessionService;
        this.accountTokenService = accountTokenService;
        this.enrollmentService = enrollmentService;
        this.paymentService = paymentService;
        this.gatewayConfigService = gatewayConfigService;
        this.reviewService = reviewService;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    // ---- Dashboard -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DashboardStats dashboard() {
        var reviewCounts = reviewService.counts();
        return new DashboardStats(
                userRepository.countByRole(Role.STUDENT),
                userRepository.countByRoleAndAccountStatus(Role.STUDENT, AccountStatus.ACTIVE),
                userRepository.countByRoleAndAccountStatus(Role.STUDENT, AccountStatus.PENDING_SETUP),
                userRepository.countByRoleAndAccountStatus(Role.STUDENT, AccountStatus.DISABLED),
                courseRepository.count(),
                courseRepository.countByStatus(CourseStatus.ACTIVE),
                enrollmentRepository.count(),
                enrollmentRepository.countByStatus(EnrollmentStatus.ACTIVE),
                paymentRepository.count(),
                paymentRepository.countByStatus(PaymentStatus.SUCCESS),
                gatewayConfigService.currentMode().name(),
                paymentRepository.countByStatusAndPaymentMode(PaymentStatus.SUCCESS, PaymentMode.DEMO),
                paymentRepository.countByStatusAndPaymentMode(PaymentStatus.SUCCESS, PaymentMode.RAZORPAY),
                paymentRepository.countByStatus(PaymentStatus.CREATED) + paymentRepository.countByStatus(PaymentStatus.PENDING),
                paymentRepository.countByStatus(PaymentStatus.FAILED),
                paymentRepository.countByStatus(PaymentStatus.REFUNDED),
                paymentRepository.sumAmountByStatus(PaymentStatus.SUCCESS),
                doubtRepository.countByStatus(DoubtStatus.OPEN) + doubtRepository.countByStatus(DoubtStatus.IN_PROGRESS),
                reviewCounts.pending(),
                reviewCounts.approved(),
                reviewCounts.declined(),
                enrollmentRepository.findTop10ByOrderByEnrolledAtDesc().stream().map(enrollmentService::toResponse).toList(),
                paymentRepository.findTop10ByOrderByCreatedAtDesc().stream().map(paymentService::toAdmin).toList());
    }

    @Transactional(readOnly = true)
    public GlobalSearchResult search(String term) {
        String q = term == null ? "" : term.trim();
        if (q.length() < 2) {
            return new GlobalSearchResult(List.of(), List.of(), List.of());
        }
        Pageable top = PageRequest.of(0, 8);
        List<StudentHit> students = userRepository.searchByRole(Role.STUDENT, q, top).stream()
                .map(u -> new StudentHit(u.getId(), studentIdOf(u), u.getFullName(), u.getEmail(), u.getAccountStatus().name()))
                .toList();
        // Also match on the student ID itself.
        studentProfileRepository.findByStudentIdIgnoreCase(q).ifPresent(p -> {
            if (students.stream().noneMatch(s -> s.id().equals(p.getUser().getId()))) {
                students.add(0, new StudentHit(p.getUser().getId(), p.getStudentId(), p.getUser().getFullName(),
                        p.getUser().getEmail(), p.getUser().getAccountStatus().name()));
            }
        });
        List<CourseHit> courses = courseRepository.findAll().stream()
                .filter(c -> c.getCourseName().toLowerCase().contains(q.toLowerCase())
                        || c.getCourseCode().toLowerCase().contains(q.toLowerCase()))
                .limit(8)
                .map(c -> new CourseHit(c.getId(), c.getCourseCode(), c.getCourseName(), c.getStatus().name()))
                .toList();
        List<PaymentHit> payments = paymentRepository.findAll().stream()
                .filter(p -> (p.getOrderRef() != null && p.getOrderRef().toLowerCase().contains(q.toLowerCase()))
                        || (p.getRazorpayPaymentId() != null && p.getRazorpayPaymentId().toLowerCase().contains(q.toLowerCase()))
                        || p.getCustomerEmail().toLowerCase().contains(q.toLowerCase()))
                .limit(8)
                .map(p -> new PaymentHit(p.getId(), p.getOrderRef(), p.getRazorpayPaymentId(), p.getCustomerEmail(),
                        p.getStatus().name(), p.getAmount()))
                .toList();
        return new GlobalSearchResult(new java.util.ArrayList<>(students), courses, payments);
    }

    // ---- Students --------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<StudentResponse> students(String search, AccountStatus status, Pageable pageable) {
        Page<User> page = (search == null || search.isBlank())
                ? userRepository.findByRole(Role.STUDENT, pageable)
                : userRepository.searchByRole(Role.STUDENT, search.trim(), pageable);
        Page<StudentResponse> mapped = page.map(this::toStudent);
        if (status == null) {
            return mapped;
        }
        List<StudentResponse> filtered = mapped.getContent().stream().filter(s -> s.accountStatus() == status).toList();
        return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public StudentResponse student(Long id) {
        return toStudent(userService.requireUser(id, Role.STUDENT));
    }

    @Transactional
    public StudentResponse createStudent(CreateStudentRequest req, User actor, String ip) {
        UserService.StudentCreation c = userService.createStudentByAdmin(req.fullName(), req.email(), req.mobile(),
                req.batch(), req.location());
        String link = accountTokenService.issueLink(c.user(), TokenPurpose.ACCOUNT_SETUP);
        emailService.sendWelcomeEmail(c.user(), link);
        auditService.record(actor, "STUDENT_CREATED", "User", c.user().getId(),
                "Created student " + c.profile().getStudentId() + " (" + c.user().getEmail() + ")", ip);
        return toStudent(c.user());
    }

    @Transactional
    public StudentResponse updateStudent(Long id, UpdateStudentRequest req, User actor, String ip) {
        User user = userService.requireUser(id, Role.STUDENT);
        String before = user.getFullName() + " <" + user.getEmail() + "> " + user.getMobile();
        userService.updateContact(user, req.fullName(), req.email(), req.mobile());
        StudentProfile profile = userService.requireStudentProfile(id);
        profile.setBatch(req.batch());
        profile.setLocation(req.location());
        studentProfileRepository.save(profile);
        auditService.record(actor, "STUDENT_UPDATED", "User", id,
                before + " -> " + user.getFullName() + " <" + user.getEmail() + "> " + user.getMobile(), ip);
        return toStudent(user);
    }

    @Transactional
    public StudentResponse setStudentStatus(Long id, AccountStatus status, User actor, String ip) {
        User user = userService.requireUser(id, Role.STUDENT);
        AccountStatus old = user.getAccountStatus();
        user.setAccountStatus(status);
        userRepository.save(user);
        if (status == AccountStatus.DISABLED) {
            sessionService.revokeAll(id, SessionRevokeReason.ACCOUNT_DISABLED);
        }
        auditService.record(actor, status == AccountStatus.DISABLED ? "STUDENT_DEACTIVATED" : "STUDENT_ACTIVATED",
                "User", id, user.getEmail() + ": " + old + " -> " + status, ip);
        return toStudent(user);
    }

    @Transactional
    public void forceLogout(Long id, User actor, String ip) {
        User user = userService.requireUser(id);
        int n = sessionService.revokeAll(id, SessionRevokeReason.ADMIN_LOGOUT);
        auditService.record(actor, "USER_FORCE_LOGOUT", "User", id, user.getEmail() + ": " + n + " session(s) ended", ip);
    }

    /** Sends a reset link rather than setting a password the admin would know. */
    @Transactional
    public void sendPasswordReset(Long id, User actor, String ip) {
        User user = userService.requireUser(id);
        if (user.getAccountStatus() == AccountStatus.DISABLED) {
            throw new ApiException(HttpStatus.CONFLICT, "Activate the account before sending a reset link.");
        }
        TokenPurpose purpose = user.getAccountStatus() == AccountStatus.PENDING_SETUP
                ? TokenPurpose.ACCOUNT_SETUP : TokenPurpose.PASSWORD_RESET;
        String link = accountTokenService.issueLink(user, purpose);
        if (purpose == TokenPurpose.ACCOUNT_SETUP) {
            emailService.sendWelcomeEmail(user, link);
        } else {
            emailService.sendPasswordResetEmail(user, link);
        }
        sessionService.revokeAll(id, SessionRevokeReason.ADMIN_LOGOUT);
        auditService.record(actor, "PASSWORD_RESET_SENT_BY_ADMIN", "User", id, user.getEmail(), ip);
    }

    /** Deleting keeps financial history intact: accounts with payments are deactivated instead. */
    @Transactional
    public void deleteStudent(Long id, User actor, String ip) {
        User user = userService.requireUser(id, Role.STUDENT);
        boolean hasHistory = !paymentRepository.findByUserIdOrderByCreatedAtDesc(id).isEmpty()
                || !enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(id).isEmpty();
        if (hasHistory) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This student has enrollment or payment history and cannot be deleted. Deactivate the account instead.");
        }
        sessionService.revokeAll(id, SessionRevokeReason.ACCOUNT_DISABLED);
        studentProfileRepository.findByUserId(id).ifPresent(studentProfileRepository::delete);
        auditService.record(actor, "STUDENT_DELETED", "User", id, "Deleted " + user.getEmail(), ip);
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public List<SessionInfo> sessions(Long userId) {
        return sessionService.history(userId).stream().map(this::toSession).toList();
    }

    // ---- Audit -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> auditLogs(Long actorId, String entityType, Long entityId, Pageable pageable) {
        Page<AuditLog> page;
        if (actorId != null) {
            page = auditLogRepository.findByActorIdOrderByCreatedAtDesc(actorId, pageable);
        } else if (entityType != null && entityId != null) {
            page = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable);
        } else {
            page = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return page.map(a -> new AuditLogResponse(a.getId(),
                a.getActor() == null ? null : a.getActor().getId(),
                a.getActor() == null ? "System" : a.getActor().getFullName(),
                a.getActor() == null ? null : a.getActor().getRole().name(),
                a.getAction(), a.getEntityType(), a.getEntityId(), a.getDescription(), a.getIpAddress(), a.getCreatedAt()));
    }

    // ---- Mapping ---------------------------------------------------------------------------

    public StudentResponse toStudent(User u) {
        StudentProfile p = studentProfileRepository.findByUserId(u.getId()).orElse(null);
        boolean live = sessionService.history(u.getId()).stream().anyMatch(s -> s.isActive() && !s.isExpired());
        return new StudentResponse(u.getId(), p == null ? null : p.getStudentId(), u.getFullName(), u.getEmail(),
                u.getMobile(), p == null ? null : p.getBatch(), p == null ? null : p.getLocation(),
                u.getAccountStatus(), p == null ? null : p.getRegistrationDate(), u.getLastLoginAt(), u.getCreatedAt(),
                enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(u.getId()).size(), live);
    }

    private SessionInfo toSession(UserSession s) {
        return new SessionInfo(s.getId(), s.getDeviceInfo(), s.getIpAddress(), s.getCreatedAt(), s.getLastActivityAt(),
                s.getExpiresAt(), s.isActive() && !s.isExpired(),
                s.getRevokeReason() == null ? null : s.getRevokeReason().name());
    }

    private String studentIdOf(User u) {
        return studentProfileRepository.findByUserId(u.getId()).map(StudentProfile::getStudentId).orElse(null);
    }

    static Instant now() {
        return Instant.now();
    }
}
