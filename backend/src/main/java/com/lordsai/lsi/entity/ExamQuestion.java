package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.McqOption;
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
 * One multiple-choice question with exactly four options. {@link #correctOption} is read only by
 * the scoring code on the backend; no student-facing DTO ever carries it.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exam_questions", indexes = @Index(name = "idx_exam_questions_exam", columnList = "exam_id, display_order"))
public class ExamQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "option_a", nullable = false, length = 1000)
    private String optionA;

    @Column(name = "option_b", nullable = false, length = 1000)
    private String optionB;

    @Column(name = "option_c", nullable = false, length = 1000)
    private String optionC;

    @Column(name = "option_d", nullable = false, length = 1000)
    private String optionD;

    @Enumerated(EnumType.STRING)
    @Column(name = "correct_option", nullable = false, length = 1)
    private McqOption correctOption;

    @Column(nullable = false)
    private int marks;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(nullable = false)
    private boolean active = true;
}
