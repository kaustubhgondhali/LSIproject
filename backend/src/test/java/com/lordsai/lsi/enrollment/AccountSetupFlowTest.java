package com.lordsai.lsi.enrollment;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.entity.AccountToken;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.TokenPurpose;
import com.lordsai.lsi.repository.AccountTokenRepository;
import com.lordsai.lsi.repository.CourseRepository;
import com.lordsai.lsi.repository.EnrollmentRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The full purchase -> Student ID -> setup link -> password -> login chain, plus the recovery
 * paths for the case the enrollment email never reaches the student.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountSetupFlowTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired CourseRepository courseRepository;
    @Autowired UserRepository userRepository;
    @Autowired StudentProfileRepository studentProfileRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired AccountTokenRepository tokenRepository;

    /** Replaces LoggingEmailService so the test controls the delivery outcome. */
    @MockitoBean EmailService emailService;

    private long courseId;

    @BeforeEach
    void setUp() {
        courseId = courseRepository.findByCourseCodeIgnoreCase("SMET-MASTER").orElseThrow().getId();
        when(emailService.sendEnrollmentEmail(any(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(EmailDelivery.sent());
        // Purchases now send the purchase-confirmation email (invoice attached); resends still use the enrollment email.
        when(emailService.sendPurchaseEmail(any())).thenReturn(EmailDelivery.sent());
    }

    /** Captures the setup link carried by the purchase-confirmation email model. */
    private String capturePurchaseSetupLink() {
        ArgumentCaptor<com.lordsai.lsi.email.PurchaseEmail> mail = ArgumentCaptor.forClass(com.lordsai.lsi.email.PurchaseEmail.class);
        verify(emailService, times(1)).sendPurchaseEmail(mail.capture());
        Object link = mail.getValue().model().get("setupLink");
        return link == null ? null : String.valueOf(link);
    }

    private Map<String, Object> buyer(String email) {
        return Map.of("courseId", courseId, "fullName", "Demo Buyer", "email", email, "mobile", "9876543210");
    }

    /** Captures the setup link handed to the email service — the only place the raw token exists. */
    private String captureSetupLink(int expectedSends) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(expectedSends)).sendEnrollmentEmail(
                any(), anyString(), anyString(), anyString(), anyString(), link.capture());
        return link.getValue();
    }

    private static String rawToken(String setupLink) {
        assertThat(setupLink).contains("set-password.html?token=");
        return setupLink.substring(setupLink.indexOf("token=") + 6);
    }

    // ---- 1-9: the complete happy path -------------------------------------------------------

    @Test
    void purchaseCreatesStudentIdAndSetupLinkThatActivatesTheAccountForLogin() throws Exception {
        JsonNode purchase = api.data(api.post(null, "/api/payments/demo-complete", buyer("chain@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newAccount").value(true))
                .andExpect(jsonPath("$.data.setupEmailSent").value(true)));

        String studentId = purchase.path("studentId").asText();
        assertThat(studentId).matches("LSI-\\d{4}-\\d{5}");

        User student = userRepository.findByEmailIgnoreCase("chain@example.com").orElseThrow();
        assertThat(student.getAccountStatus()).isEqualTo(AccountStatus.PENDING_SETUP);
        assertThat(student.getPasswordHash()).isNull();
        assertThat(enrollmentRepository.findByStudentIdAndCourseId(student.getId(), courseId)).isPresent();

        String token = rawToken(capturePurchaseSetupLink());

        // The link identifies the account without leaking the full address.
        api.get(null, "/api/auth/token-check?token=" + token)
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.purpose").value("ACCOUNT_SETUP"));

        api.post(null, "/api/auth/reset-password", Map.of("token", token, "newPassword", "Chosen0nePass"))
                .andExpect(status().isOk());

        User activated = userRepository.findById(student.getId()).orElseThrow();
        assertThat(activated.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(activated.getPasswordHash()).isNotNull().doesNotContain("Chosen0nePass");

        // The Student ID survives account setup and is accepted as the login identifier.
        assertThat(studentProfileRepository.findByUserId(student.getId()).orElseThrow().getStudentId())
                .isEqualTo(studentId);

        JsonNode login = api.data(api.post(null, "/api/auth/login",
                        Map.of("identifier", studentId, "password", "Chosen0nePass", "portal", "STUDENT"))
                .andExpect(status().isOk()));
        assertThat(login.path("accessToken").asText()).isNotBlank();
        assertThat(login.path("redirectUrl").asText()).isEqualTo("student-dashboard.html");
        assertThat(login.path("user").path("studentId").asText()).isEqualTo(studentId);

        // The issued JWT actually opens the student area, and the course is already there.
        String jwt = login.path("accessToken").asText();
        api.get(jwt, "/api/student/profile").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(studentId));
        api.get(jwt, "/api/student/courses").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseCode").value("SMET-MASTER"));
    }

    @Test
    void studentEndpointsRejectUnauthenticatedCallers() throws Exception {
        api.get(null, "/api/student/profile").andExpect(status().isUnauthorized());
        api.get(null, "/api/student/courses").andExpect(status().isUnauthorized());
    }

    // ---- 10-11: no duplicates, failed payment grants nothing ---------------------------------

    @Test
    void repeatPurchaseCreatesNoDuplicateStudentOrStudentId() throws Exception {
        String first = api.data(api.post(null, "/api/payments/demo-complete", buyer("repeat@example.com"))
                .andExpect(status().isOk())).path("studentId").asText();

        User student = userRepository.findByEmailIgnoreCase("repeat@example.com").orElseThrow();
        // A second course for the same buyer: the checkout guard only blocks the SAME course.
        api.post(null, "/api/payments/demo-complete", buyer("repeat@example.com"))
                .andExpect(status().isConflict());

        assertThat(userRepository.findAll().stream()
                .filter(u -> "repeat@example.com".equals(u.getEmail())).count()).isEqualTo(1);
        assertThat(studentProfileRepository.findByUserId(student.getId()).orElseThrow().getStudentId())
                .isEqualTo(first);
        assertThat(enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(student.getId())).hasSize(1);
    }

    // ---- 12-13: expired and used setup tokens ------------------------------------------------

    @Test
    void expiredSetupTokenIsRejected() throws Exception {
        api.post(null, "/api/payments/demo-complete", buyer("expired@example.com")).andExpect(status().isOk());
        String token = rawToken(capturePurchaseSetupLink());

        AccountToken stored = tokenRepository.findAll().stream()
                .filter(t -> t.getPurpose() == TokenPurpose.ACCOUNT_SETUP).findFirst().orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        tokenRepository.saveAndFlush(stored);

        api.get(null, "/api/auth/token-check?token=" + token).andExpect(jsonPath("$.data.valid").value(false));
        api.post(null, "/api/auth/reset-password", Map.of("token", token, "newPassword", "TooLate0ne"))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByEmailIgnoreCase("expired@example.com").orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.PENDING_SETUP);
    }

    @Test
    void usedSetupTokenCannotBeReplayed() throws Exception {
        api.post(null, "/api/payments/demo-complete", buyer("once@example.com")).andExpect(status().isOk());
        String token = rawToken(capturePurchaseSetupLink());

        api.post(null, "/api/auth/reset-password", Map.of("token", token, "newPassword", "FirstPass01"))
                .andExpect(status().isOk());
        api.post(null, "/api/auth/reset-password", Map.of("token", token, "newPassword", "SecondPass1"))
                .andExpect(status().isBadRequest());

        // The password from the replay attempt was never applied.
        api.post(null, "/api/auth/login",
                Map.of("identifier", "once@example.com", "password", "SecondPass1", "portal", "STUDENT"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 14: an email that never went out must not be reported as sent ------------------------

    @Test
    void failedEnrollmentEmailIsReportedInsteadOfClaimingSuccess() throws Exception {
        when(emailService.sendPurchaseEmail(any()))
                .thenReturn(EmailDelivery.failed("MailSendException: connection refused"));

        JsonNode res = api.data(api.post(null, "/api/payments/demo-complete", buyer("nomail@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.setupEmailSent").value(false)));

        assertThat(res.path("message").asText())
                .contains("could not email")
                .doesNotContain("We have emailed");
        // The paid enrollment still exists — only the notification failed.
        assertThat(res.path("studentId").asText()).matches("LSI-\\d{4}-\\d{5}");
        assertThat(enrollmentRepository.count()).isEqualTo(1);
    }

    @Test
    void loggingEmailServiceReportsThatNothingWasDelivered() {
        // The default local setup (MAIL_ENABLED=false) must not count as a delivered email.
        assertThat(EmailDelivery.disabled().delivered()).isFalse();
        assertThat(EmailDelivery.sent().delivered()).isTrue();
        assertThat(EmailDelivery.failed("x").delivered()).isFalse();
    }

    // ---- 15: resend ---------------------------------------------------------------------------

    @Test
    void resendIssuesAFreshTokenInvalidatesTheOldOneAndCreatesNoDuplicates() throws Exception {
        api.post(null, "/api/payments/demo-complete", buyer("resend@example.com")).andExpect(status().isOk());
        String firstToken = rawToken(capturePurchaseSetupLink());
        User student = userRepository.findByEmailIgnoreCase("resend@example.com").orElseThrow();
        long usersBefore = userRepository.count();
        long enrollmentsBefore = enrollmentRepository.count();
        clearInvocations(emailService);

        api.post(null, "/api/auth/resend-setup", Map.of("email", "resend@example.com"))
                .andExpect(status().isOk());

        String secondToken = rawToken(captureSetupLink(1));
        assertThat(secondToken).isNotEqualTo(firstToken);

        // Old link is dead, new link works.
        api.get(null, "/api/auth/token-check?token=" + firstToken).andExpect(jsonPath("$.data.valid").value(false));
        api.get(null, "/api/auth/token-check?token=" + secondToken).andExpect(jsonPath("$.data.valid").value(true));

        // Exactly one setup token survives, and nothing was duplicated.
        assertThat(tokenRepository.findAll().stream()
                .filter(t -> t.getPurpose() == TokenPurpose.ACCOUNT_SETUP).count()).isEqualTo(1);
        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(enrollmentRepository.count()).isEqualTo(enrollmentsBefore);

        api.post(null, "/api/auth/reset-password", Map.of("token", secondToken, "newPassword", "Recovered01"))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(student.getId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void resendNeverRevealsWhetherAnAccountExists() throws Exception {
        api.post(null, "/api/auth/resend-setup", Map.of("email", "nobody@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "If that email has an enrollment, we have re-sent the Student ID and password setup link."));
        verify(emailService, times(0)).sendEnrollmentEmail(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void resendForAnActiveAccountSendsNoNewSetupToken() throws Exception {
        User active = users.student("active@example.com");

        api.post(null, "/api/auth/resend-setup", Map.of("email", "active@example.com"))
                .andExpect(status().isOk());

        // The email points at the portal instead: an active account recovers via password reset.
        assertThat(captureSetupLink(1)).isNull();
        assertThat(tokenRepository.findAll()).noneMatch(t -> t.getUser().getId().equals(active.getId()));
    }

    @Test
    void adminCanResendTheSetupEmail() throws Exception {
        api.post(null, "/api/payments/demo-complete", buyer("adminresend@example.com")).andExpect(status().isOk());
        User student = userRepository.findByEmailIgnoreCase("adminresend@example.com").orElseThrow();
        clearInvocations(emailService);

        users.admin("admin@test.local");
        String admin = api.login("admin@test.local", TestUsers.PASSWORD);

        api.post(admin, "/api/admin/students/" + student.getId() + "/resend-setup", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Setup email re-sent to adminresend@example.com."));

        assertThat(rawToken(captureSetupLink(1))).isNotBlank();
    }

    @Test
    void adminIsToldWhenTheResendCouldNotBeDelivered() throws Exception {
        api.post(null, "/api/payments/demo-complete", buyer("brokenmail@example.com")).andExpect(status().isOk());
        User student = userRepository.findByEmailIgnoreCase("brokenmail@example.com").orElseThrow();
        users.admin("admin2@test.local");
        String admin = api.login("admin2@test.local", TestUsers.PASSWORD);

        when(emailService.sendEnrollmentEmail(any(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(EmailDelivery.failed("MailAuthenticationException: 535 auth failed"));

        api.post(admin, "/api/admin/students/" + student.getId() + "/resend-setup", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.startsWith("Could not send the email")));
    }

    @Test
    void studentsCannotResendForSomebodyElseThroughTheAdminApi() throws Exception {
        User other = users.student("victim@example.com");
        users.student("attacker@example.com");
        String attacker = api.login("attacker@example.com", TestUsers.PASSWORD);

        api.post(attacker, "/api/admin/students/" + other.getId() + "/resend-setup", null)
                .andExpect(status().isForbidden());
        assertThat(List.copyOf(tokenRepository.findAll())).isEmpty();
    }
}
