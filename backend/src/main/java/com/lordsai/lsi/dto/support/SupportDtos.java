package com.lordsai.lsi.dto.support;

import com.lordsai.lsi.entity.enums.DoubtStatus;
import com.lordsai.lsi.entity.enums.TradeResult;
import com.lordsai.lsi.entity.enums.TradeReviewStatus;
import com.lordsai.lsi.entity.enums.TradeType;
import com.lordsai.lsi.entity.enums.TradingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
            @Size(max = 2000) String notes,
            @Size(max = 30) String segment,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal exitPrice,
            @Min(1) @Max(100_000_000) Integer quantity,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal charges,
            @Size(max = 60) String strategy,
            TradeResult result,
            @Size(max = 2000) String tradeReason,
            @Size(max = 2000) String outcomeReason,
            TradingType tradingType
    ) {
    }

    /** grossPnl / netPnl are derived (never stored) and null until exit price and quantity are known. */
    public record TradeResponse(
            Long id,
            Long studentUserId,
            String studentName,
            String studentId,
            LocalDate tradeDate,
            String segment,
            String symbol,
            TradeType tradeType,
            BigDecimal entryPrice,
            BigDecimal exitPrice,
            Integer quantity,
            BigDecimal charges,
            BigDecimal grossPnl,
            BigDecimal netPnl,
            String strategy,
            TradeResult result,
            String tradeReason,
            String outcomeReason,
            BigDecimal stopLoss,
            BigDecimal target,
            String riskReward,
            String notes,
            TradeReviewStatus reviewStatus,
            String mentorComment,
            String reviewedBy,
            Instant reviewedAt,
            Instant createdAt,
            TradingType tradingType
    ) {
    }

    /** Performance across all of the student's own journal entries; win rate counts trades that have a result. */
    public record TradeSummary(
            long totalTrades,
            long winningTrades,
            long losingTrades,
            long breakevenTrades,
            int winRatePercent,
            BigDecimal netPnl,
            BigDecimal totalCharges
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
