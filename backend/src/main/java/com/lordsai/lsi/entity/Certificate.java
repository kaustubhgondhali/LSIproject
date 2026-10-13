package com.lordsai.lsi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A course completion certificate, generated when the admin approves the student's certificate
 * request for a completed course. The snapshot columns (printed name, Student ID, course, period,
 * issue date) mean an issued certificate is reproduced identically even if the student or course
 * record changes later. The verification code behind the QR link is random, never the id.
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

    @Column(name = "student_name", nullable = false, length = 150)
    private String studentName;

    @Column(name = "student_code", length = 20)
    private String studentCode;

    @Column(name = "course_name", nullable = false, length = 200)
    private String courseName;

    @Column(name = "course_started_at")
    private Instant courseStartedAt;

    @Column(name = "course_completed_at")
    private Instant courseCompletedAt;

    /** The printed period, e.g. "January to April 2026". */
    @Column(name = "course_period", length = 80)
    private String coursePeriod;

    @Column(name = "verification_code", length = 40, unique = true)
    private String verificationCode;

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
