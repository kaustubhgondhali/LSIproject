package com.lordsai.lsi.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminCourseAndEnrollmentTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;

    private String admin;
    private String studentToken;
    private User student;

    @BeforeEach
    void setUp() throws Exception {
        users.admin("admin@test.local");
        student = users.student("learner@test.local");
        admin = api.login("admin@test.local", TestUsers.PASSWORD);
        studentToken = api.login("learner@test.local", TestUsers.PASSWORD);
    }

    private JsonNode publicCourse(String code) throws Exception {
        for (JsonNode n : api.data(api.get(null, "/api/public/courses").andExpect(status().isOk()))) {
            if (code.equals(n.path("courseCode").asText())) {
                return n;
            }
        }
        return null;
    }

    private Map<String, Object> courseBody(String code, String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("courseCode", code);
        m.put("courseName", name);
        m.put("price", 12000);
        m.put("discountedPrice", 8999);
        m.put("shortDescription", "short");
        return m;
    }

    // ---- Course CRUD -----------------------------------------------------------------------

    @Test
    void adminCanCreateRenameRepriceActivateAndDeleteCourse() throws Exception {
        JsonNode created = api.data(api.post(admin, "/api/admin/courses", courseBody("opt-101", "Options Basics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.courseCode").value("OPT-101"))
                .andExpect(jsonPath("$.data.effectivePrice").value(8999.0)));
        long id = created.path("id").asLong();

        api.patch(admin, "/api/admin/courses/" + id + "/rename", Map.of("courseName", "Options Mastery"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.courseName").value("Options Mastery"));

        api.patch(admin, "/api/admin/courses/" + id + "/price", Map.of("price", 15000, "discountedPrice", 9999))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.effectivePrice").value(9999.0));

        // Draft courses are invisible to the public catalogue until activated.
        assertThat(publicCourse("OPT-101")).isNull();
        api.patch(admin, "/api/admin/courses/" + id + "/status", Map.of("status", "ACTIVE")).andExpect(status().isOk());
        JsonNode visible = publicCourse("OPT-101");
        assertThat(visible).isNotNull();
        assertThat(visible.path("effectivePrice").decimalValue()).isEqualByComparingTo("9999.00");

        api.delete(admin, "/api/admin/courses/" + id).andExpect(status().isOk());
        api.get(admin, "/api/admin/courses/" + id).andExpect(status().isNotFound());
    }

    @Test
    void discountHigherThanPriceIsRejected() throws Exception {
        Map<String, Object> body = courseBody("bad-1", "Bad");
        body.put("discountedPrice", 99999);
        api.post(admin, "/api/admin/courses", body).andExpect(status().isBadRequest());
    }

    @Test
    void duplicateCourseCodeIsRejected() throws Exception {
        api.post(admin, "/api/admin/courses", courseBody("smet-master", "Clone")).andExpect(status().isConflict());
    }

    @Test
    void courseWithEnrolledStudentsCannotBeDeleted() throws Exception {
        long courseId = api.data(api.get(admin, "/api/admin/courses")).get(0).path("id").asLong();
        api.post(admin, "/api/admin/enrollments", Map.of("studentUserId", student.getId(), "courseId", courseId))
                .andExpect(status().isOk());
        api.delete(admin, "/api/admin/courses/" + courseId).andExpect(status().isConflict());
    }

    // ---- Modules & lessons ----------------------------------------------------------------

    @Test
    void adminCanBuildCurriculumAndReorder() throws Exception {
        long courseId = api.data(api.get(admin, "/api/admin/courses")).get(0).path("id").asLong();
        long m = api.data(api.post(admin, "/api/admin/courses/" + courseId + "/modules", Map.of("moduleName", "Extra Module")))
                .path("id").asLong();
        long l1 = api.data(api.post(admin, "/api/admin/modules/" + m + "/lessons", Map.of("lessonTitle", "First"))).path("id").asLong();
        long l2 = api.data(api.post(admin, "/api/admin/modules/" + m + "/lessons", Map.of("lessonTitle", "Second"))).path("id").asLong();

        api.put(admin, "/api/admin/modules/" + m + "/lessons/reorder", Map.of("orderedIds", List.of(l2, l1))).andExpect(status().isOk());
        JsonNode curriculum = api.data(api.get(admin, "/api/admin/courses/" + courseId + "/modules"));
        JsonNode module = null;
        for (JsonNode n : curriculum) {
            if (n.path("id").asLong() == m) module = n;
        }
        assertThat(module).isNotNull();
        assertThat(module.path("lessons").get(0).path("lessonTitle").asText()).isEqualTo("Second");

        api.put(admin, "/api/admin/lessons/" + l1, Map.of("lessonTitle", "First (renamed)")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonTitle").value("First (renamed)"));
        api.delete(admin, "/api/admin/modules/" + m).andExpect(status().isOk());
    }

    // ---- Enrollment ------------------------------------------------------------------------

    @Test
    void manualEnrollmentIsMarkedAdminCreatedAndPreventsDuplicates() throws Exception {
        long courseId = api.data(api.get(admin, "/api/admin/courses")).get(0).path("id").asLong();

        api.post(admin, "/api/admin/enrollments", Map.of("studentUserId", student.getId(), "courseId", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("ADMIN_MANUAL"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        api.post(admin, "/api/admin/enrollments", Map.of("studentUserId", student.getId(), "courseId", courseId))
                .andExpect(status().isConflict());

        api.get(studentToken, "/api/student/courses").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].enrollmentStatus").value("ACTIVE"));
    }

    @Test
    void deactivatedEnrollmentBlocksContentAccess() throws Exception {
        long courseId = api.data(api.get(admin, "/api/admin/courses")).get(0).path("id").asLong();
        long enrollmentId = api.data(api.post(admin, "/api/admin/enrollments",
                Map.of("studentUserId", student.getId(), "courseId", courseId))).path("id").asLong();

        api.get(studentToken, "/api/student/courses/" + courseId).andExpect(status().isOk());
        api.patch(admin, "/api/admin/enrollments/" + enrollmentId + "/status", Map.of("status", "INACTIVE")).andExpect(status().isOk());
        api.get(studentToken, "/api/student/courses/" + courseId).andExpect(status().isForbidden());
    }

    // ---- Authorization on real endpoints ----------------------------------------------------

    @Test
    void studentCannotUseAdminCourseEndpoints() throws Exception {
        api.get(studentToken, "/api/admin/courses").andExpect(status().isForbidden());
        api.post(studentToken, "/api/admin/courses", courseBody("hack-1", "Hack")).andExpect(status().isForbidden());
        api.get(studentToken, "/api/admin/payments").andExpect(status().isForbidden());
        api.get(studentToken, "/api/admin/students").andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotReadStudentOrAdminData() throws Exception {
        api.get(null, "/api/student/courses").andExpect(status().isUnauthorized());
        api.get(null, "/api/admin/dashboard").andExpect(status().isUnauthorized());
        // The retired teacher API surface no longer exists; nothing under /api is open by accident.
        api.get(null, "/api/teacher/courses").andExpect(status().isUnauthorized());
        api.get(admin, "/api/teacher/courses").andExpect(status().isNotFound());
    }

    @Test
    void adminDashboardAndSearchWork() throws Exception {
        api.get(admin, "/api/admin/dashboard").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStudents").value(1))
                .andExpect(jsonPath("$.data.totalCourses").value(1))
                .andExpect(jsonPath("$.data.paymentMode").value("DEMO"))
                .andExpect(jsonPath("$.data.demoPayments").value(0))
                .andExpect(jsonPath("$.data.razorpayPayments").value(0));
        api.get(admin, "/api/admin/search?q=learner").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students[0].email").value("learner@test.local"));
    }

    @Test
    void adminCanForceLogoutStudent() throws Exception {
        api.get(studentToken, "/api/auth/me").andExpect(status().isOk());
        api.post(admin, "/api/admin/students/" + student.getId() + "/force-logout", null).andExpect(status().isOk());
        api.get(studentToken, "/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void auditLogRecordsAdminActions() throws Exception {
        api.post(admin, "/api/admin/courses", courseBody("aud-1", "Audited")).andExpect(status().isOk());
        api.get(admin, "/api/admin/audit-logs?size=5").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].action").value("COURSE_CREATED"))
                .andExpect(jsonPath("$.data.content[0].actorName").value("Admin User"));
    }
}
