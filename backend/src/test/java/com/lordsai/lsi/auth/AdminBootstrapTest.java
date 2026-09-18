package com.lordsai.lsi.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.security.AdminBootstrap;
import com.lordsai.lsi.support.ApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The default Main Admin is seeded once at startup and works through the normal login path. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminBootstrapTest {

    private static final String DEFAULT_EMAIL = "admin@lordsaiinvestment.com";

    @Autowired ApiClient api;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AdminBootstrap bootstrap;

    @Test
    void defaultAdminExistsAfterStartupWithHashedPasswordAndAdminRole() {
        User admin = userRepository.findByEmailIgnoreCase(DEFAULT_EMAIL).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(admin.getPasswordHash()).startsWith("$2a$").doesNotContain("Admin@123");
        assertThat(passwordEncoder.matches("Admin@123", admin.getPasswordHash())).isTrue();
    }

    @Test
    void repeatedStartupsNeverCreateASecondAdmin() {
        long adminsBefore = userRepository.countByRole(Role.ADMIN);
        String hashBefore = userRepository.findByEmailIgnoreCase(DEFAULT_EMAIL).orElseThrow().getPasswordHash();

        bootstrap.run(new DefaultApplicationArguments());
        bootstrap.run(new DefaultApplicationArguments());

        assertThat(userRepository.countByRole(Role.ADMIN)).isEqualTo(adminsBefore);
        assertThat(userRepository.findByUsername("admin")).hasSize(1);
        // The existing password is never overwritten.
        assertThat(userRepository.findByEmailIgnoreCase(DEFAULT_EMAIL).orElseThrow().getPasswordHash())
                .isEqualTo(hashBefore);
    }

    @Test
    void adminCanLogInWithShortUserIdAndReachAdminApis() throws Exception {
        JsonNode login = api.data(api.post(null, "/api/auth/login",
                        Map.of("identifier", "admin", "password", "Admin@123", "portal", "ADMIN"))
                .andExpect(status().isOk()));

        assertThat(login.path("user").path("role").asText()).isEqualTo("ADMIN");
        assertThat(login.path("redirectUrl").asText()).isEqualTo("admin-dashboard.html");
        String jwt = login.path("accessToken").asText();
        assertThat(jwt).isNotBlank();

        api.get(jwt, "/api/admin/dashboard").andExpect(status().isOk());
        api.get(jwt, "/api/admin/students").andExpect(status().isOk());

        api.post(jwt, "/api/auth/logout", Map.of()).andExpect(status().isOk());
        api.get(jwt, "/api/admin/dashboard").andExpect(status().isUnauthorized());
    }

    @Test
    void defaultAdminIsRefusedOnTheStudentPortal() throws Exception {
        api.post(null, "/api/auth/login", Map.of("identifier", "admin", "password", "Admin@123", "portal", "STUDENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only students can log in through Student Admin."));
    }

    @Test
    void wrongPasswordForDefaultAdminIsRejected() throws Exception {
        api.post(null, "/api/auth/login", Map.of("identifier", "admin", "password", "admin@123", "portal", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }
}
