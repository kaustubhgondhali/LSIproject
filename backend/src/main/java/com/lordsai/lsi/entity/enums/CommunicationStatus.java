package com.lordsai.lsi.entity.enums;

public enum CommunicationStatus {
    /** Queued (bulk sends are processed in batches). */
    PENDING,
    SENT,
    /** Provider rejected it, was unreachable, or is not configured. Retryable. */
    FAILED
}
