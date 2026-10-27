package com.lordsai.lsi.dto.certificate;

import com.lordsai.lsi.entity.enums.CertificateRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class CertificateDtos {

    private CertificateDtos() {
    }

    // ---- Requests --------------------------------------------------------------------------

    /** Apply for a course's certificate. The name / Student ID are read from the account, never sent. */
    /**
     * {@code certificateName}: the name the student checked (and corrected if needed) in the Apply form;
     * it is printed on the certificate. Blank = the name on the student's account.
     */
    public record ApplyRequest(@NotNull Long courseId,
                               @Size(max = 200, message = "The name on the certificate is too long.") String certificateName) {
    }

    public record RejectRequest(@Size(max = 500) String reason) {
    }

    // ---- Student ---------------------------------------------------------------------------

    /**
     * One course in Student Admin -> Certificate. {@code state} is what the student sees:
     * NOT_ELIGIBLE (course not completed yet), ELIGIBLE (can apply), PENDING, REJECTED (can apply
     * again), GENERATED (certificate available).
     */
    public record MyCourseCertificate(
            Long courseId,
            String courseName,
            String enrollmentStatus,
            boolean courseCompleted,
            Instant courseCompletedAt,
            String state,
            boolean canApply,
            MyRequest request,
            MyCertificate certificate
    ) {
    }

    public record MyRequest(
            Long id,
            CertificateRequestStatus status,
            String studentName,
            String studentId,
            Instant requestedAt,
            Instant reviewedAt,
            String rejectionReason
    ) {
    }

    public record MyCertificate(
            Long id,
            String certificateNumber,
            Long courseId,
            String courseName,
            String studentName,
            String studentId,
            String coursePeriod,
            Instant issuedAt,
            Instant courseCompletedAt
    ) {
    }

    // ---- Admin -----------------------------------------------------------------------------

    public record AdminRequest(
            Long id,
            Long studentUserId,
            String studentId,
            String studentName,
            String studentEmail,
            Long courseId,
            String courseName,
            String enrollmentStatus,
            Instant courseStartedAt,
            Instant courseCompletedAt,
            CertificateRequestStatus status,
            Instant requestedAt,
            String reviewedBy,
            Instant reviewedAt,
            String rejectionReason,
            Long certificateId,
            String certificateNumber,
            Instant generatedAt,
            /** The student's account name, so the admin can see when the name on the certificate was edited. */
            String accountName
    ) {
    }

    public record RequestCounters(long pending, long generated, long rejected) {
    }

    public record AdminCertificate(
            Long id,
            String certificateNumber,
            Long studentUserId,
            String studentId,
            String studentName,
            Long courseId,
            String courseName,
            String coursePeriod,
            Instant issuedAt,
            boolean hasPdf,
            /** The public page the certificate's QR code opens (null until the PDF was generated). */
            String verifyUrl
    ) {
    }

    // ---- Public QR verification --------------------------------------------------------------

    /** Only what is printed on the certificate itself: no email, phone or internal ids. */
    public record Verification(
            boolean valid,
            String certificateNumber,
            String studentName,
            String courseName,
            String coursePeriod,
            String issued
    ) {
    }
}
