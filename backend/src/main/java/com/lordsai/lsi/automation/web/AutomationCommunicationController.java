package com.lordsai.lsi.automation.web;

import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.AttachmentUploaded;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.BatchProgress;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.ChannelStatus;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.CommunicationLogResponse;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.Recipient;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.ReceiptShareResult;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.RecipientFilter;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.SendPreview;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.SendRequest;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.SendResult;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.WhatsAppChatPreview;
import com.lordsai.lsi.automation.service.AcadCommunicationService;
import com.lordsai.lsi.automation.service.AcadReceiptPdfService;
import com.lordsai.lsi.automation.service.AcadReceiptShareService;
import com.lordsai.lsi.communication.WhatsAppChat;
import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.invoice.InvoiceDtos.InvoiceResponse;
import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.entity.Invoice;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.CommunicationChannel;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.entity.enums.ProductType;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.InvoiceService;
import com.lordsai.lsi.service.PurchaseCommunicationService;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Email & WhatsApp automation of the Automation Admin (recipients are the academy's own
 * students in acad_students, the log is acad_communication_logs) plus the invoice record
 * endpoints. Lives under /api/automation/**; the automation part never touches website / LMS data.
 */
@RestController
@RequestMapping("/api/automation")
public class AutomationCommunicationController {

    /** Online-academy invoices are a Main Admin page; the Automation Admin (offline academy office) cannot open them. */
    static final String MAIN_ADMIN_ONLY = "hasRole('ADMIN')";

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final AcadCommunicationService communicationService;
    private final AcadReceiptShareService receiptShareService;
    private final InvoiceService invoiceService;
    private final PurchaseCommunicationService purchaseCommunication;
    private final UserService userService;

    public AutomationCommunicationController(AcadCommunicationService communicationService,
                                             AcadReceiptShareService receiptShareService,
                                             InvoiceService invoiceService,
                                             PurchaseCommunicationService purchaseCommunication,
                                             UserService userService) {
        this.communicationService = communicationService;
        this.receiptShareService = receiptShareService;
        this.invoiceService = invoiceService;
        this.purchaseCommunication = purchaseCommunication;
        this.userService = userService;
    }

    // ---- communication -----------------------------------------------------------------------

    @GetMapping("/communications/status")
    public ApiResponse<ChannelStatus> status() {
        return ApiResponse.ok(communicationService.channelStatus());
    }

    @GetMapping("/communications/counters")
    public ApiResponse<Map<String, Long>> counters() {
        return ApiResponse.ok(communicationService.counters());
    }

