package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.ExamScheduleStatus;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The exam window the admin sets for one application. The backend compares the server clock with
 * {@link #startsAt} / {@link #endsAt} before an attempt may start or be submitted.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exam_schedules", indexes = {
        @Index(name = "idx_exam_schedules_student", columnList = "student_user_id, status"),
        @Index(name = "idx_exam_schedules_window", columnList = "starts_at, ends_at")
})
public class ExamSchedule extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private ExamApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExamScheduleStatus status = ExamScheduleStatus.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheduled_by_user_id")
    private User scheduledBy;

    @Column(name = "reschedule_count", nullable = false)
    private int rescheduleCount = 0;

    public boolean isOpenAt(Instant now) {
        return status == ExamScheduleStatus.SCHEDULED && !now.isBefore(startsAt) && now.isBefore(endsAt);
    }
}
