package com.lordsai.lsi.service;

import com.lordsai.lsi.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * Private, expiring links to a payment receipt, an invoice or a message attachment, put into
 * WhatsApp messages so the student can open the PDF without logging in.
 *
 * A link is a signed reference, not a stored row: "{kind}.{ref}.{expiry day}.{signature}", the
 * signature being HMAC-SHA256 with a key derived from the server's secret. It cannot be guessed
 * or altered, and it stops working after {@link #LINK_DAYS} days. The same document on the same
 * day always gets the same link, so the message shown before sending equals the one logged.
 */
@Service
public class DocumentLinkService {

    public static final int LINK_DAYS = 30;
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    public enum Kind {
        RECEIPT("r"), INVOICE("i"), ATTACHMENT("a");

        private final String code;

        Kind(String code) {
            this.code = code;
        }

        static Optional<Kind> of(String code) {
            return Arrays.stream(values()).filter(k -> k.code.equals(code)).findFirst();
        }
    }

    /** What a valid link points at: a receipt / invoice id, or an attachment's storage path. */
    public record Target(Kind kind, String ref, LocalDate expiresOn) {
        public Long id() {
            return Long.valueOf(ref);
        }
    }

    public enum Check { VALID, EXPIRED, INVALID }

    public record Verified(Check check, Target target) {
    }

    private final byte[] key;
    private final String baseUrl;

    public DocumentLinkService(AppProperties properties) {
        String secret = properties.jwt() == null || properties.jwt().secret() == null ? "" : properties.jwt().secret();
        this.key = sha256(("lsi-document-link|" + secret).getBytes(StandardCharsets.UTF_8));
        String base = properties.publicBaseUrl() == null ? "" : properties.publicBaseUrl();
        this.baseUrl = base.replaceAll("/+$", "");
    }

    public String receiptLink(Long receiptId) {
        return url(token(Kind.RECEIPT, String.valueOf(receiptId)));
    }

    public String invoiceLink(Long invoiceId) {
        return url(token(Kind.INVOICE, String.valueOf(invoiceId)));
    }

    public String attachmentLink(String storagePath) {
        return url(token(Kind.ATTACHMENT, storagePath));
    }

    /** The public page that offers the PDF to the student. */
    private String url(String token) {
        return baseUrl + "/receipt.html?t=" + token;
    }

    String token(Kind kind, String ref) {
        return token(kind, ref, LocalDate.now(INDIA).plusDays(LINK_DAYS));
    }

    String token(Kind kind, String ref, LocalDate expiresOn) {
        String payload = kind.code + "." + b64(ref.getBytes(StandardCharsets.UTF_8)) + "." + expiresOn.toEpochDay();
        return payload + "." + sign(payload);
    }

    public Verified verify(String token) {
        if (token == null) {
            return new Verified(Check.INVALID, null);
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length != 4) {
            return new Verified(Check.INVALID, null);
        }
        String payload = parts[0] + "." + parts[1] + "." + parts[2];
        // Constant-time comparison: the signature cannot be discovered byte by byte.
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.US_ASCII), parts[3].getBytes(StandardCharsets.US_ASCII))) {
            return new Verified(Check.INVALID, null);
        }
        Optional<Kind> kind = Kind.of(parts[0]);
        String ref;
        long expiresDay;
        try {
            ref = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            expiresDay = Long.parseLong(parts[2]);
        } catch (IllegalArgumentException e) {
            return new Verified(Check.INVALID, null);
        }
        if (kind.isEmpty() || ref.isBlank()) {
            return new Verified(Check.INVALID, null);
        }
        LocalDate expiresOn = LocalDate.ofEpochDay(expiresDay);
        Target target = new Target(kind.get(), ref, expiresOn);
        return new Verified(LocalDate.now(INDIA).isAfter(expiresOn) ? Check.EXPIRED : Check.VALID, target);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] full = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return b64(Arrays.copyOf(full, 16));   // 128 bits: far beyond guessing
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
