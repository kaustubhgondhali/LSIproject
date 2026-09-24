package com.lordsai.lsi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per year, read with a pessimistic write lock inside the purchase transaction — the same
 * pattern as {@link StudentIdSequence} — so two invoices can never share a number.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "invoice_sequence")
public class InvoiceSequence {

    @Id
    @Column(name = "year_value")
    private int year;

    @Column(name = "last_number", nullable = false)
    private int lastNumber;

    public InvoiceSequence(int year) {
        this.year = year;
        this.lastNumber = 0;
    }
}
