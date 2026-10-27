package com.lordsai.lsi.controller.admin;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.certificate.CertificateDtos.AdminCertificate;
import com.lordsai.lsi.dto.certificate.CertificateDtos.AdminRequest;
import com.lordsai.lsi.dto.certificate.CertificateDtos.RejectRequest;
import com.lordsai.lsi.dto.certificate.CertificateDtos.RequestCounters;
import com.lordsai.lsi.entity.Certificate;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.CertificateRequestStatus;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.CertificateRequestService;
import com.lordsai.lsi.service.CertificateService;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

/**
 * Main Admin -> Certificate Requests and issued Certificates. /api/admin/** is ADMIN-only in
 * SecurityConfig, so approving / rejecting is enforced on the server, never just hidden buttons.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminCertificateController {

    private final CertificateRequestService requestService;
    private final CertificateService certificateService;
    private final UserService userService;

    public AdminCertificateController(CertificateRequestService requestService, CertificateService certificateService,
                                      UserService userService) {
        this.requestService = requestService;
        this.certificateService = certificateService;
        this.userService = userService;
    }

    // ---- certificate requests ----------------------------------------------------------------

    @GetMapping("/certificate-requests")
    public ApiResponse<Page<AdminRequest>> requests(@RequestParam(required = false) CertificateRequestStatus status,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(requestService.search(status, q, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100))));
    }

    @GetMapping("/certificate-requests/counters")
    public ApiResponse<RequestCounters> counters() {
        return ApiResponse.ok(requestService.counters());
    }

    @GetMapping("/certificate-requests/{id}")
    public ApiResponse<AdminRequest> request(@PathVariable Long id) {
        return ApiResponse.ok(requestService.get(id));
    }

    /** Approve = generate the certificate now (from the student's registered details). */
    @PostMapping("/certificate-requests/{id}/approve")
    public ApiResponse<AdminRequest> approve(@PathVariable Long id, HttpServletRequest req) {
        return ApiResponse.ok("Certificate approved and generated.", requestService.approve(id, actor(), RequestUtil.clientIp(req)));
    }

    @PostMapping("/certificate-requests/{id}/reject")
    public ApiResponse<AdminRequest> reject(@PathVariable Long id, @Valid @RequestBody(required = false) RejectRequest body,
                                            HttpServletRequest req) {
        return ApiResponse.ok("Certificate request rejected.",
                requestService.reject(id, body == null ? null : body.reason(), actor(), RequestUtil.clientIp(req)));
    }

    // ---- issued certificates -----------------------------------------------------------------

    @GetMapping("/certificates")
    public ApiResponse<Page<AdminCertificate>> certificates(@RequestParam(required = false) Long courseId,
                                                            @RequestParam(required = false) String q,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(certificateService.search(courseId, q, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100))));
    }

    @GetMapping("/certificates/{id}/pdf")
    public ResponseEntity<byte[]> certificatePdf(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean download) {
        Certificate certificate = certificateService.require(id);
        byte[] pdf = certificateService.pdfBytes(certificate);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, (download ? "attachment" : "inline")
                        + "; filename=\"" + CertificateService.downloadName(certificate) + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
