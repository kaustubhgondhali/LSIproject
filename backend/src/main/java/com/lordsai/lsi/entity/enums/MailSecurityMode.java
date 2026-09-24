package com.lordsai.lsi.entity.enums;

/** How the SMTP connection is secured. */
public enum MailSecurityMode {
    /** Plain connection (local relays only). */
    NONE,
    /** Plain connection upgraded with STARTTLS — typical on port 587. */
    STARTTLS,
    /** Implicit TLS from the first byte — typical on port 465. */
    SSL_TLS
}
