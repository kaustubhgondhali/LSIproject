package com.lordsai.lsi.certificate;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.lordsai.lsi.entity.Certificate;
import com.lordsai.lsi.entity.Course;
import com.lordsai.lsi.entity.Enrollment;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.EnrollmentSource;
import com.lordsai.lsi.entity.enums.EnrollmentStatus;
import com.lordsai.lsi.repository.CertificateRepository;
import com.lordsai.lsi.repository.CourseRepository;
import com.lordsai.lsi.repository.EnrollmentRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.repository.UserRepository;
import com.lordsai.lsi.service.CertificateRequestService;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Course completion certificates without examinations: completed course -> student applies ->
 * admin approves (certificate generated from the master design) or rejects.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CertificateWorkflowTest {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired CourseRepository courseRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired CertificateRepository certificateRepository;
    @Autowired StudentProfileRepository studentProfileRepository;
    @Autowired UserRepository userRepository;

    private Course course;
    private User jostna;
    private String student, other, admin;

    @BeforeEach
    void setUp() throws Exception {
        course = courseRepository.findByCourseCodeIgnoreCase("SMET-MASTER").orElseThrow();
        jostna = users.student("jostna@test.local");
        jostna.setFullName("Jostna Mhatre");
        userRepository.saveAndFlush(jostna);
        users.student("other@test.local");
        users.admin("cert-admin@test.local");
        student = api.login("jostna@test.local", TestUsers.PASSWORD);
        other = api.login("other@test.local", TestUsers.PASSWORD);
        admin = api.login("cert-admin@test.local", TestUsers.PASSWORD);
    }

    private Enrollment enroll(User u, EnrollmentStatus status) {
        Enrollment e = new Enrollment();
        e.setStudent(u);
        e.setCourse(course);
        e.setSource(EnrollmentSource.ADMIN_MANUAL);
        e.setStatus(status);
        e.setEnrolledAt(LocalDate.of(2026, 1, 12).atStartOfDay(INDIA).toInstant());
        if (status == EnrollmentStatus.COMPLETED) {
            e.setCompletedAt(LocalDate.of(2026, 4, 20).atStartOfDay(INDIA).toInstant());
        }
        return enrollmentRepository.saveAndFlush(e);
    }

    private Map<String, Object> apply() {
        Map<String, Object> body = new HashMap<>();
        body.put("courseId", course.getId());
        return body;
    }

    private Map<String, Object> applyAs(String certificateName) {
        Map<String, Object> body = apply();
        body.put("certificateName", certificateName);
        return body;
    }

    /** Apply form: the student checks the name for the certificate and corrects it if it is misspelled. */
    @Test
    void studentCorrectsTheNameBeforeApplyingAndThatNameIsPrinted() throws Exception {
        enroll(jostna, EnrollmentStatus.COMPLETED);

        // Names the certificate cannot print are refused, and nothing is created.
        api.post(student, "/api/student/certificate-requests", applyAs("J")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(CertificateRequestService.NAME_LENGTH));
        api.post(student, "/api/student/certificate-requests", applyAs("जोस्ना म्हात्रे")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(CertificateRequestService.NAME_CHARACTERS));
        api.post(student, "/api/student/certificate-requests", applyAs("Jostna 2026")).andExpect(status().isBadRequest());
        api.get(student, "/api/student/certificates/courses").andExpect(jsonPath("$.data[0].state").value("ELIGIBLE"));

        // The corrected spelling is kept (extra spaces tidied); the Student ID still comes from the account.
        JsonNode req = api.data(api.post(student, "/api/student/certificate-requests", applyAs("  Jyotsna   Mhatre "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentName").value("Jyotsna Mhatre"))
                .andExpect(jsonPath("$.data.studentId").value("LSI-TEST-" + jostna.getId())));
        long requestId = req.path("id").asLong();

        // The admin sees the name to be printed and the account name it was corrected from.
        api.get(admin, "/api/admin/certificate-requests/" + requestId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentName").value("Jyotsna Mhatre"))
                .andExpect(jsonPath("$.data.accountName").value("Jostna Mhatre"));

        JsonNode approved = api.data(api.post(admin, "/api/admin/certificate-requests/" + requestId + "/approve").andExpect(status().isOk()));
        Certificate c = certificateRepository.findById(approved.path("certificateId").asLong()).orElseThrow();
        assertThat(c.getStudentName()).isEqualTo("Jyotsna Mhatre");
        byte[] pdf = api.get(student, "/api/student/certificates/" + c.getId() + "/pdf").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(doc)).contains("JYOTSNA MHATRE");   // the design prints names in capitals
        }
        // Only the certificate uses the corrected name; the student's account is not changed.
        assertThat(userRepository.findById(jostna.getId()).orElseThrow().getFullName()).isEqualTo("Jostna Mhatre");
    }

    @Test
    void notCompletedCourseCannotApply() throws Exception {
        enroll(jostna, EnrollmentStatus.ACTIVE);
        api.get(student, "/api/student/certificates/courses").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].state").value("NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.data[0].canApply").value(false));
        api.post(student, "/api/student/certificate-requests", apply()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Complete your course before applying for a certificate."));
        // Not enrolled at all
        api.post(other, "/api/student/certificate-requests", apply()).andExpect(status().isForbidden());
    }

    @Test
    void completedCourseApplyApproveViewDownloadAndVerify() throws Exception {
        enroll(jostna, EnrollmentStatus.COMPLETED);
        api.get(student, "/api/student/certificates/courses")
                .andExpect(jsonPath("$.data[0].state").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data[0].canApply").value(true));

        // Unknown fields are ignored: without a confirmed "certificateName" the account name is used, and
        // the Student ID always comes from the account.
        Map<String, Object> body = apply();
        body.put("studentName", "Someone Else");
        body.put("studentId", "FAKE-001");
        JsonNode req = api.data(api.post(student, "/api/student/certificate-requests", body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.studentName").value("Jostna Mhatre"))
                .andExpect(jsonPath("$.data.studentId").value("LSI-TEST-" + jostna.getId())));
        long requestId = req.path("id").asLong();

        // Duplicate protection
        api.post(student, "/api/student/certificate-requests", apply()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Certificate application already submitted."));
        api.get(student, "/api/student/certificates/courses").andExpect(jsonPath("$.data[0].state").value("PENDING"));

        // Students can never approve; the admin sees the request
        api.post(student, "/api/admin/certificate-requests/" + requestId + "/approve").andExpect(status().isForbidden());
        api.get(admin, "/api/admin/certificate-requests?status=PENDING").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(requestId))
                .andExpect(jsonPath("$.data.content[0].studentName").value("Jostna Mhatre"))
                .andExpect(jsonPath("$.data.content[0].courseName").value(course.getCourseName()));
        api.get(admin, "/api/admin/certificate-requests/counters").andExpect(jsonPath("$.data.pending").value(1));
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(jostna.getId())).isEmpty(); // not before approval

        JsonNode approved = api.data(api.post(admin, "/api/admin/certificate-requests/" + requestId + "/approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GENERATED"))
                .andExpect(jsonPath("$.data.certificateNumber").value(org.hamcrest.Matchers.matchesPattern("LSI-CERT-\\d{4}-\\d{6}"))));
        api.post(admin, "/api/admin/certificate-requests/" + requestId + "/approve").andExpect(status().isConflict());
        api.post(admin, "/api/admin/certificate-requests/" + requestId + "/reject").andExpect(status().isConflict());

        Certificate c = certificateRepository.findById(approved.path("certificateId").asLong()).orElseThrow();
        assertThat(c.getStudentName()).isEqualTo("Jostna Mhatre");
        assertThat(c.getStudentCode()).isEqualTo("LSI-TEST-" + jostna.getId());
        assertThat(c.getCoursePeriod()).isEqualTo("January to April 2026");
        assertThat(c.getVerificationCode()).matches("[A-Z0-9]{12}");
        assertThat(c.getPdfPath()).isNotNull();

        // Student: certificate available, cannot apply again
        api.get(student, "/api/student/certificates/courses")
                .andExpect(jsonPath("$.data[0].state").value("GENERATED"))
                .andExpect(jsonPath("$.data[0].certificate.id").value(c.getId()))
                .andExpect(jsonPath("$.data[0].canApply").value(false));
        api.post(student, "/api/student/certificate-requests", apply()).andExpect(status().isConflict());

        // View / download: owner only
        byte[] pdf = api.get(student, "/api/student/certificates/" + c.getId() + "/pdf?download=true")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"LSA-Certificate-Jostna-Mhatre.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();
        api.get(student, "/api/student/certificates/" + c.getId() + "/pdf").andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));
        api.get(other, "/api/student/certificates/" + c.getId() + "/pdf").andExpect(status().isForbidden());
        api.get(other, "/api/student/certificates/" + c.getId()).andExpect(status().isForbidden());
        api.get(null, "/api/student/certificates/" + c.getId() + "/pdf").andExpect(status().isUnauthorized());
        api.get(admin, "/api/admin/certificates/" + c.getId() + "/pdf").andExpect(status().isOk());

        // The PDF: master page size, the student's real details, and a QR that scans to its verification link
        String issue = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH).format(c.getIssuedAt().atZone(INDIA));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            assertThat(doc.getPage(0).getMediaBox().getWidth()).isEqualTo(864f);
            assertThat(doc.getPage(0).getMediaBox().getHeight()).isEqualTo(576f);
            String text = new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
            assertThat(text).contains("JOSTNA MHATRE")
                    .contains("‘" + course.getCourseName() + " at Lord Sai Investment & Share Market Academy, Uran Navi Mumbai")
                    .contains("( January to April 2026 ) .")
                    .contains("Student ID: LSI-TEST-" + jostna.getId() + " Issue : " + issue)
                    .doesNotContain("Someone Else").doesNotContain("FAKE-001");
            BufferedImage page = new PDFRenderer(doc).renderImageWithDPI(0, 200);
            assertThat(page.getWidth()).isEqualTo(2400);
            BufferedImage qr = page.getSubimage(1650, 1110, 290, 290);
            int[] pixels = qr.getRGB(0, 0, qr.getWidth(), qr.getHeight(), null, 0, qr.getWidth());
            String decoded = new QRCodeReader().decode(
                    new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(qr.getWidth(), qr.getHeight(), pixels))),
                    Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE)).getText();
            assertThat(decoded).endsWith("/verify-certificate.html?c=" + c.getVerificationCode());
        }

        // Public verification: only what is printed on the certificate
        api.get(null, "/api/public/certificates/verify/" + c.getVerificationCode()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.studentName").value("Jostna Mhatre"))
                .andExpect(jsonPath("$.data.certificateNumber").value(c.getCertificateNumber()))
                .andExpect(jsonPath("$.data.email").doesNotExist());
        api.get(null, "/api/public/certificates/verify/NOTAREALCODE1").andExpect(jsonPath("$.data.valid").value(false));
        api.get(null, "/api/public/certificates/verify/" + c.getId()).andExpect(jsonPath("$.data.valid").value(false));
    }

    @Test
    void rejectedRequestShowsReasonAndCanBeResubmitted() throws Exception {
        enroll(jostna, EnrollmentStatus.COMPLETED);
        long first = api.data(api.post(student, "/api/student/certificate-requests", apply())).path("id").asLong();
        api.post(admin, "/api/admin/certificate-requests/" + first + "/reject", Map.of("reason", "Please complete your fee payment first."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
        api.get(student, "/api/student/certificates/courses")
                .andExpect(jsonPath("$.data[0].state").value("REJECTED"))
                .andExpect(jsonPath("$.data[0].request.rejectionReason").value("Please complete your fee payment first."))
                .andExpect(jsonPath("$.data[0].canApply").value(true));
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(jostna.getId())).isEmpty();

        long second = api.data(api.post(student, "/api/student/certificate-requests", apply())
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"))).path("id").asLong();
        assertThat(second).isNotEqualTo(first);
        api.get(student, "/api/student/certificates/courses").andExpect(jsonPath("$.data[0].state").value("PENDING"));
        api.post(admin, "/api/admin/certificate-requests/" + first + "/approve").andExpect(status().isConflict());
    }

    @Test
    void missingStudentIdStopsGenerationWithoutFakeData() throws Exception {
        enroll(jostna, EnrollmentStatus.COMPLETED);
        long id = api.data(api.post(student, "/api/student/certificate-requests", apply())).path("id").asLong();
        var profile = studentProfileRepository.findByUserId(jostna.getId()).orElseThrow();
        studentProfileRepository.delete(profile);
        studentProfileRepository.flush();
        api.post(admin, "/api/admin/certificate-requests/" + id + "/approve").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("required student/course information is missing")));
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(jostna.getId())).isEmpty();
    }

    @Test
    void examEndpointsAreGone() throws Exception {
        api.get(student, "/api/student/exams").andExpect(status().isNotFound());
        api.get(admin, "/api/admin/exams").andExpect(status().isNotFound());
        api.get(admin, "/api/admin/exam-applications").andExpect(status().isNotFound());
    }
}
