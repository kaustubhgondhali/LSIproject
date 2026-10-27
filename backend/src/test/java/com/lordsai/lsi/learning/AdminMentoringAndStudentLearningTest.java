package com.lordsai.lsi.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.entity.User;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The platform has two roles. Admin owns all mentoring/content operations that a Teacher role
 * used to cover; students only ever see their own data and enrolled content.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminMentoringAndStudentLearningTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired MockMvc mockMvc;

    private String admin, student;
    private User studentUser;
    private long courseId;

    @BeforeEach
    void setUp() throws Exception {
        users.admin("admin@test.local");
        studentUser = users.student("pupil@test.local");
        admin = api.login("admin@test.local", TestUsers.PASSWORD);
        student = api.login("pupil@test.local", TestUsers.PASSWORD);
        courseId = api.data(api.get(admin, "/api/admin/courses")).get(0).path("id").asLong();
    }

    // ---- Role structure --------------------------------------------------------------------

    @Test
    void retiredTeacherRoleIsRefusedAtLoginAndTeacherApiNoLongerExists() throws Exception {
        users.legacyTeacher("mentor@test.local");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"identifier\":\"mentor@test.local\",\"password\":\"" + TestUsers.PASSWORD + "\"}"))
                .andExpect(status().isForbidden());

        // No teacher surface: an authenticated admin gets 404, anonymous gets 401.
        api.get(admin, "/api/teacher/courses").andExpect(status().isNotFound());
        api.get(null, "/api/teacher/courses").andExpect(status().isUnauthorized());
        api.get(admin, "/api/admin/teachers").andExpect(status().isNotFound());
    }

    // ---- Admin mentoring -------------------------------------------------------------------

    @Test
    void studentJournalIsStudentOnlyAndAdminAnswersDoubts() throws Exception {
        // Student trade journal keeps working exactly as before (create / list / own-delete).
        api.post(student, "/api/student/journal", Map.of("tradeDate", "2026-09-01", "symbol", "infy", "tradeType", "SWING",
                        "entryPrice", 1500, "stopLoss", 1470, "target", 1590)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbol").value("INFY"))
                .andExpect(jsonPath("$.data.riskReward").value("1 : 3"));
        long tradeId = api.data(api.get(student, "/api/student/journal")).path("content").get(0).path("id").asLong();

        // Admin Trade Journal management was removed: no admin queue, review or per-student journal endpoints.
        api.get(admin, "/api/admin/journal").andExpect(status().isNotFound());
        api.patch(admin, "/api/admin/journal/" + tradeId + "/review", Map.of("reviewStatus", "REVIEWED", "mentorComment", "x"))
                .andExpect(status().isNotFound());
        api.get(admin, "/api/admin/students/" + studentUser.getId() + "/journal").andExpect(status().isNotFound());
        api.get(admin, "/api/admin/dashboard").andExpect(status().isOk()).andExpect(jsonPath("$.data.pendingTradeReviews").doesNotExist());
        api.delete(student, "/api/student/journal/" + tradeId).andExpect(status().isOk());

        long doubtId = api.data(api.post(student, "/api/student/doubts", Map.of("title", "Stop loss?", "description", "Where should it go?")))
                .path("id").asLong();
        api.post(admin, "/api/admin/doubts/" + doubtId + "/replies", Map.of("message", "Below the swing low."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        api.get(student, "/api/student/doubts/" + doubtId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replies[0].authorRole").value("ADMIN"));
        api.patch(admin, "/api/admin/doubts/" + doubtId + "/status", Map.of("status", "RESOLVED")).andExpect(status().isOk());
        api.post(student, "/api/student/doubts/" + doubtId + "/replies", Map.of("message", "thanks")).andExpect(status().isConflict());
    }

    @Test
    void studentCannotSeeAnotherStudentsDoubtOrJournal() throws Exception {
        users.student("other@test.local");
        String other = api.login("other@test.local", TestUsers.PASSWORD);
        long doubtId = api.data(api.post(student, "/api/student/doubts", Map.of("title", "Mine", "description", "private"))).path("id").asLong();
        api.get(other, "/api/student/doubts/" + doubtId).andExpect(status().isNotFound());
        api.post(other, "/api/student/doubts/" + doubtId + "/replies", Map.of("message", "hi")).andExpect(status().isNotFound());
    }

    // ---- Admin course media ----------------------------------------------------------------

    @Test
    void adminCanUploadAndRemoveLessonMediaAndCourseThumbnail() throws Exception {
        long moduleId = api.data(api.get(admin, "/api/admin/courses/" + courseId + "/modules")).get(0).path("id").asLong();
        long lessonId = api.data(api.post(admin, "/api/admin/modules/" + moduleId + "/lessons", Map.of("lessonTitle", "Media"))).path("id").asLong();

        MockMultipartFile video = new MockMultipartFile("file", "intro.mp4", "video/mp4", new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
        mockMvc.perform(multipart("/api/admin/lessons/" + lessonId + "/video").file(video).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasVideo").value(true));
        api.delete(admin, "/api/admin/lessons/" + lessonId + "/video").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasVideo").value(false));

        MockMultipartFile pdf = new MockMultipartFile("file", "notes.pdf", "application/pdf", "%PDF-1.4".getBytes());
        mockMvc.perform(multipart("/api/admin/lessons/" + lessonId + "/material").file(pdf).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasMaterial").value(true));
        api.delete(admin, "/api/admin/lessons/" + lessonId + "/material").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasMaterial").value(false));

        MockMultipartFile image = new MockMultipartFile("file", "thumb.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        JsonNode course = api.data(mockMvc.perform(multipart("/api/admin/courses/" + courseId + "/thumbnail").file(image)
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk()));
        String path = course.path("thumbnailPath").asText();
        assertThat(path).startsWith("images/").endsWith(".png");
        // The uploaded thumbnail is publicly served and survives a course edit that leaves the field blank.
        mockMvc.perform(get("/api/public/images/" + path.substring("images/".length()))).andExpect(status().isOk());
        api.put(admin, "/api/admin/courses/" + courseId, Map.of("courseCode", course.path("courseCode").asText(),
                        "courseName", course.path("courseName").asText(), "price", 14999, "thumbnailPath", ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.thumbnailPath").value(path));

        // A student cannot touch any of it.
        mockMvc.perform(multipart("/api/admin/lessons/" + lessonId + "/video").file(video).header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden());
        api.delete(student, "/api/admin/lessons/" + lessonId + "/video").andExpect(status().isForbidden());
    }

    // ---- Student learning ------------------------------------------------------------------

    @Test
    void progressIsCalculatedFromCompletedLessonsAndVideoTicketIsScoped() throws Exception {
        long moduleId = api.data(api.get(admin, "/api/admin/courses/" + courseId + "/modules")).get(0).path("id").asLong();
        long l1 = api.data(api.post(admin, "/api/admin/modules/" + moduleId + "/lessons", Map.of("lessonTitle", "L1"))).path("id").asLong();
        long l2 = api.data(api.post(admin, "/api/admin/modules/" + moduleId + "/lessons", Map.of("lessonTitle", "L2"))).path("id").asLong();

        // Not enrolled yet: everything is forbidden with a clear message.
        api.get(student, "/api/student/courses/" + courseId).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have access to this course."));
        api.get(student, "/api/student/lessons/" + l1).andExpect(status().isForbidden());
        api.get(student, "/api/student/lessons/" + l1 + "/stream-ticket").andExpect(status().isForbidden());

        api.post(admin, "/api/admin/enrollments", Map.of("studentUserId", studentUser.getId(), "courseId", courseId)).andExpect(status().isOk());

        api.get(student, "/api/student/courses/" + courseId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalLessons").value(2))
                .andExpect(jsonPath("$.data.progressPercent").value(0));

        api.post(student, "/api/student/lessons/" + l1 + "/progress", Map.of("watchedPercentage", 95))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.courseProgressPercent").value(50));

        JsonNode detail = api.data(api.get(student, "/api/student/lessons/" + l1).andExpect(status().isOk()));
        assertThat(detail.path("nextLessonId").asLong()).isEqualTo(l2);
        assertThat(detail.path("completed").asBoolean()).isTrue();

        api.post(student, "/api/student/lessons/" + l2 + "/progress", Map.of("completed", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseProgressPercent").value(100));
        api.get(student, "/api/student/courses").andExpect(jsonPath("$.data[0].enrollmentStatus").value("COMPLETED"));

        // No video uploaded -> 404, but the enrollment gate has already passed.
        api.get(student, "/api/student/lessons/" + l1 + "/stream-ticket").andExpect(status().isNotFound());
        // A forged ticket is rejected on the public video path.
        api.get(null, "/api/student/lessons/" + l1 + "/video?ticket=1.1.9999999999.bogus").andExpect(status().isForbidden());
    }

    @Test
    void anotherStudentCannotUseAFirstStudentsEnrollmentOrTicket() throws Exception {
        long moduleId = api.data(api.get(admin, "/api/admin/courses/" + courseId + "/modules")).get(0).path("id").asLong();
        long lessonId = api.data(api.post(admin, "/api/admin/modules/" + moduleId + "/lessons", Map.of("lessonTitle", "Secret"))).path("id").asLong();
        MockMultipartFile video = new MockMultipartFile("file", "s.mp4", "video/mp4", new byte[64]);
        mockMvc.perform(multipart("/api/admin/lessons/" + lessonId + "/video").file(video).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        api.post(admin, "/api/admin/enrollments", Map.of("studentUserId", studentUser.getId(), "courseId", courseId)).andExpect(status().isOk());

        // Enrolled student: ticket issued and the stream works without a bearer header.
        String url = api.data(api.get(student, "/api/student/lessons/" + lessonId + "/stream-ticket")).path("url").asText();
        mockMvc.perform(get(url)).andExpect(status().isPartialContent());
        // Without a ticket the video is not reachable at all.
        mockMvc.perform(get("/api/student/lessons/" + lessonId + "/video")).andExpect(status().isBadRequest());

        users.student("intruder@test.local");
        String intruder = api.login("intruder@test.local", TestUsers.PASSWORD);
        api.get(intruder, "/api/student/courses/" + courseId).andExpect(status().isForbidden());
        api.get(intruder, "/api/student/lessons/" + lessonId).andExpect(status().isForbidden());
        api.get(intruder, "/api/student/lessons/" + lessonId + "/stream-ticket").andExpect(status().isForbidden());

        // Deactivating the lesson hides it even from the enrolled student.
        api.patch(admin, "/api/admin/lessons/" + lessonId + "/active", Map.of("active", false)).andExpect(status().isOk());
        api.get(student, "/api/student/lessons/" + lessonId + "/stream-ticket").andExpect(status().is4xxClientError());
    }

    @Test
    void studentOverviewReflectsRealData() throws Exception {
        api.post(admin, "/api/admin/enrollments", Map.of("studentUserId", studentUser.getId(), "courseId", courseId)).andExpect(status().isOk());
        api.get(student, "/api/student/profile").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value("LSI-TEST-" + studentUser.getId()))
                .andExpect(jsonPath("$.data.enrolledCourses").value(1))
                .andExpect(jsonPath("$.data.journalEntries").value(0));
    }
}
