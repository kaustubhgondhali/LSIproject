package com.lordsai.lsi.security;

import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Issues short-lived tickets so a &lt;video&gt; element can stream one specific lesson without the
 * browser putting the account's bearer token in a URL. A ticket is bound to (lesson, user) and
 * the enrollment is re-checked on every request that presents it.
 */
@Service
public class StreamTicketService {

    public record Ticket(Long lessonId, Long userId) {
    }

    /** Short enough that a copied URL goes stale quickly; the player renews it silently on expiry. */
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String ALG = "HmacSHA256";

    private final byte[] secret;

    public StreamTicketService(AppProperties properties) {
        this.secret = (properties.jwt().secret() + ":stream").getBytes(StandardCharsets.UTF_8);
    }

    public String issue(Long lessonId, Long userId) {
        long exp = Instant.now().plus(TTL).getEpochSecond();
        String payload = lessonId + "." + userId + "." + exp;
        return payload + "." + sign(payload);
    }

    public Ticket verify(String ticket, Long expectedLessonId) {
        if (ticket == null) {
            throw invalid();
        }
        String[] parts = ticket.split("\\.");
        if (parts.length != 4) {
            throw invalid();
        }
        String payload = parts[0] + "." + parts[1] + "." + parts[2];
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8), parts[3].getBytes(StandardCharsets.UTF_8))) {
            throw invalid();
        }
        try {
            long lessonId = Long.parseLong(parts[0]);
            long userId = Long.parseLong(parts[1]);
            long exp = Long.parseLong(parts[2]);
            if (lessonId != expectedLessonId || Instant.now().getEpochSecond() > exp) {
                throw invalid();
            }
            return new Ticket(lessonId, userId);
        } catch (NumberFormatException e) {
            throw invalid();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(new SecretKeySpec(secret, ALG));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.FORBIDDEN, "This video link is invalid or has expired. Please reload the lesson.");
    }
}
