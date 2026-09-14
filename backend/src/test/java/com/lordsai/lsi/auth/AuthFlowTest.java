package com.lordsai.lsi.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.entity.enums.TokenPurpose;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.service.AccountTokenService;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestUsers testUsers;
    @Autowired UserRepository userRepository;
    @Autowired AccountTokenService accountTokenService;

    // ---- Login -----------------------------------------------------------------------------

    @Test
    void validLoginReturnsTokenAndRedirect() throws Exception {
        testUsers.student("valid@example.com");

        mockMvc.perform(login("valid@example.com", TestUsers.PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.studentId").isNotEmpty())
                .andExpect(jsonPath("$.data.redirectUrl").value("student-dashboard.html"));
    }

    @Test
    void studentCanLoginWithStudentIdInsteadOfEmail() throws Exception {
        User student = testUsers.student("byid@example.com");

        mockMvc.perform(login("lsi-test-" + student.getId(), TestUsers.PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("byid@example.com"));
    }

    @Test
    void invalidPasswordIsRejected() throws Exception {
        testUsers.student("wrongpw@example.com");

        mockMvc.perform(login("wrongpw@example.com", "not-the-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email/student ID or password."));
    }

    @Test
    void unknownUserGetsSameMessageAsWrongPassword() throws Exception {
        mockMvc.perform(login("nobody@example.com", "whatever1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email/student ID or password."));
    }

    @Test
    void disabledAccountCannotLogin() throws Exception {
        testUsers.create("disabled@example.com", Role.STUDENT, AccountStatus.DISABLED);

        mockMvc.perform(login("disabled@example.com", TestUsers.PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "This account has been disabled. Please contact the academy."));
    }

    @Test
    void studentCannotLoginOnSecondDeviceWhileFirstSessionIsActive() throws Exception {
        testUsers.student("twodevices@example.com");

        mockMvc.perform(login("twodevices@example.com", TestUsers.PASSWORD))
                .andExpect(status().isOk());

        mockMvc.perform(login("twodevices@example.com", TestUsers.PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "This account is already logged in on another device. Please logout from the other device first."));
    }

    @Test
    void studentCanLoginAgainAfterLoggingOut() throws Exception {
        testUsers.student("relogin@example.com");
        String token = tokenFrom(mockMvc.perform(login("relogin@example.com", TestUsers.PASSWORD)).andReturn());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(login("relogin@example.com", TestUsers.PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void tokenIsUselessAfterLogout() throws Exception {
        testUsers.student("deadtoken@example.com");
        String token = tokenFrom(mockMvc.perform(login("deadtoken@example.com", TestUsers.PASSWORD)).andReturn());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accountLocksAfterRepeatedFailures() throws Exception {
        testUsers.student("locked@example.com");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login("locked@example.com", "bad-password-1"));
        }
        mockMvc.perform(login("locked@example.com", TestUsers.PASSWORD))
                .andExpect(status().isTooManyRequests());
    }

    // ---- Authorization --------------------------------------------------------------------

    @Test
    void studentCannotAccessAdminApi() throws Exception {
        testUsers.student("s1@example.com");
        String token = tokenFrom(mockMvc.perform(login("s1@example.com", TestUsers.PASSWORD)).andReturn());

        mockMvc.perform(get("/api/admin/anything").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ---- Login portals: Student Admin accepts STUDENT only, Admin Login accepts ADMIN only --------

    private org.springframework.test.web.servlet.RequestBuilder loginVia(String identifier, String password, String portal) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\",\"portal\":\"" + portal + "\"}");
    }

    @Test
    void portalsOnlyAcceptTheirOwnRole() throws Exception {
        testUsers.student("stu@example.com");
        testUsers.admin("boss@example.com");

        // A + D: correct portal -> allowed, redirected to the matching dashboard
        mockMvc.perform(loginVia("stu@example.com", TestUsers.PASSWORD, "STUDENT")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.redirectUrl").value("student-dashboard.html"));
        mockMvc.perform(loginVia("boss@example.com", TestUsers.PASSWORD, "ADMIN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.redirectUrl").value("admin-dashboard.html"));

        // B: admin through Student Admin -> denied with a clear message, no session/token issued
        mockMvc.perform(loginVia("boss@example.com", TestUsers.PASSWORD, "STUDENT")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only students can log in through Student Admin."))
                .andExpect(jsonPath("$.data").doesNotExist());
        // C: student through Admin Login -> denied
        mockMvc.perform(loginVia("stu@example.com", TestUsers.PASSWORD, "ADMIN")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only administrators can log in through Admin Login."));
        // Wrong password on the wrong portal is still just "invalid credentials" (no role oracle).
        mockMvc.perform(loginVia("boss@example.com", "wrong-password", "STUDENT")).andExpect(status().isUnauthorized());
        // Unknown portal value is rejected.
        mockMvc.perform(loginVia("stu@example.com", TestUsers.PASSWORD, "TEACHER")).andExpect(status().isBadRequest());
    }

    @Test
    void legacyTeacherAccountCannotLogIn() throws Exception {
        testUsers.legacyTeacher("t1@example.com");
        mockMvc.perform(login("t1@example.com", TestUsers.PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Teacher accounts are no longer supported. Please contact the academy."));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        testUsers.student("tamper@example.com");
        String token = tokenFrom(mockMvc.perform(login("tamper@example.com", TestUsers.PASSWORD)).andReturn());
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    // ---- Password setup / reset -------------------------------------------------------------

    @Test
    void pendingAccountIsActivatedBySettingPasswordViaToken() throws Exception {
        User pending = testUsers.create("pending@example.com", Role.STUDENT, AccountStatus.PENDING_SETUP);
        pending.setPasswordHash(null);
        userRepository.saveAndFlush(pending);

        mockMvc.perform(login("pending@example.com", TestUsers.PASSWORD))
                .andExpect(status().isForbidden());

        String link = accountTokenService.issueLink(pending, TokenPurpose.ACCOUNT_SETUP);
        String rawToken = link.substring(link.indexOf("token=") + 6);

        mockMvc.perform(get("/api/auth/token-check").param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.purpose").value("ACCOUNT_SETUP"));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", rawToken, "newPassword", "NewPassw0rd"))))
                .andExpect(status().isOk());

        mockMvc.perform(login("pending@example.com", "NewPassw0rd"))
                .andExpect(status().isOk());

        // The token is single-use.
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", rawToken, "newPassword", "Another0ne"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetInvalidatesExistingSessions() throws Exception {
        User user = testUsers.student("reset@example.com");
        String oldToken = tokenFrom(mockMvc.perform(login("reset@example.com", TestUsers.PASSWORD)).andReturn());

        String link = accountTokenService.issueLink(user, TokenPurpose.PASSWORD_RESET);
        String rawToken = link.substring(link.indexOf("token=") + 6);
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", rawToken, "newPassword", "Changed123"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(login("reset@example.com", TestUsers.PASSWORD))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(login("reset@example.com", "Changed123"))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPasswordNeverRevealsWhetherAccountExists() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ghost@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void weakPasswordIsRejectedByValidation() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", "x", "newPassword", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    // ---- helpers ---------------------------------------------------------------------------

    private org.springframework.test.web.servlet.RequestBuilder login(String identifier, String password) throws Exception {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("identifier", identifier, "password", password)));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String tokenFrom(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = node.path("data").path("accessToken").asText(null);
        assertThat(token).as("login response should contain a token: " + node).isNotNull();
        return token;
    }
}
