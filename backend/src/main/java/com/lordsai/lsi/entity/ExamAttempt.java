package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.ExamAttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One sitting of an exam. The unique (student, exam, attempt_number) constraint is the database
 * guarantee behind the attempt limit: two concurrent "start" requests cannot both create attempt
 * N+1. Score, total, passing marks and pass/fail are written by the scoring code only.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exam_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uk_exam_attempts_number",
                columnNames = {"student_user_id", "exam_id", "attempt_number"}),
        indexes = {
                @Index(name = "idx_exam_attempts_student", columnList = "student_user_id, exam_id"),
                @Index(name = "idx_exam_attempts_exam", columnList = "exam_id, status"),
                @Index(name = "idx_exam_attempts_submit", columnList = "submitted_at")
        })
public class ExamAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private ExamSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private ExamApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExamAttemptStatus status = ExamAttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /** min(window end, start + duration) — a submission after this is refused. */
    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(nullable = false)
    private int score;

    @Column(name = "total_marks", nullable = false)
    private int totalMarks;

    @Column(name = "passing_marks", nullable = false)
    private int passingMarks;

    @Column(nullable = false)
    private boolean passed;

    public boolean isFinished() {
        return status != ExamAttemptStatus.IN_PROGRESS;
    }
}
