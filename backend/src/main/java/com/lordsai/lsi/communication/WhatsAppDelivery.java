package com.lordsai.lsi.communication;

/** Outcome of one WhatsApp send attempt. Mirrors EmailDelivery so the communication log treats both channels alike. */
public record WhatsAppDelivery(boolean sent, String providerMessageId, String reason, String provider) {

    /** Failure reason recorded when no central configuration exists (admin-facing, names no server variables). */
    public static final String NOT_CONFIGURED = "WhatsApp is not configured. Configure WhatsApp from Main Admin → Settings → WhatsApp Settings.";

    public static WhatsAppDelivery sent(String providerMessageId, String provider) {
        return new WhatsAppDelivery(true, providerMessageId, null, provider);
    }

    public static WhatsAppDelivery failed(String reason, String provider) {
        return new WhatsAppDelivery(false, null, reason, provider);
    }

    public static WhatsAppDelivery notConfigured() {
        return new WhatsAppDelivery(false, null, NOT_CONFIGURED, "NONE");
    }
}
