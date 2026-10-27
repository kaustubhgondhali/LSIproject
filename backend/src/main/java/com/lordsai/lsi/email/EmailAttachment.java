package com.lordsai.lsi.email;

/** A file attached to an outgoing email (e.g. the invoice PDF). Bytes are never logged. */
public record EmailAttachment(String filename, String contentType, byte[] content) {

    public static EmailAttachment pdf(String filename, byte[] content) {
        return new EmailAttachment(filename, "application/pdf", content);
    }
}
