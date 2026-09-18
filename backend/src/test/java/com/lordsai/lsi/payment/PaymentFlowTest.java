package com.lordsai.lsi.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lordsai.lsi.entity.Course;
import com.lordsai.lsi.entity.Payment;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.EnrollmentStatus;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.repository.CourseRepository;
import com.lordsai.lsi.repository.EnrollmentRepository;
import com.lordsai.lsi.repository.PaymentRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CourseRepository courseRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired UserRepository userRepository;
    @Autowired StudentProfileRepository studentProfileRepository;
    @Autowired TestUsers testUsers;

    @MockitoBean PaymentGateway gateway;

    private Course course;
    private final AtomicInteger orderCounter = new AtomicInteger();

    @BeforeEach
    void setUp() {
        course = courseRepository.findByCourseCodeIgnoreCase("SMET-MASTER").orElseThrow();
        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.currency()).thenReturn("INR");
        when(gateway.publicKeyId()).thenReturn("rzp_test_key");
        when(gateway.createOrder(any(), anyString(), any())).thenAnswer(inv -> {
            BigDecimal amount = inv.getArgument(0);
            return new PaymentGateway.GatewayOrder("order_" + orderCounter.incrementAndGet(),
                    amount.movePointRight(2).longValueExact(), "INR");
        });
        when(gateway.verifyPaymentSignature(anyString(), anyString(), eq("valid-signature"))).thenReturn(true);
        when(gateway.verifyPaymentSignature(anyString(), anyString(), eq("forged"))).thenReturn(false);
    }

    @Test
    void orderAmountComesFromDatabasePriceNotRequest() throws Exception {
        MvcResult result = mockMvc.perform(createOrder("buyer1@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountInr").value(9999.00))
                .andExpect(jsonPath("$.data.amountPaise").value(999900))
                .andExpect(jsonPath("$.data.razorpayKeyId").value("rzp_test_key"))
                .andReturn();

        String orderRef = json(result).path("data").path("orderRef").asText();
        Payment payment = paymentRepository.findByOrderRef(orderRef).orElseThrow();
        assertThat(payment.getAmount()).isEqualByComparingTo("9999.00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(payment.getUser()).as("no account until payment is verified").isNull();
        assertThat(userRepository.findByEmailIgnoreCase("buyer1@example.com")).isEmpty();
    }

    @Test
    void validSignatureCreatesStudentAndEnrollment() throws Exception {
        String rzpOrderId = json(mockMvc.perform(createOrder("newstudent@example.com")).andReturn())
                .path("data").path("razorpayOrderId").asText();

        mockMvc.perform(verifyPayment(rzpOrderId, "pay_123", "valid-signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.enrollmentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.newAccount").value(true))
                .andExpect(jsonPath("$.data.studentId").value(org.hamcrest.Matchers.startsWith("LSI-2026-")))
                .andExpect(jsonPath("$.data.courseName").value("Share Market Education & Training"));

        User student = userRepository.findByEmailIgnoreCase("newstudent@example.com").orElseThrow();
        assertThat(student.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(student.getPasswordHash()).isNotBlank();
        assertThat(student.getPasswordHash()).startsWith("$2a$");
        assertThat(studentProfileRepository.findByUserId(student.getId())).isPresent();

        var enrollment = enrollmentRepository.findByStudentIdAndCourseId(student.getId(), course.getId()).orElseThrow();
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(enrollment.getPayment().getRazorpayOrderId()).isEqualTo(rzpOrderId);

        Payment payment = paymentRepository.findByRazorpayOrderId(rzpOrderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getProcessedAt()).isNotNull();
        assertThat(payment.getUser().getId()).isEqualTo(student.getId());
    }

    @Test
    void forgedSignatureIsRejectedAndNothingIsCreated() throws Exception {
        String rzpOrderId = json(mockMvc.perform(createOrder("victim@example.com")).andReturn())
                .path("data").path("razorpayOrderId").asText();

        mockMvc.perform(verifyPayment(rzpOrderId, "pay_fake", "forged"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(paymentRepository.findByRazorpayOrderId(rzpOrderId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(userRepository.findByEmailIgnoreCase("victim@example.com")).isEmpty();
        assertThat(enrollmentRepository.count()).isZero();
    }

    @Test
    void duplicateCallbackDoesNotCreateSecondEnrollmentOrAccount() throws Exception {
        String rzpOrderId = json(mockMvc.perform(createOrder("twice@example.com")).andReturn())
                .path("data").path("razorpayOrderId").asText();

        mockMvc.perform(verifyPayment(rzpOrderId, "pay_1", "valid-signature")).andExpect(status().isOk());
        mockMvc.perform(verifyPayment(rzpOrderId, "pay_1", "valid-signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"));

        assertThat(userRepository.findAll().stream().filter(u -> u.getEmail().equals("twice@example.com")).count()).isEqualTo(1);
        assertThat(enrollmentRepository.count()).isEqualTo(1);
        // Fulfilment (and therefore the email) ran exactly once.
        verify(gateway, times(1)).verifyPaymentSignature(eq(rzpOrderId), eq("pay_1"), eq("valid-signature"));
    }

    @Test
    void existingStudentIsReusedAndNotDuplicated() throws Exception {
        User existing = testUsers.student("returning@example.com");

        String rzpOrderId = json(mockMvc.perform(createOrder("Returning@Example.com")).andReturn())
                .path("data").path("razorpayOrderId").asText();
        mockMvc.perform(verifyPayment(rzpOrderId, "pay_r", "valid-signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newAccount").value(false));

        assertThat(userRepository.findAll().stream().filter(u -> u.getEmail().equals("returning@example.com")).count()).isEqualTo(1);
        assertThat(enrollmentRepository.findByStudentIdAndCourseId(existing.getId(), course.getId())).isPresent();
        assertThat(userRepository.findById(existing.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void alreadyEnrolledStudentCannotStartAnotherCheckout() throws Exception {
        String rzpOrderId = json(mockMvc.perform(createOrder("once@example.com")).andReturn())
                .path("data").path("razorpayOrderId").asText();
        mockMvc.perform(verifyPayment(rzpOrderId, "pay_once", "valid-signature")).andExpect(status().isOk());

        mockMvc.perform(createOrder("once@example.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "You already have access to this course. Please log in to the Student Portal."));
    }

    @Test
    void webhookFulfilsOrderWhenBrowserCallbackNeverArrived() throws Exception {
        String rzpOrderId = json(mockMvc.perform(createOrder("webhook@example.com")).andReturn())
                .path("data").path("razorpayOrderId").asText();
        when(gateway.verifyWebhookSignature(anyString(), eq("good-webhook-sig"))).thenReturn(true);

        String body = objectMapper.writeValueAsString(Map.of(
                "event", "payment.captured",
                "payload", Map.of("payment", Map.of("entity", Map.of(
                        "id", "pay_wh", "order_id", rzpOrderId, "amount", 999900, "method", "upi")))));

        mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "good-webhook-sig")
                        .content(body))
                .andExpect(status().isOk());

        Payment payment = paymentRepository.findByRazorpayOrderId(rzpOrderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPaymentMethod()).isEqualTo("upi");
        assertThat(userRepository.findByEmailIgnoreCase("webhook@example.com")).isPresent();
        assertThat(enrollmentRepository.count()).isEqualTo(1);
    }

    @Test
    void webhookWithBadSignatureIsRejected() throws Exception {
        when(gateway.verifyWebhookSignature(anyString(), anyString())).thenReturn(false);
        mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "nope")
                        .content("{\"event\":\"payment.captured\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhookWithTamperedAmountMarksPaymentFailed() throws Exception {
        String rzpOrderId = json(mockMvc.perform(createOrder("tamper@example.com")).andReturn())
                .path("data").path("razorpayOrderId").asText();
        when(gateway.verifyWebhookSignature(anyString(), eq("sig"))).thenReturn(true);

        String body = objectMapper.writeValueAsString(Map.of(
                "event", "payment.captured",
                "payload", Map.of("payment", Map.of("entity", Map.of(
                        "id", "pay_low", "order_id", rzpOrderId, "amount", 100)))));
        mockMvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", "sig").content(body)).andExpect(status().isOk());

        assertThat(paymentRepository.findByRazorpayOrderId(rzpOrderId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(enrollmentRepository.count()).isZero();
    }

    // ---- helpers ---------------------------------------------------------------------------

    private org.springframework.test.web.servlet.RequestBuilder createOrder(String email) throws Exception {
        return post("/api/payments/create-order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "courseId", course.getId(),
                        "fullName", "Test Buyer",
                        "email", email,
                        "mobile", "9876543210")));
    }

    private org.springframework.test.web.servlet.RequestBuilder verifyPayment(String orderId, String paymentId, String sig) throws Exception {
        return post("/api/payments/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "razorpayOrderId", orderId,
                        "razorpayPaymentId", paymentId,
                        "razorpaySignature", sig)));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
