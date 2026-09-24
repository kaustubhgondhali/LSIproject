package com.lordsai.lsi.dto.payment;

import com.lordsai.lsi.dto.user.UserDtos;
import com.lordsai.lsi.entity.enums.EnrollmentStatus;
import com.lordsai.lsi.entity.enums.PaymentMode;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.entity.enums.ProductType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     * One checkout request for every product. {@code productType} defaults to COURSE (so the
     * existing course checkout keeps working unchanged); an EBOOK order sends {@code ebookId}.
     */
    public record CreateOrderRequest(
            Long courseId,
            ProductType productType,
            Long ebookId,
            @NotBlank @Size(min = 2, max = 150) String fullName,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Pattern(regexp = UserDtos.MOBILE_PATTERN, message = UserDtos.MOBILE_MESSAGE) String mobile
    ) {
    }

    /** Everything the browser needs to open Razorpay Checkout. Amount comes from the DB, never the client. */
    public record CreateOrderResponse(
            String orderRef,
            String razorpayOrderId,
            String razorpayKeyId,
            long amountPaise,
            BigDecimal amountInr,
            String currency,
            /** Product display name (kept as courseName for the existing checkout script). */
            String courseName,
            ProductType productType,
            String productName,
            String customerName,
            String customerEmail,
            String customerMobile
    ) {
    }

    public record VerifyRequest(
            @NotBlank String razorpayOrderId,
            @NotBlank String razorpayPaymentId,
            @NotBlank String razorpaySignature
    ) {
    }

    /** Shown on the success screen. */
    public record VerifyResponse(
            PaymentStatus paymentStatus,
            PaymentMode paymentMode,
            String orderRef,
            /** Product display name (kept as courseName for the existing checkout script). */
            String courseName,
            ProductType productType,
            String productName,
            String invoiceNumber,
            Long invoiceId,
            BigDecimal amountInr,
            EnrollmentStatus enrollmentStatus,
            String studentId,
            String email,
            boolean newAccount,
            /** False when the setup email was not delivered — the screen then offers a resend
             *  instead of telling the student to check an inbox that will stay empty. */
            boolean setupEmailSent,
            String message
    ) {
    }

    public record AdminPayment(
            Long id,
            String orderRef,
            String razorpayOrderId,
            String razorpayPaymentId,
            Long userId,
            String studentId,
            String customerName,
            String customerEmail,
            String customerMobile,
            Long courseId,
            String courseName,
            ProductType productType,
            Long productId,
            String productName,
            String invoiceNumber,
            Long invoiceId,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            PaymentMode paymentMode,
            String paymentMethod,
            String failureReason,
            Instant createdAt,
            Instant verifiedAt
    ) {
    }

    public record EnrollmentResponse(
            Long id,
            Long studentUserId,
            String studentId,
            String studentName,
            String studentEmail,
            Long courseId,
            String courseCode,
            String courseName,
            EnrollmentStatus status,
            String source,
            Long paymentId,
            String orderRef,
            Instant enrolledAt,
            java.time.LocalDate expiryDate,
            int progressPercent,
            long completedLessons,
            long totalLessons
    ) {
    }

    public record ManualEnrollRequest(
            @NotNull Long studentUserId,
            @NotNull Long courseId,
            java.time.LocalDate expiryDate
    ) {
    }

    public record EnrollmentStatusRequest(@NotNull EnrollmentStatus status) {
    }
}
