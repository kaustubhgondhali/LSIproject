package com.lordsai.lsi.controller.student;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.ebook.EbookDtos.MyEbook;
import com.lordsai.lsi.dto.invoice.InvoiceDtos.InvoiceResponse;
import com.lordsai.lsi.entity.Ebook;
import com.lordsai.lsi.entity.EbookEntitlement;
import com.lordsai.lsi.entity.Invoice;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.EbookEntitlementService;
import com.lordsai.lsi.service.FileStorageService;
import com.lordsai.lsi.service.InvoiceService;
import com.lordsai.lsi.service.ProtectionEventService;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * The student's library: purchased ebooks and invoices. Every file endpoint is bearer-
 * authenticated (ROLE_STUDENT, active account — enforced by the JWT filter and SecurityConfig)
 * AND checks entitlement / ownership here before a single byte is streamed. Files are never
 * exposed as public URLs; a request without a valid entitlement gets 403.
 */
@RestController
@RequestMapping("/api/student")
public class StudentLibraryController {

    private final EbookEntitlementService entitlementService;
    private final InvoiceService invoiceService;
    private final FileStorageService storage;
    private final ProtectionEventService protectionEvents;
    private final UserService userService;

    public StudentLibraryController(EbookEntitlementService entitlementService,
                                    InvoiceService invoiceService,
                                    FileStorageService storage,
                                    ProtectionEventService protectionEvents,
                                    UserService userService) {
        this.entitlementService = entitlementService;
        this.invoiceService = invoiceService;
        this.storage = storage;
        this.protectionEvents = protectionEvents;
        this.userService = userService;
    }

    // ---- ebooks ----------------------------------------------------------------------------

    @GetMapping("/ebooks")
    public ApiResponse<List<MyEbook>> ebooks() {
        return ApiResponse.ok(entitlementService.myEbooks(me()));
    }

    @GetMapping("/ebooks/{ebookId}")
    public ApiResponse<MyEbook> ebook(@PathVariable Long ebookId) {
        Long userId = me();
        entitlementService.requireAccess(userId, ebookId);
        return ApiResponse.ok(entitlementService.myEbooks(userId).stream()
                .filter(e -> e.ebookId().equals(ebookId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this ebook.")));
    }

    /**
     * Streams the protected ebook PDF to its owner only. Needs the bearer header, so it can never
     * be opened as a plain link; a denied attempt is written to the audit trail before the 403.
     */
    @GetMapping({"/ebooks/{ebookId}/download", "/ebooks/{ebookId}/view"})
    public ResponseEntity<Resource> download(@PathVariable Long ebookId, HttpServletRequest request) {
        Long userId = me();
        Ebook ebook;
        try {
            ebook = entitlementService.requireAccessibleEbook(userId, ebookId);
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.FORBIDDEN) {
                User user = userService.requireUser(userId);
                protectionEvents.recordUnauthorizedMedia(user, null, "Denied ebook access (ebook " + ebookId + "): " + e.getMessage(),
                        RequestUtil.clientIp(request));
            }
            throw e;
        }
        if (ebook.getPdfPath() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This ebook file is not available yet. Please contact the academy.");
        }
        String name = ebook.getPdfOriginalName() == null ? ebook.getEbookCode() + ".pdf" : ebook.getPdfOriginalName();
        Resource pdf = storage.load(ebook.getPdfPath());
        long length;
        try {
            length = pdf.contentLength();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This ebook file is not available yet. Please contact the academy.");
        }
        if (length <= 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This ebook file is not available yet. Please contact the academy.");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name.replace("\"", "") + "\"")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "SAMEORIGIN")
                .body(pdf);
    }

    // ---- invoices --------------------------------------------------------------------------

    @GetMapping("/invoices")
    public ApiResponse<List<InvoiceResponse>> invoices() {
        return ApiResponse.ok(invoiceService.listForStudent(me()));
    }

    @GetMapping("/invoices/{id}")
    public ApiResponse<InvoiceResponse> invoice(@PathVariable Long id) {
        return ApiResponse.ok(invoiceService.toResponse(invoiceService.requireOwned(id, me())));
    }

    /** The invoice PDF — only ever the caller's own (lookup is scoped to the owner, so no id-guessing works). */
    @GetMapping("/invoices/{id}/pdf")
    public ResponseEntity<byte[]> invoicePdf(@PathVariable Long id) {
        Invoice invoice = invoiceService.requireOwned(id, me());
        byte[] pdf = invoiceService.pdfBytes(invoice);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + invoice.fileName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }

    private Long me() {
        return CurrentUser.require().id();
    }
}
