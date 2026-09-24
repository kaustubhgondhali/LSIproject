package com.lordsai.lsi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A course-completion certificate, issued by the backend only when an exam attempt reached the
 * configured passing marks. Snapshot columns and the recorded template mean an issued certificate
 * is reproduced identically even if the student, course or active template changes later.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "certificates", indexes = {
        @Index(name = "idx_certificates_student", columnList = "student_user_id"),
        @Index(name = "idx_certificates_course", columnList = "course_id"),
        @Index(name = "idx_certificates_issued", columnList = "issued_at")
})
public class Certificate extends BaseEntity {

    @Column(name = "certificate_number", nullable = false, length = 30, unique = true)
    private String certificateNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, unique = true)
    private ExamAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private ExamApplication application;

    /** Null = the default Lord Sai certificate. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private CertificateTemplate template;

    @Column(name = "student_name", nullable = false, length = 150)
    private String studentName;

    @Column(name = "student_code", length = 20)
    private String studentCode;

    @Column(name = "course_name", nullable = false, length = 200)
    private String courseName;

    @Column(name = "exam_title", nullable = false, length = 200)
    private String examTitle;

    @Column(nullable = false)
    private int score;

    @Column(name = "total_marks", nullable = false)
    private int totalMarks;

    @Column(name = "passing_marks", nullable = false)
    private int passingMarks;

    @Column(name = "course_completed_at")
    private Instant courseCompletedAt;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "pdf_path", length = 255)
    private String pdfPath;

    @Column(name = "pdf_generated_at")
    private Instant pdfGeneratedAt;

    public String fileName() {
        return certificateNumber + ".pdf";
    }
}
