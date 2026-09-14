package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.PaymentMode;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_order_ref", columnList = "order_ref", unique = true),
        @Index(name = "idx_payments_rzp_order", columnList = "razorpay_order_id", unique = true),
        @Index(name = "idx_payments_rzp_payment", columnList = "razorpay_payment_id"),
        @Index(name = "idx_payments_status", columnList = "status"),
        @Index(name = "idx_payments_email", columnList = "customer_email")
})
public class Payment extends BaseEntity {

    /** Internal order reference shown to the customer, e.g. LSI-ORD-20260912-000123. */
    @Column(name = "order_ref", nullable = false, length = 40)
    private String orderRef;

    @Column(name = "razorpay_order_id", nullable = false, length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 64)
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature", length = 255)
    private String razorpaySignature;

    /**
     * Null until the purchase is verified and an account is created or matched.
     * The customer_* columns keep what was typed at checkout regardless.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 190)
    private String customerEmail;

    @Column(name = "customer_mobile", nullable = false, length = 20)
    private String customerMobile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** Amount in INR, copied from the course price at order time by the backend. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 20)
    private PaymentMode paymentMode = PaymentMode.RAZORPAY;

    @Column(name = "payment_method", length = 40)
    private String paymentMethod;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** Set when the verified-payment webhook or callback was processed, so retries are no-ops. */
    @Column(name = "processed_at")
    private Instant processedAt;
}
