package com.lordsai.lsi.entity.enums;

/**
 * Student Admin -> Certificate -> "Apply for Certificate".
 * PENDING until the admin decides; approving generates the certificate in the same step, so an
 * approved request is stored as GENERATED (approved + certificate available). REJECTED may be
 * re-applied for. APPROVED is kept for completeness of the workflow vocabulary.
 */
public enum CertificateRequestStatus {
    PENDING, APPROVED, REJECTED, GENERATED;

    /** Active requests block a second application for the same course. */
    public boolean isActive() {
        return this != REJECTED;
    }
}
