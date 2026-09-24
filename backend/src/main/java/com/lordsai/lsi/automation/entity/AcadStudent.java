package com.lordsai.lsi.automation.entity;

import com.lordsai.lsi.entity.BaseEntity;
import com.lordsai.lsi.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
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

    /**
     * The ONE permanent receipt/reference number for this student's fee plan (format
     * LSR/YYYY/####, same sequence as before). Assigned once, the first time a payment is
     * recorded, and reused on every installment receipt from then on — see
     * {@link com.lordsai.lsi.automation.service.AcadPaymentService#issueReceipt}.
     */
    @Column(name = "fee_receipt_no", length = 30)
    private String feeReceiptNo;

    @Column(nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(nullable = false, length = 20)
    private String source = "MANUAL";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;
}
