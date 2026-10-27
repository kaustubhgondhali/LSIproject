package com.lordsai.lsi.email.google;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * What Google's token endpoint handed back. This type never leaves the backend: it is not a DTO,
 * it is never serialised into an API response, and {@link #toString()} is overridden so an
 * accidental log statement or stack trace cannot print the tokens.
 *
 * @param accessToken  short-lived, kept in memory only
 * @param refreshToken present on the first authorisation, absent on a refresh
 * @param expiresAt    when {@code accessToken} stops working
 * @param scope        the permissions Google actually granted
 * @param email        the authorised Google address, when the ID token carried it
 */
public record GoogleTokens(String accessToken, String refreshToken, Instant expiresAt, String scope, String email) {

    static GoogleTokens from(JsonNode json, String email) {
        long expiresIn = json.path("expires_in").asLong(3600L);
        return new GoogleTokens(
                json.path("access_token").asText(null),
                json.path("refresh_token").asText(null),
                // 60s of headroom so a token never expires mid-send.
                Instant.now().plusSeconds(Math.max(60, expiresIn) - 60),
                json.path("scope").asText(null),
                email);
    }

    public boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    /** True when Google granted permission to send mail, which is the whole point of the connection. */
    public boolean grantsSend() {
        return scope != null && scope.contains(GoogleOAuthClient.SEND_SCOPE);
    }

    public boolean expired() {
        return expiresAt == null || Instant.now().isAfter(expiresAt);
    }

    /** Never print the tokens — not in a log line, not in a debugger, not in a stack trace. */
    @Override
    public String toString() {
        return "GoogleTokens[email=" + email + ", scope=" + scope + ", expiresAt=" + expiresAt
                + ", accessToken=***, refreshToken=" + (hasRefreshToken() ? "***" : "none") + "]";
    }
}
