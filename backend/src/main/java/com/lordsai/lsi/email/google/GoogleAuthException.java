package com.lordsai.lsi.email.google;

/**
 * A Google OAuth / Gmail API call could not be completed.
 *
 * <p>The message is always safe to show an administrator: it never contains the client secret,
 * an authorization code, an access token or a refresh token.
 */
public class GoogleAuthException extends RuntimeException {

    /** True when the stored authorisation is gone for good and the admin has to reconnect. */
    private final boolean reconnectRequired;

    public GoogleAuthException(String message, boolean reconnectRequired) {
        super(message);
        this.reconnectRequired = reconnectRequired;
    }

    public GoogleAuthException(String message, boolean reconnectRequired, Throwable cause) {
        super(message, cause);
        this.reconnectRequired = reconnectRequired;
    }

    public boolean isReconnectRequired() {
        return reconnectRequired;
    }

    /** The one message the admin screen shows when Google authorisation has gone away. */
    public static GoogleAuthException revoked() {
        return new GoogleAuthException(
                "Google account authorization has expired or been revoked. Please reconnect your Google account.", true);
    }
}
