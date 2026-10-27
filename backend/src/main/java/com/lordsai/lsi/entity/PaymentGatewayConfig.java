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
 * One row per provider (currently only RAZORPAY). Secrets are stored AES-GCM encrypted and are
 * never returned by any API — only whether they are set.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_gateway_config")
public class PaymentGatewayConfig extends BaseEntity {

    public static final String PROVIDER_RAZORPAY = "RAZORPAY";

    @Column(nullable = false, length = 30, unique = true)
    private String provider = PROVIDER_RAZORPAY;

    @Column(name = "key_id", nullable = false, length = 100)
    private String keyId;

    @Column(name = "key_secret_enc", nullable = false, length = 512)
    private String keySecretEnc;

    @Column(name = "webhook_secret_enc", length = 512)
    private String webhookSecretEnc;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(nullable = false)
    private boolean enabled = true;

    /** Set when the credentials were last proven to work against the provider's API. */
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
