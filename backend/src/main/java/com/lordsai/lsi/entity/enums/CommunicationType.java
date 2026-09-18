package com.lordsai.lsi.entity.enums;

/** What a communication_logs row was about; shown as "Message type" in the history screens. */
public enum CommunicationType {
    COURSE_PURCHASE,
    EBOOK_PURCHASE,
    EXISTING_STUDENT_PURCHASE,
    INVOICE,
    ADMIN_INDIVIDUAL,
    ADMIN_BULK,
    /** Automatic exam workflow emails: application received, exam scheduled, result, certificate. */
    EXAM_NOTIFICATION
}
