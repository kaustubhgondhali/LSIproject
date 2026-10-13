package com.lordsai.lsi.controller;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.service.SharedDocumentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The private receipt / invoice links sent in WhatsApp messages (public page: receipt.html).
 * No login: the signed, expiring link itself is the permission.
 */
@RestController
@RequestMapping("/api/public/documents")
public class PublicDocumentController {

    private final SharedDocumentService documents;

    public PublicDocumentController(SharedDocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/{token}/info")
    public ApiResponse<SharedDocumentService.Info> info(@PathVariable String token) {
        return ApiResponse.ok(documents.describe(token));
    }

    @GetMapping("/{token}")
    public ResponseEntity<byte[]> file(@PathVariable String token, @RequestParam(defaultValue = "false") boolean download) {
        SharedDocumentService.SharedFile f = documents.open(token);
        String name = f.fileName().replaceAll("[^A-Za-z0-9._-]", "-");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, (download ? "attachment" : "inline") + "; filename=\"" + name + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(f.content());
    }
}
