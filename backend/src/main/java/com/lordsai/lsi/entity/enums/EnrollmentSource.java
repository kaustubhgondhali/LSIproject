package com.lordsai.lsi.entity.enums;

public enum EnrollmentSource {
    /** Created automatically after a verified Razorpay payment. */
    PAYMENT,
    /** Created manually by an administrator. Always shown as such in the admin UI. */
    ADMIN_MANUAL
}
