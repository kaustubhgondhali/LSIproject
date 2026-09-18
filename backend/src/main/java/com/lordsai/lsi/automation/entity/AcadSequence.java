package com.lordsai.lsi.automation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Per-kind, per-year counter behind the generated business numbers. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "acad_sequences")
public class AcadSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(name = "seq_year", nullable = false)
    private int year;

    @Column(name = "last_number", nullable = false)
    private int lastNumber;

    public AcadSequence(String kind, int year) {
        this.kind = kind;
        this.year = year;
    }
}
