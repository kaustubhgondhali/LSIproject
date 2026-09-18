package com.lordsai.lsi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Yearly certificate counter, row-locked like {@link InvoiceSequence} (LSI-CERT-2026-000001). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "certificate_sequence")
public class CertificateSequence {

    @Id
    @Column(name = "year_value")
    private int year;

    @Column(name = "last_number", nullable = false)
    private int lastNumber;

    public CertificateSequence(int year) {
        this.year = year;
        this.lastNumber = 0;
    }
}
