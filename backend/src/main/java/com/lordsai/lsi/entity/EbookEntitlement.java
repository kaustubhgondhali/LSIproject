package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.EnrollmentSource;
import com.lordsai.lsi.entity.enums.EntitlementStatus;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Grants a student access to one ebook — the ebook counterpart of {@link Enrollment}. The unique
 * (student, ebook) constraint is the database-level guarantee against duplicate entitlements no
 * matter how many payment callbacks or webhooks arrive.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ebook_entitlements",
        uniqueConstraints = @UniqueConstraint(name = "uk_ebook_entitlement_student_ebook",
                columnNames = {"student_user_id", "ebook_id"}),
        indexes = {
                @Index(name = "idx_ebook_entitlements_student", columnList = "student_user_id"),
                @Index(name = "idx_ebook_entitlements_ebook", columnList = "ebook_id"),
                @Index(name = "idx_ebook_entitlements_status", columnList = "status")
        })
public class EbookEntitlement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ebook_id", nullable = false)
    private Ebook ebook;

    /** Null for admin-granted access. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", unique = true)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntitlementStatus status = EntitlementStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentSource source;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    public boolean grantsAccess() {
        return status == EntitlementStatus.ACTIVE;
    }
}
