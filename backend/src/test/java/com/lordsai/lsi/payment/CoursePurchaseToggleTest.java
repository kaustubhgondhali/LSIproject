package com.lordsai.lsi.payment;

import com.lordsai.lsi.entity.Course;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.repository.AuditLogRepository;
import com.lordsai.lsi.repository.CourseRepository;
import com.lordsai.lsi.repository.PaymentRepository;
import com.lordsai.lsi.service.CourseService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Main Admin's "Course Purchase" switch is stored on the server: every visitor sees the same
 * state (public course API) and the payment API refuses a closed course, in Razorpay and demo mode.
 * Manual enrollment by the admin keeps working.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CoursePurchaseToggleTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired CourseRepository courseRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @MockitoBean PaymentGateway gateway;

    private Course course;
    private String admin;
    private String student;
    private User studentUser;

    @BeforeEach
    void setUp() throws Exception {
        course = courseRepository.findByCourseCodeIgnoreCase("SMET-MASTER").orElseThrow();
        users.admin("admin@test.local");
        studentUser = users.student("s@test.local");
        admin = api.login("admin@test.local", TestUsers.PASSWORD);
        student = api.login("s@test.local", TestUsers.PASSWORD);
        when(gateway.currency()).thenReturn("INR");
        when(gateway.publicKeyId()).thenReturn("rzp_test_key");
        when(gateway.createOrder(any(), anyString(), any())).thenAnswer(inv -> {
            BigDecimal amount = inv.getArgument(0);
            return new PaymentGateway.GatewayOrder("order_toggle", amount.movePointRight(2).longValueExact(), "INR");
        });
    }

    private Map<String, Object> buyer() {
        return Map.of("productType", "COURSE", "courseId", course.getId(),
                "fullName", "Toggle Buyer", "email", "toggle.buyer@example.com", "mobile", "9876543210");
    }

    private void setPurchase(boolean enabled) throws Exception {
        api.patch(admin, "/api/admin/courses/" + course.getId() + "/purchase", Map.of("enabled", enabled))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purchaseEnabled").value(enabled));
    }

    @Test
    void closedCourseIsShownClosedToEveryoneAndCannotBeBoughtWithRazorpay() throws Exception {
        when(gateway.isConfigured()).thenReturn(true);
        api.get(null, "/api/public/courses").andExpect(jsonPath("$.data[0].purchaseEnabled").value(true));

        setPurchase(false);
        api.get(null, "/api/public/courses").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseCode").value("SMET-MASTER"))       // still listed
                .andExpect(jsonPath("$.data[0].purchaseEnabled").value(false));
        long paymentsBefore = paymentRepository.count();
        api.post(null, "/api/payments/create-order", buyer()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CourseService.PURCHASE_CLOSED));
        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore);
        assertThat(auditLogRepository.findAll()).anyMatch(a -> "COURSE_PURCHASE_CLOSED".equals(a.getAction()));

        setPurchase(true);
        api.get(null, "/api/public/courses").andExpect(jsonPath("$.data[0].purchaseEnabled").value(true));
        api.post(null, "/api/payments/create-order", buyer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.razorpayOrderId").value("order_toggle"));
        assertThat(auditLogRepository.findAll()).anyMatch(a -> "COURSE_PURCHASE_OPENED".equals(a.getAction()));
    }

    @Test
    void closedCourseCannotBeBoughtInDemoModeEither() throws Exception {
        when(gateway.isConfigured()).thenReturn(false);
        setPurchase(false);
        api.post(null, "/api/payments/demo-complete", buyer()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CourseService.PURCHASE_CLOSED));
    }

    @Test
    void adminCanStillEnrollManuallyWhilePurchaseIsClosed() throws Exception {
        setPurchase(false);
        api.post(admin, "/api/admin/enrollments", Map.of("studentUserId", studentUser.getId(), "courseId", course.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void onlyTheMainAdminCanSwitchIt() throws Exception {
        api.patch(student, "/api/admin/courses/" + course.getId() + "/purchase", Map.of("enabled", false)).andExpect(status().isForbidden());
        api.patch(null, "/api/admin/courses/" + course.getId() + "/purchase", Map.of("enabled", false)).andExpect(status().isUnauthorized());
        api.patch(admin, "/api/admin/courses/" + course.getId() + "/purchase", Map.of()).andExpect(status().isBadRequest());
        assertThat(courseRepository.findById(course.getId()).orElseThrow().isPurchaseEnabled()).isTrue();
    }
}
