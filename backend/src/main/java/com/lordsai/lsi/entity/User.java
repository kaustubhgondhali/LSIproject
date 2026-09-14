package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Authentication identity for every role. Role-specific data lives in
 * {@link StudentProfile}. The password is only ever stored as a BCrypt hash.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_role", columnList = "role")
})
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 190)
    private String email;

    @Column(length = 20)
    private String mobile;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.PENDING_SETUP;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * Incremented whenever the password changes. Embedded in JWTs so every
     * previously issued token becomes invalid immediately.
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }
}
