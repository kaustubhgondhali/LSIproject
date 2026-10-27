package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.certificate.CertificateDtos.AdminRequest;
import com.lordsai.lsi.dto.certificate.CertificateDtos.MyCourseCertificate;
import com.lordsai.lsi.dto.certificate.CertificateDtos.MyRequest;
import com.lordsai.lsi.dto.certificate.CertificateDtos.RequestCounters;
import com.lordsai.lsi.entity.Certificate;
import com.lordsai.lsi.entity.CertificateRequest;
import com.lordsai.lsi.entity.Enrollment;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.CertificateRequestStatus;
import com.lordsai.lsi.entity.enums.EnrollmentStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.CertificateRequestRepository;
import com.lordsai.lsi.repository.EnrollmentRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Course completion certificate workflow — no examination anywhere:
 * <pre>
 * course COMPLETED (enrollments.status, from lesson progress or the admin)
 *   -> student applies from Student Admin -> Certificate          (PENDING)
 *   -> admin approves: certificate generated from the master design (GENERATED)
 *      or rejects with a reason                                    (REJECTED, may apply again)
 * </pre>
 * The student's name and Student ID always come from their own account; one student can never
 * apply as, or see the request of, another. Only one active request per student and course exists
 * (enforced in code and by a unique key in the database).
 */
@Service
public class CertificateRequestService {

    /** Letters the certificate fonts can print (Latin script, accents allowed), plus space . ' - */
    static final java.util.regex.Pattern CERTIFICATE_NAME = java.util.regex.Pattern.compile("^[\\p{IsLatin}][\\p{IsLatin}\\p{M} .'-]*$");
    public static final String NAME_LENGTH = "The name on the certificate must be 2 to 150 characters.";
    public static final String NAME_CHARACTERS = "Type the name in English letters (A-Z). Only spaces, dots, apostrophes and hyphens are allowed besides letters.";
    public static final String ALREADY_SUBMITTED = "Certificate application already submitted.";

    private final CertificateRequestRepository requestRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CertificateService certificateService;
    private final CertificateNotificationService notifications;
    private final UserService userService;
    private final AuditService auditService;

    public CertificateRequestService(CertificateRequestRepository requestRepository,
                                     EnrollmentRepository enrollmentRepository,
                                     StudentProfileRepository studentProfileRepository,
                                     CertificateService certificateService,
                                     CertificateNotificationService notifications,
                                     UserService userService,
                                     AuditService auditService) {
        this.requestRepository = requestRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.certificateService = certificateService;
        this.notifications = notifications;
        this.userService = userService;
        this.auditService = auditService;
    }

    // ---- student ---------------------------------------------------------------------------

    /** Student Admin -> Certificate: every course of the student with where its certificate stands. */
    @Transactional(readOnly = true)
    public List<MyCourseCertificate> myCourses(Long studentUserId) {
        List<CertificateRequest> requests = requestRepository.findByStudentIdOrderByRequestedAtDesc(studentUserId);
        return enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(studentUserId).stream()
                .filter(e -> e.getStatus() != EnrollmentStatus.CANCELLED
                        || certificateService.findForStudentCourse(studentUserId, e.getCourse().getId()).isPresent())
                .map(e -> toMine(e, requests))
                .toList();
    }

    private MyCourseCertificate toMine(Enrollment e, List<CertificateRequest> requests) {
        Long courseId = e.getCourse().getId();
        boolean completed = e.getStatus() == EnrollmentStatus.COMPLETED;
        Optional<Certificate> certificate = certificateService.findForStudentCourse(e.getStudent().getId(), courseId);
        CertificateRequest latest = requests.stream().filter(r -> r.getCourse().getId().equals(courseId)).findFirst().orElse(null);
        String state;
        if (certificate.isPresent()) {
            state = "GENERATED";
        } else if (latest != null && latest.getStatus() == CertificateRequestStatus.PENDING) {
            state = "PENDING";
        } else if (latest != null && latest.getStatus() == CertificateRequestStatus.APPROVED) {
            state = "APPROVED";
        } else if (latest != null && latest.getStatus() == CertificateRequestStatus.REJECTED) {
            state = "REJECTED";
        } else {
            state = completed ? "ELIGIBLE" : "NOT_ELIGIBLE";
        }
        boolean canApply = completed && certificate.isEmpty()
                && (latest == null || latest.getStatus() == CertificateRequestStatus.REJECTED);
        return new MyCourseCertificate(courseId, e.getCourse().getCourseName(), e.getStatus().name(), completed,
                e.getCompletedAt(), state, canApply, latest == null ? null : toMyRequest(latest),
                certificate.map(certificateService::toMine).orElse(null));
    }

