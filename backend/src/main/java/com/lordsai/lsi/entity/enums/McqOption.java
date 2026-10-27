package com.lordsai.lsi.entity.enums;

/** The four answer options every MCQ has. Stored as a single character. */
public enum McqOption {
    A, B, C, D;

    public static McqOption parse(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase();
        return v.isEmpty() ? null : McqOption.valueOf(v);
    }
}
