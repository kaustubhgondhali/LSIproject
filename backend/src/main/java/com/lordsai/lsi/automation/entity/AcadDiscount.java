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

/**
 * A discount given to one student at one installment. Receipts of that installment and later ones
 * show it; earlier receipts do not.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "acad_discounts")
public class AcadDiscount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private AcadStudent student;

    @Column(name = "installment_no", nullable = false)
    private int installmentNo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** Kept for the office, never printed on the receipt. */
    @Column(nullable = false, length = 200)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "given_by_user_id")
    private User givenBy;
}
