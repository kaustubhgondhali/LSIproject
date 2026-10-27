package com.lordsai.lsi.entity.enums;

public enum CommunicationStatus {
    /** Queued (bulk sends are processed in batches). */
    PENDING,
    SENT,
    /** Provider rejected it, was unreachable, or is not configured. Retryable. */
    FAILED,
    /**
     * WhatsApp message prepared for the office: the chat is waiting to be opened on the office
     * computer (WhatsApp's own "click to chat" — no WhatsApp Business API is involved).
     */
    READY,
    /**
     * WhatsApp chat opened on the office computer with the message already typed; the office
     * presses Send there. The system cannot see that press, so it never claims SENT for these.
     */
    OPENED
}
