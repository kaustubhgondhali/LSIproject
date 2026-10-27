package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.CertificateRequestStatus;
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
 * A student's request for the course completion certificate of a COMPLETED course. The name and
 * Student ID are taken from the student's own account, never from the request body. The admin
 * approves (the certificate is generated) or rejects it.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "certificate_requests", indexes = {
        @Index(name = "idx_cert_requests_status", columnList = "status, requested_at"),
        @Index(name = "idx_cert_requests_student", columnList = "student_user_id, course_id")
})
public class CertificateRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CertificateRequestStatus status = CertificateRequestStatus.PENDING;

    /** 1 while active, null once rejected: unique (student, course, active_slot) blocks duplicates. */
    @Column(name = "active_slot")
    private Integer activeSlot = 1;

    @Column(name = "student_name", nullable = false, length = 150)
    private String studentName;

    @Column(name = "student_code", length = 30)
    private String studentCode;

    @Column(name = "course_name", nullable = false, length = 200)
    private String courseName;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id")
    private Certificate certificate;

    @Column(name = "generated_at")
    private Instant generatedAt;

    public void setStatus(CertificateRequestStatus status) {
        this.status = status;
        this.activeSlot = status.isActive() ? 1 : null;
    }
}
