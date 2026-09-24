package com.lordsai.lsi.automation.web;

import com.lordsai.lsi.automation.dto.AutomationDtos.AttendanceSheet;
import com.lordsai.lsi.automation.dto.AutomationDtos.FeeSummary;
import com.lordsai.lsi.automation.dto.AutomationDtos.PaymentDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.ReceiptDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.RecordPaymentRequest;
import com.lordsai.lsi.automation.dto.AutomationDtos.SaveAttendanceRequest;
import com.lordsai.lsi.automation.dto.AutomationDtos.SaveSessionRequest;
import com.lordsai.lsi.automation.dto.AutomationDtos.SessionDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.UpdatePaymentRequest;
import com.lordsai.lsi.automation.service.AcadAttendanceService;
import com.lordsai.lsi.automation.service.AcadPaymentService;
import com.lordsai.lsi.automation.service.AcadReportService;
import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.export.ExportTable;
import com.lordsai.lsi.export.ReportExportService;
import com.lordsai.lsi.service.LmsReportService;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Automation Admin: fees & payments, receipts, attendance, reports. Same access rule as AutomationController. */
@RestController
@RequestMapping("/api/automation")
public class AutomationLedgerController {

    private final AcadPaymentService paymentService;
    private final AcadAttendanceService attendanceService;
    private final AcadReportService reportService;
    private final LmsReportService lmsReportService;
    private final com.lordsai.lsi.automation.service.AcadCommunicationService acadCommunicationService;
    private final ReportExportService exportService;
    private final UserService userService;

    public AutomationLedgerController(AcadPaymentService paymentService, AcadAttendanceService attendanceService,
                                      AcadReportService reportService, LmsReportService lmsReportService,
                                      com.lordsai.lsi.automation.service.AcadCommunicationService acadCommunicationService,
                                      ReportExportService exportService, UserService userService) {
        this.paymentService = paymentService;
        this.attendanceService = attendanceService;
        this.reportService = reportService;
        this.lmsReportService = lmsReportService;
        this.acadCommunicationService = acadCommunicationService;
        this.exportService = exportService;
        this.userService = userService;
    }

    // ---- fees & payments --------------------------------------------------------------------

