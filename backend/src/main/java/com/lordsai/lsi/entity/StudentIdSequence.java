package com.lordsai.lsi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per year. Read with a pessimistic write lock, incremented, and written back inside the
 * enrollment transaction, so concurrent purchases can never produce the same student ID.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_id_sequence")
public class StudentIdSequence {

    @Id
    @Column(name = "year_value")
    private int year;

    @Column(name = "last_number", nullable = false)
    private int lastNumber;

    public StudentIdSequence(int year) {
        this.year = year;
        this.lastNumber = 0;
    }
}