    /** The academy's own students (acad_students) — the recipient list with per-row actions. */
    @GetMapping("/communications/students")
    public ApiResponse<Page<Recipient>> students(@RequestParam(required = false) Long batchId,
                                                 @RequestParam(required = false) Long courseId,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "25") int size) {
        RecipientFilter filter = new RecipientFilter(batchId, courseId, status, q);
        return ApiResponse.ok(communicationService.recipients(filter, PageRequest.of(page, Math.min(Math.max(size, 1), 200))));
    }

    @PostMapping("/communications/preview")
    public ApiResponse<SendPreview> preview(@Valid @RequestBody SendRequest body) {
        return ApiResponse.ok(communicationService.preview(body));
    }

    @PostMapping("/communications/send")
    public ApiResponse<SendResult> send(@Valid @RequestBody SendRequest body, HttpServletRequest req) {
        SendResult result = communicationService.send(body, actor(), RequestUtil.clientIp(req));
        return ApiResponse.ok(result.message(), result);
    }

    @PostMapping("/communications/attachments")
    public ApiResponse<AttachmentUploaded> attachment(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("Attachment uploaded.", communicationService.uploadAttachment(file));
    }

    @GetMapping("/communications/batches/{batchRef}")
    public ApiResponse<BatchProgress> progress(@PathVariable String batchRef) {
        return ApiResponse.ok(communicationService.progress(batchRef));
    }

    @GetMapping("/communications/history")
    public ApiResponse<Page<CommunicationLogResponse>> history(@RequestParam(required = false) CommunicationChannel channel,
                                                               @RequestParam(required = false) CommunicationStatus status,
                                                               @RequestParam(required = false) Long studentId,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                               @RequestParam(required = false) String q,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.ok(communicationService.history(channel, status, studentId, from, to, q,
                PageRequest.of(page, Math.min(Math.max(size, 1), 200))));
    }

    @GetMapping("/communications/history/{id}")
    public ApiResponse<CommunicationLogResponse> historyItem(@PathVariable Long id) {
        return ApiResponse.ok(communicationService.get(id));
    }

    /** Messages sent to one academy student (acad_students id). */
    @GetMapping("/communications/students/{studentId}/history")
    public ApiResponse<List<CommunicationLogResponse>> studentHistory(@PathVariable Long studentId) {
        return ApiResponse.ok(communicationService.historyForStudent(studentId));
    }

    @PostMapping("/communications/history/{id}/retry")
    public ApiResponse<CommunicationLogResponse> retry(@PathVariable Long id, HttpServletRequest req) {
        CommunicationLogResponse r = communicationService.retry(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.ok(r.status() == CommunicationStatus.SENT ? "Message delivered."
                : r.status() == CommunicationStatus.OPENED ? "WhatsApp chat ready — press Send in WhatsApp."
                : "Retry failed: " + r.errorReason(), r);
    }

    /** The office opened a prepared WhatsApp chat (from the list shown after sending). */
    @PostMapping("/communications/history/{id}/opened")
    public ApiResponse<CommunicationLogResponse> opened(@PathVariable Long id, HttpServletRequest req) {
        CommunicationLogResponse r = communicationService.opened(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.ok(r.status() == CommunicationStatus.OPENED ? "WhatsApp chat opened — press Send in WhatsApp."
                : "WhatsApp could not be opened: " + r.errorReason(), r);
    }

    // ---- one-click payment receipt: WhatsApp + Email -------------------------------------------

    /** The receipt's WhatsApp line, shown before sending and opened by the Send click (the picture is pasted with it). */
    @GetMapping("/communications/receipts/{receiptId}/whatsapp")
    public ApiResponse<WhatsAppChatPreview> receiptWhatsApp(@PathVariable Long receiptId) {
        return ApiResponse.ok(receiptShareService.whatsappPreview(receiptId));
    }

    /** Emails the selected receipt and records the WhatsApp chat opened for it; the result carries each channel's own outcome. */
    @PostMapping("/communications/receipts/{receiptId}/share")
    public ApiResponse<ReceiptShareResult> shareReceipt(@PathVariable Long receiptId, HttpServletRequest req) {
        ReceiptShareResult result = receiptShareService.share(receiptId, actor(), RequestUtil.clientIp(req));
        return ApiResponse.ok(result.summary(), result);
    }

    /** The receipt PDF exactly as it is sent — for the admin to check before sending. Never cached. */
    @GetMapping("/communications/receipts/{receiptId}/pdf")
    public ResponseEntity<byte[]> receiptPdf(@PathVariable Long receiptId) {
        AcadReceiptPdfService.ReceiptFile file = receiptShareService.pdf(receiptId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }

    /**
     * The same receipt as a picture (PNG). The Send click copies it, and the office pastes it into the
     * student's WhatsApp chat with Ctrl + V — WhatsApp's click to chat cannot attach files. Never cached.
     */
    @GetMapping("/communications/receipts/{receiptId}/image")
    public ResponseEntity<byte[]> receiptImage(@PathVariable Long receiptId) {
        AcadReceiptPdfService.ReceiptFile file = receiptShareService.picture(receiptId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }

    // ---- invoices (Main Admin only: the online academy's invoices live in the Main Admin panel) -----

    @PreAuthorize(MAIN_ADMIN_ONLY)
    @GetMapping("/invoices")
    public ApiResponse<Page<InvoiceResponse>> invoices(@RequestParam(required = false) ProductType productType,
                                                       @RequestParam(required = false) PaymentStatus status,
                                                       @RequestParam(required = false) Long studentId,
                                                       @RequestParam(required = false) Long courseId,
                                                       @RequestParam(required = false) Long ebookId,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                       @RequestParam(required = false) String q,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "25") int size) {
        Instant f = from == null ? null : from.atStartOfDay(INDIA).toInstant();
        Instant t = to == null ? null : to.plusDays(1).atStartOfDay(INDIA).toInstant();
        return ApiResponse.ok(invoiceService.search(productType, status, studentId, courseId, ebookId, f, t, q,
                PageRequest.of(page, Math.min(Math.max(size, 1), 200))));
    }

    @PreAuthorize(MAIN_ADMIN_ONLY)
    @GetMapping("/invoices/{id}")
    public ApiResponse<InvoiceResponse> invoice(@PathVariable Long id) {
        return ApiResponse.ok(invoiceService.toResponse(invoiceService.require(id)));
    }

    @PreAuthorize(MAIN_ADMIN_ONLY)
    @GetMapping("/invoices/{id}/pdf")
    public ResponseEntity<byte[]> invoicePdf(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean download) {
        Invoice invoice = invoiceService.require(id);
        byte[] pdf = invoiceService.pdfBytes(invoice);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, (download ? "attachment" : "inline") + "; filename=\"" + invoice.fileName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }

    /** The same invoice design as printable HTML (used by the Print button). */
    @PreAuthorize(MAIN_ADMIN_ONLY)
    @GetMapping("/invoices/{id}/print")
    public ResponseEntity<String> invoicePrint(@PathVariable Long id) {
        Invoice invoice = invoiceService.require(id);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(invoiceService.html(invoice));
    }

    @PreAuthorize(MAIN_ADMIN_ONLY)
    @PostMapping("/invoices/{id}/resend-email")
    public ApiResponse<InvoiceResponse> resend(@PathVariable Long id, HttpServletRequest req) {
        Invoice invoice = invoiceService.require(id);
        EmailDelivery d = purchaseCommunication.resendInvoice(invoice, actor(), RequestUtil.clientIp(req));
        if (!d.delivered()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The invoice could not be emailed. " + d.reason());
        }
        return ApiResponse.ok("Invoice " + invoice.getInvoiceNumber() + " emailed to " + invoice.getStudentEmail() + ".",
                invoiceService.toResponse(invoiceService.require(id)));
    }

    /** The invoice's WhatsApp chat (message + private PDF link) for the admin to open and send. */
    @PreAuthorize(MAIN_ADMIN_ONLY)
    @PostMapping("/invoices/{id}/whatsapp")
    public ApiResponse<WhatsAppChat> whatsapp(@PathVariable Long id, HttpServletRequest req) {
        Invoice invoice = invoiceService.require(id);
        WhatsAppChat chat = purchaseCommunication.prepareInvoiceWhatsApp(invoice, actor(), RequestUtil.clientIp(req));
        return ApiResponse.ok("WhatsApp chat ready for invoice " + invoice.getInvoiceNumber() + " — press Send in WhatsApp.", chat);
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
