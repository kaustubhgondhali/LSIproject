package com.lordsai.lsi.service;

import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.dto.invoice.InvoiceDtos.InvoiceResponse;
import com.lordsai.lsi.entity.Invoice;
import com.lordsai.lsi.entity.Payment;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.PaymentMode;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.entity.enums.ProductType;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.export.PdfRenderer;
import com.lordsai.lsi.repository.InvoiceRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The one invoice system for every digital product. Creates the invoice record for a verified
 * payment (idempotently — one invoice per payment, ever), renders the Lord Sai PDF from the single
 * invoice template, stores it under the protected "invoices/" folder and enforces ownership when
 * a student asks for it. Admin, student, email attachment and print all go through here.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private static final Locale EN_IN = Locale.forLanguageTag("en-IN");
    public static final String TEMPLATE = "invoice/invoice";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceNumberService invoiceNumberService;
    private final StudentProfileRepository studentProfileRepository;
    private final PdfRenderer pdfRenderer;
    private final FileStorageService storage;
    private final AuditService auditService;
    private final AppProperties properties;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoiceNumberService invoiceNumberService,
                          StudentProfileRepository studentProfileRepository,
                          PdfRenderer pdfRenderer,
                          FileStorageService storage,
                          AuditService auditService,
                          AppProperties properties) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceNumberService = invoiceNumberService;
        this.studentProfileRepository = studentProfileRepository;
        this.pdfRenderer = pdfRenderer;
        this.storage = storage;
        this.auditService = auditService;
        this.properties = properties;
    }

    // ---- creation --------------------------------------------------------------------------

    /**
     * Creates the invoice for a successful payment, or returns the existing one. Safe to call from
     * both the browser verification and the webhook: the unique payment_id constraint plus the
     * lookup make a second invoice impossible.
     */
    @Transactional
    public Invoice createForPayment(Payment payment, User student, StudentProfile profile) {
        Optional<Invoice> existing = invoiceRepository.findByPaymentId(payment.getId());
        if (existing.isPresent()) {
            log.info("[INVOICE] Reused {} for payment {}", existing.get().getInvoiceNumber(), payment.getOrderRef());
            return existing.get();
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ApiException(HttpStatus.CONFLICT, "An invoice can only be issued for a successful payment.");
        }

        BigDecimal charged = payment.getAmount();
        BigDecimal listPrice = listPriceOf(payment);
        if (listPrice == null || listPrice.compareTo(charged) < 0) {
            listPrice = charged;
        }

        Invoice inv = new Invoice();
        inv.setInvoiceNumber(invoiceNumberService.next());
        inv.setPayment(payment);
        inv.setStudent(student);
        inv.setProductType(payment.getProductType());
        inv.setCourse(payment.isEbook() ? null : payment.getCourse());
        inv.setEbook(payment.isEbook() ? payment.getEbook() : null);
        inv.setStudentName(student.getFullName());
        inv.setStudentCode(profile == null ? null : profile.getStudentId());
        inv.setStudentEmail(student.getEmail());
        inv.setStudentPhone(student.getMobile() != null ? student.getMobile() : payment.getCustomerMobile());
        inv.setProductName(payment.productName());
        inv.setQuantity(1);
        inv.setUnitPrice(listPrice);
        inv.setSubtotal(listPrice);
        inv.setDiscount(listPrice.subtract(charged).max(BigDecimal.ZERO));
        inv.setTax(BigDecimal.ZERO);
        inv.setTotal(charged);
        inv.setCurrency(payment.getCurrency() == null ? "INR" : payment.getCurrency());
        inv.setPaymentMethod(paymentMethodLabel(payment));
        inv.setTransactionId(payment.getRazorpayPaymentId() != null ? payment.getRazorpayPaymentId()
                : (payment.getPaymentMode() == PaymentMode.DEMO ? "DEMO" : payment.getRazorpayOrderId()));
        inv.setOrderRef(payment.getOrderRef());
        inv.setPaymentStatus(payment.getStatus());
        Instant purchasedAt = payment.getVerifiedAt() != null ? payment.getVerifiedAt() : Instant.now();
        inv.setPurchaseDate(purchasedAt);
        inv.setInvoiceDate(LocalDate.ofInstant(purchasedAt, INDIA));
        inv.setNotes("This is a computer-generated invoice for a digital product and does not require a signature.");
        inv = invoiceRepository.save(inv);

        try {
            renderAndStore(inv);
        } catch (RuntimeException e) {
            // The invoice record is the source of truth; the PDF can be regenerated on demand.
            log.error("[INVOICE] PDF generation failed for {}: {}", inv.getInvoiceNumber(), e.getMessage());
        }
        auditService.record(null, "INVOICE_GENERATED", "Invoice", inv.getId(),
                inv.getInvoiceNumber() + " for " + inv.getProductType() + " '" + inv.getProductName() + "' — "
                        + inv.getStudentEmail() + " (" + inv.getTotal() + ")", null);
        log.info("[INVOICE] Generated {} for payment {} ({} {})", inv.getInvoiceNumber(), payment.getOrderRef(),
                inv.getCurrency(), inv.getTotal());
        return inv;
    }

    // ---- PDF -------------------------------------------------------------------------------

    /** The stored PDF, regenerated transparently if the file is missing (e.g. storage moved). */
    @Transactional
    public byte[] pdfBytes(Invoice invoice) {
        if (invoice.getPdfPath() != null && storage.exists(invoice.getPdfPath())) {
            return storage.readBytes(invoice.getPdfPath());
        }
        return renderAndStore(invoice);
    }

    /** The invoice as XHTML — the same design, used for the in-browser print view. */
    @Transactional(readOnly = true)
    public String html(Invoice invoice) {
        return pdfRenderer.html(TEMPLATE, model(invoice));
    }

    private byte[] renderAndStore(Invoice invoice) {
        byte[] pdf = pdfRenderer.pdf(TEMPLATE, model(invoice));
        String previous = invoice.getPdfPath();
        invoice.setPdfPath(storage.storeBytes(pdf, FileStorageService.Kind.INVOICE, "pdf"));
        invoice.setPdfGeneratedAt(Instant.now());
        invoiceRepository.save(invoice);
        if (previous != null) {
            storage.deleteQuietly(previous);
        }
        return pdf;
    }

    /** Every dynamic value the invoice template consumes. Nothing customer-specific is hardcoded. */
    public Map<String, Object> model(Invoice inv) {
        Map<String, Object> m = new HashMap<>();
        m.put("invoiceNumber", inv.getInvoiceNumber());
        m.put("invoiceDate", inv.getInvoiceDate().format(DATE));
        m.put("purchaseDate", DATE_TIME.format(inv.getPurchaseDate().atZone(INDIA)) + " IST");
        m.put("studentName", inv.getStudentName());
        m.put("studentId", inv.getStudentCode() == null ? "—" : inv.getStudentCode());
        m.put("studentEmail", inv.getStudentEmail());
        m.put("studentPhone", inv.getStudentPhone() == null ? "—" : inv.getStudentPhone());
        m.put("productName", inv.getProductName());
        m.put("productType", inv.getProductType().label());
        m.put("productDescription", inv.getProductType() == ProductType.EBOOK
                ? "Digital ebook — readable inside the Lord Sai Student Portal"
                : "Online course — lessons, videos and handouts in the Lord Sai Student Portal");
        m.put("quantity", inv.getQuantity());
        m.put("unitPrice", money(inv.getUnitPrice(), inv.getCurrency()));
        m.put("subtotal", money(inv.getSubtotal(), inv.getCurrency()));
        m.put("discount", money(inv.getDiscount(), inv.getCurrency()));
        m.put("tax", money(inv.getTax(), inv.getCurrency()));
        m.put("total", money(inv.getTotal(), inv.getCurrency()));
        m.put("currency", inv.getCurrency());
        m.put("paymentMethod", inv.getPaymentMethod() == null ? "—" : inv.getPaymentMethod());
        m.put("transactionId", inv.getTransactionId() == null ? "—" : inv.getTransactionId());
        m.put("orderRef", inv.getOrderRef() == null ? "—" : inv.getOrderRef());
        m.put("paymentStatus", inv.getPaymentStatus().name());
        m.put("paymentStatusLabel", inv.getPaymentStatus() == PaymentStatus.SUCCESS ? "PAID" : inv.getPaymentStatus().name());
        m.put("notes", inv.getNotes() == null ? "" : inv.getNotes());
        m.put("supportEmail", properties.support().email());
        m.put("supportPhone", properties.support().phone());
        m.put("portalUrl", properties.publicBaseUrl().replaceAll("/+$", "") + "/student-login.html");
        return m;
    }

    public static String money(BigDecimal amount, String currency) {
        NumberFormat nf = NumberFormat.getNumberInstance(EN_IN);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        String symbol = "INR".equalsIgnoreCase(currency) ? "₹ " : currency + " ";
        return symbol + nf.format(amount == null ? BigDecimal.ZERO : amount);
    }

    // ---- lookup & ownership ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public Invoice require(Long id) {
        return invoiceRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Invoice", id));
    }

    /** Student A can never resolve Student B's invoice: the lookup itself is scoped to the owner. */
    @Transactional(readOnly = true)
    public Invoice requireOwned(Long id, Long studentUserId) {
        return invoiceRepository.findByIdAndStudentId(id, studentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this invoice."));
    }

    @Transactional(readOnly = true)
    public Optional<Invoice> findByPayment(Long paymentId) {
        return invoiceRepository.findByPaymentId(paymentId);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listForStudent(Long studentUserId) {
        return invoiceRepository.findByStudentIdOrderByPurchaseDateDesc(studentUserId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> search(ProductType productType, PaymentStatus status, Long studentUserId,
                                        Long courseId, Long ebookId, Instant from, Instant to, String q, Pageable pageable) {
        String term = q == null || q.isBlank() ? null : q.trim();
        return invoiceRepository.search(productType, status, studentUserId, courseId, ebookId, from, to, term, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public void markEmailed(Invoice invoice) {
        invoice.setEmailedAt(Instant.now());
        invoice.setEmailCount(invoice.getEmailCount() + 1);
        invoiceRepository.save(invoice);
    }

    public InvoiceResponse toResponse(Invoice i) {
        return new InvoiceResponse(i.getId(), i.getInvoiceNumber(), i.getPayment().getId(), i.getOrderRef(),
                i.getStudent().getId(), i.getStudentCode(), i.getStudentName(), i.getStudentEmail(), i.getStudentPhone(),
                i.getProductType(), i.productId(), i.getProductName(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal(),
                i.getDiscount(), i.getTax(), i.getTotal(), i.getCurrency(), i.getPaymentMethod(), i.getTransactionId(),
                i.getPaymentStatus(), i.getInvoiceDate(), i.getPurchaseDate(), i.getPdfPath() != null,
                i.getEmailedAt(), i.getEmailCount(), i.getCreatedAt());
    }

    // ---- helpers ---------------------------------------------------------------------------

    private static BigDecimal listPriceOf(Payment p) {
        if (p.isEbook()) {
            return p.getEbook() == null ? null : p.getEbook().getPrice();
        }
        return p.getCourse() == null ? null : p.getCourse().getPrice();
    }

    private static String paymentMethodLabel(Payment p) {
        if (p.getPaymentMode() == PaymentMode.DEMO) {
            return "Demo (no charge)";
        }
        String method = p.getPaymentMethod();
        if (method == null || method.isBlank()) {
            return "Razorpay";
        }
        return "Razorpay — " + method.substring(0, 1).toUpperCase(Locale.ROOT) + method.substring(1);
    }

    String studentCodeOf(User user) {
        return studentProfileRepository.findByUserId(user.getId()).map(StudentProfile::getStudentId).orElse(null);
    }
}
