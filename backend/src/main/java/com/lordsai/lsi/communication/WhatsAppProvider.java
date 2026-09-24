package com.lordsai.lsi.communication;

/**
 * Abstraction over an official WhatsApp Business API provider. The application never talks to a
 * provider directly: {@link WhatsAppMessageService} resolves the central credentials (admin
 * configuration first, environment as fallback) and hands them to the provider per call, so
 * there is exactly one place credentials are read from. Providers never log or return them.
 */
public interface WhatsAppProvider {

    /** Stable code shown in communication history and the settings screen (e.g. META_CLOUD). */
    String name();

    /** Sends a free-form text message. {@code toE164} is digits only with country code (e.g. 9198xxxxxxxx). */
    WhatsAppDelivery sendText(WhatsAppSettings settings, String toE164, String text);

    /** Sends a document (PDF invoice) with an optional caption. */
    WhatsAppDelivery sendDocument(WhatsAppSettings settings, String toE164, String caption, String filename,
                                  String contentType, byte[] content);

    /** True when the provider can deliver files (documents/media), so invoices can be sent through it. */
    boolean supportsDocuments();

    /**
     * Read-only check that the credentials are accepted by the provider ("Test WhatsApp
     * Connection"). Sends nothing. The message never contains the token.
     */
    ValidationResult verify(WhatsAppSettings settings);

    record ValidationResult(boolean ok, String message, String displayPhoneNumber) {
        public static ValidationResult success(String displayPhoneNumber) {
            return new ValidationResult(true, "WhatsApp configuration is working.", displayPhoneNumber);
        }

        public static ValidationResult failure(String detail) {
            return new ValidationResult(false,
                    "WhatsApp configuration failed. Please verify your provider, access token and phone number ID."
                            + (detail == null || detail.isBlank() ? "" : " (" + detail + ")"), null);
        }
    }
}
