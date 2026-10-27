package com.lordsai.lsi.entity.enums;

/** What a communication_logs row was about; shown as "Message type" in the history screens. */
public enum CommunicationType {
    COURSE_PURCHASE,
    EBOOK_PURCHASE,
    EXISTING_STUDENT_PURCHASE,
    INVOICE,
    ADMIN_INDIVIDUAL,
    ADMIN_BULK,
    /** Legacy: emails of the former exam workflow. Kept so their communication-log history still loads. */
    EXAM_NOTIFICATION,
    /** Automation Admin: an academy payment receipt (PDF) sent by email / WhatsApp. */
    FEE_RECEIPT,
    /** Automation Admin > Pending Fees: a reminder that part of the course fee is still to be paid. */
    FEE_REMINDER,
    /** Course completion certificate: request approved (certificate available) or rejected. */
    CERTIFICATE_NOTIFICATION
}
