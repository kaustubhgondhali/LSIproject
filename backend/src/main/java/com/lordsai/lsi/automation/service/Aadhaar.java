package com.lordsai.lsi.automation.service;

/**
 * The Aadhaar number of an academy student: 12 digits, never starting with 0 or 1, whose last
 * digit is the Verhoeff check digit UIDAI uses — so a mistyped or swapped digit is caught on the
 * form. Shown masked (XXXX XXXX 1234) everywhere except the edit form.
 */
public final class Aadhaar {

    public static final String INVALID = "Enter a valid 12-digit Aadhaar number (please check the digits).";

    private static final int[][] D = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, {1, 2, 3, 4, 0, 6, 7, 8, 9, 5}, {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
            {3, 4, 0, 1, 2, 8, 9, 5, 6, 7}, {4, 0, 1, 2, 3, 9, 5, 6, 7, 8}, {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
            {6, 5, 9, 8, 7, 1, 0, 4, 3, 2}, {7, 6, 5, 9, 8, 2, 1, 0, 4, 3}, {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
            {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}};
    private static final int[][] P = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, {1, 5, 7, 6, 2, 8, 3, 0, 9, 4}, {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
            {8, 9, 1, 6, 0, 4, 3, 5, 2, 7}, {9, 4, 5, 3, 1, 2, 6, 8, 7, 0}, {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
            {2, 7, 9, 3, 8, 0, 6, 4, 1, 5}, {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}};
    private static final int[] INV = {0, 4, 3, 2, 1, 5, 6, 7, 8, 9};

    private Aadhaar() {
    }

    /** "1234 5678 9012" / "1234-5678-9012" -> "123456789012"; null when blank. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replace(" ", "").replace("-", "").trim();
        return s.isEmpty() ? null : s;
    }

    /** True for 12 digits, first digit 2-9, with a correct Verhoeff check digit. */
    public static boolean isValid(String digits) {
        if (digits == null || !digits.matches("^[2-9][0-9]{11}$")) {
            return false;
        }
        int c = 0;
        for (int i = 0; i < digits.length(); i++) {
            c = D[c][P[i % 8][digits.charAt(digits.length() - 1 - i) - '0']];
        }
        return c == 0;
    }

    /** The check digit that completes these 11 digits (used to make valid sample numbers). */
    public static int checkDigit(String elevenDigits) {
        int c = 0;
        for (int i = 0; i < elevenDigits.length(); i++) {
            c = D[c][P[(i + 1) % 8][elevenDigits.charAt(elevenDigits.length() - 1 - i) - '0']];
        }
        return INV[c];
    }

    /** "123456789012" -> "XXXX XXXX 9012"; null stays null. */
    public static String mask(String digits) {
        if (digits == null || digits.length() < 4) {
            return null;
        }
        return "XXXX XXXX " + digits.substring(digits.length() - 4);
    }
}
