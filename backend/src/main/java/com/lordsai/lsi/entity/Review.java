package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A testimonial submitted by a website visitor. It starts PENDING and becomes visible on the
 * public testimonials page only after an admin approves it. Declined reviews are kept as a
 * record and can still be approved later.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reviews")
public class Review extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /** Visitor submissions always have one; admin-authored testimonials may not. */
    @Column(length = 190)
    private String email;

    @Column(length = 150)
    private String course;

    @Column(nullable = false)
    private int rating;

    @Column(name = "review_text", nullable = false, length = 2000)
    private String reviewText;

    @Column(name = "profile_image_path", length = 255)
    private String profileImagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(name = "submitted_ip", length = 45)
    private String submittedIp;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    /** Last accept/decline decision (either direction). */
    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_user_id")
    private User decidedBy;
}
