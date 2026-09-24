package com.lordsai.lsi.automation.entity;

import com.lordsai.lsi.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** An academy classroom programme with its standard fee (independent of the online LMS courses). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "acad_courses")
public class AcadCourse extends BaseEntity {

    @Column(nullable = false, length = 150, unique = true)
    private String name;

    @Column(name = "default_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal defaultFee;

    @Column(length = 100)
    private String duration;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
