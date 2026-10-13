package com.lordsai.lsi.email.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        return missingVariables().isEmpty();
    }

    /**
     * The variable NAMES (never values) that are unset, blank, or still the placeholder copied
     * from .env.example — so the admin screen can say exactly what to add.
     */
    public List<String> missingVariables() {
        List<String> missing = new ArrayList<>();
        if (!provided(clientId)) missing.add("GOOGLE_CLIENT_ID");
        if (!provided(clientSecret)) missing.add("GOOGLE_CLIENT_SECRET");
        if (!provided(redirectUri)) missing.add("GOOGLE_REDIRECT_URI");
        return missing;
    }

    /** The one explanation every "not configured" path shows. Names variables only, never values. */
    public String notConfiguredMessage() {
        List<String> missing = missingVariables();
        String names = String.join(" and ", missing);
        return "Google sign-in is not configured on this server: " + names + (missing.size() == 1 ? " is" : " are")
                + " not set. Add " + (missing.size() == 1 ? "it" : "them") + " to backend/.env (or set "
                + (missing.size() == 1 ? "it" : "them") + " as environment variables, like JWT_SECRET) and restart the backend. "
                + "The values come from your OAuth 2.0 Client ID (type Web application) in Google Cloud Console.";
    }

    private static boolean provided(String v) {
        return v != null && !v.isBlank() && !v.trim().toLowerCase(Locale.ROOT).startsWith("replace_with");
    }
}
