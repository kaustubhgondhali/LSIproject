package com.lordsai.lsi.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.entity.PaymentGatewayConfig;
import com.lordsai.lsi.entity.enums.PaymentMode;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.repository.CourseRepository;
import com.lordsai.lsi.repository.EnrollmentRepository;
import com.lordsai.lsi.repository.PaymentGatewayConfigRepository;
import com.lordsai.lsi.repository.PaymentRepository;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.security.SecretCrypto;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses the real RazorpayGateway (credential resolution from the DB is the thing under test)
 * with only the network call — validateCredentials — stubbed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentModeTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired CourseRepository courseRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired UserRepository userRepository;
    @Autowired PaymentGatewayConfigRepository configRepository;
    @Autowired SecretCrypto crypto;

    @MockitoSpyBean RazorpayGateway gateway;

    private String admin;
    private long courseId;

    @BeforeEach
    void setUp() throws Exception {
        users.admin("admin@test.local");
        admin = api.login("admin@test.local", TestUsers.PASSWORD);
        courseId = courseRepository.findByCourseCodeIgnoreCase("SMET-MASTER").orElseThrow().getId();
    }

    private Map<String, Object> buyer(String email) {
        return Map.of("courseId", courseId, "fullName", "Demo Buyer", "email", email, "mobile", "9876543210");
    }

    // ---- Test 1: no configuration -> DEMO ---------------------------------------------------

    @Test
    void withoutConfigurationDemoModeIsActiveAndDemoPaymentEnrolls() throws Exception {
        api.get(null, "/api/payments/mode").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("DEMO"));

        api.get(admin, "/api/admin/payment-gateway").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("DEMO"))
                .andExpect(jsonPath("$.data.recordExists").value(false));

        // Real order creation is refused in demo mode.
        api.post(null, "/api/payments/create-order", buyer("d1@example.com")).andExpect(status().isServiceUnavailable());

        api.post(null, "/api/payments/demo-complete", buyer("d1@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMode").value("DEMO"))
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.enrollmentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.studentId").isNotEmpty());

        var student = userRepository.findByEmailIgnoreCase("d1@example.com").orElseThrow();
        assertThat(enrollmentRepository.findByStudentIdAndCourseId(student.getId(), courseId)).isPresent();
        var payment = paymentRepository.findAll().stream().filter(p -> p.getCustomerEmail().equals("d1@example.com")).findFirst().orElseThrow();
        assertThat(payment.getPaymentMode()).isEqualTo(PaymentMode.DEMO);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getOrderRef()).startsWith("LSI-DEMO-");

        // Retrying the demo payment for the same email cannot create a second enrollment.
        api.post(null, "/api/payments/demo-complete", buyer("d1@example.com")).andExpect(status().isConflict());
        assertThat(enrollmentRepository.count()).isEqualTo(1);
    }

    // ---- Test 2 & 3: valid credentials -> RAZORPAY, demo refused ------------------------------

    @Test
    void validCredentialsSwitchToRealPaymentsAndBlockDemo() throws Exception {
        doReturn(PaymentGateway.ValidationResult.success()).when(gateway).validateCredentials(anyString(), anyString());

        api.put(admin, "/api/admin/payment-gateway", Map.of("keyId", "rzp_test_ABCDEF123456", "keySecret", "supersecretvalue", "webhookSecret", "whsec", "currency", "INR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("RAZORPAY"))
                .andExpect(jsonPath("$.data.credentialSource").value("ADMIN"))
                .andExpect(jsonPath("$.data.keyMode").value("TEST"))
                .andExpect(jsonPath("$.data.keySecretMasked").value("••••••••••••••••"))
                .andExpect(jsonPath("$.data.webhookSecretSet").value(true))
                .andExpect(jsonPath("$.data.keySecret").doesNotExist());

        // Secrets are encrypted at rest and decrypt back to what was entered.
        PaymentGatewayConfig cfg = configRepository.findByProvider("RAZORPAY").orElseThrow();
        assertThat(cfg.getKeySecretEnc()).doesNotContain("supersecretvalue");
        assertThat(crypto.decrypt(cfg.getKeySecretEnc())).isEqualTo("supersecretvalue");
        assertThat(gateway.publicKeyId()).isEqualTo("rzp_test_ABCDEF123456");

        api.get(null, "/api/payments/mode").andExpect(jsonPath("$.data.mode").value("RAZORPAY"));

        // Demo enrollment is impossible while Razorpay is active.
        api.post(null, "/api/payments/demo-complete", buyer("d2@example.com")).andExpect(status().isConflict());
        assertThat(userRepository.findByEmailIgnoreCase("d2@example.com")).isEmpty();
        assertThat(enrollmentRepository.count()).isZero();

        // Signature verification now uses the admin-stored secret.
        String sig = RazorpayGateway.hmacHex("order_x|pay_x", "supersecretvalue");
        assertThat(gateway.verifyPaymentSignature("order_x", "pay_x", sig)).isTrue();
        assertThat(gateway.verifyPaymentSignature("order_x", "pay_x", RazorpayGateway.hmacHex("order_x|pay_x", "wrong"))).isFalse();
        assertThat(gateway.verifyWebhookSignature("{}", RazorpayGateway.hmacHex("{}", "whsec"))).isTrue();
    }

    // ---- Test 8: Razorpay configured but unreachable -> NO demo fallback ---------------------------

    @Test
    void razorpayOutageNeverFallsBackToDemoPayment() throws Exception {
        doReturn(PaymentGateway.ValidationResult.success()).when(gateway).validateCredentials(anyString(), anyString());
        api.put(admin, "/api/admin/payment-gateway", Map.of("keyId", "rzp_test_ABCDEF123456", "keySecret", "supersecretvalue"))
                .andExpect(status().isOk());
        doThrow(new com.lordsai.lsi.exception.ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                "Payment gateway is temporarily unavailable. Please try again."))
                .when(gateway).createOrder(any(), anyString(), anyMap());

        api.post(null, "/api/payments/create-order", buyer("outage@example.com"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Payment gateway is temporarily unavailable. Please try again."));
        // Mode is still RAZORPAY and the demo path stays closed.
        api.get(null, "/api/payments/mode").andExpect(jsonPath("$.data.mode").value("RAZORPAY"));
        api.post(null, "/api/payments/demo-complete", buyer("outage@example.com")).andExpect(status().isConflict());
        assertThat(userRepository.findByEmailIgnoreCase("outage@example.com")).isEmpty();
        assertThat(enrollmentRepository.count()).isZero();
        assertThat(paymentRepository.findAll()).noneMatch(p -> p.getStatus() == PaymentStatus.SUCCESS);
    }

    // ---- Test 5: invalid credentials rejected, DEMO stays ----------------------------------------

    @Test
    void invalidCredentialsAreRejectedAndDemoModeRemains() throws Exception {
        doReturn(PaymentGateway.ValidationResult.failure("Razorpay rejected these credentials."))
                .when(gateway).validateCredentials(anyString(), anyString());

        api.put(admin, "/api/admin/payment-gateway", Map.of("keyId", "rzp_test_BADBADBAD1", "keySecret", "nope-nope-nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Razorpay rejected these credentials."));

        assertThat(configRepository.findByProvider("RAZORPAY")).isEmpty();
        api.get(null, "/api/payments/mode").andExpect(jsonPath("$.data.mode").value("DEMO"));
        api.put(admin, "/api/admin/payment-gateway", Map.of("keyId", "not-a-key", "keySecret", "x")).andExpect(status().isBadRequest());
    }

    // ---- Test 6: disable / remove -> back to DEMO -----------------------------------------------

    @Test
    void disablingAndRemovingRazorpayReturnsToDemoMode() throws Exception {
        doReturn(PaymentGateway.ValidationResult.success()).when(gateway).validateCredentials(anyString(), anyString());
        api.put(admin, "/api/admin/payment-gateway", Map.of("keyId", "rzp_test_ABCDEF123456", "keySecret", "supersecretvalue")).andExpect(status().isOk());

        api.patch(admin, "/api/admin/payment-gateway/disable", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("DEMO")).andExpect(jsonPath("$.data.enabled").value(false));
        api.post(null, "/api/payments/demo-complete", buyer("d3@example.com")).andExpect(status().isOk());

        api.patch(admin, "/api/admin/payment-gateway/enable", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("RAZORPAY"));

        // Update keeping the existing secret (blank keySecret) still works.
        api.put(admin, "/api/admin/payment-gateway", Map.of("keyId", "rzp_test_ABCDEF123456", "keySecret", "", "currency", "INR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mode").value("RAZORPAY"));

        api.delete(admin, "/api/admin/payment-gateway").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("DEMO")).andExpect(jsonPath("$.data.recordExists").value(false));
        assertThat(configRepository.count()).isZero();

        JsonNode audit = api.data(api.get(admin, "/api/admin/audit-logs?size=20"));
        assertThat(audit.path("content").toString()).contains("RAZORPAY_CONFIG_REMOVED", "PAYMENT_MODE_DEMO_ACTIVATED", "RAZORPAY_CONFIG_CREATED");
        assertThat(audit.path("content").toString()).doesNotContain("supersecretvalue");
    }

    // ---- Test 7: RBAC ---------------------------------------------------------------------------

    @Test
    void studentsCannotTouchGatewayConfiguration() throws Exception {
        users.student("s@test.local");
        String s = api.login("s@test.local", TestUsers.PASSWORD);
        for (String tok : new String[]{s}) {
            api.get(tok, "/api/admin/payment-gateway").andExpect(status().isForbidden());
            api.put(tok, "/api/admin/payment-gateway", Map.of("keyId", "rzp_test_ABCDEF123456", "keySecret", "x")).andExpect(status().isForbidden());
            api.delete(tok, "/api/admin/payment-gateway").andExpect(status().isForbidden());
        }
        api.get(null, "/api/admin/payment-gateway").andExpect(status().isUnauthorized());
    }

    // ---- Test 8: price manipulation ------------------------------------------------------------

    @Test
    void demoPaymentIgnoresAnyAmountFromTheBrowser() throws Exception {
        api.post(null, "/api/payments/demo-complete", Map.of("courseId", courseId, "fullName", "Hacker", "email", "h@example.com",
                        "mobile", "9876543210", "amount", 1, "amountInr", 1, "price", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountInr").value(9999.0));
    }
}