    /**
     * Creates the request for the logged-in student. The Student ID always comes from the account. The
     * name is the one the student checked in the Apply form (and corrected if it was misspelled) — that
     * is the name printed on the certificate; when none is sent, the account name is used.
     */
    @Transactional
    public MyRequest apply(Long studentUserId, Long courseId, String certificateName) {
        User student = userService.requireUser(studentUserId, Role.STUDENT);
        Enrollment enrollment = enrollmentRepository.findByStudentIdAndCourseId(studentUserId, courseId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "You are not enrolled in this course."));
        if (certificateService.findForStudentCourse(studentUserId, courseId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Your certificate for this course is already available.");
        }
        if (enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "Complete your course before applying for a certificate.");
        }
        if (requestRepository.findFirstByStudentIdAndCourseIdAndActiveSlotIsNotNull(studentUserId, courseId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_SUBMITTED);
        }
        String accountName = student.getFullName() == null ? "" : student.getFullName().trim();
        String typed = certificateName == null ? "" : certificateName.trim().replaceAll("\\s+", " ");
        if (!typed.isEmpty()) {
            if (typed.length() < 2 || typed.length() > 150) {
                throw new ApiException(HttpStatus.BAD_REQUEST, NAME_LENGTH);
            }
            if (!CERTIFICATE_NAME.matcher(typed).matches()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, NAME_CHARACTERS);
            }
        }
        String name = typed.isEmpty() ? accountName : typed;
        String studentCode = studentProfileRepository.findByUserId(studentUserId).map(StudentProfile::getStudentId).orElse(null);
        if (name.isEmpty() || studentCode == null || studentCode.isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Certificate cannot be requested because required student information is missing from your account. "
                            + "Please contact the academy.");
        }

        CertificateRequest r = new CertificateRequest();
        r.setStudent(student);
        r.setCourse(enrollment.getCourse());
        r.setEnrollment(enrollment);
        r.setStatus(CertificateRequestStatus.PENDING);
        r.setStudentName(name);
        r.setStudentCode(studentCode);
        r.setCourseName(enrollment.getCourse().getCourseName());
        r.setRequestedAt(Instant.now());
        try {
            r = requestRepository.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            // Two submissions at the same moment: the unique key let only one through.
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_SUBMITTED);
        }
        auditService.record(student, "CERTIFICATE_REQUESTED", "CertificateRequest", r.getId(),
                name + " (" + studentCode + ") — " + r.getCourseName()
                        + (name.equals(accountName) ? "" : " — name on certificate corrected by the student (account name: " + accountName + ")"), null);
        return toMyRequest(r);
    }

    // ---- admin -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<AdminRequest> search(CertificateRequestStatus status, String q, Pageable pageable) {
        String term = q == null || q.isBlank() ? null : q.trim();
        return requestRepository.search(status, term, pageable).map(this::toAdmin);
    }

    @Transactional(readOnly = true)
    public AdminRequest get(Long id) {
        return toAdmin(require(id));
    }

    @Transactional(readOnly = true)
    public RequestCounters counters() {
        return new RequestCounters(requestRepository.countByStatus(CertificateRequestStatus.PENDING),
                requestRepository.countByStatus(CertificateRequestStatus.GENERATED),
                requestRepository.countByStatus(CertificateRequestStatus.REJECTED));
    }

    /**
     * Approves a PENDING request and generates the certificate in the same transaction: if the
     * certificate cannot be generated (e.g. missing Student ID), nothing changes and the admin sees why.
     */
    @Transactional
    public AdminRequest approve(Long id, User admin, String ip) {
        CertificateRequest r = requestRepository.findForUpdate(id).orElseThrow(() -> ResourceNotFoundException.of("Certificate request", id));
        if (r.getStatus() == CertificateRequestStatus.GENERATED) {
            throw new ApiException(HttpStatus.CONFLICT, "This certificate has already been generated.");
        }
        if (r.getStatus() == CertificateRequestStatus.REJECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "This request was rejected. The student can apply again.");
        }
        if (r.getEnrollment().getStatus() != EnrollmentStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "The student's course is no longer marked as completed, so the certificate cannot be issued.");
        }
        r.setStatus(CertificateRequestStatus.APPROVED);
        r.setReviewedBy(admin);
        r.setReviewedAt(Instant.now());
        Certificate certificate = certificateService.issueForRequest(r, admin);
        // What is printed is what the admin sees on the request.
        r.setStudentName(certificate.getStudentName());
        r.setStudentCode(certificate.getStudentCode());
        r.setCertificate(certificate);
        r.setGeneratedAt(Instant.now());
        r.setStatus(CertificateRequestStatus.GENERATED);
        requestRepository.save(r);
        auditService.record(admin, "CERTIFICATE_REQUEST_APPROVED", "CertificateRequest", r.getId(),
                r.getStudentName() + " (" + r.getStudentCode() + ") — " + r.getCourseName() + " -> " + certificate.getCertificateNumber(), ip);
        notifications.certificateAvailable(r.getStudent(), r.getStudentCode(), r.getCourseName(),
                certificate.getCertificateNumber(), r.getCourse().getId());
        return toAdmin(r);
    }

    @Transactional
    public AdminRequest reject(Long id, String reason, User admin, String ip) {
        CertificateRequest r = requestRepository.findForUpdate(id).orElseThrow(() -> ResourceNotFoundException.of("Certificate request", id));
        if (r.getStatus() != CertificateRequestStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, r.getStatus() == CertificateRequestStatus.REJECTED
                    ? "This request has already been rejected." : "An approved certificate cannot be rejected.");
        }
        String clean = reason == null || reason.isBlank() ? null : reason.trim();
        r.setStatus(CertificateRequestStatus.REJECTED);
        r.setReviewedBy(admin);
        r.setReviewedAt(Instant.now());
        r.setRejectionReason(clean);
        requestRepository.save(r);
        auditService.record(admin, "CERTIFICATE_REQUEST_REJECTED", "CertificateRequest", r.getId(),
                r.getStudentName() + " (" + r.getStudentCode() + ") — " + r.getCourseName() + (clean == null ? "" : " — " + clean), ip);
        notifications.requestRejected(r.getStudent(), r.getStudentCode(), r.getCourseName(), clean, r.getCourse().getId());
        return toAdmin(r);
    }

    // ---- mapping ---------------------------------------------------------------------------

    private CertificateRequest require(Long id) {
        return requestRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Certificate request", id));
    }

    private static MyRequest toMyRequest(CertificateRequest r) {
        return new MyRequest(r.getId(), r.getStatus(), r.getStudentName(), r.getStudentCode(), r.getRequestedAt(),
                r.getReviewedAt(), r.getRejectionReason());
    }

    private AdminRequest toAdmin(CertificateRequest r) {
        Enrollment e = r.getEnrollment();
        Certificate c = r.getCertificate();
        return new AdminRequest(r.getId(), r.getStudent().getId(), r.getStudentCode(), r.getStudentName(), r.getStudent().getEmail(),
                r.getCourse().getId(), r.getCourseName(), e.getStatus().name(), e.getEnrolledAt(), e.getCompletedAt(),
                r.getStatus(), r.getRequestedAt(), r.getReviewedBy() == null ? null : r.getReviewedBy().getFullName(),
                r.getReviewedAt(), r.getRejectionReason(), c == null ? null : c.getId(),
                c == null ? null : c.getCertificateNumber(), r.getGeneratedAt(), r.getStudent().getFullName());
    }
}
