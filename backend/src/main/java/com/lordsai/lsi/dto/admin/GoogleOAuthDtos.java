package com.lordsai.lsi.dto.admin;

import java.time.Instant;

/**
 * What the Email Settings screen is allowed to know about the Google connection.
 *
 * <p>There is deliberately no field here for an access token, a refresh token, an authorization
 * code or the client secret. These records are the only shape the Google endpoints return, so
 * those values cannot reach the browser even by accident.
 */
public final class GoogleOAuthDtos {

    private GoogleOAuthDtos() {
    }

    /** Safe connection information for the admin screen. */
    public record GoogleStatus(
            /** True once GOOGLE_CLIENT_ID / SECRET / REDIRECT_URI are present on the server. */
            boolean configured,
            boolean connected,
            /** The authorised Gmail address — shown so the admin can see which account is in use. */
            String email,
            Instant connectedAt,
            String connectedBy,
            /** False if Google withheld send permission, which would make sending fail later. */
            boolean sendPermissionGranted,
            /** The exact callback URL this server uses, to register in Google Cloud. Not a secret. */
            String redirectUri,
            /** True when a Google account is connected AND it is the active sending channel. */
            boolean sendingThroughGmail,
            String message
    ) {
    }

    /** Where to send the admin's browser to begin authorisation. Contains no secret. */
    public record GoogleAuthorizationUrl(String authorizationUrl) {
    }
}
