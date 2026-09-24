package com.lordsai.lsi.email.google;

import com.lordsai.lsi.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CSRF protection for the OAuth round trip.
 *
 * <p>The callback is necessarily a public endpoint — Google sends the administrator's browser
 * there as a plain top-level navigation with no Authorization header — so it must be able to prove
 * that the response belongs to an authorization request <em>this</em> server started for
 * <em>that</em> administrator. The {@code state} parameter carries that proof:
 *
 * <ul>
 *   <li>it is signed with HMAC-SHA256 under a key derived from the server's JWT secret, so it
 *       cannot be forged;</li>
 *   <li>it names the admin who started the flow, so the callback credits the right person;</li>
 *   <li>it expires after ten minutes;</li>
 *   <li>it is single-use — the nonce is consumed on the first callback, so a captured callback URL
 *       cannot be replayed.</li>
 * </ul>
 *
 * <p>The pending set lives in memory. A restart mid-authorisation simply means the admin clicks
 * Connect again, which is the correct outcome for an abandoned flow.
 */
@Component
public class GoogleOAuthStateService {

    private static final long TTL_SECONDS = 600;
    private static final int MAX_PENDING = 100;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    /** nonce -> expiry. Presence means "issued and not yet used". */
    private final Map<String, Instant> pending = new ConcurrentHashMap<>();

    private final byte[] key;

    public GoogleOAuthStateService(AppProperties properties) {
        try {
            this.key = MessageDigest.getInstance("SHA-256")
                    .digest((properties.jwt().secret() + ":google-oauth-state").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialise OAuth state signing", e);
        }
    }

    /** Mints a single-use state value for an authorization request started by this admin. */
    public String issue(Long adminUserId) {
        evictExpired();
        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);
        String nonce = B64.encodeToString(raw);
        String payload = nonce + ":" + adminUserId + ":" + (Instant.now().getEpochSecond() + TTL_SECONDS);
        pending.put(nonce, Instant.now().plusSeconds(TTL_SECONDS));
        return B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + sign(payload);
    }

    /**
     * Validates and consumes a state value returned by Google.
     *
     * @return the id of the admin who started the flow, or empty when the state is forged, expired,
     *         already used, or simply not one of ours
     */
    public Optional<Long> consume(String state) {
        evictExpired();
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        int dot = state.lastIndexOf('.');
        if (dot <= 0 || dot == state.length() - 1) {
            return Optional.empty();
        }
        String payload;
        try {
            payload = new String(B64D.decode(state.substring(0, dot)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        // Constant-time comparison: a timing oracle on the signature would let a state be forged.
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
                state.substring(dot + 1).getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        String[] parts = payload.split(":");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            if (Instant.now().getEpochSecond() > Long.parseLong(parts[2])) {
                pending.remove(parts[0]);
                return Optional.empty();
            }
            // Single use: removal returning null means this state was already redeemed.
            if (pending.remove(parts[0]) == null) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return B64.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign OAuth state", e);
        }
    }

    /** Keeps abandoned authorisations from accumulating; the map is tiny by design. */
    private void evictExpired() {
        Instant now = Instant.now();
        pending.entrySet().removeIf(e -> e.getValue().isBefore(now));
        // A flood of Connect clicks must not grow the map without bound; the oldest go first.
        while (pending.size() > MAX_PENDING) {
            String oldest = null;
            Instant oldestAt = null;
            for (Map.Entry<String, Instant> e : pending.entrySet()) {
                if (oldestAt == null || e.getValue().isBefore(oldestAt)) {
                    oldest = e.getKey();
                    oldestAt = e.getValue();
                }
            }
            if (oldest == null) {
                return;
            }
            pending.remove(oldest);
        }
    }
}
