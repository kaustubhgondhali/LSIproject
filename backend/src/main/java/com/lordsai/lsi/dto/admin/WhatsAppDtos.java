package com.lordsai.lsi.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Main Admin -> Settings -> WhatsApp Settings. No record here ever carries the access token. */
public final class WhatsAppDtos {

    private WhatsAppDtos() {
    }

    /** Full status with the secret masked; what the settings screen renders. */
    public record WhatsAppStatus(
            boolean configured,
            /** ADMIN (saved in Settings), ENVIRONMENT (server variables) or null. */
            String credentialSource,
            String message,
            boolean recordExists,
            boolean enabled,
            String provider,
            String phoneNumberId,
            String businessAccountId,
            String displayPhoneNumber,
            String accessTokenMasked,
            boolean supportsDocuments,
            Instant validatedAt,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            String updatedBy,
            List<String> providers
    ) {
    }

    public record SaveWhatsAppRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Z_]+$", message = "Choose a provider.") String provider,
            /** Blank on update means "keep the saved token". */
            @Size(max = 1500) String accessToken,
            @NotBlank @Size(max = 50) @Pattern(regexp = "^[0-9]+$", message = "Phone Number ID is the numeric ID from Meta, not the phone number.") String phoneNumberId,
            @Size(max = 50) @Pattern(regexp = "^[0-9]*$", message = "Business Account ID must be numeric.") String businessAccountId
    ) {
    }

    public record TestMessageRequest(
            @NotBlank @Pattern(regexp = "^[0-9+\\s-]{10,18}$", message = "Enter a valid WhatsApp number with country code, e.g. 919876543210.") String mobile,
            @NotBlank @Size(max = 1000) String message
    ) {
    }

    public record WhatsAppTestResult(boolean ok, String message, String provider, String providerMessageId) {
    }
}
