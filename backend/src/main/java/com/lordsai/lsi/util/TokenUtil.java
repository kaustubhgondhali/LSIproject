package com.lordsai.lsi.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class TokenUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenUtil() {
    }

    /** URL-safe random token suitable for emailed setup/reset links. */
    public static String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * A 12-character temporary password that satisfies the login rule (letters + digits) and
     * avoids look-alike characters. Only its BCrypt hash is stored; the value lives in one email.
     */
    public static String generateTemporaryPassword() {
        final String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String lower = "abcdefghijkmnpqrstuvwxyz";
        final String digits = "23456789";
        final String all = upper + lower + digits;
        char[] out = new char[12];
        out[0] = upper.charAt(RANDOM.nextInt(upper.length()));
        out[1] = lower.charAt(RANDOM.nextInt(lower.length()));
        out[2] = digits.charAt(RANDOM.nextInt(digits.length()));
        for (int i = 3; i < out.length; i++) {
            out[i] = all.charAt(RANDOM.nextInt(all.length()));
        }
        for (int i = out.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char t = out[i]; out[i] = out[j]; out[j] = t;
        }
        return new String(out);
    }

    /** Only this hash is ever stored; the raw token lives solely in the emailed link. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
