package com.lordsai.lsi.dto.exam;

import java.time.Instant;
import java.util.List;

public final class CertificateDtos {

    private CertificateDtos() {
    }

    /** Name shown for the built-in template when no custom template is active. */
    public static final String DEFAULT_TEMPLATE_NAME = "Default Lord Sai Certificate";

    public record TemplateResponse(
            Long id,
            String name,
            String originalName,
            String contentType,
            Long sizeBytes,
            boolean active,
            long certificatesIssued,
            String uploadedBy,
            Instant createdAt
    ) {
    }

    /** Current certificate configuration: which design new certificates will use. */
    public record TemplateStatus(
            boolean usingDefault,
            String activeTemplateName,
            Long activeTemplateId,
            String defaultTemplateName,
            List<TemplateResponse> templates
    ) {
    }

    /** A student's own certificate (My Certificates) and the admin listing. */
    public record MyCertificate(
            Long id,
            String certificateNumber,
            Long courseId,
            String courseName,
            String examTitle,
            int score,
            int totalMarks,
            int passingMarks,
            Instant issuedAt,
            Instant courseCompletedAt,
            String templateName
    ) {
    }

    public record AdminCertificate(
            Long id,
            String certificateNumber,
            Long studentUserId,
            String studentId,
            String studentName,
            Long courseId,
            String courseName,
            Long examId,
            String examTitle,
            Long attemptId,
            int attemptNumber,
            int score,
            int totalMarks,
            int passingMarks,
            Instant issuedAt,
            String templateName,
            boolean hasPdf
    ) {
    }
}
