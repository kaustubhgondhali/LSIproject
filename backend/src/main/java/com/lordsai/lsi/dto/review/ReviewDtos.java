package com.lordsai.lsi.dto.review;

import com.lordsai.lsi.entity.enums.ReviewStatus;
import com.lordsai.lsi.entity.enums.SiteCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ReviewDtos {

    public static final int MIN_REVIEW_LENGTH = 20;
    public static final int MAX_REVIEW_LENGTH = 1000;

    private ReviewDtos() {
    }

    /** Submitted as multipart/form-data (the optional photo rides along) — bound with @ModelAttribute. */
    public record ReviewRequest(
            @NotNull(message = "Please tell us which website you are reviewing.") SiteCode site,
            @NotBlank(message = "Please enter your full name.") @Size(max = 150, message = "Name is too long.") String fullName,
            @NotBlank(message = "Please enter your email address.") @Email(message = "Please enter a valid email address.")
            @Size(max = 190, message = "Email is too long.") String email,
            @Size(max = 150, message = "Course / program is too long.") String course,
            @NotNull(message = "Please select a star rating.")
            @Min(value = 1, message = "Rating must be between 1 and 5 stars.")
            @Max(value = 5, message = "Rating must be between 1 and 5 stars.") Integer rating,
            @NotBlank(message = "Please write your review.")
            @Size(min = MIN_REVIEW_LENGTH, max = MAX_REVIEW_LENGTH,
                    message = "Your review must be between " + MIN_REVIEW_LENGTH + " and " + MAX_REVIEW_LENGTH + " characters.")
            String reviewText
    ) {
    }

    /** Admin Panel create/update of a testimonial (JSON). Email is optional here; status decides
     *  visibility on the public page exactly like moderated visitor reviews. */
    public record AdminReviewRequest(
            @NotNull(message = "Please choose the website this testimonial belongs to.") SiteCode site,
            @NotBlank(message = "Please enter the name.") @Size(max = 150, message = "Name is too long.") String fullName,
            @Email(message = "Please enter a valid email address.") @Size(max = 190, message = "Email is too long.") String email,
            @Size(max = 150, message = "Course / program is too long.") String course,
            @NotNull(message = "Please select a star rating.")
            @Min(value = 1, message = "Rating must be between 1 and 5 stars.")
            @Max(value = 5, message = "Rating must be between 1 and 5 stars.") Integer rating,
            @NotBlank(message = "Please enter the testimonial text.")
            @Size(min = MIN_REVIEW_LENGTH, max = MAX_REVIEW_LENGTH,
                    message = "The testimonial must be between " + MIN_REVIEW_LENGTH + " and " + MAX_REVIEW_LENGTH + " characters.")
            String reviewText,
            /** APPROVED = visible on the website; PENDING / DECLINED = hidden. Defaults to APPROVED. */
            ReviewStatus status
    ) {
    }

    /** What visitors see: never the email, IP or who approved it. */
    public record PublicReview(
            Long id,
            String fullName,
            String course,
            int rating,
            String reviewText,
            String profileImagePath,
            Instant approvedAt
    ) {
    }

    public record AdminReview(
            Long id,
            SiteCode site,
            String fullName,
            String email,
            String course,
            int rating,
            String reviewText,
            String profileImagePath,
            ReviewStatus status,
            String submittedIp,
            Instant createdAt,
            Instant approvedAt,
            String approvedBy,
            Instant decidedAt,
            String decidedBy
    ) {
    }

    public record ReviewCounts(long pending, long approved, long declined) {
    }
}
