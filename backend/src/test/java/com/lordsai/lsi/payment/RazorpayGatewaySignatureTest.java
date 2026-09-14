package com.lordsai.lsi.payment;

import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.repository.PaymentGatewayConfigRepository;
import com.lordsai.lsi.security.SecretCrypto;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

/** Pure HMAC checks using environment-supplied credentials — no network, no Spring context. */
class RazorpayGatewaySignatureTest {

    private static RazorpayGateway gateway(RazorpayProperties props) {
        PaymentGatewayConfigRepository repo = Mockito.mock(PaymentGatewayConfigRepository.class);
        Mockito.when(repo.findByProvider(anyString())).thenReturn(Optional.empty());
        AppProperties app = new AppProperties("http://localhost", new AppProperties.Cors(List.of()),
                new AppProperties.Jwt("unit-test-secret-key-that-is-long-enough-000", 60, 7, "t"),
                new AppProperties.Storage("./target", 1, 1), new AppProperties.Support("a@b", "0"),
                new AppProperties.Mail(false, "a@b"));
        return new RazorpayGateway(props, repo, new SecretCrypto(app));
    }

    private final RazorpayGateway gateway = gateway(new RazorpayProperties("rzp_test_abc", "secret123", "whsecret", "INR"));

    @Test
    void acceptsSignatureProducedWithKeySecret() {
        String sig = RazorpayGateway.hmacHex("order_1|pay_1", "secret123");
        assertThat(gateway.isConfigured()).isTrue();
        assertThat(gateway.credentialSource()).isEqualTo("ENVIRONMENT");
        assertThat(gateway.verifyPaymentSignature("order_1", "pay_1", sig)).isTrue();
        assertThat(gateway.verifyPaymentSignature("order_1", "pay_1", sig.toUpperCase())).isTrue();
    }

    @Test
    void rejectsSignatureForDifferentPaymentOrSecret() {
        String sig = RazorpayGateway.hmacHex("order_1|pay_1", "secret123");
        assertThat(gateway.verifyPaymentSignature("order_1", "pay_2", sig)).isFalse();
        assertThat(gateway.verifyPaymentSignature("order_2", "pay_1", sig)).isFalse();
        assertThat(gateway.verifyPaymentSignature("order_1", "pay_1", RazorpayGateway.hmacHex("order_1|pay_1", "wrong"))).isFalse();
        assertThat(gateway.verifyPaymentSignature("order_1", "pay_1", "")).isFalse();
        assertThat(gateway.verifyPaymentSignature(null, "pay_1", sig)).isFalse();
    }

    @Test
    void webhookSignatureUsesWebhookSecret() {
        String body = "{\"event\":\"payment.captured\"}";
        assertThat(gateway.verifyWebhookSignature(body, RazorpayGateway.hmacHex(body, "whsecret"))).isTrue();
        assertThat(gateway.verifyWebhookSignature(body, RazorpayGateway.hmacHex(body, "secret123"))).isFalse();
        assertThat(gateway.verifyWebhookSignature(body + " ", RazorpayGateway.hmacHex(body, "whsecret"))).isFalse();
    }

    @Test
    void unconfiguredGatewayRejectsEverything() {
        RazorpayGateway none = gateway(new RazorpayProperties("", "", "", "INR"));
        assertThat(none.isConfigured()).isFalse();
        assertThat(none.credentialSource()).isNull();
        assertThat(none.verifyPaymentSignature("o", "p", RazorpayGateway.hmacHex("o|p", "x"))).isFalse();
        assertThat(none.validateCredentials("bad", "x").ok()).isFalse();
    }
}
