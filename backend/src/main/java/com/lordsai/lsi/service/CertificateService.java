package com.lordsai.lsi.service;

import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.dto.certificate.CertificateDtos.AdminCertificate;
import com.lordsai.lsi.dto.certificate.CertificateDtos.MyCertificate;
import com.lordsai.lsi.dto.certificate.CertificateDtos.Verification;
import com.lordsai.lsi.entity.Certificate;
import com.lordsai.lsi.entity.CertificateRequest;
import com.lordsai.lsi.entity.CertificateSequence;
import com.lordsai.lsi.entity.Enrollment;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.CertificateRepository;
import com.lordsai.lsi.repository.CertificateSequenceRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Issues and serves course completion certificates. A certificate is created only by
 * {@link CertificateRequestService#approve} — i.e. a completed course, a student request and an
 * admin approval; there is no exam and no other entry point. The PDF is drawn on the one master
 * Lord Sai design by {@link CertificatePdfService}, stored under the protected "certificates/"
 * folder and streamed only to its owner or an admin.
 */
@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);
    public static final String PREFIX = "LSI-CERT";
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter ISSUE_MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O, 1/I lookalikes
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Stored PDFs drawn before this moment use an older design (with the QR code) and are re-drawn. */
    private static final Instant CURRENT_DESIGN_SINCE = Instant.parse("2026-10-14T01:30:00Z");

    private final CertificateRepository certificateRepository;
    private final CertificateSequenceRepository sequenceRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CertificatePdfService pdfService;
    private final FileStorageService storage;
    private final AuditService auditService;
    private final AppProperties properties;

    public CertificateService(CertificateRepository certificateRepository,
                              CertificateSequenceRepository sequenceRepository,
                              StudentProfileRepository studentProfileRepository,
                              CertificatePdfService pdfService,
                              FileStorageService storage,
                              AuditService auditService,
                              AppProperties properties) {
        this.certificateRepository = certificateRepository;
        this.sequenceRepository = sequenceRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.pdfService = pdfService;
        this.storage = storage;
        this.auditService = auditService;
        this.properties = properties;
    }

    // ---- issuing ---------------------------------------------------------------------------

    /**
     * Generates the certificate for an approved request from the student's REAL registered details
     * (the account's current name and Student ID, the enrollment's start / completion dates). Missing
     * data stops generation with a clear message; nothing is ever filled in with defaults. A student
     * who already holds a certificate for the course (issued earlier) gets that one, not a duplicate.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Certificate issueForRequest(CertificateRequest request, User approvedBy) {
        User student = request.getStudent();
        Optional<Certificate> existing = certificateRepository
                .findFirstByStudentIdAndCourseIdOrderByIssuedAtDesc(student.getId(), request.getCourse().getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        Enrollment enrollment = request.getEnrollment();
        String studentCode = studentProfileRepository.findByUserId(student.getId())
                .map(StudentProfile::getStudentId).orElse(null);
        // The name the student confirmed when applying (shown to the admin on the request); older
        // requests without one fall back to the account name.
        String name = blank(request.getStudentName()) ? student.getFullName() : request.getStudentName();
        String courseName = request.getCourse().getCourseName();
        List<String> missing = new java.util.ArrayList<>();
        if (blank(name)) missing.add("student name");
        if (blank(studentCode)) missing.add("Student ID");
        if (blank(courseName)) missing.add("course name");
        if (enrollment.getEnrolledAt() == null) missing.add("course start date");
        if (enrollment.getCompletedAt() == null) missing.add("course completion date");
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Certificate cannot be generated because required student/course information is missing: "
                            + String.join(", ", missing) + ".");
        }

        Certificate c = new Certificate();
        c.setCertificateNumber(nextNumber());
        c.setStudent(student);
        c.setCourse(request.getCourse());
        c.setStudentName(name.trim());
        c.setStudentCode(studentCode.trim());
        c.setCourseName(courseName.trim());
        c.setCourseStartedAt(enrollment.getEnrolledAt());
        c.setCourseCompletedAt(enrollment.getCompletedAt());
        c.setCoursePeriod(period(enrollment.getEnrolledAt(), enrollment.getCompletedAt()));
        c.setVerificationCode(newVerificationCode());
        c.setIssuedAt(Instant.now());
        c = certificateRepository.save(c);
        renderAndStore(c); // a failure here rolls the whole approval back: no certificate without its PDF

        auditService.record(approvedBy, "CERTIFICATE_ISSUED", "Certificate", c.getId(),
                c.getCertificateNumber() + " for " + c.getStudentName() + " (" + c.getStudentCode() + ") — "
                        + c.getCourseName() + " (" + c.getCoursePeriod() + ")", null);
        log.info("[CERTIFICATE] Issued {} to {} for {}", c.getCertificateNumber(), student.getEmail(), c.getCourseName());
        return c;
    }

    private String nextNumber() {
        int year = LocalDate.now(INDIA).getYear();
        CertificateSequence seq = sequenceRepository.findForUpdate(year)
                .orElseGet(() -> sequenceRepository.saveAndFlush(new CertificateSequence(year)));
        seq.setLastNumber(seq.getLastNumber() + 1);
        sequenceRepository.save(seq);
        return String.format("%s-%d-%06d", PREFIX, year, seq.getLastNumber());
    }

    private String newVerificationCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(12);
            for (int i = 0; i < 12; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (certificateRepository.existsByVerificationCode(code));
        return code;
    }

    /** "January to April 2026", "November 2025 to April 2026" or "April 2026" (same month). */
    static String period(Instant start, Instant end) {
        YearMonth from = YearMonth.from(start.atZone(INDIA));
        YearMonth to = YearMonth.from(end.atZone(INDIA));
        if (to.isBefore(from)) {
            to = from;
        }
        String toText = month(to) + " " + to.getYear();
        if (from.equals(to)) {
            return toText;
        }
        return (from.getYear() == to.getYear() ? month(from) : month(from) + " " + from.getYear()) + " to " + toText;
    }

    private static String month(YearMonth ym) {
        return ym.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
    }

    // ---- PDF -------------------------------------------------------------------------------

    /**
     * The stored PDF. Re-drawn when the file is missing, was drawn with an older certificate design,
     * or when a legacy certificate still has no verification code (so the public verify link exists).
     */
    @Transactional
    public byte[] pdfBytes(Certificate certificate) {
        boolean hasFile = certificate.getPdfPath() != null && storage.exists(certificate.getPdfPath());
        boolean currentDesign = certificate.getPdfGeneratedAt() != null
                && !certificate.getPdfGeneratedAt().isBefore(CURRENT_DESIGN_SINCE);
        if (hasFile && currentDesign && certificate.getVerificationCode() != null) {
            return storage.readBytes(certificate.getPdfPath());
        }
        return renderAndStore(certificate);
    }

    private byte[] renderAndStore(Certificate c) {
        if (c.getVerificationCode() == null) { // certificates issued before QR verification existed
            c.setVerificationCode(newVerificationCode());
        }
        if (c.getCoursePeriod() == null) {
            Instant end = c.getCourseCompletedAt() != null ? c.getCourseCompletedAt() : c.getIssuedAt();
            c.setCoursePeriod(period(c.getCourseStartedAt() != null ? c.getCourseStartedAt() : end, end));
        }
        byte[] pdf = pdfService.render(new CertificatePdfService.Content(c.getStudentName(), c.getStudentCode(),
                c.getCourseName(), c.getCoursePeriod(), ISSUE_MONTH.format(c.getIssuedAt().atZone(INDIA)), verifyUrl(c)));
        String previous = c.getPdfPath();
        c.setPdfPath(storage.storeBytes(pdf, FileStorageService.Kind.CERTIFICATE, "pdf"));
        c.setPdfGeneratedAt(Instant.now());
        certificateRepository.save(c);
        if (previous != null) {
            storage.deleteQuietly(previous);
        }
        return pdf;
    }

    /** The QR target: the public verification page for this certificate's random code. */
    public String verifyUrl(Certificate c) {
        String base = properties.publicBaseUrl() == null ? "" : properties.publicBaseUrl().replaceAll("/+$", "");
        return base + "/verify-certificate.html?c=" + c.getVerificationCode();
    }

    /** Download name, e.g. "LSA-Certificate-Jostna-Mhatre.pdf" — letters, digits and hyphens only. */
    public static String downloadName(Certificate c) {
        String name = c.getStudentName() == null ? "" : c.getStudentName().trim()
                .replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return "LSA-Certificate-" + (name.isEmpty() ? c.getCertificateNumber() : name) + ".pdf";
    }

    // ---- public verification ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public Verification verify(String code) {
        String clean = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!clean.matches("[A-Z0-9]{6,40}")) {
            return new Verification(false, null, null, null, null, null);
        }
        return certificateRepository.findByVerificationCode(clean)
                .map(c -> new Verification(true, c.getCertificateNumber(), c.getStudentName(), c.getCourseName(),
                        c.getCoursePeriod(), LONG_DATE.format(c.getIssuedAt().atZone(INDIA))))
                .orElseGet(() -> new Verification(false, null, null, null, null, null));
    }

    // ---- lookup & ownership ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public Certificate require(Long id) {
        return certificateRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Certificate", id));
    }

    /** Student A can never resolve Student B's certificate: the lookup itself is scoped to the owner. */
    @Transactional(readOnly = true)
    public Certificate requireOwned(Long id, Long studentUserId) {
        return certificateRepository.findByIdAndStudentId(id, studentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this certificate."));
    }

    @Transactional(readOnly = true)
    public List<MyCertificate> listForStudent(Long studentUserId) {
        return certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentUserId).stream().map(this::toMine).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Certificate> findForStudentCourse(Long studentUserId, Long courseId) {
        return certificateRepository.findFirstByStudentIdAndCourseIdOrderByIssuedAtDesc(studentUserId, courseId);
    }

    @Transactional(readOnly = true)
    public Page<AdminCertificate> search(Long courseId, String q, Pageable pageable) {
        String term = q == null || q.isBlank() ? null : q.trim();
        return certificateRepository.search(courseId, term, pageable).map(this::toAdmin);
    }

    public MyCertificate toMine(Certificate c) {
        return new MyCertificate(c.getId(), c.getCertificateNumber(), c.getCourse().getId(), c.getCourseName(),
                c.getStudentName(), c.getStudentCode(), c.getCoursePeriod(), c.getIssuedAt(), c.getCourseCompletedAt());
    }

    public AdminCertificate toAdmin(Certificate c) {
        return new AdminCertificate(c.getId(), c.getCertificateNumber(), c.getStudent().getId(), c.getStudentCode(),
                c.getStudentName(), c.getCourse().getId(), c.getCourseName(), c.getCoursePeriod(), c.getIssuedAt(),
                c.getPdfPath() != null, c.getVerificationCode() == null ? null : verifyUrl(c));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
