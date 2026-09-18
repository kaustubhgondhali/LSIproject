package com.lordsai.lsi.payment;

import java.math.BigDecimal;
import java.util.Map;

/** The subset of the payment provider the application depends on. */
public interface PaymentGateway {

    record GatewayOrder(String gatewayOrderId, long amountMinor, String currency) {
    }

    record ValidationResult(boolean ok, String message) {
        public static ValidationResult success() {
            return new ValidationResult(true, "Credentials verified with Razorpay.");
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }
    }

    /** True when usable credentials exist (admin configuration first, environment as fallback). */
    boolean isConfigured();

    /** Where the active credentials come from: "ADMIN", "ENVIRONMENT", or null when unconfigured. */
    String credentialSource();

    /** Proves a key pair works by calling the provider's API. Never stores anything. */
    ValidationResult validateCredentials(String keyId, String keySecret);

    /** Creates an order on the provider for the given INR amount. */
    GatewayOrder createOrder(BigDecimal amountInr, String receipt, Map<String, String> notes);

    /** True only if the signature proves the provider produced (orderId, paymentId). */
    boolean verifyPaymentSignature(String orderId, String paymentId, String signature);

    /** True only if the webhook body was signed with our webhook secret. */
    boolean verifyWebhookSignature(String rawBody, String signature);

    String publicKeyId();

    String currency();
}
