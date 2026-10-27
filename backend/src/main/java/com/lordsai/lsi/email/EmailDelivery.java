package com.lordsai.lsi.email;

/**
 * Outcome of a single send attempt. Returned instead of swallowed so the purchase flow can tell
 * the student the truth ("check your inbox" vs "we could not email you — resend") rather than
 * reporting success for an email that was never delivered.
 */
public record EmailDelivery(Status status, String reason, String provider) {

    public EmailDelivery(Status status, String reason) {
        this(status, reason, null);
    }

    public enum Status {
        /** Handed to the SMTP server. */
        SENT,
        /** lsi.mail.enabled=false — the message was written to the log instead of being sent. */
        DISABLED,
        /** SMTP rejected it or was unreachable. */
        FAILED
    }

    public static EmailDelivery sent() {
        return new EmailDelivery(Status.SENT, null);
    }

    /** Delivered, recording which channel carried it (ADMIN / GOOGLE_OAUTH / ENVIRONMENT). */
    public static EmailDelivery sent(String provider) {
        return new EmailDelivery(Status.SENT, null, provider);
    }

    public static EmailDelivery disabled() {
        return disabled("No SMTP server is configured. Save Email Settings in the Main Admin panel"
                + " (or set MAIL_ENABLED=true on the server).");
    }

    public static EmailDelivery disabled(String reason) {
        return new EmailDelivery(Status.DISABLED, reason);
    }

    public static EmailDelivery failed(String reason) {
        return new EmailDelivery(Status.FAILED, reason);
    }

    public boolean delivered() {
        return status == Status.SENT;
    }
}
