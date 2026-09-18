package com.lordsai.lsi.entity.enums;

/**
 * Exam application lifecycle: PENDING (submitted) -> APPROVED (admin review) -> SCHEDULED
 * (window set) -> COMPLETED (passed, or every allowed attempt used). REJECTED ends it early.
 */
public enum ExamApplicationStatus {
    PENDING, APPROVED, SCHEDULED, COMPLETED, REJECTED;

    /** PENDING / APPROVED / SCHEDULED — a student can hold only one of these per course. */
    public boolean isOpen() {
        return this == PENDING || this == APPROVED || this == SCHEDULED;
    }
}
