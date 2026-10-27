package com.lordsai.lsi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lesson_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_progress_student_lesson",
                columnNames = {"student_user_id", "lesson_id"}),
        indexes = {
                @Index(name = "idx_lesson_progress_student", columnList = "student_user_id")
        })
public class LessonProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(nullable = false)
    private boolean completed = false;

    /** 0–100. */
    @Column(name = "watched_percentage", nullable = false)
    private int watchedPercentage = 0;

    @Column(name = "last_watched_at")
    private Instant lastWatchedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
