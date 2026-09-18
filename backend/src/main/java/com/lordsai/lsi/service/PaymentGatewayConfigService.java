package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.payment.GatewayDtos.GatewayStatus;
import com.lordsai.lsi.dto.payment.GatewayDtos.PublicPaymentMode;
import com.lordsai.lsi.dto.payment.GatewayDtos.SaveGatewayRequest;
import com.lordsai.lsi.entity.PaymentGatewayConfig;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.PaymentMode;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.payment.PaymentGateway;
import com.lordsai.lsi.repository.PaymentGatewayConfigRepository;
import com.lordsai.lsi.security.SecretCrypto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Admin-managed Razorpay configuration and the single source of truth for the payment mode:
 *   active validated credentials  -> PaymentMode.RAZORPAY
 *   otherwise                     -> PaymentMode.DEMO
 */
@Service
public class PaymentGatewayConfigService {

    private static final String MASK = "••••••••••••••••";

    private final PaymentGatewayConfigRepository repository;
    private final PaymentGateway gateway;
    private final SecretCrypto crypto;
    private final AuditService auditService;

    public PaymentGatewayConfigService(PaymentGatewayConfigRepository repository,
                                       PaymentGateway gateway,
                                       SecretCrypto crypto,
                                       AuditService auditService) {
        this.repository = repository;
        this.gateway = gateway;
        this.crypto = crypto;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PaymentMode currentMode() {
        return gateway.isConfigured() ? PaymentMode.RAZORPAY : PaymentMode.DEMO;
    }

    @Transactional(readOnly = true)
    public PublicPaymentMode publicMode() {
        PaymentMode mode = currentMode();
        return new PublicPaymentMode(mode, mode == PaymentMode.RAZORPAY ? "RAZORPAY" : null, gateway.currency(),
                mode == PaymentMode.RAZORPAY
                        ? "Secure payment powered by Razorpay."
                        : "Demo payment is currently enabled. No real money will be charged.");
    }

    @Transactional(readOnly = true)
    public GatewayStatus status() {
        Optional<PaymentGatewayConfig> cfg = repository.findByProvider(PaymentGatewayConfig.PROVIDER_RAZORPAY);
        PaymentMode mode = currentMode();
        return cfg.map(c -> new GatewayStatus(mode, gateway.isConfigured(), gateway.credentialSource(), true,
                        c.isEnabled(), c.getKeyId(), keyMode(c.getKeyId()), MASK,
                        c.getWebhookSecretEnc() == null ? null : MASK, c.getWebhookSecretEnc() != null,
                        c.getCurrency(), c.getValidatedAt(), c.getCreatedAt(), c.getUpdatedAt(),
                        c.getCreatedBy() == null ? null : c.getCreatedBy().getFullName(),
                        c.getUpdatedBy() == null ? null : c.getUpdatedBy().getFullName()))
                .orElseGet(() -> new GatewayStatus(mode, gateway.isConfigured(), gateway.credentialSource(), false,
                        false, gateway.isConfigured() ? gateway.publicKeyId() : null,
                        gateway.isConfigured() ? keyMode(gateway.publicKeyId()) : null,
                        null, null, false, gateway.currency(), null, null, null, null, null));
    }

    /**
     * Creates or updates the single Razorpay configuration. Real-payment mode is only activated
     * after the credentials are proven against Razorpay's API; a failed validation leaves the
     * previous state (and DEMO mode, if that was active) untouched.
     */
    @Transactional
    public GatewayStatus save(SaveGatewayRequest req, User actor, String ip) {
        Optional<PaymentGatewayConfig> existing = repository.findByProvider(PaymentGatewayConfig.PROVIDER_RAZORPAY);
        String keyId = req.keyId().trim();
        String secret = req.keySecret() == null ? "" : req.keySecret().trim();

        if (secret.isBlank()) {
            if (existing.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Key Secret is required.");
            }
            secret = crypto.decrypt(existing.get().getKeySecretEnc());
        }

        PaymentGateway.ValidationResult result = gateway.validateCredentials(keyId, secret);
        if (!result.ok()) {
            auditService.record(actor, "RAZORPAY_CONFIG_REJECTED", "PaymentGatewayConfig",
                    existing.map(PaymentGatewayConfig::getId).orElse(null), "Validation failed for key " + keyId, ip);
            throw new ApiException(HttpStatus.BAD_REQUEST, result.message());
        }

        boolean wasRazorpay = currentMode() == PaymentMode.RAZORPAY;
        PaymentGatewayConfig cfg = existing.orElseGet(PaymentGatewayConfig::new);
        boolean created = cfg.getId() == null;
        cfg.setProvider(PaymentGatewayConfig.PROVIDER_RAZORPAY);
        cfg.setKeyId(keyId);
        cfg.setKeySecretEnc(crypto.encrypt(secret));
        if (req.webhookSecret() != null && !req.webhookSecret().isBlank()) {
            cfg.setWebhookSecretEnc(crypto.encrypt(req.webhookSecret().trim()));
        }
        if (req.currency() != null && !req.currency().isBlank()) {
            cfg.setCurrency(req.currency().trim().toUpperCase());
        }
        cfg.setEnabled(true);
        cfg.setValidatedAt(Instant.now());
        if (created) {
            cfg.setCreatedBy(actor);
        }
        cfg.setUpdatedBy(actor);
        cfg = repository.save(cfg);

        auditService.record(actor, created ? "RAZORPAY_CONFIG_CREATED" : "RAZORPAY_CONFIG_UPDATED",
                "PaymentGatewayConfig", cfg.getId(), "Key " + keyId + " (" + keyMode(keyId) + " mode) validated and enabled", ip);
        if (!wasRazorpay) {
            auditService.record(actor, "PAYMENT_MODE_RAZORPAY_ACTIVATED", "PaymentGatewayConfig", cfg.getId(),
                    "Real payments enabled via Razorpay (" + keyMode(keyId) + " mode)", ip);
        }
        return status();
    }

    @Transactional
    public GatewayStatus disable(User actor, String ip) {
        PaymentGatewayConfig cfg = repository.findByProvider(PaymentGatewayConfig.PROVIDER_RAZORPAY)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No Razorpay configuration exists."));
        cfg.setEnabled(false);
        cfg.setUpdatedBy(actor);
        repository.save(cfg);
        auditService.record(actor, "RAZORPAY_CONFIG_DISABLED", "PaymentGatewayConfig", cfg.getId(), "Key " + cfg.getKeyId(), ip);
        if (currentMode() == PaymentMode.DEMO) {
            auditService.record(actor, "PAYMENT_MODE_DEMO_ACTIVATED", "PaymentGatewayConfig", cfg.getId(),
                    "Demo payments active (Razorpay disabled)", ip);
        }
        return status();
    }

    @Transactional
    public GatewayStatus enable(User actor, String ip) {
        PaymentGatewayConfig cfg = repository.findByProvider(PaymentGatewayConfig.PROVIDER_RAZORPAY)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No Razorpay configuration exists."));
        PaymentGateway.ValidationResult result = gateway.validateCredentials(cfg.getKeyId(), crypto.decrypt(cfg.getKeySecretEnc()));
        if (!result.ok()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, result.message());
        }
        cfg.setEnabled(true);
        cfg.setValidatedAt(Instant.now());
        cfg.setUpdatedBy(actor);
        repository.save(cfg);
        auditService.record(actor, "RAZORPAY_CONFIG_ENABLED", "PaymentGatewayConfig", cfg.getId(), "Key " + cfg.getKeyId(), ip);
        auditService.record(actor, "PAYMENT_MODE_RAZORPAY_ACTIVATED", "PaymentGatewayConfig", cfg.getId(), "Real payments re-enabled", ip);
        return status();
    }

    @Transactional
    public GatewayStatus remove(User actor, String ip) {
        PaymentGatewayConfig cfg = repository.findByProvider(PaymentGatewayConfig.PROVIDER_RAZORPAY)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No Razorpay configuration exists."));
        Long id = cfg.getId();
        String keyId = cfg.getKeyId();
        repository.delete(cfg);
        auditService.record(actor, "RAZORPAY_CONFIG_REMOVED", "PaymentGatewayConfig", id, "Removed key " + keyId, ip);
        if (currentMode() == PaymentMode.DEMO) {
            auditService.record(actor, "PAYMENT_MODE_DEMO_ACTIVATED", "PaymentGatewayConfig", id,
                    "Demo payments active (Razorpay configuration removed)", ip);
        }
        return status();
    }

    private static String keyMode(String keyId) {
        if (keyId == null) {
            return null;
        }
        return keyId.startsWith("rzp_live_") ? "LIVE" : "TEST";
    }
}
