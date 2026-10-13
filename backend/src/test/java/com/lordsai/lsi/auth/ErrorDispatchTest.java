package com.lordsai.lsi.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.repository.AuditLogRepository;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.repository.UserSessionRepository;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a real server (MockMvc never forwards to /error): a failure outside a controller
 * must be reported as what it is, not as "Authentication required" for a perfectly valid login.
 * Deliberately NOT @Transactional — the server thread only sees committed rows — so it cleans up.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorDispatchTest {

    @LocalServerPort int port;
    @Autowired TestUsers users;
    @Autowired UserRepository userRepository;
    @Autowired UserSessionRepository sessionRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();
    private User created;

    @AfterEach
    void cleanUp() {
        if (created != null) {
            sessionRepository.findAll().stream().filter(s -> s.getUser().getId().equals(created.getId())).forEach(sessionRepository::delete);
            auditLogRepository.findAll().stream().filter(a -> a.getActor() != null && a.getActor().getId().equals(created.getId())).forEach(auditLogRepository::delete);
            userRepository.findById(created.getId()).ifPresent(userRepository::delete);
        }
    }

    @Test
    void aFailureOutsideAControllerIsNotReportedAsAuthenticationRequired() throws Exception {
        created = users.create("error.dispatch@test.local", Role.ADMIN, AccountStatus.ACTIVE);
        HttpResponse<String> login = send(HttpRequest.newBuilder(uri("/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                        Map.of("identifier", "error.dispatch@test.local", "password", TestUsers.PASSWORD, "portal", "ADMIN")))));
        assertThat(login.statusCode()).isEqualTo(200);
        String token = objectMapper.readTree(login.body()).path("data").path("accessToken").asText();

        assertThat(send(get("/api/auth/me", token)).statusCode()).isEqualTo(200);

        // The request firewall refuses this URL before any controller runs. The login is valid, so the
        // answer must be the real problem (400), never "Authentication required".
        HttpResponse<String> refused = send(get("/api/auth/me;x=1", token));
        JsonNode body = objectMapper.readTree(refused.body());
        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(body.path("success").asBoolean(true)).isFalse();
        assertThat(body.path("message").asText())
                .isEqualTo("The request could not be understood. Please check the submitted values.");

        // A genuinely missing login is still a 401, and the error page itself is not publicly reachable.
        HttpResponse<String> anonymous = send(get("/api/auth/me", null));
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(objectMapper.readTree(anonymous.body()).path("message").asText()).isEqualTo("Authentication required. Please sign in.");
        assertThat(send(get("/error", null)).statusCode()).isEqualTo(401);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpRequest.Builder get(String path, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(path)).GET();
        return token == null ? b : b.header("Authorization", "Bearer " + token);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
