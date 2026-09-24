package com.lordsai.lsi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_profiles", indexes = {
        @Index(name = "idx_student_profiles_student_id", columnList = "student_id", unique = true)
})
public class StudentProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Human-facing identifier, e.g. LSI-2026-00001. Generated, never typed by hand. */
    @Column(name = "student_id", nullable = false, length = 20)
    private String studentId;

    @Column(length = 100)
    private String batch;

    @Column(length = 150)
    private String location;

    @Column(name = "profile_photo_path", length = 255)
    private String profilePhotoPath;

    @Column(name = "registration_date", nullable = false)
    private LocalDate registrationDate;
}
