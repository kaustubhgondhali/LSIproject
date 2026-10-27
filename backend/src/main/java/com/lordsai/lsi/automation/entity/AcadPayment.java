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

/** One fee payment (an installment). Totals and balances are always derived from these rows. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "acad_payments")
public class AcadPayment extends BaseEntity {

    @Column(name = "payment_no", nullable = false, length = 30, unique = true)
    private String paymentNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private AcadStudent student;

    @Column(name = "installment_no", nullable = false)
    private int installmentNo;

    /** Null only for imported workbook rows that carried an amount without a date. */
    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_mode", nullable = false, length = 40)
    private String paymentMode;

    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    @Column(length = 500)
    private String notes;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(nullable = false, length = 20)
    private String source = "MANUAL";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id")
    private User recordedBy;
}
