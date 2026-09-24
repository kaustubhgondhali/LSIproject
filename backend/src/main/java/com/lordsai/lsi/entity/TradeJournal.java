package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.TradeReviewStatus;
import com.lordsai.lsi.entity.enums.TradeType;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "trade_journal", indexes = {
        @Index(name = "idx_trade_journal_student", columnList = "student_user_id, trade_date")
})
public class TradeJournal extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false, length = 40)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false, length = 20)
    private TradeType tradeType;

    @Column(name = "entry_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal entryPrice;

    @Column(name = "stop_loss", precision = 12, scale = 2)
    private BigDecimal stopLoss;

    @Column(precision = 12, scale = 2)
    private BigDecimal target;

    @Column(name = "risk_reward", length = 20)
    private String riskReward;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private TradeReviewStatus reviewStatus = TradeReviewStatus.PENDING_REVIEW;

    @Column(name = "mentor_comment", columnDefinition = "TEXT")
    private String mentorComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
