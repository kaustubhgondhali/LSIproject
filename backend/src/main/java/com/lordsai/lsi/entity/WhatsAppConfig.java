package com.lordsai.lsi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The ONE central WhatsApp configuration, managed from Main Admin -> Settings -> WhatsApp
 * Settings. The access token is stored encrypted and never leaves the server; every WhatsApp
 * send in the application (invoice on WhatsApp, Automation Admin automation) resolves this row
 * first and falls back to the WHATSAPP_* environment variables only when no enabled row exists.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "whatsapp_config")
public class WhatsAppConfig extends BaseEntity {

    @Column(nullable = false, length = 30, unique = true)
    private String provider;

    @Column(name = "access_token_enc", nullable = false, length = 2048)
    private String accessTokenEnc;

    @Column(name = "phone_number_id", nullable = false, length = 50)
    private String phoneNumberId;

    @Column(name = "business_account_id", length = 50)
    private String businessAccountId;

    @Column(name = "api_url", length = 200)
    private String apiUrl;

    @Column(name = "api_version", length = 20)
    private String apiVersion;

    /** Human-readable number reported by the provider when the credentials were validated. */
    @Column(name = "display_phone_number", length = 40)
    private String displayPhoneNumber;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Set when the credentials were last proven against the provider; null = never validated. */
    @Column(name = "validated_at")
    private Instant validatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    public boolean isActive() {
        return enabled && validatedAt != null;
    }
}