    /** Everything the payment form auto-fills once a student is chosen. */
    @GetMapping("/students/{id}/fees")
    public ApiResponse<FeeSummary> fees(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.feeSummary(id));
    }

    @GetMapping("/payments")
    public ApiResponse<List<PaymentDto>> payments(@RequestParam(required = false) Long studentId,
                                                  @RequestParam(required = false) Long batchId,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(required = false) String mode) {
        return ApiResponse.ok(paymentService.report(studentId, batchId, from, to, mode));
    }

    @GetMapping("/payments/{id}")
    public ApiResponse<PaymentDto> payment(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.get(id));
    }

    @PostMapping("/payments")
    public ApiResponse<PaymentDto> record(@Valid @RequestBody RecordPaymentRequest body, HttpServletRequest req) {
        PaymentDto p = paymentService.record(body, actor(), RequestUtil.clientIp(req));
        return ApiResponse.ok("Payment " + p.paymentNo() + " recorded. Receipt " + p.receiptNo() + " generated.", p);
    }

    @PutMapping("/payments/{id}")
    public ApiResponse<PaymentDto> update(@PathVariable Long id, @Valid @RequestBody UpdatePaymentRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Payment updated and receipt re-issued.", paymentService.update(id, body, actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping("/payments/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        paymentService.delete(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Payment deleted and its receipt voided.");
    }

    // ---- receipts ---------------------------------------------------------------------------

    @GetMapping("/receipts")
    public ApiResponse<Page<ReceiptDto>> receipts(@RequestParam(required = false) Long studentId,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.ok(paymentService.receipts(studentId, from, to, q, page, size));
    }

    @GetMapping("/receipts/{id}")
    public ApiResponse<ReceiptDto> receipt(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.receipt(id));
    }

    @PostMapping("/receipts/{id}/email")
    public ApiResponse<Void> emailReceipt(@PathVariable Long id, HttpServletRequest req) {
        return ApiResponse.message(paymentService.emailReceipt(id, actor(), RequestUtil.clientIp(req)));
    }

    // ---- attendance -------------------------------------------------------------------------

    @GetMapping("/attendance/sessions")
    public ApiResponse<List<SessionDto>> sessions(@RequestParam(required = false) Long batchId,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(attendanceService.sessions(batchId, from, to));
    }

    /** Select batch + date -> the session is found or created and every student in the batch is loaded. */
    @PostMapping("/attendance/sessions")
    public ApiResponse<AttendanceSheet> openSheet(@Valid @RequestBody SaveSessionRequest body, HttpServletRequest req) {
        return ApiResponse.ok(attendanceService.openSheet(body, actor(), RequestUtil.clientIp(req)));
    }

    @GetMapping("/attendance/sessions/{id}")
    public ApiResponse<AttendanceSheet> sheet(@PathVariable Long id) {
        return ApiResponse.ok(attendanceService.sheet(id));
    }

    @PostMapping("/attendance/save")
    public ApiResponse<AttendanceSheet> save(@Valid @RequestBody SaveAttendanceRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Attendance saved.", attendanceService.save(body, actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping("/attendance/sessions/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable Long id, HttpServletRequest req) {
        attendanceService.deleteSession(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Session deleted.");
    }

    // ---- reports ----------------------------------------------------------------------------

    /**
     * Every filter any Automation Admin / Main Admin record screen can apply. Office-ledger
     * reports (students, fees, receipts, attendance, masters) and online-academy reports
     * (purchases, invoices, communications, portal students, ebooks) share these parameters, so
     * Print / PDF / Excel always export exactly what is on screen.
     */
    public record ReportParams(Long batchId, Long courseId, String status, Integer year, LocalDate from, LocalDate to,
                               String mode, Double threshold, String q, String paymentStatus, Long studentId,
                               String productType, Long ebookId, String channel) {
    }

    private ExportTable table(String report, ReportParams p) {
        if (AcadReportService.knows(report)) {
            AcadReportService.Table t = reportService.build(report, new AcadReportService.ReportQuery(p.batchId(), p.courseId(),
                    p.status(), p.year(), p.from(), p.to(), p.mode(), p.threshold(), p.q(), p.paymentStatus(), p.studentId()));
            java.util.Map<String, String> filters = new java.util.LinkedHashMap<>();
            if (p.batchId() != null) filters.put("Batch", "#" + p.batchId());
            if (p.courseId() != null) filters.put("Course", "#" + p.courseId());
            if (p.status() != null && !p.status().isBlank()) filters.put("Status", p.status());
            if (p.paymentStatus() != null && !p.paymentStatus().isBlank()) filters.put("Payment status", p.paymentStatus());
            if (p.year() != null) filters.put("Admission year", String.valueOf(p.year()));
            if (p.mode() != null && !p.mode().isBlank()) filters.put("Mode", p.mode());
            if (p.from() != null || p.to() != null) filters.put("Date", (p.from() == null ? "…" : p.from()) + " – " + (p.to() == null ? "…" : p.to()));
            if (p.threshold() != null && "low-attendance".equals(report)) filters.put("Threshold", p.threshold() + "%");
            if (p.studentId() != null) filters.put("Student", "#" + p.studentId());
            if (p.q() != null && !p.q().isBlank()) filters.put("Search", p.q());
            return ExportTable.from(t, filters);
        }
        if ("communications".equals(report)) {
            return communicationsTable(p);
        }
        if (LmsReportService.knows(report)) {
            return lmsReportService.build(report, new LmsReportService.Query(p.productType(), p.courseId(), p.ebookId(),
                    p.status(), p.channel(), p.studentId(), p.from(), p.to(), p.q()));
        }
        throw new com.lordsai.lsi.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Unknown report: " + report);
    }

    /** Automation Admin message history (acad_communication_logs) as an exportable table. */
    private ExportTable communicationsTable(ReportParams p) {
        com.lordsai.lsi.entity.enums.CommunicationChannel channel = p.channel() == null || p.channel().isBlank() || "ALL".equalsIgnoreCase(p.channel())
                ? null : com.lordsai.lsi.entity.enums.CommunicationChannel.valueOf(p.channel().toUpperCase(java.util.Locale.ROOT));
        com.lordsai.lsi.entity.enums.CommunicationStatus status = p.status() == null || p.status().isBlank() || "ALL".equalsIgnoreCase(p.status())
                ? null : com.lordsai.lsi.entity.enums.CommunicationStatus.valueOf(p.status().toUpperCase(java.util.Locale.ROOT));
        java.time.format.DateTimeFormatter dt = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm", java.util.Locale.ENGLISH)
                .withZone(java.time.ZoneId.of("Asia/Kolkata"));
        java.util.List<com.lordsai.lsi.automation.dto.AcadCommunicationDtos.CommunicationLogResponse> list = acadCommunicationService
                .history(channel, status, p.studentId(), p.from(), p.to(), p.q(), org.springframework.data.domain.PageRequest.of(0, 5000)).getContent();
        java.util.List<java.util.List<Object>> rows = new java.util.ArrayList<>();
        long sent = 0;
        long failed = 0;
        for (com.lordsai.lsi.automation.dto.AcadCommunicationDtos.CommunicationLogResponse c : list) {
            rows.add(java.util.List.of(dt.format(c.createdAt()), c.studentCode() == null ? "" : c.studentCode(),
                    c.studentName() == null ? "" : c.studentName(), c.recipient(), c.channel().name(),
                    c.subject() == null ? "" : c.subject(), c.status().name(), c.provider() == null ? "" : c.provider(),
                    c.errorReason() == null ? "" : c.errorReason(), c.sentBy()));
            if (c.status() == com.lordsai.lsi.entity.enums.CommunicationStatus.SENT) sent++;
            else if (c.status() == com.lordsai.lsi.entity.enums.CommunicationStatus.FAILED) failed++;
        }
        java.util.Map<String, String> filters = new java.util.LinkedHashMap<>();
        if (channel != null) filters.put("Channel", channel.name());
        if (status != null) filters.put("Status", status.name());
        if (p.from() != null || p.to() != null) filters.put("Date", (p.from() == null ? "…" : p.from()) + " – " + (p.to() == null ? "…" : p.to()));
        if (p.q() != null && !p.q().isBlank()) filters.put("Search", p.q());
        java.util.Map<String, Object> totals = new java.util.LinkedHashMap<>();
        totals.put("messages", rows.size());
        totals.put("sent", sent);
        totals.put("failed", failed);
        return ExportTable.of("Automation Message History", java.util.List.of("Date", "Student ID", "Student", "Recipient", "Channel",
                "Subject", "Status", "Provider", "Error", "Sent by"), rows, filters, totals);
    }

    private static ReportParams params(Long batchId, Long courseId, String status, Integer year, LocalDate from, LocalDate to,
                                       String mode, Double threshold, String q, String paymentStatus, Long studentId,
                                       String productType, Long ebookId, String channel) {
        return new ReportParams(batchId, courseId, status, year, from, to, mode, threshold, q, paymentStatus, studentId,
                productType, ebookId, channel);
    }

    @GetMapping("/reports/{report}")
    public ApiResponse<AcadReportService.Table> report(@PathVariable String report,
                                                       @RequestParam(required = false) Long batchId,
                                                       @RequestParam(required = false) Long courseId,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) Integer year,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                       @RequestParam(required = false) String mode,
                                                       @RequestParam(required = false) Double threshold,
                                                       @RequestParam(required = false) String q,
                                                       @RequestParam(required = false) String paymentStatus,
                                                       @RequestParam(required = false) Long studentId,
                                                       @RequestParam(required = false) String productType,
                                                       @RequestParam(required = false) Long ebookId,
                                                       @RequestParam(required = false) String channel) {
        return ApiResponse.ok(table(report, params(batchId, courseId, status, year, from, to, mode, threshold, q, paymentStatus,
                studentId, productType, ebookId, channel)).toAcadTable());
    }

    /** {format} = csv | xlsx | pdf. The CSV path is unchanged for the existing screens. */
    @GetMapping("/reports/{report}/{format:csv|xlsx|pdf}")
    public ResponseEntity<byte[]> reportExport(@PathVariable String report,
                                               @PathVariable String format,
                                               @RequestParam(required = false) Long batchId,
                                               @RequestParam(required = false) Long courseId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) Integer year,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(required = false) String mode,
                                               @RequestParam(required = false) Double threshold,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(required = false) String paymentStatus,
                                               @RequestParam(required = false) Long studentId,
                                               @RequestParam(required = false) String productType,
                                               @RequestParam(required = false) Long ebookId,
                                               @RequestParam(required = false) String channel) {
        ExportTable t = table(report, params(batchId, courseId, status, year, from, to, mode, threshold, q, paymentStatus,
                studentId, productType, ebookId, channel));
        return exportService.respond(format, report, t);
    }

    /** Printer-friendly HTML of the same report (title, filters, generated time, table, record count). */
    @GetMapping("/reports/{report}/print")
    public ResponseEntity<String> reportPrint(@PathVariable String report,
                                              @RequestParam(required = false) Long batchId,
                                              @RequestParam(required = false) Long courseId,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) Integer year,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(required = false) String mode,
                                              @RequestParam(required = false) Double threshold,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(required = false) String paymentStatus,
                                              @RequestParam(required = false) Long studentId,
                                              @RequestParam(required = false) String productType,
                                              @RequestParam(required = false) Long ebookId,
                                              @RequestParam(required = false) String channel) {
        ExportTable t = table(report, params(batchId, courseId, status, year, from, to, mode, threshold, q, paymentStatus,
                studentId, productType, ebookId, channel));
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(exportService.html(t));
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
