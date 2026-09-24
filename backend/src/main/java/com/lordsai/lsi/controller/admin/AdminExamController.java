package com.lordsai.lsi.controller.admin;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.exam.CertificateDtos.AdminCertificate;
import com.lordsai.lsi.dto.exam.CertificateDtos.TemplateResponse;
import com.lordsai.lsi.dto.exam.CertificateDtos.TemplateStatus;
import com.lordsai.lsi.dto.exam.ExamDtos.AdminExam;
import com.lordsai.lsi.dto.exam.ExamDtos.AdminQuestion;
import com.lordsai.lsi.dto.exam.ExamDtos.ApplicationCounters;
import com.lordsai.lsi.dto.exam.ExamDtos.ApplicationResponse;
import com.lordsai.lsi.dto.exam.ExamDtos.AttemptSummary;
import com.lordsai.lsi.dto.exam.ExamDtos.ExamRequest;
import com.lordsai.lsi.dto.exam.ExamDtos.ExamStatusRequest;
import com.lordsai.lsi.dto.exam.ExamDtos.QuestionRequest;
import com.lordsai.lsi.dto.exam.ExamDtos.ResultRow;
import com.lordsai.lsi.dto.exam.ExamDtos.ReviewRequest;
import com.lordsai.lsi.dto.exam.ExamDtos.ScheduleRequest;
import com.lordsai.lsi.dto.exam.ExamDtos.ScheduleResponse;
import com.lordsai.lsi.entity.Certificate;
import com.lordsai.lsi.entity.CertificateTemplate;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.ExamApplicationStatus;
import com.lordsai.lsi.entity.enums.ExamScheduleStatus;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.CertificateService;
import com.lordsai.lsi.service.CertificateTemplateService;
import com.lordsai.lsi.service.ExamService;
import com.lordsai.lsi.service.ExamWorkflowService;
import com.lordsai.lsi.service.FileStorageService;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Main Admin: exam applications, exams and MCQ questions, scheduling, results and certificates
 * (ROLE_ADMIN, enforced by SecurityConfig for everything under /api/admin).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminExamController {

    private final ExamService examService;
    private final ExamWorkflowService workflow;
    private final CertificateService certificateService;
    private final CertificateTemplateService templateService;
    private final FileStorageService storage;
    private final UserService userService;

    public AdminExamController(ExamService examService,
                               ExamWorkflowService workflow,
                               CertificateService certificateService,
                               CertificateTemplateService templateService,
                               FileStorageService storage,
                               UserService userService) {
        this.examService = examService;
        this.workflow = workflow;
        this.certificateService = certificateService;
        this.templateService = templateService;
        this.storage = storage;
        this.userService = userService;
    }

    // ---- exams -----------------------------------------------------------------------------

    @GetMapping("/exams")
    public ApiResponse<List<AdminExam>> exams(@RequestParam(required = false) Long courseId) {
        return ApiResponse.ok(examService.list(courseId));
    }

    @GetMapping("/exams/{id}")
    public ApiResponse<AdminExam> exam(@PathVariable Long id) {
        return ApiResponse.ok(examService.get(id));
    }

    @PostMapping("/exams")
    public ApiResponse<AdminExam> createExam(@Valid @RequestBody ExamRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Exam created. Add its questions, then activate it.", examService.create(body, actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/exams/{id}")
    public ApiResponse<AdminExam> updateExam(@PathVariable Long id, @Valid @RequestBody ExamRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Exam updated.", examService.update(id, body, actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/exams/{id}/status")
    public ApiResponse<AdminExam> examStatus(@PathVariable Long id, @Valid @RequestBody ExamStatusRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Exam status updated.", examService.setStatus(id, body.status(), actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping("/exams/{id}")
    public ApiResponse<Void> deleteExam(@PathVariable Long id, HttpServletRequest req) {
        examService.delete(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Exam deleted.");
    }

    // ---- questions -------------------------------------------------------------------------

    @GetMapping("/exams/{examId}/questions")
    public ApiResponse<List<AdminQuestion>> questions(@PathVariable Long examId) {
        return ApiResponse.ok(examService.questions(examId));
    }

    @PostMapping("/exams/{examId}/questions")
    public ApiResponse<AdminQuestion> addQuestion(@PathVariable Long examId, @Valid @RequestBody QuestionRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Question added.", examService.addQuestion(examId, body, actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/exams/{examId}/questions/{questionId}")
    public ApiResponse<AdminQuestion> updateQuestion(@PathVariable Long examId, @PathVariable Long questionId,
                                                     @Valid @RequestBody QuestionRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Question updated.", examService.updateQuestion(examId, questionId, body, actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping("/exams/{examId}/questions/{questionId}")
    public ApiResponse<Void> deleteQuestion(@PathVariable Long examId, @PathVariable Long questionId, HttpServletRequest req) {
        boolean deleted = examService.deleteQuestion(examId, questionId, actor(), RequestUtil.clientIp(req));
        return ApiResponse.message(deleted ? "Question deleted."
                : "This question has already been answered in submitted attempts, so it was deactivated instead of deleted.");
    }

    // ---- applications ----------------------------------------------------------------------

    @GetMapping("/exam-applications")
    public ApiResponse<Page<ApplicationResponse>> applications(@RequestParam(required = false) ExamApplicationStatus status,
                                                               @RequestParam(required = false) Long courseId,
                                                               @RequestParam(required = false) String q,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(workflow.applications(status, courseId, q, PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/exam-applications/counters")
    public ApiResponse<ApplicationCounters> counters() {
        return ApiResponse.ok(workflow.counters());
    }

    @GetMapping("/exam-applications/{id}")
    public ApiResponse<ApplicationResponse> application(@PathVariable Long id) {
        return ApiResponse.ok(workflow.application(id));
    }

    @GetMapping("/exam-applications/{id}/attempts")
    public ApiResponse<List<AttemptSummary>> attempts(@PathVariable Long id) {
        return ApiResponse.ok(workflow.attemptsForApplication(id));
    }

    @GetMapping("/students/{id}/exam-applications")
    public ApiResponse<List<ApplicationResponse>> studentApplications(@PathVariable Long id) {
        return ApiResponse.ok(workflow.applicationsForStudent(id));
    }

    @PostMapping("/exam-applications/{id}/approve")
    public ApiResponse<ApplicationResponse> approve(@PathVariable Long id, @RequestBody(required = false) ReviewRequest body,
                                                    HttpServletRequest req) {
        return ApiResponse.ok("Application approved.", workflow.approve(id, body == null ? null : body.remarks(), actor(), RequestUtil.clientIp(req)));
    }

    @PostMapping("/exam-applications/{id}/reject")
    public ApiResponse<ApplicationResponse> reject(@PathVariable Long id, @RequestBody(required = false) ReviewRequest body,
                                                   HttpServletRequest req) {
        return ApiResponse.ok("Application rejected.", workflow.reject(id, body == null ? null : body.remarks(), actor(), RequestUtil.clientIp(req)));
    }

    /** Creates or edits the exam window (approves a pending application implicitly). */
    @PostMapping("/exam-applications/{id}/schedule")
    public ApiResponse<ApplicationResponse> schedule(@PathVariable Long id, @Valid @RequestBody ScheduleRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Exam scheduled. The student has been notified.", workflow.schedule(id, body, actor(), RequestUtil.clientIp(req)));
    }

    @PostMapping("/exam-applications/{id}/cancel-schedule")
    public ApiResponse<ApplicationResponse> cancelSchedule(@PathVariable Long id, HttpServletRequest req) {
        return ApiResponse.ok("Schedule cancelled. The application is back to Approved and can be rescheduled.",
                workflow.cancelSchedule(id, actor(), RequestUtil.clientIp(req)));
    }

    // ---- schedules & results ---------------------------------------------------------------

    @GetMapping("/exam-schedules")
    public ApiResponse<Page<ScheduleResponse>> schedules(@RequestParam(required = false) ExamScheduleStatus status,
                                                         @RequestParam(required = false) Long examId,
                                                         @RequestParam(required = false) Long courseId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(workflow.schedules(status, examId, courseId, PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/exam-results")
    public ApiResponse<Page<ResultRow>> results(@RequestParam(required = false) Long examId,
                                                @RequestParam(required = false) Long courseId,
                                                @RequestParam(required = false) Boolean passed,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(workflow.results(examId, courseId, passed, q, PageRequest.of(page, Math.min(size, 100))));
    }

    // ---- certificates ----------------------------------------------------------------------

    @GetMapping("/certificates")
    public ApiResponse<Page<AdminCertificate>> certificates(@RequestParam(required = false) Long courseId,
                                                            @RequestParam(required = false) String q,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(certificateService.search(courseId, q, PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/certificates/{id}/pdf")
    public ResponseEntity<byte[]> certificatePdf(@PathVariable Long id) {
        Certificate certificate = certificateService.require(id);
        return pdf(certificateService.pdfBytes(certificate), certificate.fileName());
    }

    // ---- certificate templates -------------------------------------------------------------

    @GetMapping("/certificate-templates")
    public ApiResponse<TemplateStatus> templates() {
        return ApiResponse.ok(templateService.status());
    }

    /** Upload a new design (JPG / PNG, A4 landscape recommended). It becomes active unless activate=false. */
    @PostMapping(value = "/certificate-templates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TemplateResponse> uploadTemplate(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(value = "name", required = false) String name,
                                                        @RequestParam(value = "activate", required = false, defaultValue = "true") boolean activate,
                                                        HttpServletRequest req) {
        return ApiResponse.ok(activate ? "Certificate template uploaded and activated. New certificates will use it."
                        : "Certificate template uploaded.",
                templateService.upload(name, file, activate, actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/certificate-templates/{id}/activate")
    public ApiResponse<TemplateResponse> activateTemplate(@PathVariable Long id, HttpServletRequest req) {
        return ApiResponse.ok("Template activated. New certificates will use this design.",
                templateService.activate(id, actor(), RequestUtil.clientIp(req)));
    }

    /** Switch back to the built-in default Lord Sai certificate. */
    @PostMapping("/certificate-templates/use-default")
    public ApiResponse<TemplateStatus> useDefault(HttpServletRequest req) {
        return ApiResponse.ok("The default Lord Sai certificate is now active.", templateService.useDefault(actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping("/certificate-templates/{id}")
    public ApiResponse<TemplateStatus> deleteTemplate(@PathVariable Long id, HttpServletRequest req) {
        CertificateTemplateService.DeleteOutcome outcome = templateService.delete(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.ok(outcome.message(), outcome.status());
    }

    /** Admin preview of an uploaded design image (bearer-authenticated, never a public URL). */
    @GetMapping("/certificate-templates/{id}/image")
    public ResponseEntity<Resource> templateImage(@PathVariable Long id) {
        CertificateTemplate t = templateService.require(id);
        MediaType type = t.getContentType() != null && t.getContentType().contains("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(storage.load(t.getImagePath()));
    }

    /** Sample certificate rendered with whatever design is active right now (default or uploaded). */
    @GetMapping("/certificate-templates/preview")
    public ResponseEntity<byte[]> previewTemplate() {
        return pdf(certificateService.previewPdf(), "certificate-preview.pdf");
    }

    private ResponseEntity<byte[]> pdf(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
