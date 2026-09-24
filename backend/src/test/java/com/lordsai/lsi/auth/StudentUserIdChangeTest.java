package com.lordsai.lsi.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Student Settings -> Change User ID: the sign-in Student ID changes, nothing else does. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentUserIdChangeTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestUsers testUsers;
    @Autowired StudentProfileRepository studentProfileRepository;

    @Test
    void studentChangesUserIdAndOnlyTheNewOneLogsInAfterwards() throws Exception {
        User student = testUsers.student("rename@example.com");
        String oldId = "LSI-TEST-" + student.getId();
        String token = tokenFrom(mockMvc.perform(login(oldId, TestUsers.PASSWORD)).andReturn());

        mockMvc.perform(change(token, oldId, "ravi.trader", "ravi.trader", TestUsers.PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("ravi.trader"));

        // Same account, same row, only the sign-in ID changed.
        StudentProfile profile = studentProfileRepository.findByUserId(student.getId()).orElseThrow();
        assertThat(profile.getStudentId()).isEqualTo("ravi.trader");
        assertThat(profile.getUser().getEmail()).isEqualTo("rename@example.com");
        assertThat(studentProfileRepository.findByStudentIdIgnoreCase(oldId)).isEmpty();

        // The current session is still valid (the token is keyed on the user id, not the Student ID).
        mockMvc.perform(get("/api/student/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value("ravi.trader"))
                .andExpect(jsonPath("$.data.email").value("rename@example.com"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value("ravi.trader"));

        // New ID logs in (case-insensitively, like the academy-issued one); the old ID no longer does.
        mockMvc.perform(login("Ravi.Trader", TestUsers.PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("rename@example.com"))
                .andExpect(jsonPath("$.data.user.studentId").value("ravi.trader"));
        mockMvc.perform(login(oldId, TestUsers.PASSWORD))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(login("rename@example.com", TestUsers.PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordIsRefusedAndNothingChanges() throws Exception {
        User student = testUsers.student("wrongpw@example.com");
        String oldId = "LSI-TEST-" + student.getId();
        String token = tokenFrom(mockMvc.perform(login(oldId, TestUsers.PASSWORD)).andReturn());

        mockMvc.perform(change(token, oldId, "newname1", "newname1", "not-the-password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));

        assertThat(studentProfileRepository.findByUserId(student.getId()).orElseThrow().getStudentId()).isEqualTo(oldId);
    }

    @Test
    void currentUserIdMustBelongToTheLoggedInStudent() throws Exception {
        User student = testUsers.student("mine@example.com");
        User other = testUsers.student("other@example.com");
        String token = tokenFrom(mockMvc.perform(login("mine@example.com", TestUsers.PASSWORD)).andReturn());

        mockMvc.perform(change(token, "LSI-TEST-" + other.getId(), "newname1", "newname1", TestUsers.PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The current User ID does not match your account."));

        // Neither account moved.
        assertThat(studentProfileRepository.findByUserId(student.getId()).orElseThrow().getStudentId()).isEqualTo("LSI-TEST-" + student.getId());
        assertThat(studentProfileRepository.findByUserId(other.getId()).orElseThrow().getStudentId()).isEqualTo("LSI-TEST-" + other.getId());
    }

    @Test
    void mismatchedConfirmationSameIdAndReservedFormatAreRefused() throws Exception {
        User student = testUsers.student("rules@example.com");
        String oldId = "LSI-TEST-" + student.getId();
        String token = tokenFrom(mockMvc.perform(login(oldId, TestUsers.PASSWORD)).andReturn());

        mockMvc.perform(change(token, oldId, "newname1", "newname2", TestUsers.PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The new User IDs do not match."));

        mockMvc.perform(change(token, oldId, oldId.toLowerCase(), oldId.toLowerCase(), TestUsers.PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The new User ID is the same as your current one."));

        mockMvc.perform(change(token, oldId, "LSI-2030-00001", "LSI-2030-00001", TestUsers.PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("IDs in the format LSI-YYYY-NNNNN are issued by the academy and cannot be chosen."));

        // Bean validation: too short / illegal characters / email-like are field errors, never applied.
        mockMvc.perform(change(token, oldId, "ab", "ab", TestUsers.PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newUserId").isNotEmpty());
        mockMvc.perform(change(token, oldId, "ravi@home", "ravi@home", TestUsers.PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newUserId").isNotEmpty());
        mockMvc.perform(change(token, oldId, "", "", TestUsers.PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newUserId").isNotEmpty());

        assertThat(studentProfileRepository.findByUserId(student.getId()).orElseThrow().getStudentId()).isEqualTo(oldId);
    }

    @Test
    void userIdAlreadyUsedByAnotherAccountIsRefused() throws Exception {
        User student = testUsers.student("first@example.com");
        User other = testUsers.student("second@example.com");
        testUsers.admin("office@example.com");
        String oldId = "LSI-TEST-" + student.getId();
        String token = tokenFrom(mockMvc.perform(login(oldId, TestUsers.PASSWORD)).andReturn());

        // Another student's ID, in any letter case.
        mockMvc.perform(change(token, oldId, ("lsi-test-" + other.getId()), ("lsi-test-" + other.getId()), TestUsers.PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That User ID is already in use. Please choose a different one."));

        // Another account's short username (email local part) is also a sign-in identifier.
        mockMvc.perform(change(token, oldId, "office", "office", TestUsers.PASSWORD))
                .andExpect(status().isConflict());

        // The student's own email local part is fine — it already points at them.
        mockMvc.perform(change(token, oldId, "first", "first", TestUsers.PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("first"));
    }

    @Test
    void adminsCannotUseTheStudentEndpoint() throws Exception {
        testUsers.admin("admin@example.com");
        String token = tokenFrom(mockMvc.perform(login("admin@example.com", TestUsers.PASSWORD)).andReturn());

        mockMvc.perform(change(token, "anything", "newname1", "newname1", TestUsers.PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void endpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/student/change-user-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentUserId", "x", "newUserId", "newname1", "confirmUserId", "newname1", "currentPassword", "p"))))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ---------------------------------------------------------------------------

    private RequestBuilder change(String token, String current, String next, String confirm, String password) throws Exception {
        return post("/api/student/change-user-id")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("currentUserId", current, "newUserId", next, "confirmUserId", confirm, "currentPassword", password)));
    }

    private RequestBuilder login(String identifier, String password) throws Exception {
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
