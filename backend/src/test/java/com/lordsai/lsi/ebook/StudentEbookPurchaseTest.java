package com.lordsai.lsi.ebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.entity.Ebook;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.EbookStatus;
import com.lordsai.lsi.entity.enums.PaymentMode;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.payment.PaymentGateway;
import com.lordsai.lsi.repository.CourseRepository;
import com.lordsai.lsi.repository.EbookEntitlementRepository;
import com.lordsai.lsi.repository.EbookRepository;
import com.lordsai.lsi.repository.PaymentRepository;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Student Portal -> My Ebooks -> Buy ebooks: a logged-in student buys an ebook for their OWN
 * account through the same payment pipeline as the public store (Razorpay or demo mode), without
 * filling in any details — name, email and mobile are the ones already on the account.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentEbookPurchaseTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired EbookRepository ebookRepository;
    @Autowired EbookEntitlementRepository entitlementRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired UserRepository userRepository;
    @Autowired CourseRepository courseRepository;

    @MockitoBean PaymentGateway gateway;

    private Ebook ebook;
    private User studentUser;
    private String student;
    private final AtomicInteger counter = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        ebook = new Ebook();
        ebook.setEbookCode("EB-PORTAL");
        ebook.setTitle("Option Chain Handbook");
        ebook.setPrice(new BigDecimal("799.00"));
        ebook.setDiscountedPrice(new BigDecimal("499.00"));
        ebook.setPdfPath("ebooks/portal.pdf");
        ebook.setStatus(EbookStatus.ACTIVE);
        ebook = ebookRepository.saveAndFlush(ebook);

        studentUser = users.student("portal-buyer@test.local");
        student = api.login("portal-buyer@test.local", TestUsers.PASSWORD);

        razorpayMode();
        when(gateway.verifyPaymentSignature(anyString(), anyString(), eq("valid-signature"))).thenReturn(true);
        when(gateway.verifyPaymentSignature(anyString(), anyString(), eq("forged"))).thenReturn(false);
    }

    private void razorpayMode() {
        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.currency()).thenReturn("INR");
        when(gateway.publicKeyId()).thenReturn("rzp_test_key");
        when(gateway.createOrder(any(), anyString(), any())).thenAnswer(inv -> {
            BigDecimal amount = inv.getArgument(0);
            return new PaymentGateway.GatewayOrder("order_portal_" + counter.incrementAndGet(),
                    amount.movePointRight(2).longValueExact(), "INR");
        });
    }

    private String orderPath() {
        return "/api/student/ebooks/" + ebook.getId() + "/order";
    }

    private String demoPath() {
        return "/api/student/ebooks/" + ebook.getId() + "/demo-complete";
    }

    @Test
    void razorpayPurchaseLandsInTheLoggedInStudentsOwnAccount() throws Exception {
        long usersBefore = userRepository.count();
        // Nothing is sent: the details come from the account.
        JsonNode order = api.data(api.post(student, orderPath())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountPaise").value(49900))
                .andExpect(jsonPath("$.data.productType").value("EBOOK"))
                .andExpect(jsonPath("$.data.customerEmail").value("portal-buyer@test.local"))
                .andExpect(jsonPath("$.data.customerName").value(studentUser.getFullName()))
                .andExpect(jsonPath("$.data.customerMobile").value("9876543210")));

        // Nothing is granted before the payment is verified.
        assertThat(entitlementRepository.findByStudentIdAndEbookId(studentUser.getId(), ebook.getId())).isEmpty();
        api.get(student, "/api/student/ebooks/" + ebook.getId() + "/download").andExpect(status().isForbidden());

        api.post(null, "/api/payments/verify", Map.of("razorpayOrderId", order.path("razorpayOrderId").asText(),
                        "razorpayPaymentId", "pay_portal_1", "razorpaySignature", "valid-signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.newAccount").value(false))
                .andExpect(jsonPath("$.data.studentId").value("LSI-TEST-" + studentUser.getId()))
                .andExpect(jsonPath("$.data.invoiceNumber").value(org.hamcrest.Matchers.startsWith("LSI-INV-")));

        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(entitlementRepository.findByStudentIdAndEbookId(studentUser.getId(), ebook.getId())).isPresent();
        var payment = paymentRepository.findByRazorpayOrderId(order.path("razorpayOrderId").asText()).orElseThrow();
        assertThat(payment.getUser().getId()).isEqualTo(studentUser.getId());
        assertThat(payment.getAmount()).isEqualByComparingTo("499.00");
        api.get(student, "/api/student/ebooks")
                .andExpect(jsonPath("$.data[0].ebookId").value(ebook.getId()))
                .andExpect(jsonPath("$.data[0].invoiceNumber").value(org.hamcrest.Matchers.startsWith("LSI-INV-")));

        // Buying the same ebook again is refused.
        api.post(student, orderPath()).andExpect(status().isConflict());
    }

    @Test
    void theRequestCannotChangeTheBuyerOrThePrice() throws Exception {
        users.student("victim@test.local");
        Map<String, Object> body = new HashMap<>();
        body.put("mobile", "9123456780");
        body.put("email", "victim@test.local");
        body.put("fullName", "Someone Else");
        body.put("amount", 1);
        body.put("price", 1);
        api.post(student, orderPath(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerEmail").value("portal-buyer@test.local"))
                .andExpect(jsonPath("$.data.customerName").value(studentUser.getFullName()))
                .andExpect(jsonPath("$.data.customerMobile").value("9876543210"))
                .andExpect(jsonPath("$.data.amountPaise").value(49900));
    }

    @Test
    void withoutAMobileOnTheAccountTheOneFromTheCoursePurchaseIsUsed() throws Exception {
        when(gateway.isConfigured()).thenReturn(false);
        var course = courseRepository.findByCourseCodeIgnoreCase("SMET-MASTER").orElseThrow();
        // The student bought the course on the public site with this mobile.
        api.post(null, "/api/payments/demo-complete", Map.of("productType", "COURSE", "courseId", course.getId(),
                        "fullName", "Portal Buyer", "email", "portal-buyer@test.local", "mobile", "9123456780"))
                .andExpect(status().isOk());
        studentUser.setMobile(null);
        userRepository.saveAndFlush(studentUser);

        api.post(student, demoPath()).andExpect(status().isOk());
        var ebookPayment = paymentRepository.findAll().stream().filter(p -> p.getEbook() != null).findFirst().orElseThrow();
        assertThat(ebookPayment.getCustomerMobile()).isEqualTo("9123456780");
        assertThat(ebookPayment.getUser().getId()).isEqualTo(studentUser.getId());
    }

    @Test
    void noMobileAnywhereIsExplainedAndNothingIsCharged() throws Exception {
        studentUser.setMobile(null);
        userRepository.saveAndFlush(studentUser);
        api.post(student, orderPath()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("contact the academy")));
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    void demoModeGrantsTheEbookWithoutChargingAndIsRefusedOnceRazorpayIsActive() throws Exception {
        // Razorpay active: the demo path is refused and nothing is granted.
        api.post(student, demoPath()).andExpect(status().isConflict());
        assertThat(entitlementRepository.findByStudentIdAndEbookId(studentUser.getId(), ebook.getId())).isEmpty();

        when(gateway.isConfigured()).thenReturn(false);
        api.post(student, orderPath()).andExpect(status().isServiceUnavailable());
        api.post(student, demoPath())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMode").value("DEMO"))
                .andExpect(jsonPath("$.data.newAccount").value(false))
                .andExpect(jsonPath("$.data.email").value("portal-buyer@test.local"));

        assertThat(entitlementRepository.findByStudentIdAndEbookId(studentUser.getId(), ebook.getId())).isPresent();
        var payment = paymentRepository.findAll().stream().filter(p -> p.getEbook() != null && p.getEbook().getId().equals(ebook.getId()))
                .findFirst().orElseThrow();
        assertThat(payment.getPaymentMode()).isEqualTo(PaymentMode.DEMO);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        api.post(student, demoPath()).andExpect(status().isConflict());
    }

    @Test
    void forgedPaymentGrantsNothing() throws Exception {
        String orderId = api.data(api.post(student, orderPath())).path("razorpayOrderId").asText();
        api.post(null, "/api/payments/verify", Map.of("razorpayOrderId", orderId,
                "razorpayPaymentId", "pay_x", "razorpaySignature", "forged")).andExpect(status().isBadRequest());
        assertThat(entitlementRepository.findByStudentIdAndEbookId(studentUser.getId(), ebook.getId())).isEmpty();
    }

    @Test
    void unavailableEbookAndNonStudentsAreRefused() throws Exception {
        ebook.setStatus(EbookStatus.INACTIVE);
        ebookRepository.saveAndFlush(ebook);
        api.post(student, orderPath()).andExpect(status().isBadRequest());

        api.post(null, orderPath()).andExpect(status().isUnauthorized());
        users.admin("portal-admin@test.local");
        String admin = api.login("portal-admin@test.local", TestUsers.PASSWORD);
        api.post(admin, orderPath()).andExpect(status().isForbidden());
    }
}
