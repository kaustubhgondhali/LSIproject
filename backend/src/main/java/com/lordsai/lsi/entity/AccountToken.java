package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.TokenPurpose;
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
 * One-time token for password setup (after purchase) and password reset.
 * Only the SHA-256 hash is stored; the raw token exists solely inside the emailed link.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "account_tokens", indexes = {
        @Index(name = "idx_account_tokens_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_account_tokens_user", columnList = "user_id")
})
public class AccountToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TokenPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public boolean isUsable() {
        return usedAt == null && Instant.now().isBefore(expiresAt);
    }
}
