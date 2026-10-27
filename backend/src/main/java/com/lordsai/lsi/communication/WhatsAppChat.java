package com.lordsai.lsi.communication;

/**
 * A WhatsApp chat ready to open with WhatsApp's own "click to chat" link: the number in
 * international format (digits only, e.g. 919876543210) and the full message. The server sends
 * nothing — the office opens the chat on its computer (WhatsApp Web or the app), the message is
 * already typed, and the office presses Send. No WhatsApp Business account or API is involved.
 */
public record WhatsAppChat(String phone, String text) {

    /** Recorded as the "provider" of log rows that were opened this way. */
    public static final String PROVIDER = "WHATSAPP_CHAT";

    /**
     * The number as WhatsApp's chat link needs it. India first: 10 digits get 91, a leading 0
     * (trunk prefix) or 00 (international prefix) is dropped. Null when it cannot be a WhatsApp number.
     */
    public static String number(String mobile) {
        if (mobile == null) {
            return null;
        }
        String digits = mobile.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() == 10) {
            return "91" + digits;
        }
        if (digits.length() >= 11 && digits.length() <= 15) {
            return digits;
        }
        return null;
    }

    /** The chat for this mobile number, or null when the number cannot be used on WhatsApp. */
    public static WhatsAppChat of(String mobile, String text) {
        String n = number(mobile);
        return n == null ? null : new WhatsAppChat(n, text);
    }
}
