package com.lordsai.lsi.automation.entity;

import com.lordsai.lsi.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Dropdown values grouped by category: PAYMENT_MODE, INSTALLMENT, SESSION_TYPE. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "acad_master_data")
public class AcadMasterData extends BaseEntity {

    public static final String PAYMENT_MODE = "PAYMENT_MODE";
    public static final String INSTALLMENT = "INSTALLMENT";
    public static final String SESSION_TYPE = "SESSION_TYPE";

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
