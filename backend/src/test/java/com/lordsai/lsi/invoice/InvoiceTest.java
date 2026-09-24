package com.lordsai.lsi.invoice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lordsai.lsi.entity.Course;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.CommunicationType;
import com.lordsai.lsi.payment.PaymentGateway;
import com.lordsai.lsi.repository.AuditLogRepository;
import com.lordsai.lsi.repository.CommunicationLogRepository;
import com.lordsai.lsi.repository.CourseRepository;
import com.lordsai.lsi.repository.InvoiceRepository;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.support.ApiClient;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Invoice records: student ownership, admin access, PDF / print, resend, export. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InvoiceTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired CourseRepository courseRepository;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired CommunicationLogRepository communicationLogRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired UserRepository userRepository;

    @MockitoBean PaymentGateway gateway;

    private final AtomicInteger counter = new AtomicInteger();
    private Course course;
    private String admin, buyer, other;
    private User buyerUser;
    private long invoiceId;

    @BeforeEach
    void setUp() throws Exception {
        course = courseRepository.findByCourseCodeIgnoreCase("SMET-MASTER").orElseThrow();
        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.currency()).thenReturn("INR");
        when(gateway.publicKeyId()).thenReturn("rzp_test_key");
        when(gateway.createOrder(any(), anyString(), any())).thenAnswer(inv -> {
            BigDecimal amount = inv.getArgument(0);
            return new PaymentGateway.GatewayOrder("order_inv_" + counter.incrementAndGet(), amount.movePointRight(2).longValueExact(), "INR");
        });
        when(gateway.verifyPaymentSignature(anyString(), anyString(), eq("valid-signature"))).thenReturn(true);

        users.admin("admin@test.local");
        buyerUser = users.student("buyer@test.local");
        users.student("other@test.local");
        admin = api.login("admin@test.local", TestUsers.PASSWORD);
        buyer = api.login("buyer@test.local", TestUsers.PASSWORD);
        other = api.login("other@test.local", TestUsers.PASSWORD);

        String orderId = json(mockMvc.perform(post("/api/payments/create-order").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("courseId", course.getId(), "fullName", "Buyer One",
                        "email", "buyer@test.local", "mobile", "9876543210")))).andReturn()).path("data").path("razorpayOrderId").asText();
        mockMvc.perform(post("/api/payments/verify").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("razorpayOrderId", orderId, "razorpayPaymentId", "pay_inv",
                        "razorpaySignature", "valid-signature")))).andExpect(status().isOk());
        invoiceId = invoiceRepository.findAll().get(0).getId();
        assertThat(userRepository.findById(buyerUser.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void studentSeesAndDownloadsOnlyOwnInvoices() throws Exception {
        api.get(buyer, "/api/student/invoices").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].invoiceNumber").value(org.hamcrest.Matchers.startsWith("LSI-INV-")))
                .andExpect(jsonPath("$.data[0].productName").value("Share Market Education & Training"))
                .andExpect(jsonPath("$.data[0].total").value(9999.00))
                .andExpect(jsonPath("$.data[0].paymentStatus").value("SUCCESS"));
        byte[] pdf = api.get(buyer, "/api/student/invoices/" + invoiceId + "/pdf").andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        // Student B cannot read Student A's invoice by guessing the id; anonymous gets 401.
        api.get(other, "/api/student/invoices/" + invoiceId).andExpect(status().isForbidden());
        api.get(other, "/api/student/invoices/" + invoiceId + "/pdf").andExpect(status().isForbidden());
        api.get(other, "/api/student/invoices").andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/student/invoices/" + invoiceId + "/pdf")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/automation/invoices/" + invoiceId + "/pdf")).andExpect(status().isUnauthorized());
        api.get(buyer, "/api/automation/invoices").andExpect(status().isForbidden());
    }

    @Test
    void adminListsDownloadsPrintsAndResendsInvoices() throws Exception {
        api.get(admin, "/api/automation/invoices?productType=COURSE&q=buyer").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].studentEmail").value("buyer@test.local"));
        api.get(admin, "/api/automation/invoices?productType=EBOOK").andExpect(jsonPath("$.data.totalElements").value(0));
        api.get(admin, "/api/automation/invoices/" + invoiceId + "/pdf?download=true").andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment; filename=\"LSI-INV-")));
        String html = api.get(admin, "/api/automation/invoices/" + invoiceId + "/print").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("INVOICE", "Student User", "buyer@test.local", "Share Market Education", "9,999.00", "data:image/png;base64,");
        assertThat(html).doesNotContain("${");

        // Resend: mail is disabled in tests, so the API reports the failure honestly and logs it.
        api.post(admin, "/api/automation/invoices/" + invoiceId + "/resend-email", null).andExpect(status().isBadGateway());
        assertThat(communicationLogRepository.findAll().stream().filter(c -> c.getMessageType() == CommunicationType.INVOICE).count()).isEqualTo(1);
        assertThat(auditLogRepository.findAll().stream().anyMatch(a -> "INVOICE_RESEND_FAILED".equals(a.getAction()))).isTrue();
        assertThat(auditLogRepository.findAll().stream().anyMatch(a -> "INVOICE_GENERATED".equals(a.getAction()))).isTrue();

        // WhatsApp is not configured: no pretend success.
        api.post(admin, "/api/automation/invoices/" + invoiceId + "/whatsapp", null).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(com.lordsai.lsi.communication.WhatsAppDelivery.NOT_CONFIGURED));
    }

    @Test
    void invoiceExportsRespectFilters() throws Exception {
        byte[] xlsx = api.get(admin, "/api/automation/reports/invoices/xlsx?productType=COURSE").andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/vnd.openxmlformats")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(xlsx.length).isGreaterThan(1000);
        byte[] pdf = api.get(admin, "/api/automation/reports/invoices/pdf?productType=COURSE").andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        JsonNode filtered = api.data(api.get(admin, "/api/automation/reports/invoices?productType=EBOOK"));
        assertThat(filtered.path("rows").size()).isZero();
        JsonNode all = api.data(api.get(admin, "/api/automation/reports/invoices"));
        assertThat(all.path("rows").size()).isEqualTo(1);
        assertThat(all.path("columns").get(0).asText()).isEqualTo("Invoice Number");
    }

    @Test
    void studentPaymentsListCarriesTheInvoiceNumber() throws Exception {
        api.get(buyer, "/api/student/payments").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].invoiceNumber").value(org.hamcrest.Matchers.startsWith("LSI-INV-")))
                .andExpect(jsonPath("$.data[0].productType").value("COURSE"));
    }

    private JsonNode json(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
