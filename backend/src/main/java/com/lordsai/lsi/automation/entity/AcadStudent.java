package com.lordsai.lsi.automation.entity;

import com.lordsai.lsi.entity.BaseEntity;
import com.lordsai.lsi.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * An academy student record keyed by the business Student ID (LSA/YYYY/####). Payments,
 * receipts and attendance all link here, so the office enters each fact once.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "acad_students")
public class AcadStudent extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Column(name = "student_id", nullable = false, length = 30, unique = true)
    private String studentId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /** Upper-cased, single-spaced copy used for searching and duplicate detection. */
    @Column(name = "name_normalized", nullable = false, length = 150)
    private String nameNormalized;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    /** The original text when the workbook value could not be parsed (kept for admin review). */
    @Column(name = "admission_date_raw", length = 40)
    private String admissionDateRaw;

    @Column(length = 15)
    private String mobile;

    @Column(length = 190)
    private String email;

    @Column(length = 500)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private AcadBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private AcadCourse course;

    @Column(name = "course_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal courseFee = BigDecimal.ZERO;

    /** A discount given to this one student (0 = none); see {@link #netFee()}. */
    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** Why the discount was given — kept for the office, never printed on the receipt. */
    @Column(name = "discount_reason", length = 200)
    private String discountReason;

    @Column(name = "discount_at")
    private Instant discountAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_by_user_id")
    private User discountBy;

    /** The student's 12-digit Aadhaar number, digits only (null on records saved before it was required). */
    @Column(name = "aadhaar_no", length = 12, unique = true)
    private String aadhaarNo;

    // ---- free ebook login (Student Panel, ebook only), made after the first installment ----------

    /** The student's website login; null until it is made. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portal_user_id", unique = true)
    private User portalUser;

    /** The first (or office-reset) password, encrypted, shown to the office until the student changes it. */
    @Column(name = "portal_password_enc", length = 255)
    private String portalPasswordEnc;

    @Column(name = "portal_created_at")
    private Instant portalCreatedAt;

    /** When the login details last reached the student's email; null if they could not be emailed. */
    @Column(name = "portal_emailed_at")
    private Instant portalEmailedAt;

    /** The office blocked this student's ebook access. */
    @Column(name = "ebook_blocked", nullable = false)
    private boolean ebookBlocked;

    /**
     * The ONE permanent receipt/reference number for this student's fee plan: the Student ID with
     * its "LSA" turned into "LSR" (LSA/2026/0025 -> LSR/2026/0025). Assigned the first time a
     * payment is recorded and reused on every installment receipt — see
     * {@link com.lordsai.lsi.automation.service.AcadPaymentService#receiptNoFor(String)}.
     */
    @Column(name = "fee_receipt_no", length = 40)
    private String feeReceiptNo;

    @Column(nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    // ---- admission form details (all optional; older records leave them null) ----------------

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** MALE / FEMALE / OTHER. */
    @Column(length = 10)
    private String gender;

    @Column(name = "highest_qualification", length = 100)
    private String highestQualification;

    @Column(name = "institute_name", length = 150)
    private String instituteName;

    @Column(name = "year_of_completion")
    private Integer yearOfCompletion;

    /** EMPLOYED / SELF_EMPLOYED / UNEMPLOYED / STUDENT / OTHER. */
    @Column(name = "employment_status", length = 20)
    private String employmentStatus;

    @Column(name = "current_employer", length = 150)
    private String currentEmployer;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "annual_income", precision = 14, scale = 2)
    private BigDecimal annualIncome;

    @Column(name = "stock_market_experience")
    private Boolean stockMarketExperience;

    @Column(name = "stock_market_years", precision = 4, scale = 1)
    private BigDecimal stockMarketYears;

    @Column(name = "trading_experience")
    private Boolean tradingExperience;

    @Column(name = "trading_years", precision = 4, scale = 1)
    private BigDecimal tradingYears;

    @Column(name = "trading_platforms", length = 255)
    private String tradingPlatforms;

    @Column(name = "trading_strategies", length = 500)
    private String tradingStrategies;

    @Column(name = "preferred_start_date")
    private LocalDate preferredStartDate;

    /** CREDIT_CARD / BANK_TRANSFER / CASH — recorded on the admission form only; no payment is processed. */
    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(nullable = false, length = 20)
    private String source = "MANUAL";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    /** What the student has to pay: the course fee less any discount given to them. */
    public BigDecimal netFee() {
        BigDecimal fee = courseFee == null ? BigDecimal.ZERO : courseFee;
        return discountAmount == null ? fee : fee.subtract(discountAmount);
    }
}
