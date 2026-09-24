package com.lordsai.lsi.controller.student;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.exam.CertificateDtos.MyCertificate;
import com.lordsai.lsi.dto.exam.ExamDtos.ApplicationResponse;
import com.lordsai.lsi.dto.exam.ExamDtos.ApplyRequest;
import com.lordsai.lsi.dto.exam.ExamDtos.AttemptResult;
import com.lordsai.lsi.dto.exam.ExamDtos.ExamPaper;
import com.lordsai.lsi.dto.exam.ExamDtos.MyExamCourse;
import com.lordsai.lsi.dto.exam.ExamDtos.SubmitRequest;
import com.lordsai.lsi.entity.Certificate;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.CertificateService;
import com.lordsai.lsi.service.ExamWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Student side of the exam workflow (ROLE_STUDENT, enforced by SecurityConfig). Every lookup is
 * scoped to the caller's own applications / attempts / certificates, so changing an id in the URL
 * can never reach another student's data. Nothing here accepts marks, results or attempt counts.
 */
@RestController
@RequestMapping("/api/student")
public class StudentExamController {

    private final ExamWorkflowService workflow;
    private final CertificateService certificateService;

    public StudentExamController(ExamWorkflowService workflow, CertificateService certificateService) {
        this.workflow = workflow;
        this.certificateService = certificateService;
    }

    /** "My Exams": one card per accessible course with completion, eligibility, application state and certificate. */
    @GetMapping("/exams")
    public ApiResponse<List<MyExamCourse>> myExams() {
        return ApiResponse.ok(workflow.myExams(me()));
    }

    @PostMapping("/exams/apply")
    public ApiResponse<ApplicationResponse> apply(@Valid @RequestBody ApplyRequest body) {
        return ApiResponse.ok("Exam application submitted successfully.", workflow.apply(me(), body.courseId()));
    }

    @GetMapping("/exams/applications")
    public ApiResponse<List<ApplicationResponse>> applications() {
        return ApiResponse.ok(workflow.myApplications(me()));
    }

    @GetMapping("/exams/applications/{id}")
    public ApiResponse<ApplicationResponse> application(@PathVariable Long id) {
        return ApiResponse.ok(workflow.myApplication(me(), id));
    }

    /** Starts (or resumes) an attempt; the window, exam status and attempt limit are checked server-side. */
    @PostMapping("/exams/applications/{id}/start")
    public ApiResponse<ExamPaper> start(@PathVariable Long id) {
        return ApiResponse.ok(workflow.start(me(), id));
    }

    @GetMapping("/exams/attempts/{id}/paper")
    public ApiResponse<ExamPaper> paper(@PathVariable Long id) {
        return ApiResponse.ok(workflow.paper(me(), id));
    }

    @PostMapping("/exams/attempts/{id}/submit")
    public ApiResponse<AttemptResult> submit(@PathVariable Long id, @Valid @RequestBody SubmitRequest body) {
        AttemptResult result = workflow.submit(me(), id, body.answers());
        return ApiResponse.ok(result.message(), result);
    }

    @GetMapping("/exams/attempts/{id}/result")
    public ApiResponse<AttemptResult> result(@PathVariable Long id) {
        return ApiResponse.ok(workflow.result(me(), id));
    }

    // ---- certificates ----------------------------------------------------------------------

    @GetMapping("/certificates")
    public ApiResponse<List<MyCertificate>> certificates() {
        return ApiResponse.ok(certificateService.listForStudent(me()));
    }

    @GetMapping("/certificates/{id}")
    public ApiResponse<MyCertificate> certificate(@PathVariable Long id) {
        return ApiResponse.ok(certificateService.toMine(certificateService.requireOwned(id, me())));
    }

    /** The certificate PDF — only ever the caller's own (owner-scoped lookup, bearer header required). */
    @GetMapping("/certificates/{id}/pdf")
    public ResponseEntity<byte[]> certificatePdf(@PathVariable Long id,
                                                 @RequestParam(value = "download", required = false) Boolean download) {
        Certificate certificate = certificateService.requireOwned(id, me());
        byte[] pdf = certificateService.pdfBytes(certificate);
        String disposition = Boolean.TRUE.equals(download) ? "attachment" : "inline";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + certificate.fileName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }

    private Long me() {
        return CurrentUser.require().id();
    }
}
