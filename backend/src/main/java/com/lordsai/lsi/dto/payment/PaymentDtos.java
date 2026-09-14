package com.lordsai.lsi.dto.payment;

import com.lordsai.lsi.dto.user.UserDtos;
import com.lordsai.lsi.entity.enums.EnrollmentStatus;
import com.lordsai.lsi.entity.enums.PaymentMode;
import com.lordsai.lsi.entity.enums.PaymentStatus;
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

    public record CreateOrderRequest(
            @NotNull Long courseId,
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
            String courseName,
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
            String courseName,
            BigDecimal amountInr,
            EnrollmentStatus enrollmentStatus,
            String studentId,
            String email,
            boolean newAccount,
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
