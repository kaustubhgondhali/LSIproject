package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.EnrollmentSource;
import com.lordsai.lsi.entity.enums.EnrollmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Grants a student access to a course. The unique (student, course) constraint is the
 * database-level guarantee against duplicate enrollments, regardless of how many payment
 * callbacks arrive.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "enrollments",
        uniqueConstraints = @UniqueConstraint(name = "uk_enrollment_student_course",
                columnNames = {"student_user_id", "course_id"}),
        indexes = {
                @Index(name = "idx_enrollments_student", columnList = "student_user_id"),
                @Index(name = "idx_enrollments_course", columnList = "course_id"),
                @Index(name = "idx_enrollments_status", columnList = "status")
        })
public class Enrollment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** Null for admin-created enrollments. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", unique = true)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentSource source;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    public boolean grantsAccess() {
        if (status != EnrollmentStatus.ACTIVE && status != EnrollmentStatus.COMPLETED) {
            return false;
        }
        return expiryDate == null || !LocalDate.now().isAfter(expiryDate);
    }
}
