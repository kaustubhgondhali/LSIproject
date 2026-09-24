package com.lordsai.lsi.ebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lordsai.lsi.entity.Course;
import com.lordsai.lsi.entity.Ebook;
import com.lordsai.lsi.entity.Invoice;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.CommunicationChannel;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import com.lordsai.lsi.entity.enums.CommunicationType;
import com.lordsai.lsi.entity.enums.EbookStatus;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.entity.enums.ProductType;
import com.lordsai.lsi.payment.PaymentGateway;
import com.lordsai.lsi.repository.CommunicationLogRepository;
import com.lordsai.lsi.repository.CourseRepository;
import com.lordsai.lsi.repository.EbookEntitlementRepository;
import com.lordsai.lsi.repository.EbookRepository;
import com.lordsai.lsi.repository.EnrollmentRepository;
import com.lordsai.lsi.repository.InvoiceRepository;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ebook purchases through the SAME Razorpay pipeline as courses, plus the course + ebook
 * combination scenarios: one student account, one Student ID, one entitlement per product, one
 * invoice per payment, one purchase email per payment, no duplicates on repeated callbacks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EbookPurchaseFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CourseRepository courseRepository;
    @Autowired EbookRepository ebookRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired EbookEntitlementRepository entitlementRepository;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired CommunicationLogRepository communicationLogRepository;
    @Autowired UserRepository userRepository;
    @Autowired StudentProfileRepository studentProfileRepository;
    @Autowired TestUsers testUsers;

    @MockitoBean PaymentGateway gateway;

    private Course course;
    private Ebook ebook;
    private final AtomicInteger counter = new AtomicInteger();

    @BeforeEach
    void setUp() {
        course = courseRepository.findByCourseCodeIgnoreCase("SMET-MASTER").orElseThrow();
        ebook = new Ebook();
        ebook.setEbookCode("EB-TEST");
        ebook.setTitle("Price Action Workbook");
        ebook.setPrice(new BigDecimal("1499.00"));
        ebook.setDiscountedPrice(new BigDecimal("999.00"));
        ebook.setPdfPath("ebooks/test.pdf");
        ebook.setStatus(EbookStatus.ACTIVE);
        ebook = ebookRepository.saveAndFlush(ebook);

        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.currency()).thenReturn("INR");
        when(gateway.publicKeyId()).thenReturn("rzp_test_key");
        when(gateway.createOrder(any(), anyString(), any())).thenAnswer(inv -> {
            BigDecimal amount = inv.getArgument(0);
            return new PaymentGateway.GatewayOrder("order_eb_" + counter.incrementAndGet(),
                    amount.movePointRight(2).longValueExact(), "INR");
        });
        when(gateway.verifyPaymentSignature(anyString(), anyString(), eq("valid-signature"))).thenReturn(true);
        when(gateway.verifyPaymentSignature(anyString(), anyString(), eq("forged"))).thenReturn(false);
        when(gateway.verifyWebhookSignature(anyString(), eq("good-webhook-sig"))).thenReturn(true);
    }

    // ---- TEST 3: new email purchases an ebook ----------------------------------------------

    @Test
    void newStudentEbookPurchaseCreatesOneAccountEntitlementInvoiceAndEmail() throws Exception {
        MvcResult order = mockMvc.perform(createOrder("reader@example.com", ProductType.EBOOK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountInr").value(999.00))
                .andExpect(jsonPath("$.data.productType").value("EBOOK"))
                .andExpect(jsonPath("$.data.productName").value("Price Action Workbook"))
                .andReturn();
        String rzpOrderId = json(order).path("data").path("razorpayOrderId").asText();

        mockMvc.perform(verify(rzpOrderId, "pay_eb1", "valid-signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.productType").value("EBOOK"))
                .andExpect(jsonPath("$.data.newAccount").value(true))
                .andExpect(jsonPath("$.data.invoiceNumber").value(org.hamcrest.Matchers.startsWith("LSI-INV-")))
                .andExpect(jsonPath("$.data.studentId").value(org.hamcrest.Matchers.startsWith("LSI-2026-")));

        User student = userRepository.findByEmailIgnoreCase("reader@example.com").orElseThrow();
        assertThat(studentProfileRepository.findByUserId(student.getId())).isPresent();
        assertThat(entitlementRepository.findByStudentIdAndEbookId(student.getId(), ebook.getId())).isPresent();
        assertThat(enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(student.getId())).isEmpty();

        var payment = paymentRepository.findByRazorpayOrderId(rzpOrderId).orElseThrow();
        assertThat(payment.getProductType()).isEqualTo(ProductType.EBOOK);
        assertThat(payment.getEbook().getId()).isEqualTo(ebook.getId());
        assertThat(payment.getCourse()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        Invoice invoice = invoiceRepository.findByPaymentId(payment.getId()).orElseThrow();
        assertThat(invoice.getProductType()).isEqualTo(ProductType.EBOOK);
        assertThat(invoice.getTotal()).isEqualByComparingTo("999.00");
        assertThat(invoice.getUnitPrice()).isEqualByComparingTo("1499.00");
        assertThat(invoice.getDiscount()).isEqualByComparingTo("500.00");
        assertThat(invoice.getStudentCode()).startsWith("LSI-2026-");
        assertThat(invoice.getPdfPath()).startsWith("invoices/");

        var emails = communicationLogRepository.findByStudentIdOrderByCreatedAtDesc(student.getId());
        assertThat(emails).hasSize(1);
        assertThat(emails.get(0).getChannel()).isEqualTo(CommunicationChannel.EMAIL);
        assertThat(emails.get(0).getMessageType()).isEqualTo(CommunicationType.EBOOK_PURCHASE);
        // Mail is disabled in tests: the attempt is recorded as FAILED with the reason, never as sent.
        assertThat(emails.get(0).getStatus()).isEqualTo(CommunicationStatus.FAILED);
        assertThat(emails.get(0).getErrorReason()).isNotBlank();
        assertThat(emails.get(0).getInvoice().getId()).isEqualTo(invoice.getId());
    }

    // ---- TEST 4 / Scenario C: existing ebook buyer purchases a course ---------------------------

    @Test
    void ebookFirstThenCourseReusesTheSameStudentId() throws Exception {
        String ebookOrder = json(mockMvc.perform(createOrder("combo@example.com", ProductType.EBOOK)).andReturn()).path("data").path("razorpayOrderId").asText();
        mockMvc.perform(verify(ebookOrder, "pay_c1", "valid-signature")).andExpect(status().isOk());
        User student = userRepository.findByEmailIgnoreCase("combo@example.com").orElseThrow();
        String studentId = studentProfileRepository.findByUserId(student.getId()).orElseThrow().getStudentId();

        String courseOrder = json(mockMvc.perform(createOrder("Combo@Example.com", ProductType.COURSE)).andReturn()).path("data").path("razorpayOrderId").asText();
        mockMvc.perform(verify(courseOrder, "pay_c2", "valid-signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newAccount").value(false))
                .andExpect(jsonPath("$.data.studentId").value(studentId));

        assertThat(userRepository.findAll().stream().filter(u -> u.getEmail().equals("combo@example.com")).count()).isEqualTo(1);
        assertThat(studentProfileRepository.findAll().stream().filter(p -> p.getUser().getId().equals(student.getId())).count()).isEqualTo(1);
        assertThat(entitlementRepository.findByStudentIdAndEbookId(student.getId(), ebook.getId())).isPresent();
        assertThat(enrollmentRepository.findByStudentIdAndCourseId(student.getId(), course.getId())).isPresent();
        assertThat(invoiceRepository.findByStudentIdOrderByPurchaseDateDesc(student.getId())).hasSize(2);
        var emails = communicationLogRepository.findByStudentIdOrderByCreatedAtDesc(student.getId());
        assertThat(emails).hasSize(2);
        assertThat(emails.stream().map(e -> e.getMessageType()).toList())
                .containsExactlyInAnyOrder(CommunicationType.EBOOK_PURCHASE, CommunicationType.EXISTING_STUDENT_PURCHASE);
    }

    // ---- Scenario B: existing course student purchases an ebook ------------------------------------

    @Test
    void courseFirstThenEbookAddsEntitlementWithoutNewAccount() throws Exception {
        String courseOrder = json(mockMvc.perform(createOrder("both@example.com", ProductType.COURSE)).andReturn()).path("data").path("razorpayOrderId").asText();
        mockMvc.perform(verify(courseOrder, "pay_b1", "valid-signature")).andExpect(status().isOk());
        String ebookOrder = json(mockMvc.perform(createOrder("both@example.com", ProductType.EBOOK)).andReturn()).path("data").path("razorpayOrderId").asText();
        mockMvc.perform(verify(ebookOrder, "pay_b2", "valid-signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newAccount").value(false))
                .andExpect(jsonPath("$.data.productType").value("EBOOK"));

        User student = userRepository.findByEmailIgnoreCase("both@example.com").orElseThrow();
        assertThat(enrollmentRepository.findByStudentIdAndCourseId(student.getId(), course.getId())).isPresent();
        assertThat(entitlementRepository.findByStudentIdAndEbookId(student.getId(), ebook.getId())).isPresent();
        assertThat(invoiceRepository.findByStudentIdOrderByPurchaseDateDesc(student.getId())).hasSize(2);
        assertThat(invoiceRepository.findByStudentIdOrderByPurchaseDateDesc(student.getId()).stream()
                .map(Invoice::getInvoiceNumber).distinct().count()).isEqualTo(2);
    }

    // ---- Scenario F: duplicate ebook purchase is refused -------------------------------------------

    @Test
    void duplicateEbookPurchaseIsRefused() throws Exception {
        String orderId = json(mockMvc.perform(createOrder("dup@example.com", ProductType.EBOOK)).andReturn()).path("data").path("razorpayOrderId").asText();
        mockMvc.perform(verify(orderId, "pay_d1", "valid-signature")).andExpect(status().isOk());

        mockMvc.perform(createOrder("dup@example.com", ProductType.EBOOK))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("You already have access to this ebook.")));
        assertThat(entitlementRepository.count()).isEqualTo(1);
    }

    @Test
    void existingStudentCannotBuyTheSameEbookTwiceViaDemoEither() throws Exception {
        User existing = testUsers.student("owner@example.com");
        String orderId = json(mockMvc.perform(createOrder("owner@example.com", ProductType.EBOOK)).andReturn()).path("data").path("razorpayOrderId").asText();
        mockMvc.perform(verify(orderId, "pay_o1", "valid-signature")).andExpect(status().isOk());
        assertThat(entitlementRepository.findByStudentIdAndEbookId(existing.getId(), ebook.getId())).isPresent();
        mockMvc.perform(createOrder("owner@example.com", ProductType.EBOOK)).andExpect(status().isConflict());
    }

    // ---- invalid payment / duplicate webhook -------------------------------------------------------

    @Test
    void forgedEbookSignatureCreatesNothing() throws Exception {
        String orderId = json(mockMvc.perform(createOrder("forge@example.com", ProductType.EBOOK)).andReturn()).path("data").path("razorpayOrderId").asText();
        mockMvc.perform(verify(orderId, "pay_f", "forged")).andExpect(status().isBadRequest());
        assertThat(userRepository.findByEmailIgnoreCase("forge@example.com")).isEmpty();
        assertThat(entitlementRepository.count()).isZero();
        assertThat(invoiceRepository.count()).isZero();
    }

    @Test
    void browserVerifyThenWebhookDoNotDuplicateAnything() throws Exception {
        String orderId = json(mockMvc.perform(createOrder("idem@example.com", ProductType.EBOOK)).andReturn()).path("data").path("razorpayOrderId").asText();
        mockMvc.perform(verify(orderId, "pay_i1", "valid-signature")).andExpect(status().isOk());

        String body = objectMapper.writeValueAsString(Map.of(
                "event", "payment.captured",
                "payload", Map.of("payment", Map.of("entity", Map.of(
                        "id", "pay_i1", "order_id", orderId, "amount", 99900, "method", "upi")))));
        mockMvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", "good-webhook-sig").content(body)).andExpect(status().isOk());
        mockMvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", "good-webhook-sig").content(body)).andExpect(status().isOk());
        mockMvc.perform(verify(orderId, "pay_i1", "valid-signature")).andExpect(status().isOk());

        User student = userRepository.findByEmailIgnoreCase("idem@example.com").orElseThrow();
        assertThat(userRepository.findAll().stream().filter(u -> u.getEmail().equals("idem@example.com")).count()).isEqualTo(1);
        assertThat(entitlementRepository.count()).isEqualTo(1);
        assertThat(invoiceRepository.count()).isEqualTo(1);
        assertThat(communicationLogRepository.findByStudentIdOrderByCreatedAtDesc(student.getId())).hasSize(1);
    }

    @Test
    void webhookAloneFulfilsAnEbookOrder() throws Exception {
        String orderId = json(mockMvc.perform(createOrder("wh@example.com", ProductType.EBOOK)).andReturn()).path("data").path("razorpayOrderId").asText();
        String body = objectMapper.writeValueAsString(Map.of(
                "event", "payment.captured",
                "payload", Map.of("payment", Map.of("entity", Map.of(
                        "id", "pay_w1", "order_id", orderId, "amount", 99900, "method", "card")))));
        mockMvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", "good-webhook-sig").content(body)).andExpect(status().isOk());
        User student = userRepository.findByEmailIgnoreCase("wh@example.com").orElseThrow();
        assertThat(entitlementRepository.findByStudentIdAndEbookId(student.getId(), ebook.getId())).isPresent();
        Invoice invoice = invoiceRepository.findByStudentIdOrderByPurchaseDateDesc(student.getId()).get(0);
        assertThat(invoice.getPaymentMethod()).isEqualTo("Razorpay — Card");
        assertThat(invoice.getTransactionId()).isEqualTo("pay_w1");
    }

    // ---- price comes from the database ------------------------------------------------------------

    @Test
    void ebookPriceCannotBeSuppliedByTheClient() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("productType", "EBOOK");
        body.put("ebookId", ebook.getId());
        body.put("fullName", "Hacker");
        body.put("email", "hack@example.com");
        body.put("mobile", "9876543210");
        body.put("amount", 1);
        body.put("price", 1);
        mockMvc.perform(post("/api/payments/create-order").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountPaise").value(99900));
    }

    @Test
    void inactiveEbookCannotBePurchased() throws Exception {
        ebook.setStatus(EbookStatus.INACTIVE);
        ebookRepository.saveAndFlush(ebook);
        mockMvc.perform(createOrder("nope@example.com", ProductType.EBOOK)).andExpect(status().isBadRequest());
    }

    @Test
    void invoiceNumbersAreSequentialAndUnique() throws Exception {
        String a = json(mockMvc.perform(createOrder("seq1@example.com", ProductType.EBOOK)).andReturn()).path("data").path("razorpayOrderId").asText();
        mockMvc.perform(verify(a, "pay_s1", "valid-signature")).andExpect(status().isOk());
        String b = json(mockMvc.perform(createOrder("seq2@example.com", ProductType.COURSE)).andReturn()).path("data").path("razorpayOrderId").asText();
        mockMvc.perform(verify(b, "pay_s2", "valid-signature")).andExpect(status().isOk());
        var numbers = invoiceRepository.findAll().stream().map(Invoice::getInvoiceNumber).sorted().toList();
        assertThat(numbers).hasSize(2);
        assertThat(numbers.get(0)).matches("LSI-INV-\\d{4}-\\d{6}");
        int n0 = Integer.parseInt(numbers.get(0).substring(numbers.get(0).lastIndexOf('-') + 1));
        int n1 = Integer.parseInt(numbers.get(1).substring(numbers.get(1).lastIndexOf('-') + 1));
        assertThat(n1).isEqualTo(n0 + 1);
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private RequestBuilder createOrder(String email, ProductType type) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("productType", type.name());
        if (type == ProductType.EBOOK) {
            body.put("ebookId", ebook.getId());
        } else {
            body.put("courseId", course.getId());
        }
        body.put("fullName", "Test Reader");
        body.put("email", email);
        body.put("mobile", "9876543210");
        return post("/api/payments/create-order").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private RequestBuilder verify(String orderId, String paymentId, String sig) throws Exception {
        return post("/api/payments/verify").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "razorpayOrderId", orderId, "razorpayPaymentId", paymentId, "razorpaySignature", sig)));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
