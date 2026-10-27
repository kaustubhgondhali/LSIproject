package com.lordsai.lsi.dto.auth;

import com.lordsai.lsi.entity.enums.DeviceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class DeviceBindingDtos {

    private DeviceBindingDtos() {}

    public record DeviceRegisterRequest(
            @NotBlank String tempToken,
            @NotBlank @Size(min = 6, max = 6) String otp,
            String publicKey,
            String deviceName,
            String devicePlatform
    ) {}

    public record DeviceLinkRequest(
            @NotBlank String tempToken,
            @NotBlank @Size(min = 6, max = 6) String otp,
            String publicKey,
            String devicePlatform
    ) {}

    public record DeviceResetInitRequest(
            @NotBlank String identifier
    ) {}

    public record DeviceResetConfirmRequest(
            @NotBlank String tempToken,
            @NotBlank @Size(min = 6, max = 6) String otp
    ) {}

    public record StudentDeviceResponse(
            Long studentUserId,
            String studentId,
            String studentName,
            String deviceId,
            String deviceName,
            String devicePlatform,
            DeviceStatus deviceStatus,
            boolean resetRequired,
            Instant registeredAt,
            Instant lastSeenAt,
            Instant lastVerifiedAt
    ) {}

    public record DeviceChallengeRequest(
            @NotBlank String deviceId
    ) {}

    public record DeviceChallengeResponse(
            String challenge,
            Instant expiresAt
    ) {}
}

