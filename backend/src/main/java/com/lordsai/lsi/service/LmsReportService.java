package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.ebook.EbookDtos.AdminEbook;
import com.lordsai.lsi.dto.invoice.InvoiceDtos.InvoiceResponse;
import com.lordsai.lsi.entity.Payment;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.entity.enums.ProductType;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.export.ExportTable;
import com.lordsai.lsi.repository.InvoiceRepository;
import com.lordsai.lsi.repository.PaymentRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Report datasets for the online-academy (LMS) records shown in the Automation Admin and Main
 * Admin panels — purchases, invoices, ebooks — as
 * {@link ExportTable}s, so the same Print / PDF / Excel pipeline serves all of them and every
 * export respects the filters that were applied on screen.
 */
@Service
public class LmsReportService {

    public static final List<String> REPORTS = List.of("purchases", "invoices", "ebooks");
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);
    private static final int LIMIT = 5000;

    /** Filters any LMS report can receive; unused ones are ignored by each report. */
    public record Query(String productType, Long courseId, Long ebookId, String status, String channel, Long studentId,
                        LocalDate from, LocalDate to, String q) {
    }

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InvoiceService invoiceService;
    private final EbookService ebookService;

    public LmsReportService(PaymentRepository paymentRepository,
                            InvoiceRepository invoiceRepository,
                            StudentProfileRepository studentProfileRepository,
                            InvoiceService invoiceService,
                            EbookService ebookService) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.invoiceService = invoiceService;
        this.ebookService = ebookService;
    }

    public static boolean knows(String report) {
        return REPORTS.contains(report);
    }

    @Transactional(readOnly = true)
    public ExportTable build(String report, Query f) {
        return switch (report) {
            case "purchases" -> purchases(f);
            case "invoices" -> invoices(f);
            case "ebooks" -> ebooks();
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown report: " + report);
        };
    }

    // ---- purchases (all successful payments, any product) ---------------------------------

    private ExportTable purchases(Query f) {
        ProductType type = parseType(f.productType());
        PaymentStatus status = f.status() == null || f.status().isBlank() ? null : PaymentStatus.valueOf(f.status().toUpperCase(Locale.ROOT));
        Instant from = startOf(f.from());
        Instant to = endOf(f.to());
        String q = f.q() == null ? "" : f.q().trim().toLowerCase(Locale.ROOT);

        List<Payment> all = paymentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        long success = 0;
        for (Payment p : all) {
            if (type != null && p.getProductType() != type) continue;
            if (status != null && p.getStatus() != status) continue;
            if (f.courseId() != null && (p.getCourse() == null || !p.getCourse().getId().equals(f.courseId()))) continue;
            if (f.ebookId() != null && (p.getEbook() == null || !p.getEbook().getId().equals(f.ebookId()))) continue;
            if (f.studentId() != null && (p.getUser() == null || !p.getUser().getId().equals(f.studentId()))) continue;
            Instant when = p.getVerifiedAt() != null ? p.getVerifiedAt() : p.getCreatedAt();
            if (from != null && when.isBefore(from)) continue;
            if (to != null && !when.isBefore(to)) continue;
            String studentCode = p.getUser() == null ? "" : studentProfileRepository.findByUserId(p.getUser().getId()).map(StudentProfile::getStudentId).orElse("");
            String invoiceNo = invoiceRepository.findByPaymentId(p.getId()).map(i -> i.getInvoiceNumber()).orElse("");
            if (!q.isEmpty()) {
                String hay = (studentCode + " " + p.getCustomerName() + " " + p.getCustomerEmail() + " " + p.getCustomerMobile() + " "
                        + p.productName() + " " + p.getOrderRef() + " " + n(p.getRazorpayPaymentId()) + " " + invoiceNo).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
            }
            rows.add(List.of(studentCode, p.getCustomerName(), p.getCustomerEmail(), n(p.getCustomerMobile()), p.productName(),
                    p.getProductType().label(), dt(when), p.getAmount(), p.getStatus().name(), invoiceNo,
                    n(p.getRazorpayPaymentId()), p.getOrderRef(), p.getPaymentMode().name()));
            if (p.getStatus() == PaymentStatus.SUCCESS) {
                success++;
                total = total.add(p.getAmount());
            }
            if (rows.size() >= LIMIT) break;
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("purchases", rows.size());
        totals.put("successful", success);
        totals.put("collected", total);
        return ExportTable.of("Purchase Report", List.of("Student ID", "Student Name", "Email", "Mobile", "Product", "Product Type",
                "Purchase Date", "Amount", "Payment Status", "Invoice Number", "Transaction ID", "Order Ref", "Mode"), rows, filters(f), totals);
    }

    // ---- invoices ---------------------------------------------------------------------------

    private ExportTable invoices(Query f) {
        ProductType type = parseType(f.productType());
        PaymentStatus status = f.status() == null || f.status().isBlank() ? null : PaymentStatus.valueOf(f.status().toUpperCase(Locale.ROOT));
        List<InvoiceResponse> list = invoiceService.search(type, status, f.studentId(), f.courseId(), f.ebookId(),
                startOf(f.from()), endOf(f.to()), f.q(), PageRequest.of(0, LIMIT)).getContent();
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (InvoiceResponse i : list) {
            rows.add(List.of(i.invoiceNumber(), n(i.studentId()), i.studentName(), i.studentEmail(), n(i.studentPhone()), i.productName(),
                    i.productType().label(), dt(i.purchaseDate()), i.total(), i.paymentStatus().name(), n(i.transactionId()),
                    n(i.paymentMethod()), i.emailedAt() == null ? "No" : "Yes"));
            total = total.add(i.total());
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("invoices", rows.size());
        totals.put("amount", total);
        return ExportTable.of("Invoice Report", List.of("Invoice Number", "Student ID", "Student Name", "Email", "Mobile", "Product",
                "Product Type", "Purchase Date", "Amount", "Payment Status", "Transaction ID", "Payment Method", "Emailed"), rows, filters(f), totals);
    }

    private ExportTable ebooks() {
        List<AdminEbook> list = ebookService.listAdmin();
        List<List<Object>> rows = new ArrayList<>();
        for (AdminEbook e : list) {
            rows.add(List.of(e.ebookCode(), e.title(), n(e.author()), n(e.category()), e.price(), e.effectivePrice(), e.status().name(),
                    e.purchaseCount(), dt(e.createdAt()), dt(e.updatedAt())));
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("ebooks", rows.size());
        return ExportTable.of("Ebooks", List.of("Code", "Title", "Author", "Category", "List Price", "Selling Price", "Status",
                "Purchases", "Created", "Updated"), rows, new LinkedHashMap<>(), totals);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private static Map<String, String> filters(Query f) {
        Map<String, String> m = new LinkedHashMap<>();
        if (f.productType() != null && !f.productType().isBlank() && !"ALL".equalsIgnoreCase(f.productType())) m.put("Product type", f.productType());
        if (f.courseId() != null) m.put("Course", "#" + f.courseId());
        if (f.ebookId() != null) m.put("Ebook", "#" + f.ebookId());
        if (f.status() != null && !f.status().isBlank() && !"ALL".equalsIgnoreCase(f.status())) m.put("Status", f.status());
        if (f.channel() != null && !f.channel().isBlank() && !"ALL".equalsIgnoreCase(f.channel())) m.put("Channel", f.channel());
        if (f.studentId() != null) m.put("Student", "#" + f.studentId());
        if (f.from() != null || f.to() != null) {
            m.put("Date", (f.from() == null ? "…" : f.from().format(D)) + " – " + (f.to() == null ? "…" : f.to().format(D)));
        }
        if (f.q() != null && !f.q().isBlank()) m.put("Search", f.q());
        return m;
    }

    private static ProductType parseType(String s) {
        if (s == null || s.isBlank() || "ALL".equalsIgnoreCase(s)) {
            return null;
        }
        return ProductType.valueOf(s.trim().toUpperCase(Locale.ROOT));
    }

    private static Instant startOf(LocalDate d) {
        return d == null ? null : d.atStartOfDay(INDIA).toInstant();
    }

    private static Instant endOf(LocalDate d) {
        return d == null ? null : d.plusDays(1).atStartOfDay(INDIA).toInstant();
    }

    private static String dt(Instant t) {
        return t == null ? "" : DT.format(t.atZone(INDIA));
    }

    private static String n(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
