package com.lordsai.lsi.controller.admin;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.course.CourseDtos.ReorderRequest;
import com.lordsai.lsi.dto.ebook.EbookDtos.AdminEbook;
import com.lordsai.lsi.dto.ebook.EbookDtos.EbookPriceRequest;
import com.lordsai.lsi.dto.ebook.EbookDtos.EbookRequest;
import com.lordsai.lsi.dto.ebook.EbookDtos.EbookStatusRequest;
import com.lordsai.lsi.dto.ebook.EbookDtos.EntitlementResponse;
import com.lordsai.lsi.dto.ebook.EbookDtos.GrantEbookRequest;
import com.lordsai.lsi.entity.Ebook;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.EntitlementStatus;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.EbookEntitlementService;
import com.lordsai.lsi.service.EbookService;
import com.lordsai.lsi.service.FileStorageService;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

/** Main Admin: ebook catalogue (add / edit / price / status / files) and ebook entitlements. */
@RestController
@RequestMapping("/api/admin")
public class AdminEbookController {

    private final EbookService ebookService;
    private final EbookEntitlementService entitlementService;
    private final FileStorageService storage;
    private final UserService userService;

    public AdminEbookController(EbookService ebookService, EbookEntitlementService entitlementService,
                                FileStorageService storage, UserService userService) {
        this.ebookService = ebookService;
        this.entitlementService = entitlementService;
        this.storage = storage;
        this.userService = userService;
    }

    @GetMapping("/ebooks")
    public ApiResponse<List<AdminEbook>> list() {
        return ApiResponse.ok(ebookService.listAdmin());
    }

    @GetMapping("/ebooks/{id}")
    public ApiResponse<AdminEbook> get(@PathVariable Long id) {
        return ApiResponse.ok(ebookService.getAdmin(id));
    }

    @PostMapping("/ebooks")
    public ApiResponse<AdminEbook> create(@Valid @RequestBody EbookRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Ebook added. Upload its PDF, then activate it to put it on sale.",
                ebookService.create(body, actor(), RequestUtil.clientIp(req)));
    }

    /**
     * The Main Admin form: name + price + cover + PDF (+ optional info) in ONE request. Files are
     * validated by extension, declared type, content signature and size; the ebook is created
     * ACTIVE so it appears on the store immediately. Field names match the admin form exactly.
     */
    @PostMapping(value = "/ebooks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdminEbook> createWithFiles(@RequestParam(value = "title", required = false) String title,
                                                   @RequestParam(value = "price", required = false) java.math.BigDecimal price,
                                                   @RequestParam(value = "description", required = false) String description,
                                                   @RequestParam(value = "cover", required = false) MultipartFile cover,
                                                   @RequestParam(value = "pdf", required = false) MultipartFile pdf,
                                                   HttpServletRequest req) {
        return ApiResponse.ok("Ebook uploaded successfully and is now available in the Store.",
                ebookService.createSimple(title, price, description, cover, pdf, actor(), RequestUtil.clientIp(req)));
    }

    /** Same form for editing: a missing cover / PDF keeps the current file. */
    @PutMapping(value = "/ebooks/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdminEbook> updateWithFiles(@PathVariable Long id,
                                                   @RequestParam(value = "title", required = false) String title,
                                                   @RequestParam(value = "price", required = false) java.math.BigDecimal price,
                                                   @RequestParam(value = "description", required = false) String description,
                                                   @RequestParam(value = "cover", required = false) MultipartFile cover,
                                                   @RequestParam(value = "pdf", required = false) MultipartFile pdf,
                                                   HttpServletRequest req) {
        return ApiResponse.ok("Ebook updated.",
                ebookService.updateSimple(id, title, price, description, cover, pdf, actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/ebooks/{id}")
    public ApiResponse<AdminEbook> update(@PathVariable Long id, @Valid @RequestBody EbookRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Ebook updated.", ebookService.update(id, body, actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/ebooks/{id}/price")
    public ApiResponse<AdminEbook> price(@PathVariable Long id, @Valid @RequestBody EbookPriceRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Price updated.",
                ebookService.changePrice(id, body.price(), body.discountedPrice(), actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/ebooks/{id}/status")
    public ApiResponse<AdminEbook> status(@PathVariable Long id, @Valid @RequestBody EbookStatusRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Status updated.", ebookService.setStatus(id, body.status(), actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/ebooks/reorder")
    public ApiResponse<Void> reorder(@Valid @RequestBody ReorderRequest body, HttpServletRequest req) {
        ebookService.reorder(body.orderedIds(), actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Order saved.");
    }

    @PostMapping("/ebooks/{id}/cover")
    public ApiResponse<AdminEbook> cover(@PathVariable Long id, @RequestParam("file") MultipartFile file, HttpServletRequest req) {
        return ApiResponse.ok("Cover uploaded.", ebookService.uploadCover(id, file, actor(), RequestUtil.clientIp(req)));
    }

    @PostMapping("/ebooks/{id}/pdf")
    public ApiResponse<AdminEbook> pdf(@PathVariable Long id, @RequestParam("file") MultipartFile file, HttpServletRequest req) {
        return ApiResponse.ok("Ebook PDF uploaded.", ebookService.uploadPdf(id, file, actor(), RequestUtil.clientIp(req)));
    }

    /** Admin preview of the protected file (bearer-authenticated, never a public URL). */
    @GetMapping("/ebooks/{id}/pdf")
    public ResponseEntity<Resource> viewPdf(@PathVariable Long id) {
        Ebook ebook = ebookService.requireEbook(id);
        if (ebook.getPdfPath() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No PDF has been uploaded for this ebook yet.");
        }
        String name = ebook.getPdfOriginalName() == null ? ebook.getEbookCode() + ".pdf" : ebook.getPdfOriginalName();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name.replace("\"", "") + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(storage.load(ebook.getPdfPath()));
    }

    @DeleteMapping("/ebooks/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        ebookService.delete(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Ebook deleted.");
    }

    // ---- entitlements ----------------------------------------------------------------------

    @GetMapping("/ebooks/{id}/entitlements")
    public ApiResponse<List<EntitlementResponse>> entitlements(@PathVariable Long id) {
        return ApiResponse.ok(entitlementService.listForEbook(id));
    }

    @GetMapping("/students/{id}/ebooks")
    public ApiResponse<List<EntitlementResponse>> studentEbooks(@PathVariable Long id) {
        return ApiResponse.ok(entitlementService.listForStudent(id));
    }

    @PostMapping("/ebook-entitlements")
    public ApiResponse<EntitlementResponse> grant(@Valid @RequestBody GrantEbookRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Ebook access granted (manual).",
                entitlementService.adminGrant(body.studentUserId(), body.ebookId(), actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/ebook-entitlements/{id}/status")
    public ApiResponse<EntitlementResponse> entitlementStatus(@PathVariable Long id, @RequestParam EntitlementStatus status,
                                                              HttpServletRequest req) {
        return ApiResponse.ok("Ebook access updated.", entitlementService.setStatus(id, status, actor(), RequestUtil.clientIp(req)));
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
