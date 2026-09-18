package com.lordsai.lsi.dto.support;

import com.lordsai.lsi.entity.enums.DoubtStatus;
import com.lordsai.lsi.entity.enums.TradeReviewStatus;
import com.lordsai.lsi.entity.enums.TradeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SupportDtos {

    private SupportDtos() {
    }

    // ---- Trade journal ---------------------------------------------------------------------

    public record TradeRequest(
            @NotNull LocalDate tradeDate,
            @NotBlank @Size(max = 40) String symbol,
            @NotNull TradeType tradeType,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal entryPrice,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal stopLoss,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal target,
            @Size(max = 2000) String notes
    ) {
    }

    public record TradeResponse(
            Long id,
            Long studentUserId,
            String studentName,
            String studentId,
            LocalDate tradeDate,
            String symbol,
            TradeType tradeType,
            BigDecimal entryPrice,
            BigDecimal stopLoss,
            BigDecimal target,
            String riskReward,
            String notes,
            TradeReviewStatus reviewStatus,
            String mentorComment,
            String reviewedBy,
            Instant reviewedAt,
            Instant createdAt
    ) {
    }

    // ---- Doubt desk ------------------------------------------------------------------------

    public record DoubtRequest(
            Long courseId,
            Long lessonId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 5000) String description
    ) {
    }

    public record DoubtReplyRequest(@NotBlank @Size(max = 5000) String message) {
    }

    public record DoubtStatusRequest(@NotNull DoubtStatus status) {
    }

    public record DoubtSummary(
            Long id,
            Long studentUserId,
            String studentName,
            String studentId,
            Long courseId,
            String courseName,
            Long lessonId,
            String lessonTitle,
            String title,
            String description,
            DoubtStatus status,
            int replyCount,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt
    ) {
    }

    public record DoubtDetail(
            Long id,
            Long studentUserId,
            String studentName,
            String studentId,
            Long courseId,
            String courseName,
            Long lessonId,
            String lessonTitle,
            String title,
            String description,
            String attachmentPath,
            DoubtStatus status,
            Instant createdAt,
            Instant resolvedAt,
            List<Reply> replies
    ) {
    }

    public record Reply(Long id, Long authorUserId, String authorName, String authorRole, String message, Instant createdAt) {
    }
}
