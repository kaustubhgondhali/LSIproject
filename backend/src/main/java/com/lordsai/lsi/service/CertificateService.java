package com.lordsai.lsi.service;

import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.dto.exam.CertificateDtos;
import com.lordsai.lsi.dto.exam.CertificateDtos.AdminCertificate;
import com.lordsai.lsi.dto.exam.CertificateDtos.MyCertificate;
import com.lordsai.lsi.entity.Certificate;
import com.lordsai.lsi.entity.CertificateSequence;
import com.lordsai.lsi.entity.CertificateTemplate;
import com.lordsai.lsi.entity.ExamApplication;
import com.lordsai.lsi.entity.ExamAttempt;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.export.PdfRenderer;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Issues and serves course certificates. A certificate exists only because {@link ExamWorkflowService}
 * scored an attempt at or above the exam's configured passing marks — there is no other entry
 * point. The PDF is rendered from the ONE certificate template (default Lord Sai design, or the
 * admin's active uploaded background) by the same {@link PdfRenderer} the invoices use, stored under
 * the protected "certificates/" folder and streamed only to its owner or an admin.
 */
@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);
    public static final String TEMPLATE = "certificate/certificate";
    public static final String PREFIX = "LSI-CERT";
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);

    private final CertificateRepository certificateRepository;
    private final CertificateSequenceRepository sequenceRepository;
    private final CertificateTemplateService templateService;
    private final StudentProfileRepository studentProfileRepository;
    private final PdfRenderer pdfRenderer;
    private final FileStorageService storage;
    private final AuditService auditService;
    private final AppProperties properties;

    public CertificateService(CertificateRepository certificateRepository,
                              CertificateSequenceRepository sequenceRepository,
                              CertificateTemplateService templateService,
                              StudentProfileRepository studentProfileRepository,
                              PdfRenderer pdfRenderer,
                              FileStorageService storage,
                              AuditService auditService,
                              AppProperties properties) {
        this.certificateRepository = certificateRepository;
        this.sequenceRepository = sequenceRepository;
        this.templateService = templateService;
        this.studentProfileRepository = studentProfileRepository;
        this.pdfRenderer = pdfRenderer;
        this.storage = storage;
        this.auditService = auditService;
        this.properties = properties;
    }

    // ---- issuing ---------------------------------------------------------------------------

    /**
     * Issues the certificate for a PASSED attempt (idempotent per attempt). The caller has already
     * established {@code attempt.isPassed()} from the exam's passing marks; this method re-checks it
     * so a certificate can never exist for a failed or unfinished attempt.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Certificate issueForPassedAttempt(ExamAttempt attempt) {
        Optional<Certificate> existing = certificateRepository.findByAttemptId(attempt.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!attempt.isFinished() || !attempt.isPassed() || attempt.getScore() < attempt.getPassingMarks()) {
            throw new ApiException(HttpStatus.CONFLICT, "A certificate can only be issued for a passed exam attempt.");
        }
        ExamApplication application = attempt.getApplication();
        User student = attempt.getStudent();
        StudentProfile profile = studentProfileRepository.findByUserId(student.getId()).orElse(null);
        CertificateTemplate template = templateService.activeTemplate().orElse(null);

        Certificate c = new Certificate();
        c.setCertificateNumber(nextNumber());
        c.setStudent(student);
        c.setCourse(application.getCourse());
        c.setExam(attempt.getExam());
        c.setAttempt(attempt);
        c.setApplication(application);
        c.setTemplate(template);
        c.setStudentName(student.getFullName());
        c.setStudentCode(profile == null ? null : profile.getStudentId());
        c.setCourseName(application.getCourse().getCourseName());
        c.setExamTitle(attempt.getExam().getTitle());
        c.setScore(attempt.getScore());
        c.setTotalMarks(attempt.getTotalMarks());
        c.setPassingMarks(attempt.getPassingMarks());
        c.setCourseCompletedAt(application.getCourseCompletedAt());
        c.setIssuedAt(Instant.now());
        c = certificateRepository.save(c);

        try {
            renderAndStore(c);
        } catch (RuntimeException e) {
            // The certificate record is the source of truth; the PDF is regenerated on first download.
            log.error("[CERTIFICATE] PDF generation failed for {}: {}", c.getCertificateNumber(), e.getMessage());
        }
        auditService.record(null, "CERTIFICATE_ISSUED", "Certificate", c.getId(),
                c.getCertificateNumber() + " for " + c.getStudentName() + " (" + c.getStudentCode() + ") — "
                        + c.getCourseName() + " — score " + c.getScore() + "/" + c.getTotalMarks()
                        + " — template: " + (template == null ? CertificateDtos.DEFAULT_TEMPLATE_NAME : template.getName()), null);
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

    // ---- PDF -------------------------------------------------------------------------------

    /** The stored PDF; regenerated with the template recorded on the certificate if the file is missing. */
    @Transactional
    public byte[] pdfBytes(Certificate certificate) {
        if (certificate.getPdfPath() != null && storage.exists(certificate.getPdfPath())) {
            return storage.readBytes(certificate.getPdfPath());
        }
        return renderAndStore(certificate);
    }

    /** Admin preview of what a certificate looks like with the currently active design (sample data). */
    @Transactional(readOnly = true)
    public byte[] previewPdf() {
        CertificateTemplate template = templateService.activeTemplate().orElse(null);
        Map<String, Object> m = baseModel(template);
        m.put("certificateNumber", PREFIX + "-" + LocalDate.now(INDIA).getYear() + "-000000");
        m.put("studentName", "Sample Student Name");
        m.put("studentId", "LSI-" + LocalDate.now(INDIA).getYear() + "-00000");
        m.put("courseName", "Share Market Education & Training");
        m.put("examTitle", "Final Course Examination");
        m.put("score", 45);
        m.put("totalMarks", 50);
        m.put("passingMarks", 30);
        m.put("issueDate", DATE.format(LocalDate.now(INDIA)));
        m.put("completionDate", DATE.format(LocalDate.now(INDIA)));
        m.put("preview", true);
        return pdfRenderer.pdf(TEMPLATE, m);
    }

    private byte[] renderAndStore(Certificate c) {
        byte[] pdf = pdfRenderer.pdf(TEMPLATE, model(c));
        String previous = c.getPdfPath();
        c.setPdfPath(storage.storeBytes(pdf, FileStorageService.Kind.CERTIFICATE, "pdf"));
        c.setPdfGeneratedAt(Instant.now());
        certificateRepository.save(c);
        if (previous != null) {
            storage.deleteQuietly(previous);
        }
        return pdf;
    }

    /** Every dynamic value the certificate template consumes. Never includes credentials. */
    public Map<String, Object> model(Certificate c) {
        // The design recorded at issue time; if that upload was removed, the default is used.
        CertificateTemplate template = c.getTemplate();
        Map<String, Object> m = baseModel(template);
        m.put("certificateNumber", c.getCertificateNumber());
        m.put("studentName", c.getStudentName());
        m.put("studentId", c.getStudentCode() == null ? "—" : c.getStudentCode());
        m.put("courseName", c.getCourseName());
        m.put("examTitle", c.getExamTitle());
        m.put("score", c.getScore());
        m.put("totalMarks", c.getTotalMarks());
        m.put("passingMarks", c.getPassingMarks());
        m.put("issueDate", DATE.format(c.getIssuedAt().atZone(INDIA)));
        m.put("completionDate", c.getCourseCompletedAt() == null ? DATE.format(c.getIssuedAt().atZone(INDIA))
                : DATE.format(c.getCourseCompletedAt().atZone(INDIA)));
        m.put("preview", false);
        return m;
    }

    private Map<String, Object> baseModel(CertificateTemplate template) {
        Map<String, Object> m = new HashMap<>();
        String background = templateService.imageDataUri(template);
        m.put("customBackground", background);            // null -> default Lord Sai design
        m.put("usingDefault", background == null);
        m.put("templateName", background == null ? CertificateDtos.DEFAULT_TEMPLATE_NAME : template.getName());
        m.put("academyName", "Lord Sai Investment & Share Market Academy");
        m.put("signatoryName", "Vaibhav S. Pawar");
        m.put("signatoryTitle", "Founder & Lead Educator");
        m.put("supportEmail", properties.support().email());
        m.put("supportPhone", properties.support().phone());
        m.put("siteUrl", properties.publicBaseUrl());
        return m;
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
                c.getExamTitle(), c.getScore(), c.getTotalMarks(), c.getPassingMarks(), c.getIssuedAt(),
                c.getCourseCompletedAt(), templateName(c));
    }

    public AdminCertificate toAdmin(Certificate c) {
        return new AdminCertificate(c.getId(), c.getCertificateNumber(), c.getStudent().getId(), c.getStudentCode(),
                c.getStudentName(), c.getCourse().getId(), c.getCourseName(), c.getExam().getId(), c.getExamTitle(),
                c.getAttempt().getId(), c.getAttempt().getAttemptNumber(), c.getScore(), c.getTotalMarks(),
                c.getPassingMarks(), c.getIssuedAt(), templateName(c), c.getPdfPath() != null);
    }

    private static String templateName(Certificate c) {
        return c.getTemplate() == null ? CertificateDtos.DEFAULT_TEMPLATE_NAME : c.getTemplate().getName();
    }
}
