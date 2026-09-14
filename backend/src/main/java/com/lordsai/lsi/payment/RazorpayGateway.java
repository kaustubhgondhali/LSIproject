package com.lordsai.lsi.payment;

import com.lordsai.lsi.entity.PaymentGatewayConfig;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.repository.PaymentGatewayConfigRepository;
import com.lordsai.lsi.security.SecretCrypto;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import jakarta.annotation.PostConstruct;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Razorpay integration. Credentials are resolved on every call, in this order:
 *   1. the enabled, validated admin configuration stored in payment_gateway_config;
 *   2. RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET environment variables (fallback for ops-managed setups).
 * When neither exists the gateway reports "not configured" and the application runs in DEMO mode.
 */
@Service
public class RazorpayGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String UNAVAILABLE = "Payment gateway is temporarily unavailable. Please try again.";

    record Credentials(String keyId, String keySecret, String webhookSecret, String currency, String source) {
    }

    private final RazorpayProperties envProps;
    private final PaymentGatewayConfigRepository configRepository;
    private final SecretCrypto crypto;
    private final Map<String, RazorpayClient> clients = new ConcurrentHashMap<>();

    public RazorpayGateway(RazorpayProperties envProps,
                           PaymentGatewayConfigRepository configRepository,
                           SecretCrypto crypto) {
        this.envProps = envProps;
        this.configRepository = configRepository;
        this.crypto = crypto;
    }

    @PostConstruct
    void logStartupState() {
        if (envProps.isConfigured()) {
            log.info("Razorpay environment credentials present ({} mode). Admin-panel configuration, when saved, takes precedence.",
                    envProps.keyId().startsWith("rzp_live_") ? "LIVE" : "TEST");
        } else {
            log.info("No Razorpay environment credentials. Payment mode is decided by the admin panel configuration (DEMO until Razorpay is configured).");
        }
    }

    // ---- credential resolution ---------------------------------------------------------------

    Optional<Credentials> resolve() {
        Optional<PaymentGatewayConfig> cfg = configRepository.findByProvider(PaymentGatewayConfig.PROVIDER_RAZORPAY)
                .filter(PaymentGatewayConfig::isActive);
        if (cfg.isPresent()) {
            PaymentGatewayConfig c = cfg.get();
            return Optional.of(new Credentials(c.getKeyId(), crypto.decrypt(c.getKeySecretEnc()),
                    c.getWebhookSecretEnc() == null ? null : crypto.decrypt(c.getWebhookSecretEnc()),
                    c.getCurrency(), "ADMIN"));
        }
        if (envProps.isConfigured()) {
            return Optional.of(new Credentials(envProps.keyId(), envProps.keySecret(), envProps.webhookSecret(),
                    envProps.currency() == null ? "INR" : envProps.currency(), "ENVIRONMENT"));
        }
        return Optional.empty();
    }

    private Credentials require() {
        return resolve().orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Online payments are not configured. Please contact the academy."));
    }

    @Override
    public boolean isConfigured() {
        return resolve().isPresent();
    }

    @Override
    public String credentialSource() {
        return resolve().map(Credentials::source).orElse(null);
    }

    @Override
    public String publicKeyId() {
        return require().keyId();
    }

    @Override
    public String currency() {
        return resolve().map(Credentials::currency).orElse("INR");
    }

    // ---- validation --------------------------------------------------------------------------

    @Override
    public ValidationResult validateCredentials(String keyId, String keySecret) {
        if (keyId == null || !keyId.matches("^rzp_(test|live)_[A-Za-z0-9]{6,}$")) {
            return ValidationResult.failure("Key ID must look like rzp_test_XXXX or rzp_live_XXXX.");
        }
        if (keySecret == null || keySecret.trim().length() < 8) {
            return ValidationResult.failure("Key Secret looks too short.");
        }
        try {
            JSONObject params = new JSONObject();
            params.put("count", 1);
            new RazorpayClient(keyId, keySecret.trim()).orders.fetchAll(params);
            return ValidationResult.success();
        } catch (RazorpayException e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            if (m.toLowerCase().contains("authentication") || m.contains("401") || m.toLowerCase().contains("invalid")) {
                return ValidationResult.failure("Razorpay rejected these credentials. Check the Key ID and Key Secret.");
            }
            log.warn("Razorpay validation call failed: {}", m);
            return ValidationResult.failure("Could not reach Razorpay to verify the credentials. Check your internet connection and try again.");
        }
    }

    // ---- orders ------------------------------------------------------------------------------

    @Override
    public GatewayOrder createOrder(BigDecimal amountInr, String receipt, Map<String, String> notes) {
        Credentials c = require();
        long amountPaise = amountInr.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        try {
            JSONObject request = new JSONObject();
            request.put("amount", amountPaise);
            request.put("currency", c.currency());
            request.put("receipt", receipt);
            request.put("notes", new JSONObject(notes));
            Order order = client(c).orders.create(request);
            String id = order.get("id");
            return new GatewayOrder(id, amountPaise, c.currency());
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed for receipt {}", receipt, e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
        }
    }

    /** Razorpay signs HMAC_SHA256(order_id + "|" + payment_id, key_secret). */
    @Override
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }
        Optional<Credentials> c = resolve();
        if (c.isEmpty()) {
            return false;
        }
        String expected = hmacHex(orderId + "|" + paymentId, c.get().keySecret());
        return constantTimeEquals(expected, signature.trim().toLowerCase());
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        Optional<Credentials> c = resolve();
        if (rawBody == null || signature == null || c.isEmpty()
                || c.get().webhookSecret() == null || c.get().webhookSecret().isBlank()) {
            return false;
        }
        String expected = hmacHex(rawBody, c.get().webhookSecret());
        return constantTimeEquals(expected, signature.trim().toLowerCase());
    }

    // ---- helpers -----------------------------------------------------------------------------

    private RazorpayClient client(Credentials c) throws RazorpayException {
        String cacheKey = c.keyId() + ":" + c.keySecret().hashCode();
        RazorpayClient existing = clients.get(cacheKey);
        if (existing != null) {
            return existing;
        }
        RazorpayClient created = new RazorpayClient(c.keyId(), c.keySecret());
        clients.clear();
        clients.put(cacheKey, created);
        return created;
    }

    static String hmacHex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
