package com.lordsai.lsi.service;

import com.lordsai.lsi.communication.WhatsAppDelivery;
import com.lordsai.lsi.communication.WhatsAppMessageService;
import com.lordsai.lsi.communication.WhatsAppProvider;
import com.lordsai.lsi.communication.WhatsAppSettings;
import com.lordsai.lsi.dto.admin.WhatsAppDtos.SaveWhatsAppRequest;
import com.lordsai.lsi.dto.admin.WhatsAppDtos.TestMessageRequest;
import com.lordsai.lsi.dto.admin.WhatsAppDtos.WhatsAppStatus;
import com.lordsai.lsi.dto.admin.WhatsAppDtos.WhatsAppTestResult;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.WhatsAppConfig;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.repository.WhatsAppConfigRepository;
import com.lordsai.lsi.security.SecretCrypto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * The central WhatsApp configuration managed by the Main Admin (mirrors the Razorpay
 * configuration): one row, token encrypted with the same SecretCrypto, credentials proven
 * against the provider before they are enabled, status always masked. Every WhatsApp send in
 * the application resolves this configuration through {@link WhatsAppMessageService}.
 */
@Service
public class WhatsAppConfigService {

    private static final String MASK = "••••••••••••••••••••••••";

    private final WhatsAppConfigRepository repository;
    private final WhatsAppMessageService whatsApp;
    private final SecretCrypto crypto;
    private final AuditService auditService;

    public WhatsAppConfigService(WhatsAppConfigRepository repository,
                                 WhatsAppMessageService whatsApp,
                                 SecretCrypto crypto,
                                 AuditService auditService) {
        this.repository = repository;
        this.whatsApp = whatsApp;
        this.crypto = crypto;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public WhatsAppStatus status() {
        Optional<WhatsAppConfig> cfg = repository.findFirstByOrderByIdAsc();
        boolean configured = whatsApp.isConfigured();
        String source = whatsApp.credentialSource();
        return cfg.map(c -> new WhatsAppStatus(configured, source, whatsApp.statusMessage(), true, c.isEnabled(),
                        c.getProvider(), c.getPhoneNumberId(), c.getBusinessAccountId(), c.getDisplayPhoneNumber(), MASK,
                        whatsApp.supportsDocuments(), c.getValidatedAt(), c.getCreatedAt(), c.getUpdatedAt(),
                        c.getCreatedBy() == null ? null : c.getCreatedBy().getFullName(),
                        c.getUpdatedBy() == null ? null : c.getUpdatedBy().getFullName(), whatsApp.providerNames()))
                .orElseGet(() -> {
                    Optional<WhatsAppSettings> env = whatsApp.resolve();
                    return new WhatsAppStatus(configured, source, whatsApp.statusMessage(), false, false,
                            env.map(WhatsAppSettings::provider).orElse(null),
                            env.map(WhatsAppSettings::phoneNumberId).orElse(null),
                            env.map(WhatsAppSettings::businessAccountId).orElse(null), null,
                            env.isPresent() ? MASK : null, whatsApp.supportsDocuments(), null, null, null, null, null,
                            whatsApp.providerNames());
                });
    }

    /**
     * Creates or updates the single configuration. The credentials are proven against the
     * provider (a read-only call) before they are enabled; a failed check leaves the previous
     * state untouched and never stores the rejected token.
     */
    @Transactional
    public WhatsAppStatus save(SaveWhatsAppRequest req, User actor, String ip) {
        Optional<WhatsAppConfig> existing = repository.findFirstByOrderByIdAsc();
        String provider = req.provider().trim().toUpperCase(Locale.ROOT);
        if (whatsApp.providerFor(provider).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown WhatsApp provider. Supported: " + String.join(", ", whatsApp.providerNames()) + ".");
        }
        String token = req.accessToken() == null ? "" : req.accessToken().trim();
        if (token.isBlank()) {
            if (existing.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Access Token is required.");
            }
            token = crypto.decrypt(existing.get().getAccessTokenEnc());
        }
        String phoneNumberId = req.phoneNumberId().trim();
        String businessAccountId = req.businessAccountId() == null || req.businessAccountId().isBlank() ? null : req.businessAccountId().trim();

        WhatsAppSettings candidate = new WhatsAppSettings(provider, null, null, token, phoneNumberId, businessAccountId, WhatsAppSettings.SOURCE_ADMIN);
        WhatsAppProvider.ValidationResult result = whatsApp.verify(candidate);
        if (!result.ok()) {
            auditService.record(actor, "WHATSAPP_CONFIG_REJECTED", "WhatsAppConfig",
                    existing.map(WhatsAppConfig::getId).orElse(null), "Validation failed for phone number ID " + phoneNumberId, ip);
            throw new ApiException(HttpStatus.BAD_REQUEST, result.message());
        }

        WhatsAppConfig cfg = existing.orElseGet(WhatsAppConfig::new);
        boolean created = cfg.getId() == null;
        cfg.setProvider(provider);
        cfg.setAccessTokenEnc(crypto.encrypt(token));
        cfg.setPhoneNumberId(phoneNumberId);
        cfg.setBusinessAccountId(businessAccountId);
        cfg.setDisplayPhoneNumber(result.displayPhoneNumber());
        cfg.setEnabled(true);
        cfg.setValidatedAt(Instant.now());
        if (created) {
            cfg.setCreatedBy(actor);
        }
        cfg.setUpdatedBy(actor);
        cfg = repository.save(cfg);
        auditService.record(actor, created ? "WHATSAPP_CONFIG_CREATED" : "WHATSAPP_CONFIG_UPDATED", "WhatsAppConfig", cfg.getId(),
                provider + " phone number ID " + phoneNumberId + " validated and enabled", ip);
        return status();
    }

    @Transactional
    public WhatsAppStatus disable(User actor, String ip) {
        WhatsAppConfig cfg = require();
        cfg.setEnabled(false);
        cfg.setUpdatedBy(actor);
        repository.save(cfg);
        auditService.record(actor, "WHATSAPP_CONFIG_DISABLED", "WhatsAppConfig", cfg.getId(), "Phone number ID " + cfg.getPhoneNumberId(), ip);
        return status();
    }

    @Transactional
    public WhatsAppStatus enable(User actor, String ip) {
        WhatsAppConfig cfg = require();
        WhatsAppProvider.ValidationResult result = whatsApp.verify(settingsOf(cfg));
        if (!result.ok()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, result.message());
        }
        cfg.setEnabled(true);
        cfg.setValidatedAt(Instant.now());
        cfg.setDisplayPhoneNumber(result.displayPhoneNumber());
        cfg.setUpdatedBy(actor);
        repository.save(cfg);
        auditService.record(actor, "WHATSAPP_CONFIG_ENABLED", "WhatsAppConfig", cfg.getId(), "Phone number ID " + cfg.getPhoneNumberId(), ip);
        return status();
    }

