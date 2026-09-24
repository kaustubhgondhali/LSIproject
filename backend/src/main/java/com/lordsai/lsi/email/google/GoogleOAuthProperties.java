package com.lordsai.lsi.email.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Cloud OAuth client settings. These come from the environment
 * (GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET / GOOGLE_REDIRECT_URI) — never from source code,
 * and the secret is never returned by any API or written to a log.
 */
@ConfigurationProperties(prefix = "lsi.google")
public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        /** Must match an "Authorized redirect URI" on the OAuth client in Google Cloud, character for character. */
        String redirectUri
) {

    /** True once the three environment variables are present, so the screen can say what is missing. */
    public boolean isConfigured() {
        return notBlank(clientId) && notBlank(clientSecret) && notBlank(redirectUri);
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }
}
