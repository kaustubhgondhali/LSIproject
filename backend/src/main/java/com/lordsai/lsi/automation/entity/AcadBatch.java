package com.lordsai.lsi.automation.entity;

import com.lordsai.lsi.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** A classroom batch (1ST, 2ND, …) students are assigned to; attendance is taken per batch. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "acad_batches")
public class AcadBatch extends BaseEntity {

    @Column(nullable = false, length = 50, unique = true)
    private String name;

    @Column(length = 100)
    private String schedule;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
