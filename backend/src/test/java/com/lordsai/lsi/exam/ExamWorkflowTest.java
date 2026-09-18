package com.lordsai.lsi.exam;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Course completion -> exam application -> admin schedule -> MCQ attempt -> backend scoring ->
 * certificate, plus the security rules (no correct answers in the paper, attempt limit, ownership).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExamWorkflowTest {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired MockMvc mockMvc;

    private String admin, student;
    private User studentUser;
    private long courseId;
    private final List<Long> lessonIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        users.admin("admin@test.local");
        studentUser = users.student("learner@test.local");
        admin = api.login("admin@test.local", TestUsers.PASSWORD);
        student = api.login("learner@test.local", TestUsers.PASSWORD);
        courseId = api.data(api.get(admin, "/api/admin/courses")).get(0).path("id").asLong();

        // Two lessons in a fresh module; the completion rule is "every active lesson completed".
        long moduleId = api.data(api.post(admin, "/api/admin/courses/" + courseId + "/modules",
                Map.of("moduleName", "Exam prep"))).path("id").asLong();
        for (String title : List.of("Lesson one", "Lesson two")) {
            lessonIds.add(api.data(api.post(admin, "/api/admin/modules/" + moduleId + "/lessons",
                    Map.of("lessonTitle", title))).path("id").asLong());
        }
        api.post(admin, "/api/admin/enrollments", Map.of("studentUserId", studentUser.getId(), "courseId", courseId))
                .andExpect(status().isOk());
    }

    // ---- helpers ---------------------------------------------------------------------------

    private void completeCourse(String token) throws Exception {
        // Any pre-existing seeded lessons in the course must be completed too.
        JsonNode content = api.data(api.get(token, "/api/student/courses/" + courseId));
        for (JsonNode module : content.path("modules")) {
            for (JsonNode lesson : module.path("lessons")) {
                api.post(token, "/api/student/lessons/" + lesson.path("id").asLong() + "/progress",
                        Map.of("watchedPercentage", 100, "completed", true)).andExpect(status().isOk());
            }
        }
    }

    private long createReadyExam(int total, int passing, int maxAttempts) throws Exception {
        long examId = api.data(api.post(admin, "/api/admin/exams", Map.of("courseId", courseId, "title", "Final Course Exam",
                "totalMarks", total, "passingMarks", passing, "maxAttempts", maxAttempts))).path("id").asLong();
        // two questions worth total/2 each (A and C are the correct answers)
        api.post(admin, "/api/admin/exams/" + examId + "/questions", question("What is a stop loss?", "A", total / 2)).andExpect(status().isOk());
        api.post(admin, "/api/admin/exams/" + examId + "/questions", question("What does NSE stand for?", "C", total - total / 2)).andExpect(status().isOk());
        api.patch(admin, "/api/admin/exams/" + examId + "/status", Map.of("status", "ACTIVE")).andExpect(status().isOk());
        return examId;
    }

    private static Map<String, Object> question(String text, String correct, int marks) {
        Map<String, Object> q = new HashMap<>();
        q.put("questionText", text);
        q.put("optionA", "Option A text");
        q.put("optionB", "Option B text");
        q.put("optionC", "Option C text");
        q.put("optionD", "Option D text");
        q.put("correctOption", correct);
        q.put("marks", marks);
        return q;
    }

    private Map<String, Object> openWindow(long examId) {
        // Whole of today (IST) — open right now.
        return Map.of("examId", examId, "examDate", LocalDate.now(INDIA).toString(), "startTime", "00:00", "endTime", "23:59",
                "instructions", "Answer every question.");
    }

    private Map<String, Object> futureWindow(long examId) {
        return Map.of("examId", examId, "examDate", LocalDate.now(INDIA).plusDays(2).toString(), "startTime", "10:00", "endTime", "12:00");
    }

    private List<Map<String, Object>> answers(JsonNode paper, String forQ1, String forQ2) {
        List<Map<String, Object>> list = new ArrayList<>();
        JsonNode qs = paper.path("questions");
        list.add(Map.of("questionId", qs.get(0).path("id").asLong(), "selectedOption", forQ1));
        list.add(Map.of("questionId", qs.get(1).path("id").asLong(), "selectedOption", forQ2));
        return list;
    }

    // ---- tests -----------------------------------------------------------------------------

    @Test
    void applyIsRefusedUntilEveryLessonIsCompleted() throws Exception {
        api.get(student, "/api/student/exams").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseCompleted").value(false))
                .andExpect(jsonPath("$.data[0].canApply").value(false))
                .andExpect(jsonPath("$.data[0].eligibilityMessage").value("Complete the course before applying for the exam."));
        api.post(student, "/api/student/exams/apply", Map.of("courseId", courseId)).andExpect(status().isConflict());

        // Completing only one lesson is not enough.
        api.post(student, "/api/student/lessons/" + lessonIds.get(0) + "/progress", Map.of("completed", true)).andExpect(status().isOk());
        api.post(student, "/api/student/exams/apply", Map.of("courseId", courseId)).andExpect(status().isConflict());

        completeCourse(student);
        api.get(student, "/api/student/exams").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseCompleted").value(true))
                .andExpect(jsonPath("$.data[0].canApply").value(true));
        api.post(student, "/api/student/exams/apply", Map.of("courseId", courseId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Exam application submitted successfully."))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.studentId").value("LSI-TEST-" + studentUser.getId()));
        // CASE 2: no duplicate active application.
        api.post(student, "/api/student/exams/apply", Map.of("courseId", courseId)).andExpect(status().isConflict());

        // It shows up in Main Admin.
        api.get(admin, "/api/admin/exam-applications/counters").andExpect(jsonPath("$.data.pending").value(1));
        api.get(admin, "/api/admin/exam-applications?status=PENDING").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].studentEmail").value("learner@test.local"))
                .andExpect(jsonPath("$.data.content[0].courseId").value((int) courseId));
    }

    @Test
    void examConfigurationIsValidatedOnTheBackend() throws Exception {
        // Passing marks above total.
        api.post(admin, "/api/admin/exams", Map.of("courseId", courseId, "title", "Bad", "totalMarks", 10, "passingMarks", 11, "maxAttempts", 1))
                .andExpect(status().isBadRequest());
        long examId = api.data(api.post(admin, "/api/admin/exams", Map.of("courseId", courseId, "title", "Final", "totalMarks", 10,
                "passingMarks", 6, "maxAttempts", 2))).path("id").asLong();

        // Question validation: missing option, duplicate options, missing correct answer, bad marks.
        Map<String, Object> q = question("Q", "A", 5);
        q.put("optionD", "");
        api.post(admin, "/api/admin/exams/" + examId + "/questions", q).andExpect(status().isBadRequest());
        q = question("Q", "A", 5);
        q.put("optionD", "Option A text");
        api.post(admin, "/api/admin/exams/" + examId + "/questions", q).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("duplicates")));
        q = question("Q", "A", 5);
        q.remove("correctOption");
        api.post(admin, "/api/admin/exams/" + examId + "/questions", q).andExpect(status().isBadRequest());
        q = question("Q", "A", 0);
        api.post(admin, "/api/admin/exams/" + examId + "/questions", q).andExpect(status().isBadRequest());

        // Cannot activate without questions; cannot activate when question marks != total marks.
        api.patch(admin, "/api/admin/exams/" + examId + "/status", Map.of("status", "ACTIVE")).andExpect(status().isConflict());
        api.post(admin, "/api/admin/exams/" + examId + "/questions", question("Q1", "A", 4)).andExpect(status().isOk());
        api.patch(admin, "/api/admin/exams/" + examId + "/status", Map.of("status", "ACTIVE")).andExpect(status().isConflict());
        long q2 = api.data(api.post(admin, "/api/admin/exams/" + examId + "/questions", question("Q2", "B", 6))).path("id").asLong();
        api.get(admin, "/api/admin/exams/" + examId).andExpect(jsonPath("$.data.marksConsistent").value(true))
                .andExpect(jsonPath("$.data.questionCount").value(2));
        api.patch(admin, "/api/admin/exams/" + examId + "/status", Map.of("status", "ACTIVE")).andExpect(status().isOk());

        // Edit + delete a question; the admin view carries the correct option, nothing else does.
        api.put(admin, "/api/admin/exams/" + examId + "/questions/" + q2, question("Q2 edited", "D", 6)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correctOption").value("D"));
        api.delete(admin, "/api/admin/exams/" + examId + "/questions/" + q2).andExpect(status().isOk());
        api.get(admin, "/api/admin/exams/" + examId).andExpect(jsonPath("$.data.questionCount").value(1));

        // Students have no admin surface.
        api.get(student, "/api/admin/exams").andExpect(status().isForbidden());
        api.post(student, "/api/admin/exams/" + examId + "/questions", question("Hack", "A", 4)).andExpect(status().isForbidden());
    }

    @Test
    void fullFlowFailThenPassIssuesCertificateOnlyForThePass() throws Exception {
        completeCourse(student);
        long appId = api.data(api.post(student, "/api/student/exams/apply", Map.of("courseId", courseId))).path("id").asLong();
        long examId = createReadyExam(10, 6, 3);

        // CASE 3: not scheduled -> cannot start.
        api.post(student, "/api/student/exams/applications/" + appId + "/start").andExpect(status().isConflict());

        // Approve, then schedule in the future -> student sees the schedule but cannot start (CASE 4).
        api.post(admin, "/api/admin/exam-applications/" + appId + "/approve", Map.of("remarks", "ok")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        api.post(admin, "/api/admin/exam-applications/" + appId + "/schedule", futureWindow(examId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.schedule.window").value("UPCOMING"));
        api.get(student, "/api/student/exams").andExpect(jsonPath("$.data[0].application.action").value("WAIT"))
                .andExpect(jsonPath("$.data[0].application.examTitle").value("Final Course Exam"))
                .andExpect(jsonPath("$.data[0].application.totalMarks").value(10))
                .andExpect(jsonPath("$.data[0].application.passingMarks").value(6));
        api.post(student, "/api/student/exams/applications/" + appId + "/start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not opened yet")));

        // Reschedule to a window that is open now.
        api.post(admin, "/api/admin/exam-applications/" + appId + "/schedule", openWindow(examId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schedule.window").value("OPEN"))
                .andExpect(jsonPath("$.data.schedule.rescheduleCount").value(1));
        api.get(student, "/api/student/exams").andExpect(jsonPath("$.data[0].application.action").value("START"));

        // Start: the paper never contains the correct option.
        MvcResult started = api.post(student, "/api/student/exams/applications/" + appId + "/start").andExpect(status().isOk()).andReturn();
        String paperJson = started.getResponse().getContentAsString();
        assertThat(paperJson).doesNotContain("correctOption").doesNotContain("\"correct\"");
        JsonNode paper = api.data(api.get(student, "/api/student/exams/attempts/"
                + com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(paperJson).path("data").path("attemptId").asLong() + "/paper"));
        long attemptId = paper.path("attemptId").asLong();
        assertThat(paper.path("questions").size()).isEqualTo(2);
        assertThat(paper.path("attemptNumber").asInt()).isEqualTo(1);

        // Starting again while in progress resumes the same attempt (no new attempt consumed).
        api.post(student, "/api/student/exams/applications/" + appId + "/start").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptId").value((int) attemptId));

        // Submit with one wrong answer: 5/10 < 6 -> FAIL, 2 attempts remaining, no certificate.
        api.post(student, "/api/student/exams/attempts/" + attemptId + "/submit", Map.of("answers", answers(paper, "A", "B")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(5))
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.result").value("FAIL"))
                .andExpect(jsonPath("$.data.attemptsRemaining").value(2))
                .andExpect(jsonPath("$.data.certificateId").doesNotExist());
        // Submitted attempts are immutable.
        api.post(student, "/api/student/exams/attempts/" + attemptId + "/submit", Map.of("answers", answers(paper, "A", "C")))
                .andExpect(status().isConflict());
        api.get(student, "/api/student/certificates").andExpect(jsonPath("$.data.length()").value(0));
        api.get(student, "/api/student/exams").andExpect(jsonPath("$.data[0].application.attemptsUsed").value(1))
                .andExpect(jsonPath("$.data[0].application.message").value("Exam not cleared. You have 2 attempt(s) remaining."));

        // Second attempt, all correct: 10/10 -> PASS -> certificate issued with the default Lord Sai design.
        JsonNode paper2 = api.data(api.post(student, "/api/student/exams/applications/" + appId + "/start").andExpect(status().isOk()));
        long attempt2 = paper2.path("attemptId").asLong();
        assertThat(paper2.path("attemptNumber").asInt()).isEqualTo(2);
        JsonNode result = api.data(api.post(student, "/api/student/exams/attempts/" + attempt2 + "/submit",
                Map.of("answers", answers(paper2, "A", "C"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(10))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.result").value("PASS"))
                .andExpect(jsonPath("$.data.certificateEligible").value(true)));
        long certificateId = result.path("certificateId").asLong();
        assertThat(result.path("certificateNumber").asText()).startsWith("LSI-CERT-");

        api.get(student, "/api/student/exams").andExpect(jsonPath("$.data[0].application.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].application.result").value("PASSED"))
                .andExpect(jsonPath("$.data[0].application.action").value("PASSED"))
                .andExpect(jsonPath("$.data[0].certificate.certificateNumber").value(result.path("certificateNumber").asText()))
                .andExpect(jsonPath("$.data[0].certificate.templateName").value("Default Lord Sai Certificate"));
        api.get(student, "/api/student/certificates").andExpect(jsonPath("$.data.length()").value(1));
        MvcResult pdf = api.get(student, "/api/student/certificates/" + certificateId + "/pdf").andExpect(status().isOk()).andReturn();
        assertThat(pdf.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");

        // Passed -> no further attempts, no new application (CASE 7).
        api.post(student, "/api/student/exams/applications/" + appId + "/start").andExpect(status().isConflict());
        api.post(student, "/api/student/exams/apply", Map.of("courseId", courseId)).andExpect(status().isConflict());

        // Admin sees results and the certificate.
        api.get(admin, "/api/admin/exam-results?examId=" + examId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].passed").value(true))
                .andExpect(jsonPath("$.data.content[0].certificateNumber").value(result.path("certificateNumber").asText()));
        api.get(admin, "/api/admin/certificates").andExpect(jsonPath("$.data.content[0].studentName").value(studentUser.getFullName()));
        api.get(admin, "/api/admin/certificates/" + certificateId + "/pdf").andExpect(status().isOk());
        api.get(admin, "/api/admin/exam-applications/" + appId + "/attempts").andExpect(jsonPath("$.data.length()").value(2));

        // CASE 11: another student cannot read this student's certificate, result or application.
        users.student("intruder@test.local");
        String intruder = api.login("intruder@test.local", TestUsers.PASSWORD);
        api.get(intruder, "/api/student/certificates/" + certificateId + "/pdf").andExpect(status().isForbidden());
        api.get(intruder, "/api/student/certificates/" + certificateId).andExpect(status().isForbidden());
        api.get(intruder, "/api/student/exams/attempts/" + attempt2 + "/result").andExpect(status().isForbidden());
        api.get(intruder, "/api/student/exams/applications/" + appId).andExpect(status().isForbidden());
        api.post(intruder, "/api/student/exams/applications/" + appId + "/start").andExpect(status().isForbidden());
        api.get(null, "/api/student/certificates/" + certificateId + "/pdf").andExpect(status().isUnauthorized());
    }

    @Test
    void maximumAttemptsAreEnforcedByTheBackend() throws Exception {
        completeCourse(student);
        long appId = api.data(api.post(student, "/api/student/exams/apply", Map.of("courseId", courseId))).path("id").asLong();
        long examId = createReadyExam(10, 6, 1);
        api.post(admin, "/api/admin/exam-applications/" + appId + "/schedule", openWindow(examId)).andExpect(status().isOk());

        JsonNode paper = api.data(api.post(student, "/api/student/exams/applications/" + appId + "/start").andExpect(status().isOk()));
        // CASE 12: the request cannot carry a score or a result — unknown fields are ignored, score comes from the DB.
        Map<String, Object> body = new HashMap<>();
        body.put("answers", answers(paper, "B", "B"));
        body.put("score", 10);
        body.put("passed", true);
        api.post(student, "/api/student/exams/attempts/" + paper.path("attemptId").asLong() + "/submit", body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(0))
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.attemptsRemaining").value(0))
                .andExpect(jsonPath("$.data.message").value("Exam not cleared. Maximum exam attempts reached."));

        // CASE 5 / CASE 8: no more attempts, no certificate, application completed as FAILED.
        api.post(student, "/api/student/exams/applications/" + appId + "/start").andExpect(status().isConflict());
        api.get(student, "/api/student/exams").andExpect(jsonPath("$.data[0].application.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].application.result").value("FAILED"))
                .andExpect(jsonPath("$.data[0].application.action").value("FAILED"))
                .andExpect(jsonPath("$.data[0].certificate").doesNotExist());
        api.get(student, "/api/student/certificates").andExpect(jsonPath("$.data.length()").value(0));

        // Admin cannot schedule the same exam again without raising the attempt limit.
        api.post(admin, "/api/admin/exam-applications/" + appId + "/schedule", openWindow(examId)).andExpect(status().isConflict());
    }

    @Test
    void rejectedApplicationCannotStartAndSchedulingIsValidated() throws Exception {
        completeCourse(student);
        long appId = api.data(api.post(student, "/api/student/exams/apply", Map.of("courseId", courseId))).path("id").asLong();
        long examId = createReadyExam(10, 6, 2);

        // End before start, past window.
        api.post(admin, "/api/admin/exam-applications/" + appId + "/schedule", Map.of("examId", examId,
                "examDate", LocalDate.now(INDIA).plusDays(1).toString(), "startTime", "12:00", "endTime", "11:00")).andExpect(status().isBadRequest());
        api.post(admin, "/api/admin/exam-applications/" + appId + "/schedule", Map.of("examId", examId,
                "examDate", LocalDate.now(INDIA).minusDays(1).toString(), "startTime", "10:00", "endTime", "11:00")).andExpect(status().isBadRequest());

        api.post(admin, "/api/admin/exam-applications/" + appId + "/reject", Map.of("remarks", "Please contact the office."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
        api.post(student, "/api/student/exams/applications/" + appId + "/start").andExpect(status().isForbidden());
        api.get(student, "/api/student/exams").andExpect(jsonPath("$.data[0].application.action").value("REJECTED"))
                .andExpect(jsonPath("$.data[0].application.adminRemarks").value("Please contact the office."))
                .andExpect(jsonPath("$.data[0].canApply").value(true));
        api.post(admin, "/api/admin/exam-applications/" + appId + "/schedule", openWindow(examId)).andExpect(status().isConflict());

        // Cancel schedule flow on a fresh application.
        long app2 = api.data(api.post(student, "/api/student/exams/apply", Map.of("courseId", courseId))).path("id").asLong();
        api.post(admin, "/api/admin/exam-applications/" + app2 + "/schedule", futureWindow(examId)).andExpect(status().isOk());
        api.post(admin, "/api/admin/exam-applications/" + app2 + "/cancel-schedule").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        api.get(admin, "/api/admin/exam-schedules?status=CANCELLED").andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void certificateTemplateUploadChangesFutureCertificatesAndFallsBackToDefault() throws Exception {
        api.get(admin, "/api/admin/certificate-templates").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usingDefault").value(true))
                .andExpect(jsonPath("$.data.activeTemplateName").value("Default Lord Sai Certificate"));
        api.get(admin, "/api/admin/certificate-templates/preview").andExpect(status().isOk());

        // A non-image is refused; a real PNG is accepted and activated.
        MockMultipartFile fake = new MockMultipartFile("file", "design.png", "image/png", "not really a png".getBytes());
        mockMvc.perform(multipart("/api/admin/certificate-templates").file(fake).header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        MockMultipartFile png = new MockMultipartFile("file", "design.png", "image/png", png());
        long templateId = api.data(mockMvc.perform(multipart("/api/admin/certificate-templates").file(png).param("name", "Gold design")
                        .header("Authorization", "Bearer " + admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))).path("id").asLong();
        api.get(admin, "/api/admin/certificate-templates").andExpect(jsonPath("$.data.usingDefault").value(false))
                .andExpect(jsonPath("$.data.activeTemplateName").value("Gold design"));
        api.get(admin, "/api/admin/certificate-templates/" + templateId + "/image").andExpect(status().isOk());
        api.get(admin, "/api/admin/certificate-templates/preview").andExpect(status().isOk());

        // A certificate issued now records the custom design (CASE 9).
        completeCourse(student);
        long appId = api.data(api.post(student, "/api/student/exams/apply", Map.of("courseId", courseId))).path("id").asLong();
        long examId = createReadyExam(10, 6, 1);
        api.post(admin, "/api/admin/exam-applications/" + appId + "/schedule", openWindow(examId)).andExpect(status().isOk());
        JsonNode paper = api.data(api.post(student, "/api/student/exams/applications/" + appId + "/start"));
        api.post(student, "/api/student/exams/attempts/" + paper.path("attemptId").asLong() + "/submit",
                Map.of("answers", answers(paper, "A", "C"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.passed").value(true));
        api.get(student, "/api/student/certificates").andExpect(jsonPath("$.data[0].templateName").value("Gold design"));

        // Back to the default (CASE 10); the template that issued a certificate is retired, not deleted.
        api.post(admin, "/api/admin/certificate-templates/use-default", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usingDefault").value(true));
        api.delete(admin, "/api/admin/certificate-templates/" + templateId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templates.length()").value(1));
        api.get(student, "/api/student/certificates").andExpect(jsonPath("$.data[0].templateName").value("Gold design"));
        api.get(student, "/api/student/certificates/" + api.data(api.get(student, "/api/student/certificates")).get(0).path("id").asLong() + "/pdf")
                .andExpect(status().isOk());

        // Students cannot manage templates.
        api.get(student, "/api/admin/certificate-templates").andExpect(status().isForbidden());
    }

    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(40, 28, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
