package com.lordsai.lsi.communication;

/**
 * The resolved WhatsApp credentials handed to a provider for one call. Built by
 * {@link WhatsAppMessageService} from the central admin configuration (source ADMIN) or, when
 * none is enabled, from the WHATSAPP_* environment variables (source ENVIRONMENT). Never
 * serialised, logged or returned by an API.
 */
public record WhatsAppSettings(String provider, String apiUrl, String apiVersion, String accessToken,
                               String phoneNumberId, String businessAccountId, String source) {

    public static final String SOURCE_ADMIN = "ADMIN";
    public static final String SOURCE_ENVIRONMENT = "ENVIRONMENT";

    public boolean complete() {
        return provider != null && !provider.isBlank() && !"NONE".equalsIgnoreCase(provider)
                && accessToken != null && !accessToken.isBlank()
                && phoneNumberId != null && !phoneNumberId.isBlank();
    }
}