    @Transactional
    public WhatsAppStatus remove(User actor, String ip) {
        WhatsAppConfig cfg = require();
        Long id = cfg.getId();
        String phone = cfg.getPhoneNumberId();
        repository.delete(cfg);
        auditService.record(actor, "WHATSAPP_CONFIG_REMOVED", "WhatsAppConfig", id, "Removed phone number ID " + phone, ip);
        return status();
    }

    /** "Test WhatsApp Connection": a read-only check of the configuration currently in force. */
    @Transactional(readOnly = true)
    public WhatsAppTestResult testConnection(User actor, String ip) {
        WhatsAppProvider.ValidationResult r = whatsApp.verifyCurrent();
        auditService.record(actor, r.ok() ? "WHATSAPP_CONNECTION_OK" : "WHATSAPP_CONNECTION_FAILED", "WhatsAppConfig", null,
                r.ok() ? "Verified " + (r.displayPhoneNumber() == null ? "" : r.displayPhoneNumber()) : r.message(), ip);
        return new WhatsAppTestResult(r.ok(), r.message(), whatsApp.providerName(), null);
    }

    /** Sends one real message through the central configuration. The recipient is not stored anywhere. */
    @Transactional(readOnly = true)
    public WhatsAppTestResult sendTest(TestMessageRequest req, User actor, String ip) {
        if (!whatsApp.isConfigured()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, WhatsAppMessageService.NOT_CONFIGURED_ADMIN);
        }
        WhatsAppDelivery d = whatsApp.sendText(req.mobile(), req.message().trim() + "\n\n— Test message from Lord Sai Investment & Share Market Academy");
        String masked = "***" + req.mobile().replaceAll("\\D", "").replaceAll("^.*(\\d{4})$", "$1");
        auditService.record(actor, d.sent() ? "WHATSAPP_TEST_SENT" : "WHATSAPP_TEST_FAILED", "WhatsAppConfig", null,
                "Test message to " + masked + (d.sent() ? " via " + d.provider() : " — " + d.reason()), ip);
        if (!d.sent()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Test WhatsApp message could not be sent. " + d.reason());
        }
        return new WhatsAppTestResult(true, "Test WhatsApp message sent successfully.", d.provider(), d.providerMessageId());
    }

    private WhatsAppConfig require() {
        return repository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No WhatsApp configuration has been saved yet."));
    }

    private WhatsAppSettings settingsOf(WhatsAppConfig c) {
        return new WhatsAppSettings(c.getProvider(), c.getApiUrl(), c.getApiVersion(), crypto.decrypt(c.getAccessTokenEnc()),
                c.getPhoneNumberId(), c.getBusinessAccountId(), WhatsAppSettings.SOURCE_ADMIN);
    }
}
