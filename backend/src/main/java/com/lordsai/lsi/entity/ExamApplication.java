package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.ExamApplicationStatus;
import com.lordsai.lsi.entity.enums.ExamResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A student's request to sit the final exam of a course they have completed. Created only after
 * the backend has re-checked the enrollment and the lesson-progress completion rule.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exam_applications", indexes = {
        @Index(name = "idx_exam_apps_student", columnList = "student_user_id, course_id"),
        @Index(name = "idx_exam_apps_status", columnList = "status"),
        @Index(name = "idx_exam_apps_applied", columnList = "applied_at")
})
public class ExamApplication extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    /** Set by the admin when the exam is scheduled. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id")
    private Exam exam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExamApplicationStatus status = ExamApplicationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExamResult result;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "course_completed_at")
    private Instant courseCompletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "admin_remarks", length = 500)
    private String adminRemarks;

    @Column(name = "completed_at")
    private Instant completedAt;
}
