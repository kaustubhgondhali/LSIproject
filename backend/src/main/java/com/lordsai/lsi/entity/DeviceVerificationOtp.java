package com.lordsai.lsi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Temporary verification OTP for device registration, linking secondary browsers
 * on the registered computer, or initiating device recovery/reset.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "device_verification_otps", indexes = {
        @Index(name = "idx_device_otps_user_purpose", columnList = "user_id, purpose, expires_at")
})
public class DeviceVerificationOtp extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "otp_hash", nullable = false, length = 100)
    private String otpHash;

    @Column(nullable = false, length = 30)
    private String purpose; // REGISTRATION, LINK_BROWSER, DEVICE_RESET

    @Column(name = "target_device_id", length = 64)
    private String targetDeviceId;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Column(name = "device_platform", length = 500)
    private String devicePlatform;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return usedAt == null && !isExpired() && attempts < 5;
    }
}

