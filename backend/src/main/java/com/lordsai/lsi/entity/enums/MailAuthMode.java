package com.lordsai.lsi.entity.enums;

/**
 * How the academy's outgoing mailbox authenticates.
 *
 * <p>Both modes share the one {@code email_settings} row: switching to {@link #GMAIL_OAUTH} leaves
 * the SMTP host/port/username columns untouched, so an existing SMTP setup remains available as a
 * fallback and can be switched back on without re-entering anything.
 */
public enum MailAuthMode {

    /** Classic SMTP with a username and a stored password / App Password. */
    SMTP,

    /**
     * Google OAuth 2.0: the admin authorises the academy's Google account once and mail is sent
     * through the Gmail API. No Google password or App Password is ever held by this application.
     */
    GMAIL_OAUTH
}
