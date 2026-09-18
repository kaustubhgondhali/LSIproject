package com.lordsai.lsi.entity.enums;

/**
 * The digital products the purchase pipeline (Razorpay order -> verify/webhook -> entitlement ->
 * invoice -> email) can sell. Every {@code Payment} and {@code Invoice} carries one of these, so a
 * future product only needs a new value here plus its own entitlement service.
 */
public enum ProductType {
    COURSE("Course"),
    EBOOK("Ebook");

    private final String label;

    ProductType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
