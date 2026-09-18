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
 * The fee receipt issued for one payment. Linked to the student and payment, and it also keeps
 * a snapshot of what was printed so a later name/fee edit never rewrites an issued receipt.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "acad_receipts")
public class AcadReceipt extends BaseEntity {

    @Column(name = "receipt_no", nullable = false, length = 30, unique = true)
    private String receiptNo;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private AcadPayment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private AcadStudent student;

    @Column(name = "student_code", nullable = false, length = 30)
    private String studentCode;

    @Column(name = "student_name", nullable = false, length = 150)
    private String studentName;

    @Column(name = "course_name", length = 150)
    private String courseName;

    @Column(name = "batch_name", length = 50)
    private String batchName;

    @Column(name = "batch_schedule", length = 100)
    private String batchSchedule;

    @Column(name = "total_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalFee;

    @Column(name = "amount_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "total_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPaid;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal balance;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "payment_mode", nullable = false, length = 40)
    private String paymentMode;

    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    @Column(name = "amount_in_words", nullable = false, length = 200)
    private String amountInWords;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by_user_id")
    private User issuedBy;

    @Column(name = "emailed_at")
    private Instant emailedAt;
}
