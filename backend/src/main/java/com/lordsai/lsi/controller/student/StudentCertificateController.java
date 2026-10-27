package com.lordsai.lsi.controller.student;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.certificate.CertificateDtos.ApplyRequest;
import com.lordsai.lsi.dto.certificate.CertificateDtos.MyCertificate;
import com.lordsai.lsi.dto.certificate.CertificateDtos.MyCourseCertificate;
import com.lordsai.lsi.dto.certificate.CertificateDtos.MyRequest;
import com.lordsai.lsi.entity.Certificate;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.CertificateRequestService;
import com.lordsai.lsi.service.CertificateService;
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
 * Student Admin -> Certificate. Every endpoint is for the logged-in student only (ROLE_STUDENT via
 * SecurityConfig); the student is always taken from the token, and a certificate lookup is scoped
 * to its owner, so changing an id in the URL can never reach another student's certificate.
 */
@RestController
@RequestMapping("/api/student")
public class StudentCertificateController {

    private final CertificateRequestService requestService;
    private final CertificateService certificateService;

    public StudentCertificateController(CertificateRequestService requestService, CertificateService certificateService) {
        this.requestService = requestService;
        this.certificateService = certificateService;
    }

    /** Each enrolled course with its certificate state (not eligible / apply / pending / rejected / available). */
    @GetMapping("/certificates/courses")
    public ApiResponse<List<MyCourseCertificate>> courses() {
        return ApiResponse.ok(requestService.myCourses(me()));
    }

    /** Apply for a completed course's certificate: the course, and the name the student confirmed for the certificate. */
    @PostMapping("/certificate-requests")
    public ApiResponse<MyRequest> apply(@Valid @RequestBody ApplyRequest body) {
        return ApiResponse.ok("Certificate application submitted. The academy will review it shortly.",
                requestService.apply(me(), body.courseId(), body.certificateName()));
    }

    @GetMapping("/certificates")
    public ApiResponse<List<MyCertificate>> certificates() {
        return ApiResponse.ok(certificateService.listForStudent(me()));
    }

    @GetMapping("/certificates/{id}")
    public ApiResponse<MyCertificate> certificate(@PathVariable Long id) {
        return ApiResponse.ok(certificateService.toMine(certificateService.requireOwned(id, me())));
    }

    /** The stored certificate PDF — only the owner's; inline to view, attachment to download. */
    @GetMapping("/certificates/{id}/pdf")
    public ResponseEntity<byte[]> certificatePdf(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "false") boolean download) {
        Certificate certificate = certificateService.requireOwned(id, me());
        byte[] pdf = certificateService.pdfBytes(certificate);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, (download ? "attachment" : "inline")
                        + "; filename=\"" + CertificateService.downloadName(certificate) + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }

    private Long me() {
        return CurrentUser.require().id();
    }
}
