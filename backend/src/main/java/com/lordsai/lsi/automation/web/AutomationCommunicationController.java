package com.lordsai.lsi.automation.web;

import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.AttachmentUploaded;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.BatchProgress;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.ChannelStatus;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.CommunicationLogResponse;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.Recipient;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.RecipientFilter;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.SendPreview;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.SendRequest;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.SendResult;
import com.lordsai.lsi.automation.service.AcadCommunicationService;
import com.lordsai.lsi.communication.WhatsAppDelivery;
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

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final AcadCommunicationService communicationService;
    private final InvoiceService invoiceService;
    private final PurchaseCommunicationService purchaseCommunication;
    private final UserService userService;

    public AutomationCommunicationController(AcadCommunicationService communicationService,
                                             InvoiceService invoiceService,
                                             PurchaseCommunicationService purchaseCommunication,
                                             UserService userService) {
        this.communicationService = communicationService;
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
        return ApiResponse.ok(r.status() == CommunicationStatus.SENT ? "Message delivered." : "Retry failed: " + r.errorReason(), r);
    }

    // ---- invoices ----------------------------------------------------------------------------

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

    @GetMapping("/invoices/{id}")
    public ApiResponse<InvoiceResponse> invoice(@PathVariable Long id) {
        return ApiResponse.ok(invoiceService.toResponse(invoiceService.require(id)));
    }

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
    @GetMapping("/invoices/{id}/print")
    public ResponseEntity<String> invoicePrint(@PathVariable Long id) {
        Invoice invoice = invoiceService.require(id);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(invoiceService.html(invoice));
    }

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

    @PostMapping("/invoices/{id}/whatsapp")
    public ApiResponse<InvoiceResponse> whatsapp(@PathVariable Long id, HttpServletRequest req) {
        Invoice invoice = invoiceService.require(id);
        WhatsAppDelivery d = purchaseCommunication.sendInvoiceWhatsApp(invoice, actor(), RequestUtil.clientIp(req));
        if (!d.sent()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, d.reason());
        }
        return ApiResponse.ok("Invoice " + invoice.getInvoiceNumber() + " sent on WhatsApp.", invoiceService.toResponse(invoice));
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
