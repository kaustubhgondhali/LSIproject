package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.ExamStatus;
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

/**
 * An MCQ examination for one course. Total marks, passing marks and the attempt limit are
 * configured by the admin here and are the only source the backend uses when it scores an
 * attempt or decides certificate eligibility — the client never sends any of them.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exams", indexes = @Index(name = "idx_exams_course", columnList = "course_id, status"))
public class Exam extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 200)
    private String title;

    /** Instructions shown to the student before and during the exam. */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "total_marks", nullable = false)
    private int totalMarks;

    @Column(name = "passing_marks", nullable = false)
    private int passingMarks;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 1;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExamStatus status = ExamStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;
}
