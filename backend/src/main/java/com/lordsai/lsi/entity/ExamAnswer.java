package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.McqOption;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The option a student chose for one question of one attempt; written once at submission. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exam_answers",
        uniqueConstraints = @UniqueConstraint(name = "uk_exam_answers_question", columnNames = {"attempt_id", "question_id"}))
public class ExamAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private ExamQuestion question;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_option", length = 1)
    private McqOption selectedOption;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "marks_awarded", nullable = false)
    private int marksAwarded;
}
