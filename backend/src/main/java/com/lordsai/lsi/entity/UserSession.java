package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.SessionRevokeReason;
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
 * Server-side record of every login. This — not the browser — is the source of truth for
 * "is this account already logged in on another device?". A student may have at most one
 * active row at a time.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_sessions", indexes = {
        @Index(name = "idx_user_sessions_token_id", columnList = "token_id", unique = true),
        @Index(name = "idx_user_sessions_user_active", columnList = "user_id, active")
})
public class UserSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The JWT "jti" claim. The raw token itself is never persisted. */
    @Column(name = "token_id", nullable = false, length = 64)
    private String tokenId;

    @Column(name = "device_info", length = 300)
    private String deviceInfo;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 30)
    private SessionRevokeReason revokeReason;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public void revoke(SessionRevokeReason reason) {
        this.active = false;
        this.revokeReason = reason;
        this.revokedAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
