package com.lordsai.lsi.dto.admin;

import com.lordsai.lsi.dto.payment.PaymentDtos.AdminPayment;
import com.lordsai.lsi.dto.payment.PaymentDtos.EnrollmentResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record DashboardStats(
            long totalStudents,
            long activeStudents,
            long pendingSetupStudents,
            long disabledStudents,
            long totalCourses,
            long activeCourses,
            long totalEnrollments,
            long activeEnrollments,
            long totalPayments,
            long successfulPayments,
            /** DEMO or RAZORPAY — which checkout visitors currently get. */
            String paymentMode,
            long demoPayments,
            long razorpayPayments,
            long pendingPayments,
            long failedPayments,
            long refundedPayments,
            BigDecimal totalRevenue,
            long openDoubts,
            long pendingReviews,
            long approvedReviews,
            long declinedReviews,
            List<EnrollmentResponse> recentEnrollments,
            List<AdminPayment> recentPayments
    ) {
    }

    public record SessionInfo(
            Long id,
            String deviceInfo,
            String ipAddress,
            Instant createdAt,
            Instant lastActivityAt,
            Instant expiresAt,
            boolean active,
            String revokeReason
    ) {
    }

    public record AuditLogResponse(
            Long id,
            Long actorUserId,
            String actorName,
            String actorRole,
            String action,
            String entityType,
            Long entityId,
            String description,
            String ipAddress,
            Instant createdAt
    ) {
    }

    public record SiteContentRequest(
            @NotBlank @Size(max = 150) String contentKey,
            @NotBlank @Size(max = 200) String label,
            @Size(max = 20000) String contentValue,
            @NotBlank @Size(max = 20) String contentType
    ) {
    }

    public record SiteContentValueRequest(@Size(max = 20000) String contentValue) {
    }

    public record SiteContentResponse(
            Long id,
            String siteCode,
            String contentKey,
            String label,
            String contentValue,
            String contentType,
            String updatedBy,
            Instant updatedAt
    ) {
    }

    public record GlobalSearchResult(
            List<StudentHit> students,
            List<CourseHit> courses,
            List<PaymentHit> payments
    ) {
    }

    public record StudentHit(Long id, String studentId, String fullName, String email, String status) {
    }

    public record CourseHit(Long id, String courseCode, String courseName, String status) {
    }

    public record PaymentHit(Long id, String orderRef, String razorpayPaymentId, String customerEmail, String status, BigDecimal amount) {
    }
}
