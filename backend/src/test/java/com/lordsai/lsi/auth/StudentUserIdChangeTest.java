package com.lordsai.lsi.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.service.UserService;
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

/**
 * Student ID vs User ID: students sign in with their User ID (generated for new students, changeable in
 * Settings -> Change User ID); the academy-issued Student ID (certificates, invoices, admin panel) is
 * only a reference, never changes and does not sign in by itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentUserIdChangeTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestUsers testUsers;
    @Autowired StudentProfileRepository studentProfileRepository;
    @Autowired com.lordsai.lsi.service.StudentIdRepair studentIdRepair;
    @Autowired com.lordsai.lsi.service.AuditService auditService;
    @Autowired UserService userService;
    @Autowired com.lordsai.lsi.repository.UserRepository userRepository;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    void studentChangesUserIdAndOnlyTheNewOneLogsInAfterwards() throws Exception {
        User student = testUsers.student("rename@example.com");
        String oldId = "LSI-TEST-" + student.getId();
        String token = tokenFrom(mockMvc.perform(login(oldId, TestUsers.PASSWORD)).andReturn());

        mockMvc.perform(change(token, oldId, "ravi.trader", "ravi.trader", TestUsers.PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("ravi.trader"));

        // Same account, same row: the User ID changed, the Student ID did NOT.
        StudentProfile profile = studentProfileRepository.findByUserId(student.getId()).orElseThrow();
        assertThat(profile.getStudentId()).isEqualTo(oldId);
        assertThat(profile.getLoginId()).isEqualTo("ravi.trader");
        assertThat(profile.getUser().getEmail()).isEqualTo("rename@example.com");

        // The current session is still valid (the token is keyed on the user id, not on any ID text).
        mockMvc.perform(get("/api/student/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(oldId))
                .andExpect(jsonPath("$.data.loginUserId").value("ravi.trader"))
                .andExpect(jsonPath("$.data.email").value("rename@example.com"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(oldId))
                .andExpect(jsonPath("$.data.loginUserId").value("ravi.trader"));

        // The new User ID logs in (case-insensitively); the Student ID no longer signs in, email still does.
        mockMvc.perform(login("Ravi.Trader", TestUsers.PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("rename@example.com"))
                .andExpect(jsonPath("$.data.user.studentId").value(oldId))
                .andExpect(jsonPath("$.data.user.loginUserId").value("ravi.trader"));
        mockMvc.perform(login(oldId, TestUsers.PASSWORD))
                .andExpect(status().isUnauthorized());
        // (One login per account: each login above replaced the previous one, so continue with the newest.)
        token = tokenFrom(mockMvc.perform(login("rename@example.com", TestUsers.PASSWORD))
                .andExpect(status().isOk()).andReturn());

        // Choosing the Student ID again makes it the User ID once more.
        mockMvc.perform(change(token, "ravi.trader", oldId, oldId, TestUsers.PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(oldId));
        assertThat(studentProfileRepository.findByUserId(student.getId()).orElseThrow().getLoginId()).isEqualTo(oldId);
        mockMvc.perform(login(oldId, TestUsers.PASSWORD)).andExpect(status().isOk());
        mockMvc.perform(login("ravi.trader", TestUsers.PASSWORD)).andExpect(status().isUnauthorized());
    }

    @Test
    void anotherStudentsChosenUserIdIsTaken() throws Exception {
        User first = testUsers.student("taken1@example.com");
        User second = testUsers.student("taken2@example.com");
        String t1 = tokenFrom(mockMvc.perform(login("LSI-TEST-" + first.getId(), TestUsers.PASSWORD)).andReturn());
        String t2 = tokenFrom(mockMvc.perform(login("LSI-TEST-" + second.getId(), TestUsers.PASSWORD)).andReturn());
        mockMvc.perform(change(t1, "LSI-TEST-" + first.getId(), "market.king", "market.king", TestUsers.PASSWORD)).andExpect(status().isOk());
        mockMvc.perform(change(t2, "LSI-TEST-" + second.getId(), "Market.King", "Market.King", TestUsers.PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That User ID is already in use. Please choose a different one."));
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

    /** Before the fix, a chosen ID overwrote the Student ID: the repair gives it back and keeps the login. */
    @Test
    void repairRestoresOverwrittenStudentIdsAndKeepsTheChosenIdForLogin() throws Exception {
        User pranjal = testUsers.student("pranjal@example.com");
        User noHistory = testUsers.student("nohistory@example.com");
        StudentProfile p1 = studentProfileRepository.findByUserId(pranjal.getId()).orElseThrow();
        p1.setStudentId("Pranjal");
        p1.setLoginId(null);
        studentProfileRepository.saveAndFlush(p1);
        auditService.record(pranjal, "USER_ID_CHANGED", "StudentProfile", p1.getId(),
                "Student ID changed from LSI-2026-00042 to Pranjal by the student", null);
        StudentProfile p2 = studentProfileRepository.findByUserId(noHistory.getId()).orElseThrow();
        p2.setStudentId("trader99");
        p2.setLoginId(null);
        studentProfileRepository.saveAndFlush(p2);

        assertThat(studentIdRepair.repair()).isEqualTo(2);

        StudentProfile r1 = studentProfileRepository.findByUserId(pranjal.getId()).orElseThrow();
        assertThat(r1.getStudentId()).isEqualTo("LSI-2026-00042");
        assertThat(r1.getLoginId()).isEqualTo("Pranjal");
        StudentProfile r2 = studentProfileRepository.findByUserId(noHistory.getId()).orElseThrow();
        assertThat(r2.getStudentId()).matches("LSI-\\d{4}-\\d{5}");
        assertThat(r2.getLoginId()).isEqualTo("trader99");

        // They keep signing in exactly as before, and see their Student ID again.
        mockMvc.perform(login("Pranjal", TestUsers.PASSWORD)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.studentId").value("LSI-2026-00042"))
                .andExpect(jsonPath("$.data.user.loginUserId").value("Pranjal"));
        mockMvc.perform(login("trader99", TestUsers.PASSWORD)).andExpect(status().isOk());

        // Running it again changes nothing.
        assertThat(studentIdRepair.repair()).isZero();
    }

    /** Student ID and User ID are separate: a new student gets a generated User ID, and only that (or email) signs in. */
    @Test
    void newStudentGetsASeparateUserIdAndTheStudentIdDoesNotSignIn() throws Exception {
        UserService.StudentCreation c = userService.createStudentByAdmin("Rahul Patil", "rahul.patil@example.com", "9876500001", null, null);
        StudentProfile profile = c.profile();
        assertThat(profile.getStudentId()).matches("LSI-\\d{4}-\\d{5}");
        assertThat(profile.getLoginId()).matches("rahul\\d{4}").isNotEqualTo(profile.getStudentId());
        User user = c.user();
        user.setPasswordHash(passwordEncoder.encode(TestUsers.PASSWORD));
        user.setAccountStatus(com.lordsai.lsi.entity.enums.AccountStatus.ACTIVE);
        userRepository.saveAndFlush(user);

        mockMvc.perform(login(profile.getLoginId().toUpperCase(), TestUsers.PASSWORD)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.studentId").value(profile.getStudentId()))
                .andExpect(jsonPath("$.data.user.loginUserId").value(profile.getLoginId()));
        mockMvc.perform(login("rahul.patil@example.com", TestUsers.PASSWORD)).andExpect(status().isOk());
        mockMvc.perform(login(profile.getStudentId(), TestUsers.PASSWORD)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email/User ID or password."));

        // Names without usable Latin letters still get a valid User ID; accents are simplified.
        assertThat(userService.createStudentByAdmin("राहुल पाटील", "devanagari@example.com", "9876500002", null, null)
                .profile().getLoginId()).matches("student\\d{4}");
        assertThat(userService.createStudentByAdmin("  Zoë  Fernandes ", "zoe@example.com", "9876500003", null, null)
                .profile().getLoginId()).matches("zoe\\d{4}");
    }

    /** A legacy profile with an academy Student ID and no User ID keeps signing in with that text (same as V34). */
    @Test
    void legacyProfileWithoutUserIdGetsItsStudentIdAsUserId() throws Exception {
        User legacy = testUsers.student("legacy@example.com");
        StudentProfile p = studentProfileRepository.findByUserId(legacy.getId()).orElseThrow();
        p.setStudentId("LSI-2025-00077");
        p.setLoginId(null);
        studentProfileRepository.saveAndFlush(p);

        assertThat(studentIdRepair.repair()).isZero();   // not a "repair": the Student ID was never overwritten
        assertThat(studentProfileRepository.findByUserId(legacy.getId()).orElseThrow().getLoginId()).isEqualTo("LSI-2025-00077");
        mockMvc.perform(login("LSI-2025-00077", TestUsers.PASSWORD)).andExpect(status().isOk());
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
