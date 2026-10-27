package com.lordsai.lsi.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.repository.AuditLogRepository;
import com.lordsai.lsi.security.StreamTicketService;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Server side of the Student Portal content protection: media stays behind enrollment checks,
 * denied requests are audited, and browser-reported protection events land in the audit log.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContentProtectionTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired MockMvc mockMvc;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired StreamTicketService tickets;

    private String admin, student;
    private User studentUser;
    private long courseId, lessonId;

    @BeforeEach
    void setUp() throws Exception {
        users.admin("admin@test.local");
        studentUser = users.student("pupil@test.local");
        admin = api.login("admin@test.local", TestUsers.PASSWORD);
        student = api.login("pupil@test.local", TestUsers.PASSWORD);
        courseId = api.data(api.get(admin, "/api/admin/courses")).get(0).path("id").asLong();
        long moduleId = api.data(api.get(admin, "/api/admin/courses/" + courseId + "/modules")).get(0).path("id").asLong();
        lessonId = api.data(api.post(admin, "/api/admin/modules/" + moduleId + "/lessons", Map.of("lessonTitle", "Paid"))).path("id").asLong();
        mockMvc.perform(multipart("/api/admin/lessons/" + lessonId + "/video")
                .file(new MockMultipartFile("file", "s.mp4", "video/mp4", new byte[64])).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/admin/lessons/" + lessonId + "/material")
                .file(new MockMultipartFile("file", "h.pdf", "application/pdf", "%PDF-1.4 test".getBytes())).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    private long auditCount(String action) {
        return auditLogRepository.findAll().stream().filter(a -> action.equals(a.getAction())).count();
    }

    // ---- server-side authorization ------------------------------------------------------------

    @Test
    void mediaIsOnlyServedToAnEnrolledStudentAndDenialsAreAudited() throws Exception {
        // Not enrolled: ticket, material and a forged ticket are all refused.
        api.get(student, "/api/student/lessons/" + lessonId + "/stream-ticket").andExpect(status().isForbidden());
        api.get(student, "/api/student/lessons/" + lessonId + "/material").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/student/lessons/" + lessonId + "/video?ticket=" + lessonId + "." + studentUser.getId() + ".9999999999.forged"))
                .andExpect(status().isForbidden());
        assertThat(auditCount("PROTECTION_UNAUTHORIZED_MEDIA_REQUEST")).isEqualTo(3);

        // A ticket for a user who is NOT enrolled is refused on the stream path even though it is validly signed.
        String signedButUnenrolled = tickets.issue(lessonId, studentUser.getId());
        mockMvc.perform(get("/api/student/lessons/" + lessonId + "/video?ticket=" + signedButUnenrolled)).andExpect(status().isForbidden());
        assertThat(auditCount("PROTECTION_UNAUTHORIZED_MEDIA_REQUEST")).isEqualTo(4);

        api.post(admin, "/api/admin/enrollments", Map.of("studentUserId", studentUser.getId(), "courseId", courseId)).andExpect(status().isOk());

        String url = api.data(api.get(student, "/api/student/lessons/" + lessonId + "/stream-ticket")).path("url").asText();
        mockMvc.perform(get(url)).andExpect(status().isPartialContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "private, no-store"));
        api.get(student, "/api/student/lessons/" + lessonId + "/material").andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "private, no-store"));
        assertThat(auditCount("PROTECTION_UNAUTHORIZED_MEDIA_REQUEST")).as("no new denials once enrolled").isEqualTo(4);
    }

    @Test
    void nonStudentsAndAnonymousCallersCannotReachStudentContent() throws Exception {
        api.post(admin, "/api/admin/enrollments", Map.of("studentUserId", studentUser.getId(), "courseId", courseId)).andExpect(status().isOk());
        api.get(admin, "/api/student/lessons/" + lessonId + "/material").andExpect(status().isForbidden());
        api.get(admin, "/api/student/lessons/" + lessonId + "/stream-ticket").andExpect(status().isForbidden());
        api.get(null, "/api/student/lessons/" + lessonId + "/material").andExpect(status().isUnauthorized());
        api.get(null, "/api/student/lessons/" + lessonId).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/student/lessons/" + lessonId + "/video")).andExpect(status().isBadRequest());
    }

    @Test
    void streamTicketsExpireAndAreBoundToTheLesson() {
        String t = tickets.issue(lessonId, studentUser.getId());
        assertThat(tickets.verify(t, lessonId).userId()).isEqualTo(studentUser.getId());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tickets.verify(t, lessonId + 1))
                .isInstanceOf(com.lordsai.lsi.exception.ApiException.class);
        // The expiry stamped into the ticket is at most 30 minutes out.
        long exp = Long.parseLong(t.split("\\.")[2]);
        assertThat(exp - java.time.Instant.now().getEpochSecond()).isBetween(29 * 60L, 30 * 60L + 5);
    }

    // ---- browser-reported events -----------------------------------------------------------------

    @Test
    void protectionEventsAreRecordedForTheStudentWithCourseAndLessonContext() throws Exception {
        api.post(admin, "/api/admin/enrollments", Map.of("studentUserId", studentUser.getId(), "courseId", courseId)).andExpect(status().isOk());

        api.post(student, "/api/student/protection-events",
                        Map.of("type", "SCREEN_CAPTURE_ATTEMPT", "courseId", courseId, "lessonId", lessonId, "detail", "PrintScreen key"))
                .andExpect(status().isOk());

        var row = auditLogRepository.findAll().stream()
                .filter(a -> "PROTECTION_SCREEN_CAPTURE_ATTEMPT".equals(a.getAction())).findFirst().orElseThrow();
        assertThat(row.getActor().getId()).isEqualTo(studentUser.getId());
        assertThat(row.getEntityType()).isEqualTo("Lesson");
        assertThat(row.getEntityId()).isEqualTo(lessonId);
        assertThat(row.getDescription()).contains("course=" + courseId, "lesson=" + lessonId, "PrintScreen key");

        // Visible to the admin, never containing a token.
        JsonNode audit = api.data(api.get(admin, "/api/admin/audit-logs?size=20"));
        String all = audit.path("content").toString();
        assertThat(all).contains("PROTECTION_SCREEN_CAPTURE_ATTEMPT").doesNotContain(student);
    }

    @Test
    void repeatedEventsOfOneTypeAreThrottledAndNeverFail() throws Exception {
        for (int i = 0; i < 5; i++) {
            api.post(student, "/api/student/protection-events", Map.of("type", "COPY_ATTEMPT", "detail", "Ctrl+C " + i))
                    .andExpect(status().isOk());
        }
        assertThat(auditCount("PROTECTION_COPY_ATTEMPT")).isEqualTo(1);
        // A different type is independent.
        api.post(student, "/api/student/protection-events", Map.of("type", "PRINT_ATTEMPT")).andExpect(status().isOk());
        assertThat(auditCount("PROTECTION_PRINT_ATTEMPT")).isEqualTo(1);
    }

    @Test
    void eventEndpointIsStudentOnlyAndValidated() throws Exception {
        api.post(null, "/api/student/protection-events", Map.of("type", "PRINT_ATTEMPT")).andExpect(status().isUnauthorized());
        api.post(admin, "/api/student/protection-events", Map.of("type", "PRINT_ATTEMPT")).andExpect(status().isForbidden());
        api.post(student, "/api/student/protection-events", Map.of("type", "NOT_A_TYPE")).andExpect(status().isBadRequest());
        api.post(student, "/api/student/protection-events", Map.of("type", "UNAUTHORIZED_MEDIA_REQUEST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This event type is raised by the server only."));
        Map<String, Object> tooLong = new HashMap<>();
        tooLong.put("type", "DOWNLOAD_ATTEMPT");
        tooLong.put("detail", "x".repeat(500));
        api.post(student, "/api/student/protection-events", tooLong).andExpect(status().isBadRequest());
        assertThat(auditLogRepository.findAll().stream().filter(a -> a.getAction().startsWith("PROTECTION_")).count()).isZero();
    }

    @Test
    void crossStudentTicketUsageIsRejectedAndAudited() throws Exception {
        User studentUser2 = users.student("pupil2@test.local");
        String student2 = api.login("pupil2@test.local", TestUsers.PASSWORD);

        // Student 1 is enrolled, obtains valid ticket
        api.post(admin, "/api/admin/enrollments", Map.of("studentUserId", studentUser.getId(), "courseId", courseId)).andExpect(status().isOk());
        String ticketUrl = api.data(api.get(student, "/api/student/lessons/" + lessonId + "/stream-ticket")).path("url").asText();

        // Student 2 calls with student 2's session bearer token and student 1's ticket
        mockMvc.perform(get(ticketUrl).header("Authorization", "Bearer " + student2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have access to this stream ticket."));

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> "PROTECTION_UNAUTHORIZED_MEDIA_REQUEST".equals(a.getAction())
                        && a.getDescription().contains("Cross-student video access attempt"))).isTrue();
    }

    @Test
    void videoRangeSeekingReturnsPartialContentWithHardenedHeaders() throws Exception {
        api.post(admin, "/api/admin/enrollments", Map.of("studentUserId", studentUser.getId(), "courseId", courseId)).andExpect(status().isOk());
        String ticketUrl = api.data(api.get(student, "/api/student/lessons/" + lessonId + "/stream-ticket")).path("url").asText();

        mockMvc.perform(get(ticketUrl).header("Range", "bytes=0-15"))
                .andExpect(status().isPartialContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Accept-Ranges", "bytes"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "inline"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "private, no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Pragma", "no-cache"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Expires", "0"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    void unmappedDirectUploadsUrlsAreProtected() throws Exception {
        // Direct attempt to access unmapped uploads directory paths should be denied, never statically served
        mockMvc.perform(get("/uploads/videos/s.mp4")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/uploads/ebooks/test.pdf")).andExpect(status().isUnauthorized());
    }
}
