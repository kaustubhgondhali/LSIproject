package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.DeviceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Enforces ONE STUDENT ACCOUNT = ONE REGISTERED DEVICE.
 * Binds a student account to their single registered physical computer, storing
 * cryptographic public key and secret token hash.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_devices", indexes = {
        @Index(name = "idx_student_devices_user_status", columnList = "user_id, device_status"),
        @Index(name = "idx_student_devices_device_id", columnList = "device_id")
})
public class StudentDevice extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "device_id", nullable = false, unique = true, length = 64)
    private String deviceId;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Column(name = "device_platform", length = 500)
    private String devicePlatform;

    @Column(name = "device_public_key", columnDefinition = "TEXT")
    private String devicePublicKey;

    @Column(name = "device_secret_hash", nullable = false, length = 100)
    private String deviceSecretHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_status", nullable = false, length = 30)
    private DeviceStatus deviceStatus = DeviceStatus.ACTIVE;

    @Column(name = "reset_required", nullable = false)
    private boolean resetRequired = false;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "last_verified_at", nullable = false)
    private Instant lastVerifiedAt;

    @jakarta.persistence.PrePersist
    public void onPrePersist() {
        Instant now = Instant.now();
        if (registeredAt == null) registeredAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
        if (lastVerifiedAt == null) lastVerifiedAt = now;
    }

    public boolean isActive() {
        return deviceStatus == DeviceStatus.ACTIVE && !resetRequired;
    }
}

