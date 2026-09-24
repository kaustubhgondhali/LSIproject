package com.lordsai.lsi.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.entity.enums.TokenPurpose;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.service.AccountTokenService;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Send login credentials by email" and the password-changed notice. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LoginCredentialsEmailTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired UserRepository userRepository;
    @Autowired AccountTokenService accountTokenService;

    @MockitoBean EmailService emailService;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        users.admin("creds-admin@test.local");
        admin = api.login("creds-admin@test.local", TestUsers.PASSWORD);
        when(emailService.sendLoginCredentials(any(), anyString(), anyString(), anyString())).thenReturn(EmailDelivery.sent());
        when(emailService.sendWelcomeEmail(any(), anyString())).thenReturn(EmailDelivery.sent());
        when(emailService.sendPasswordChanged(any())).thenReturn(EmailDelivery.sent());
    }

    private Map<String, Object> newStudent(String email, boolean sendCredentials) {
        return Map.of("fullName", "Creds Student", "email", email, "mobile", "9876501234", "sendCredentials", sendCredentials);
    }

    private String capturedPassword(int times) {
        ArgumentCaptor<String> pw = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(times)).sendLoginCredentials(any(), anyString(), pw.capture(), anyString());
        return pw.getValue();
    }

    @Test
    void createStudentWithCredentialsActivatesTheAccountAndEmailsAWorkingPassword() throws Exception {
        JsonNode res = api.data(api.post(admin, "/api/admin/students", newStudent("creds1@example.com", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Student created. Login credentials have been emailed to creds1@example.com."))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE")));
        String studentId = res.path("studentId").asText();

        ArgumentCaptor<String> userId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendLoginCredentials(any(), userId.capture(), link.capture(), url.capture());
        assertThat(userId.getValue()).isEqualTo(studentId);
        assertThat(link.getValue()).contains("set-password.html?token=");
        assertThat(url.getValue()).isEqualTo("http://localhost:5500/student-login.html");
        // No direct welcome email path was taken.
        verify(emailService, never()).sendWelcomeEmail(any(), anyString());

        User user = userRepository.findByEmailIgnoreCase("creds1@example.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNull();

        // The student uses the secure one-time link to set their password and log in
        String rawToken = link.getValue().substring(link.getValue().indexOf("token=") + 6);
        api.post(null, "/api/auth/reset-password", Map.of("token", rawToken, "newPassword", "SecureP@ss123"))
                .andExpect(status().isOk());

        api.post(null, "/api/auth/login", Map.of("identifier", studentId, "password", "SecureP@ss123", "portal", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.redirectUrl").value("student-dashboard.html"));
    }

    @Test
    void createStudentWithoutTheOptionStillUsesTheSetupLink() throws Exception {
        api.post(admin, "/api/admin/students", newStudent("link1@example.com", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Student created. A password setup email has been sent."))
                .andExpect(jsonPath("$.data.accountStatus").value("PENDING_SETUP"));
        verify(emailService).sendWelcomeEmail(any(), anyString());
        verify(emailService, never()).sendLoginCredentials(any(), anyString(), anyString(), anyString());
        assertThat(userRepository.findByEmailIgnoreCase("link1@example.com").orElseThrow().getPasswordHash()).isNull();
    }

    @Test
    void sendCredentialsForAnExistingStudentIsAnExplicitResetToAFreshPassword() throws Exception {
        User existing = users.student("reset-me@example.com");
        String oldToken = api.login("reset-me@example.com", TestUsers.PASSWORD);

        api.post(admin, "/api/admin/students/" + existing.getId() + "/send-credentials", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("A secure password setup link has been emailed to reset-me@example.com."));
        String freshLink = capturedPassword(1);
        assertThat(freshLink).contains("set-password.html?token=");

        // Old session is revoked
        api.get(oldToken, "/api/student/profile").andExpect(status().isUnauthorized());

        // Student sets new password via the setup link
        String rawToken = freshLink.substring(freshLink.indexOf("token=") + 6);
        api.post(null, "/api/auth/reset-password", Map.of("token", rawToken, "newPassword", "BrandNewP@ss1"))
                .andExpect(status().isOk());

        api.post(null, "/api/auth/login", Map.of("identifier", "reset-me@example.com", "password", "BrandNewP@ss1", "portal", "STUDENT"))
                .andExpect(status().isOk());

        // Every call generates a different link.
        clearInvocations(emailService);
        api.post(admin, "/api/admin/students/" + existing.getId() + "/send-credentials", null).andExpect(status().isOk());
        assertThat(capturedPassword(1)).isNotEqualTo(freshLink);
    }

    @Test
    void sendCredentialsActivatesAPendingAccount() throws Exception {
        api.post(admin, "/api/admin/students", newStudent("pending-creds@example.com", false)).andExpect(status().isOk());
        User pending = userRepository.findByEmailIgnoreCase("pending-creds@example.com").orElseThrow();
        assertThat(pending.getAccountStatus()).isEqualTo(AccountStatus.PENDING_SETUP);

        api.post(admin, "/api/admin/students/" + pending.getId() + "/send-credentials", null).andExpect(status().isOk());
        assertThat(userRepository.findById(pending.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void whenTheCredentialsEmailFailsTheAdminIsToldAndCanRetry() throws Exception {
        when(emailService.sendLoginCredentials(any(), anyString(), anyString(), anyString()))
                .thenReturn(EmailDelivery.failed("Could not connect to the SMTP server. Check the host and port."));

        api.post(admin, "/api/admin/students", newStudent("nomail-creds@example.com", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Student created, but the credentials email could not be sent")));
        User user = userRepository.findByEmailIgnoreCase("nomail-creds@example.com").orElseThrow();
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);

        api.post(admin, "/api/admin/students/" + user.getId() + "/send-credentials", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("each attempt generates a new secure setup link")));
    }

    @Test
    void disabledAndNonStudentAccountsAreRefused() throws Exception {
        User disabled = users.create("off@example.com", Role.STUDENT, AccountStatus.DISABLED);
        api.post(admin, "/api/admin/students/" + disabled.getId() + "/send-credentials", null)
                .andExpect(status().isConflict());
        User otherAdmin = users.admin("other-admin@test.local");
        api.post(admin, "/api/admin/students/" + otherAdmin.getId() + "/send-credentials", null)
                .andExpect(status().isNotFound());
        verify(emailService, never()).sendLoginCredentials(any(), anyString(), anyString(), anyString());
    }

    @Test
    void studentsCannotTriggerCredentialEmails() throws Exception {
        User victim = users.student("victim2@example.com");
        users.student("attacker2@example.com");
        String attacker = api.login("attacker2@example.com", TestUsers.PASSWORD);
        api.post(attacker, "/api/admin/students/" + victim.getId() + "/send-credentials", null).andExpect(status().isForbidden());
        verify(emailService, never()).sendLoginCredentials(any(), anyString(), anyString(), anyString());
    }

    // ---- password-changed notice ------------------------------------------------------------

    @Test
    void passwordChangedNoticeIsSentAfterResetLinkAndAfterChangePassword() throws Exception {
        User user = users.student("notify@example.com");
        String link = accountTokenService.issueLink(user, TokenPurpose.PASSWORD_RESET);
        String raw = link.substring(link.indexOf("token=") + 6);
        api.post(null, "/api/auth/reset-password", Map.of("token", raw, "newPassword", "Reset0kPass")).andExpect(status().isOk());
        verify(emailService, times(1)).sendPasswordChanged(any());

        String jwt = api.login("notify@example.com", "Reset0kPass");
        api.post(jwt, "/api/auth/change-password", Map.of("currentPassword", "Reset0kPass", "newPassword", "Changed1Again"))
                .andExpect(status().isOk());
        verify(emailService, times(2)).sendPasswordChanged(any());
    }

    @Test
    void aMailOutageNeverUndoesACompletedReset() throws Exception {
        when(emailService.sendPasswordChanged(any())).thenReturn(EmailDelivery.failed("SMTP down"));
        User user = users.student("outage@example.com");
        String link = accountTokenService.issueLink(user, TokenPurpose.PASSWORD_RESET);
        String raw = link.substring(link.indexOf("token=") + 6);
        api.post(null, "/api/auth/reset-password", Map.of("token", raw, "newPassword", "StillWorks1")).andExpect(status().isOk());
        api.post(null, "/api/auth/login", Map.of("identifier", "outage@example.com", "password", "StillWorks1", "portal", "STUDENT"))
                .andExpect(status().isOk());
    }
}
