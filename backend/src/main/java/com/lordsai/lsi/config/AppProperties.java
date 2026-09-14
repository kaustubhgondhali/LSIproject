package com.lordsai.lsi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Application settings bound from configuration/environment variables.
 * No secret ever has a usable default here — secrets come from the environment.
 */
@ConfigurationProperties(prefix = "lsi")
public record AppProperties(
        String publicBaseUrl,
        Cors cors,
        Jwt jwt,
        Storage storage,
        Support support,
        Mail mail
) {

    public record Mail(boolean enabled, String from) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Jwt(
            String secret,
            long accessTokenMinutes,
            long refreshTokenDays,
            String issuer
    ) {
    }

    public record Storage(
            String basePath,
            long maxVideoSizeMb,
            long maxDocumentSizeMb
    ) {
    }

    public record Support(String email, String phone) {
    }
}
