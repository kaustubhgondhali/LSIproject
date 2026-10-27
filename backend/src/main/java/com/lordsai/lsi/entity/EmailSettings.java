package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.MailAuthMode;
import com.lordsai.lsi.entity.enums.MailSecurityMode;
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
 * The single admin-managed SMTP configuration. The password is AES-GCM encrypted at rest and
 * is never returned by any API — only whether it is set.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "email_settings")
public class EmailSettings extends BaseEntity {

    @Column(name = "sender_name", nullable = false, length = 100)
    private String senderName;

    @Column(name = "sender_email", nullable = false, length = 190)
    private String senderEmail;

    @Column(name = "smtp_host", nullable = false, length = 255)
    private String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    private int smtpPort;

    @Column(name = "smtp_username", length = 190)
    private String smtpUsername;

    @Column(name = "smtp_password_enc", length = 512)
    private String smtpPasswordEnc;

    @Enumerated(EnumType.STRING)
    @Column(name = "security_mode", nullable = false, length = 20)
    private MailSecurityMode securityMode = MailSecurityMode.STARTTLS;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "test_recipient", length = 190)
    private String testRecipient;

    @Column(name = "last_tested_at")
    private Instant lastTestedAt;

    @Column(name = "last_test_ok")
    private Boolean lastTestOk;

    /** Provider code from MailProviderRegistry (GMAIL, OUTLOOK, … CUSTOM); null for rows saved before V15. */
    @Column(length = 40)
    private String provider;

    /** When "Test SMTP Connection" last succeeded against these exact settings. */
    @Column(name = "connection_verified_at")
    private Instant connectionVerifiedAt;

    @Column(name = "reply_to", length = 190)
    private String replyTo;

    @Column(name = "sending_domain", length = 190)
    private String sendingDomain;

    @Column(name = "dkim_selector", length = 100)
    private String dkimSelector;

    // ---- Gmail via Google OAuth 2.0 -----------------------------------------------------------
    // These live alongside the SMTP columns rather than replacing them: switching to OAuth leaves a
    // working SMTP configuration intact and switchable-back.

    /** SMTP (username + stored password) or GMAIL_OAUTH (Gmail API with a granted refresh token). */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_mode", nullable = false, length = 20)
    private MailAuthMode authMode = MailAuthMode.SMTP;

    /** The Google account the admin authorised. Safe to display; it is the visible "From" identity. */
    @Column(name = "google_email", length = 190)
    private String googleEmail;

    /**
     * The OAuth refresh token, AES-GCM encrypted with the same {@code SecretCrypto} as the SMTP
     * password. Never returned by an API, never logged. Access tokens are not persisted at all.
     */
    @Column(name = "google_refresh_token_enc", length = 1024)
    private String googleRefreshTokenEnc;

    /** The scopes Google actually granted, so the screen can warn if send permission was withheld. */
    @Column(name = "google_scope", length = 255)
    private String googleScope;

    @Column(name = "google_connected_at")
    private Instant googleConnectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "google_connected_by_user_id")
    private User googleConnectedBy;

    /** True when Gmail OAuth is the chosen mode and a refresh token is actually stored. */
    public boolean googleConnected() {
        return authMode == MailAuthMode.GMAIL_OAUTH && googleRefreshTokenEnc != null;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;
}
