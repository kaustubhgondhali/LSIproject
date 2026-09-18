package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.payment.PaymentDtos.EnrollmentResponse;
import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.entity.Course;
import com.lordsai.lsi.entity.Enrollment;
import com.lordsai.lsi.entity.Invoice;
import com.lordsai.lsi.entity.Payment;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.EnrollmentSource;
import com.lordsai.lsi.entity.enums.EnrollmentStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.entity.enums.TokenPurpose;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.EnrollmentRepository;
import com.lordsai.lsi.repository.LessonProgressRepository;
import com.lordsai.lsi.repository.LessonRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    private final EnrollmentRepository enrollmentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final UserService userService;
    private final CourseService courseService;
    private final AccountTokenService accountTokenService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final InvoiceService invoiceService;
    private final PurchaseCommunicationService purchaseCommunication;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             StudentProfileRepository studentProfileRepository,
                             LessonRepository lessonRepository,
                             LessonProgressRepository progressRepository,
                             UserService userService,
                             CourseService courseService,
                             AccountTokenService accountTokenService,
                             EmailService emailService,
                             AuditService auditService,
                             InvoiceService invoiceService,
                             PurchaseCommunicationService purchaseCommunication) {
        this.enrollmentRepository = enrollmentRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
        this.userService = userService;
        this.courseService = courseService;
        this.accountTokenService = accountTokenService;
        this.emailService = emailService;
        this.auditService = auditService;
        this.invoiceService = invoiceService;
        this.purchaseCommunication = purchaseCommunication;
    }

    /**
     * Turns a verified COURSE payment into access. Idempotent: a repeated call for the same payment
     * finds the existing student, enrollment and invoice and changes nothing.
     * Sequence: find/create student -> enrollment -> invoice (+PDF) -> purchase email with invoice attached.
     */
    @Transactional
    public PurchaseFulfilment fulfilPayment(Payment payment) {
        log.info("[ENROLLMENT] Payment verified: order={} status={} mode={} course={} email={}",
                payment.getOrderRef(), payment.getStatus(), payment.getPaymentMode(),
                payment.getCourse().getCourseCode(), payment.getCustomerEmail());

        UserService.StudentCreation creation = userService.findOrCreateStudent(
                payment.getCustomerName(), payment.getCustomerEmail(), payment.getCustomerMobile());
        User student = creation.user();
        Course course = payment.getCourse();

        log.info("[STUDENT] {} (userId={}, status={})",
                creation.created() ? "New student created" : "Existing student found",
                student.getId(), student.getAccountStatus());
        log.info("[STUDENT] Student ID {}: {}", creation.created() ? "generated" : "reused",
                creation.profile().getStudentId());

        if (payment.getUser() == null) {
            payment.setUser(student);
        }

        Optional<Enrollment> existing = enrollmentRepository.findByStudentIdAndCourseId(student.getId(), course.getId());
        boolean newEnrollment = existing.isEmpty();
        Enrollment enrollment = existing.orElseGet(() -> {
            Enrollment e = new Enrollment();
            e.setStudent(student);
            e.setCourse(course);
            e.setPayment(payment);
            e.setSource(EnrollmentSource.PAYMENT);
            e.setStatus(EnrollmentStatus.ACTIVE);
            e.setEnrolledAt(Instant.now());
            return enrollmentRepository.save(e);
        });

        if (!newEnrollment && enrollment.getStatus() != EnrollmentStatus.ACTIVE
                && enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
            // A previously cancelled/inactive enrollment is re-activated by a fresh payment.
            enrollment.setStatus(EnrollmentStatus.ACTIVE);
            enrollment.setEnrolledAt(Instant.now());
            if (enrollment.getPayment() == null) {
                enrollment.setPayment(payment);
            }
            enrollmentRepository.save(enrollment);
        }

        log.info("[ENROLLMENT] {} (enrollmentId={}, status={})",
                newEnrollment ? "Enrollment created" : "Enrollment reused",
                enrollment.getId(), enrollment.getStatus());

        String setupLink = null;
        if (student.getAccountStatus() == AccountStatus.PENDING_SETUP) {
            setupLink = accountTokenService.issueLink(student, TokenPurpose.ACCOUNT_SETUP);
            log.info("[ACCOUNT SETUP] Setup token generated for userId={} (valid 48h)", student.getId());
        } else {
            log.info("[ACCOUNT SETUP] Skipped — account is already {}", student.getAccountStatus());
        }

        // Invoice first (one per payment, ever), then the purchase email carries it as a PDF.
        Invoice invoice = invoiceService.createForPayment(payment, student, creation.profile());
        EmailDelivery delivery = purchaseCommunication.sendPurchaseConfirmation(payment, student,
                creation.profile().getStudentId(), invoice, creation.created(), setupLink, creation.temporaryPassword());
        if (delivery.delivered()) {
            log.info("[EMAIL] Course purchase email sent to {} (invoice {})", student.getEmail(), invoice.getInvoiceNumber());
        } else {
            log.error("[EMAIL] Course purchase email NOT delivered to {} — {}: {}",
                    student.getEmail(), delivery.status(), delivery.reason());
        }

        auditService.record(null, creation.created() ? "STUDENT_CREATED_BY_PURCHASE" : "STUDENT_MATCHED_BY_PURCHASE",
                "User", student.getId(), "Payment " + payment.getOrderRef() + " for " + course.getCourseCode(), null);
        auditService.record(null, newEnrollment ? "ENROLLMENT_CREATED" : "ENROLLMENT_REUSED",
                "Enrollment", enrollment.getId(),
                student.getEmail() + " -> " + course.getCourseCode() + " via payment " + payment.getOrderRef(), null);
        if (!delivery.delivered()) {
            auditService.record(null, "ENROLLMENT_EMAIL_NOT_DELIVERED", "User", student.getId(),
                    student.getEmail() + ": " + delivery.status() + " — " + delivery.reason(), null);
        }

        return new PurchaseFulfilment(student, creation.profile(), enrollment.getId(), creation.created(), newEnrollment,
                delivery, invoice);
    }

    /**
     * Re-issues the account setup link and emails it again, with the Student ID. Creates neither a
     * student nor an enrollment — it only replaces the token, so a lost or expired link is
     * recoverable without touching the paid enrollment.
     */
    @Transactional
    public EmailDelivery resendSetupEmail(User student) {
        if (student.getAccountStatus() == AccountStatus.DISABLED) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This account is disabled. Please contact the academy.");
        }
        StudentProfile profile = userService.requireStudentProfile(student.getId());

        // An already-active account must not be handed a fresh setup token: the email then simply
        // points at the portal, and a forgotten password goes through the reset flow instead.
        String setupLink = null;
        if (student.getAccountStatus() == AccountStatus.PENDING_SETUP) {
            setupLink = accountTokenService.issueLink(student, TokenPurpose.ACCOUNT_SETUP);
            log.info("[ACCOUNT SETUP] Setup token re-issued for userId={} (previous token invalidated)", student.getId());
        }

        Enrollment latest = enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(student.getId())
                .stream().findFirst().orElse(null);
        String courseName = latest == null ? "your course" : latest.getCourse().getCourseName();
        String orderRef = latest == null || latest.getPayment() == null ? "—" : latest.getPayment().getOrderRef();
        String amount = latest == null || latest.getPayment() == null
                ? "—" : "₹" + latest.getPayment().getAmount().toPlainString();

        return sendEnrollmentEmail(student, profile.getStudentId(), courseName, orderRef, amount, setupLink);
    }

    private EmailDelivery sendEnrollmentEmail(User student, String studentId, String courseName,
                                              String orderRef, String amount, String setupLink) {
        log.info("[EMAIL] Sending enrollment email to {} (studentId={})", student.getEmail(), studentId);
        EmailDelivery delivery = emailService.sendEnrollmentEmail(student, studentId, courseName,
                orderRef, amount, setupLink);
        if (delivery.delivered()) {
            log.info("[EMAIL] Enrollment email sent successfully to {}", student.getEmail());
        } else {
            log.error("[EMAIL] Enrollment email NOT delivered to {} — {}: {}",
                    student.getEmail(), delivery.status(), delivery.reason());
        }
        return delivery;
    }

    /** Refuses to start a checkout for a student who already has access, so nobody pays twice. */
    @Transactional(readOnly = true)
    public void assertNotAlreadyEnrolled(String email, Long courseId) {
        userService.findByEmail(email).ifPresent(user -> assertNotAlreadyEnrolled(user.getId(), courseId));
    }

    /** Same check by user id (used when a logged-in student starts a checkout). */
    @Transactional(readOnly = true)
    public void assertNotAlreadyEnrolled(Long studentUserId, Long courseId) {
        enrollmentRepository.findByStudentIdAndCourseId(studentUserId, courseId)
                .filter(Enrollment::grantsAccess)
                .ifPresent(e -> {
                    throw new ApiException(HttpStatus.CONFLICT,
                            "You already have access to this course. Please log in to the Student Portal.");
                });
    }

    // ---- Admin ----------------------------------------------------------------------------

    @Transactional
    public EnrollmentResponse adminEnroll(Long studentUserId, Long courseId, LocalDate expiryDate, User actor, String ip) {
        User student = userService.requireUser(studentUserId, Role.STUDENT);
        Course course = courseService.requireCourse(courseId);
        if (enrollmentRepository.existsByStudentIdAndCourseId(studentUserId, courseId)) {
            throw new ApiException(HttpStatus.CONFLICT, "You are already enrolled in this course.");
        }
        Enrollment e = new Enrollment();
        e.setStudent(student);
        e.setCourse(course);
        e.setSource(EnrollmentSource.ADMIN_MANUAL);
        e.setStatus(EnrollmentStatus.ACTIVE);
        e.setEnrolledAt(Instant.now());
        e.setExpiryDate(expiryDate);
        e.setCreatedBy(actor);
        e = enrollmentRepository.save(e);
        auditService.record(actor, "ENROLLMENT_MANUAL_CREATED", "Enrollment", e.getId(),
                "Manually enrolled " + student.getEmail() + " in " + course.getCourseCode(), ip);
        return toResponse(e);
    }

    @Transactional
    public EnrollmentResponse setStatus(Long enrollmentId, EnrollmentStatus status, User actor, String ip) {
        Enrollment e = requireEnrollment(enrollmentId);
        EnrollmentStatus old = e.getStatus();
        e.setStatus(status);
        if (status == EnrollmentStatus.COMPLETED && e.getCompletedAt() == null) {
            e.setCompletedAt(Instant.now());
        }
        enrollmentRepository.save(e);
        auditService.record(actor, "ENROLLMENT_STATUS_CHANGED", "Enrollment", enrollmentId, old + " -> " + status, ip);
        return toResponse(e);
    }

    @Transactional(readOnly = true)
    public Page<EnrollmentResponse> listAll(Pageable pageable) {
        return enrollmentRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<EnrollmentResponse> listByCourse(Long courseId, Pageable pageable) {
        return enrollmentRepository.findByCourseId(courseId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> listForStudent(Long studentUserId) {
        return enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(studentUserId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Enrollment requireEnrollment(Long id) {
        return enrollmentRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Enrollment", id));
    }

    /** The single check every student content endpoint relies on. */
    @Transactional(readOnly = true)
    public Enrollment requireAccess(Long studentUserId, Long courseId) {
        return enrollmentRepository.findByStudentIdAndCourseId(studentUserId, courseId)
                .filter(Enrollment::grantsAccess)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN,
                        "You do not have access to this course."));
    }

    // ---- Mapping --------------------------------------------------------------------------

    public EnrollmentResponse toResponse(Enrollment e) {
        User s = e.getStudent();
        Course c = e.getCourse();
        long total = lessonRepository.countActiveByCourseId(c.getId());
        long done = progressRepository.countCompletedByStudentAndCourse(s.getId(), c.getId());
        int percent = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
        String studentId = studentProfileRepository.findByUserId(s.getId()).map(StudentProfile::getStudentId).orElse(null);
        return new EnrollmentResponse(e.getId(), s.getId(), studentId, s.getFullName(), s.getEmail(),
                c.getId(), c.getCourseCode(), c.getCourseName(), e.getStatus(), e.getSource().name(),
                e.getPayment() == null ? null : e.getPayment().getId(),
                e.getPayment() == null ? null : e.getPayment().getOrderRef(),
                e.getEnrolledAt(), e.getExpiryDate(), percent, done, total);
    }
}
