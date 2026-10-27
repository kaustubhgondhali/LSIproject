package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.DoubtStatus;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "doubts", indexes = {
        @Index(name = "idx_doubts_student", columnList = "student_user_id, status"),
        @Index(name = "idx_doubts_status", columnList = "status")
})
public class Doubt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "attachment_path", length = 255)
    private String attachmentPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DoubtStatus status = DoubtStatus.OPEN;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
