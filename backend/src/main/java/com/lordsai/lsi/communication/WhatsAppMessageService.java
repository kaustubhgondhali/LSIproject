package com.lordsai.lsi.communication;

import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.entity.WhatsAppConfig;
import com.lordsai.lsi.repository.WhatsAppConfigRepository;
import com.lordsai.lsi.security.SecretCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * The single entry point for sending WhatsApp messages, and the ONE place credentials are
 * resolved from. Resolution order on every call:
 *   1. the enabled, validated central configuration saved in Main Admin -> Settings -> WhatsApp
 *      Settings (whatsapp_config, token decrypted on the fly);
 *   2. the WHATSAPP_PROVIDER / WHATSAPP_ACCESS_TOKEN / WHATSAPP_PHONE_NUMBER_ID environment
 *      variables (backward-compatible fallback for ops-managed setups);
 *   3. otherwise "not configured": every send returns a FAILED delivery — it never pretends a
 *      message was sent.
 * Consumers (invoice on WhatsApp, Automation Admin automation, test messages) share this one
 * configuration; their data stays separate. Credentials are never logged or returned.
 */
@Service
public class WhatsAppMessageService {

    public static final String NOT_CONFIGURED_ADMIN = "WhatsApp is not configured. Configure WhatsApp from Settings → WhatsApp Settings.";
    public static final String NOT_CONFIGURED_AUTOMATION = "WhatsApp is not configured. Please ask the Main Admin to configure WhatsApp in Settings.";

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMessageService.class);

    private final List<WhatsAppProvider> providers;
    private final AppProperties properties;
    private final WhatsAppConfigRepository configRepository;
    private final SecretCrypto crypto;

    public WhatsAppMessageService(List<WhatsAppProvider> providers, AppProperties properties,
                                  WhatsAppConfigRepository configRepository, SecretCrypto crypto) {
        this.providers = providers;
        this.properties = properties;
        this.configRepository = configRepository;
        this.crypto = crypto;
    }

    // ---- central credential resolution ---------------------------------------------------------

    /** Admin configuration first, environment second, empty when neither is usable. */
    public Optional<WhatsAppSettings> resolve() {
        Optional<WhatsAppConfig> cfg = configRepository.findFirstByOrderByIdAsc().filter(WhatsAppConfig::isActive);
        if (cfg.isPresent()) {
            WhatsAppConfig c = cfg.get();
            WhatsAppSettings s = new WhatsAppSettings(c.getProvider(), c.getApiUrl(), c.getApiVersion(),
                    crypto.decrypt(c.getAccessTokenEnc()), c.getPhoneNumberId(), c.getBusinessAccountId(),
                    WhatsAppSettings.SOURCE_ADMIN);
            if (s.complete() && providerFor(s.provider()).isPresent()) {
                return Optional.of(s);
            }
        }
        AppProperties.WhatsApp w = properties.whatsapp();
        if (w != null && w.configured()) {
            WhatsAppSettings s = new WhatsAppSettings(w.provider(), w.apiUrl(), w.apiVersion(), w.accessToken(),
                    w.phoneNumberId(), w.businessAccountId(), WhatsAppSettings.SOURCE_ENVIRONMENT);
            if (providerFor(s.provider()).isPresent()) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    public Optional<WhatsAppProvider> providerFor(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return providers.stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst();
    }

    public List<String> providerNames() {
        return providers.stream().map(WhatsAppProvider::name).toList();
    }

    public Optional<WhatsAppProvider> activeProvider() {
        return resolve().flatMap(s -> providerFor(s.provider()));
    }

    public boolean isConfigured() {
        return resolve().isPresent();
    }

    public String providerName() {
        return resolve().map(WhatsAppSettings::provider).orElse("NONE");
    }

    /** ADMIN (saved in Main Admin), ENVIRONMENT (server variables) or null. */
    public String credentialSource() {
        return resolve().map(WhatsAppSettings::source).orElse(null);
    }

    public boolean supportsDocuments() {
        return activeProvider().map(WhatsAppProvider::supportsDocuments).orElse(false);
    }

    /** Status line for the Main Admin screens; never includes credentials. */
    public String statusMessage() {
        return resolve()
                .map(s -> "WhatsApp is connected via " + s.provider()
                        + (WhatsAppSettings.SOURCE_ADMIN.equals(s.source()) ? " (configured in Settings → WhatsApp Settings)." : " (configured on the server)."))
                .orElse(NOT_CONFIGURED_ADMIN);
    }

    /** Read-only credential check with explicit settings (used before saving) — sends nothing. */
    public WhatsAppProvider.ValidationResult verify(WhatsAppSettings settings) {
        Optional<WhatsAppProvider> provider = providerFor(settings == null ? null : settings.provider());
        if (provider.isEmpty()) {
            return WhatsAppProvider.ValidationResult.failure("unknown provider");
        }
        return provider.get().verify(settings);
    }

    /** Read-only check of whatever configuration is currently active. */
    public WhatsAppProvider.ValidationResult verifyCurrent() {
        return resolve().map(this::verify)
                .orElseGet(() -> new WhatsAppProvider.ValidationResult(false, NOT_CONFIGURED_ADMIN, null));
    }

    // ---- sending -------------------------------------------------------------------------------

    public WhatsAppDelivery sendText(String mobile, String text) {
        Optional<WhatsAppSettings> settings = resolve();
        if (settings.isEmpty()) {
            return WhatsAppDelivery.notConfigured();
        }
        WhatsAppProvider provider = providerFor(settings.get().provider()).orElseThrow();
        String to = toE164(mobile);
        if (to == null) {
            return WhatsAppDelivery.failed("No valid mobile number on record.", provider.name());
        }
        WhatsAppDelivery d = provider.sendText(settings.get(), to, text);
        log.info("[WHATSAPP] {} text to ***{} via {}{}", d.sent() ? "Sent" : "FAILED", to.substring(Math.max(0, to.length() - 4)),
                provider.name(), d.sent() ? "" : ": " + d.reason());
        return d;
    }

    public WhatsAppDelivery sendDocument(String mobile, String caption, String filename, String contentType, byte[] content) {
        Optional<WhatsAppSettings> settings = resolve();
        if (settings.isEmpty()) {
            return WhatsAppDelivery.notConfigured();
        }
        WhatsAppProvider provider = providerFor(settings.get().provider()).orElseThrow();
        if (!provider.supportsDocuments()) {
            return WhatsAppDelivery.failed("The configured WhatsApp provider does not support document messages.", provider.name());
        }
        String to = toE164(mobile);
        if (to == null) {
            return WhatsAppDelivery.failed("No valid mobile number on record.", provider.name());
        }
        return provider.sendDocument(settings.get(), to, caption, filename, contentType, content);
    }

    /** Indian numbers are stored as 10 digits; WhatsApp wants country code + number, digits only. */
    public static String toE164(String mobile) {
        if (mobile == null) {
            return null;
        }
        String digits = mobile.replaceAll("\\D", "");
        if (digits.length() == 10) {
            return "91" + digits;
        }
        if (digits.length() >= 11 && digits.length() <= 15) {
            return digits;
        }
        return null;
    }
}
