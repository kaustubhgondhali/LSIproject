package com.lordsai.lsi.controller;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.certificate.CertificateDtos.Verification;
import com.lordsai.lsi.service.CertificateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Target of the QR code on every certificate (verify-certificate.html?c=CODE). The code is random,
 * not the certificate id, and the answer holds only what is printed on the certificate itself.
 */
@RestController
@RequestMapping("/api/public/certificates")
public class PublicCertificateController {

    private final CertificateService certificateService;

    public PublicCertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @GetMapping("/verify/{code}")
    public ApiResponse<Verification> verify(@PathVariable String code) {
        return ApiResponse.ok(certificateService.verify(code));
    }
}
