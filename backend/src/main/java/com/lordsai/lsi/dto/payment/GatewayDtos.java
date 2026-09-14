package com.lordsai.lsi.dto.payment;

import com.lordsai.lsi.entity.enums.PaymentMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class GatewayDtos {

    private GatewayDtos() {
    }

    /** Public: what the checkout needs to decide between demo and real payment. */
    public record PublicPaymentMode(PaymentMode mode, String provider, String currency, String message) {
    }

    /** Admin: full status with secrets masked. */
    public record GatewayStatus(
            PaymentMode mode,
            boolean configured,
            String credentialSource,
            boolean recordExists,
            boolean enabled,
            String keyId,
            String keyMode,
            String keySecretMasked,
            String webhookSecretMasked,
            boolean webhookSecretSet,
            String currency,
            Instant validatedAt,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            String updatedBy
    ) {
    }

    public record SaveGatewayRequest(
            @NotBlank @Size(max = 100)
            @Pattern(regexp = "^rzp_(test|live)_[A-Za-z0-9]+$", message = "Key ID must start with rzp_test_ or rzp_live_.")
            String keyId,
            /** Blank on update means "keep the existing secret". */
            @Size(max = 200) String keySecret,
            @Size(max = 200) String webhookSecret,
            @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code.") String currency
    ) {
    }

    public record DemoPaymentRequest(
            @jakarta.validation.constraints.NotNull Long courseId,
            @NotBlank @Size(min = 2, max = 150) String fullName,
            @NotBlank @jakarta.validation.constraints.Email @Size(max = 190) String email,
            @NotBlank @Pattern(regexp = com.lordsai.lsi.dto.user.UserDtos.MOBILE_PATTERN,
                    message = com.lordsai.lsi.dto.user.UserDtos.MOBILE_MESSAGE) String mobile
    ) {
    }
}
