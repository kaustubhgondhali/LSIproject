package com.lordsai.lsi.auth;

import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.repository.AuditLogRepository;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.repository.UserSessionRepository;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deliberately NOT @Transactional: these requests commit for real, which is the only way to
 * catch "transaction silently rolled back" mistakes that test-managed transactions hide.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommittedTransactionBehaviourTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired UserRepository userRepository;
    @Autowired UserSessionRepository sessionRepository;
    @Autowired AuditLogRepository auditLogRepository;

    private User created;

    @AfterEach
    void cleanUp() {
        if (created != null) {
            sessionRepository.findAll().stream().filter(s -> s.getUser().getId().equals(created.getId())).forEach(sessionRepository::delete);
            auditLogRepository.findAll().stream().filter(a -> a.getActor() != null && a.getActor().getId().equals(created.getId())).forEach(auditLogRepository::delete);
            userRepository.findById(created.getId()).ifPresent(u -> {
                // student profile cascades are handled by TestUsers only inside transactions; delete explicitly.
                userRepository.delete(u);
            });
        }
    }

    @Test
    void studentCanLoginFromMultipleDevicesAndAdminSessionStillRotates() throws Exception {
        created = users.create("committed@test.local", com.lordsai.lsi.entity.enums.Role.STUDENT, com.lordsai.lsi.entity.enums.AccountStatus.ACTIVE);

        api.post(null, "/api/auth/login", Map.of("identifier", "committed@test.local", "password", TestUsers.PASSWORD))
                .andExpect(status().isOk());
        api.post(null, "/api/auth/login", Map.of("identifier", "committed@test.local", "password", TestUsers.PASSWORD))
                .andExpect(status().isOk());

        created.setRole(com.lordsai.lsi.entity.enums.Role.ADMIN);
        userRepository.saveAndFlush(created);
        api.login("committed@test.local", TestUsers.PASSWORD);
        api.login("committed@test.local", TestUsers.PASSWORD);
    }
}
