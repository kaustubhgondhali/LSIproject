package com.lordsai.lsi.entity.enums;

public enum PaymentMode {
    /** No real gateway configured: enrollment created without charging anyone. */
    DEMO,
    /** Real money via Razorpay, verified server-side. */
    RAZORPAY
}
