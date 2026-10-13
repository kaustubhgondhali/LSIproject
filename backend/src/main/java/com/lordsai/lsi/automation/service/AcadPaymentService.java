package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.dto.AutomationDtos.DiscountRequest;
import com.lordsai.lsi.automation.dto.AutomationDtos.FeeSummary;
import com.lordsai.lsi.automation.dto.AutomationDtos.InstallmentSlot;
import com.lordsai.lsi.automation.dto.AutomationDtos.PaymentDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.ReceiptDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.ReceiptPaymentLine;
import com.lordsai.lsi.automation.dto.AutomationDtos.RecordPaymentRequest;
import com.lordsai.lsi.automation.dto.AutomationDtos.UpdatePaymentRequest;
import com.lordsai.lsi.automation.entity.AcadPayment;
import com.lordsai.lsi.automation.entity.AcadReceipt;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadPaymentRepository;
import com.lordsai.lsi.automation.repository.AcadReceiptRepository;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.email.EmailAttachment;
import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.service.AuditService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Fee ledger: recording a payment recalculates the student's totals, checks the installment
 * rules and issues the receipt in the same transaction. Nothing is typed twice.
 */
@Service
public class AcadPaymentService {

    public static final String ACADEMY_NAME = "LORD SAI INVESTMENT AND SHARE MARKET ACADEMY";
    public static final String ACADEMY_TAGLINE = "Learn • Invest • Grow Together";
    public static final String ACADEMY_ADDRESS = "Shreeraj Nagar Apartment, Kamtha Road, Near Dr. Gade Hospital, Uran, Navi Mumbai, Raigad, Maharashtra – 400702, India.";
    public static final String ACADEMY_EMAIL = "lordsai.academy@gmail.com";
    public static final String ACADEMY_PHONE = "9920254354";
    public static final String SIGNATORY = "Vaibhav S. Pawar, Proprietor";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final AcadPaymentRepository payments;
    private final AcadReceiptRepository receipts;
    private final AcadStudentRepository students;
    private final AcadMasterService master;
    private final AcadSequenceService sequences;
    private final AuditService auditService;
    private final EmailService emailService;
    private final AppProperties properties;
    /** Looked up lazily: the PDF service itself depends on this service (for the receipt snapshot). */
    private final ObjectProvider<AcadReceiptPdfService> receiptPdf;

    public AcadPaymentService(AcadPaymentRepository payments, AcadReceiptRepository receipts, AcadStudentRepository students,
                              AcadMasterService master, AcadSequenceService sequences, AuditService auditService,
                              EmailService emailService, AppProperties properties,
                              ObjectProvider<AcadReceiptPdfService> receiptPdf) {
        this.payments = payments;
        this.receipts = receipts;
        this.students = students;
        this.master = master;
        this.sequences = sequences;
        this.auditService = auditService;
        this.emailService = emailService;
        this.properties = properties;
        this.receiptPdf = receiptPdf;
    }

    // ---- fee summary -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public FeeSummary feeSummary(Long studentId) {
        return feeSummary(requireStudent(studentId));
    }

    @Transactional(readOnly = true)
    public FeeSummary feeSummary(AcadStudent s) {
        List<AcadPayment> list = payments.findByStudentIdOrderByInstallmentNoAsc(s.getId());
        BigDecimal paid = list.stream().map(AcadPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Integer, AcadPayment> byNo = new HashMap<>();
        list.forEach(p -> byNo.put(p.getInstallmentNo(), p));
        // One receipt per payment, keyed by payment id, so each installment slot can show its own
        // receipt link even though the receipt_no printed on all of them is the same shared number.
        Map<Long, AcadReceipt> receiptByPaymentId = new HashMap<>();
        receipts.findByStudentIdOrderByIssuedAtDesc(s.getId()).forEach(r -> receiptByPaymentId.put(r.getPayment().getId(), r));
        int maxSlots = Math.max(5, list.stream().mapToInt(AcadPayment::getInstallmentNo).max().orElse(0));
        List<InstallmentSlot> slots = new java.util.ArrayList<>();
        Integer next = null;
        for (int n = 1; n <= maxSlots; n++) {
            AcadPayment p = byNo.get(n);
            AcadReceipt r = p == null ? null : receiptByPaymentId.get(p.getId());
            slots.add(new InstallmentSlot(n, master.installmentLabel(n), p != null, p == null ? null : p.getAmount(),
                    p == null ? null : p.getPaymentDate(), p == null ? null : p.getPaymentNo(),
                    r == null ? null : r.getId(), r == null ? null : r.getReceiptNo()));
            if (next == null && p == null) {
                next = n;
            }
        }
        BigDecimal netFee = s.netFee();
        BigDecimal balance = netFee.subtract(paid);
        return new FeeSummary(s.getId(), s.getStudentId(), s.getFullName(),
                s.getBatch() == null ? null : s.getBatch().getName(), s.getCourse() == null ? null : s.getCourse().getName(),
                s.getCourseFee(), s.getDiscountAmount(), netFee, s.getDiscountReason(), s.getDiscountAt(),
                s.getDiscountBy() == null ? null : s.getDiscountBy().getFullName(),
                paid, balance, AcadRowMapper.paymentStatus(netFee, paid), slots,
                balance.signum() > 0 ? (next == null ? maxSlots + 1 : next) : null, s.getFeeReceiptNo(), list.size());
    }

    // ---- record / edit / delete ------------------------------------------------------------------

    @Transactional
    public PaymentDto record(RecordPaymentRequest req, User actor, String ip) {
        AcadStudent s = requireStudent(req.studentId());
        if (AcadStudent.STATUS_ARCHIVED.equals(s.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "This student is archived. Restore the record before recording a payment.");
        }
        if (payments.existsByStudentIdAndInstallmentNo(s.getId(), req.installmentNo())) {
            throw new ApiException(HttpStatus.CONFLICT, master.installmentLabel(req.installmentNo())
                    + " is already recorded for " + s.getStudentId() + ". Choose the next installment or edit the existing payment.");
        }
        validateAmount(s, req.amount(), null, req.paymentDate());

        AcadPayment p = new AcadPayment();
        p.setPaymentNo(sequences.next(AcadSequenceService.PAYMENT));
        p.setStudent(s);
        p.setInstallmentNo(req.installmentNo());
        p.setPaymentDate(req.paymentDate());
        p.setAmount(req.amount());
        p.setPaymentMode(master.requirePaymentMode(req.paymentMode()));
        p.setReferenceNo(AcadMasterService.blank(req.referenceNo()));
        p.setNotes(AcadMasterService.blank(req.notes()));
        p.setRecordedBy(actor);
        p = payments.save(p);

        AcadReceipt r = issueReceipt(p, actor);
        auditService.record(actor, "ACAD_PAYMENT_RECORDED", "AcadPayment", p.getId(),
                s.getStudentId() + " " + master.installmentLabel(p.getInstallmentNo()) + " ₹" + p.getAmount().toPlainString()
                        + " " + p.getPaymentMode() + " -> receipt " + r.getReceiptNo(), ip);
        auditService.record(actor, "ACAD_RECEIPT_GENERATED", "AcadReceipt", r.getId(), r.getReceiptNo() + " for " + s.getStudentId(), ip);
        return toDto(p, r);
    }

    @Transactional
    public PaymentDto update(Long id, UpdatePaymentRequest req, User actor, String ip) {
        AcadPayment p = requirePayment(id);
        AcadStudent s = p.getStudent();
        if (req.installmentNo() != p.getInstallmentNo() && payments.existsByStudentIdAndInstallmentNo(s.getId(), req.installmentNo())) {
            throw new ApiException(HttpStatus.CONFLICT, master.installmentLabel(req.installmentNo()) + " is already recorded for " + s.getStudentId() + ".");
        }
        validateAmount(s, req.amount(), p, req.paymentDate());
        String before = master.installmentLabel(p.getInstallmentNo()) + " ₹" + p.getAmount() + " " + p.getPaymentMode() + " on " + p.getPaymentDate();
        p.setInstallmentNo(req.installmentNo());
        p.setPaymentDate(req.paymentDate());
        p.setAmount(req.amount());
        p.setPaymentMode(master.requirePaymentMode(req.paymentMode()));
        p.setReferenceNo(AcadMasterService.blank(req.referenceNo()));
        p.setNotes(AcadMasterService.blank(req.notes()));
        p.setReviewNote(null);
        payments.save(p);

        // The receipt is re-issued under the same number so it always matches the corrected payment.
        AcadReceipt r = receipts.findByPaymentId(p.getId()).orElseGet(() -> issueReceipt(p, actor));
        fillSnapshot(r, p, actor);
        receipts.save(r);
        auditService.record(actor, "ACAD_PAYMENT_UPDATED", "AcadPayment", id, s.getStudentId() + ": " + before + " -> "
                + master.installmentLabel(p.getInstallmentNo()) + " ₹" + p.getAmount() + " " + p.getPaymentMode() + " on " + p.getPaymentDate(), ip);
        return toDto(p, r);
    }

    @Transactional
    public void delete(Long id, User actor, String ip) {
        AcadPayment p = requirePayment(id);
        String desc = p.getStudent().getStudentId() + " " + p.getPaymentNo() + " ₹" + p.getAmount();
        receipts.findByPaymentId(id).ifPresent(r -> {
            auditService.record(actor, "ACAD_RECEIPT_VOIDED", "AcadReceipt", r.getId(), r.getReceiptNo() + " (payment deleted)", ip);
            receipts.delete(r);
        });
        payments.delete(p);
        auditService.record(actor, "ACAD_PAYMENT_DELETED", "AcadPayment", id, desc, ip);
    }

    private void validateAmount(AcadStudent s, BigDecimal amount, AcadPayment editing, LocalDate date) {
        BigDecimal paidOthers = payments.findByStudentIdOrderByInstallmentNoAsc(s.getId()).stream()
                .filter(x -> editing == null || !x.getId().equals(editing.getId()))
                .map(AcadPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = s.netFee().subtract(paidOthers);   // what is left after any discount
        if (amount.compareTo(remaining) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount ₹" + amount.toPlainString() + " exceeds the outstanding balance of ₹"
                    + remaining.toPlainString() + " for " + s.getStudentId() + ".");
        }
        if (date != null && date.isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payment date cannot be in the future.");
        }
    }

    // ---- receipts ----------------------------------------------------------------------------

    /**
     * Creates the receipt for a payment (also used by the importer, which passes null for actor).
     * Every receipt for the same student shares ONE permanent receipt/reference number
     * ({@link AcadStudent#getFeeReceiptNo()}), which follows the Student ID — see
     * {@link #receiptNoFor(String)}. The payment itself keeps its own unique {@code payment_no},
     * so individual payments stay traceable although all of a student's receipts print the same
     * receipt number.
     */
    @Transactional
    public AcadReceipt issueReceipt(AcadPayment p, User actor) {
        AcadStudent s = p.getStudent();
        String feeReceiptNo = receiptNoFor(s.getStudentId());
        if (!feeReceiptNo.equals(s.getFeeReceiptNo())) {
            s.setFeeReceiptNo(feeReceiptNo);
            students.save(s);
        }
        AcadReceipt r = new AcadReceipt();
        r.setReceiptNo(feeReceiptNo);
        r.setPayment(p);
        r.setStudent(s);
        fillSnapshot(r, p, actor);
        return receipts.save(r);
    }

    private void fillSnapshot(AcadReceipt r, AcadPayment p, User actor) {
        AcadStudent s = p.getStudent();
        BigDecimal totalPaid = payments.totalPaidByStudent(s.getId());
        r.setStudentCode(s.getStudentId());
        r.setStudentName(s.getFullName());
        r.setCourseName(s.getCourse() == null ? null : s.getCourse().getName());
        r.setBatchName(s.getBatch() == null ? null : s.getBatch().getName());
        r.setBatchSchedule(s.getBatch() == null ? null : s.getBatch().getSchedule());
        r.setTotalFee(s.getCourseFee());
        r.setDiscount(s.getDiscountAmount());
        r.setAmountPaid(p.getAmount());
        r.setTotalPaid(totalPaid);
        r.setBalance(s.netFee().subtract(totalPaid));
        r.setPaymentDate(p.getPaymentDate());
        r.setPaymentMode(p.getPaymentMode());
        r.setReferenceNo(p.getReferenceNo());
        r.setAmountInWords(AmountInWords.rupees(p.getAmount()));
        r.setIssuedAt(Instant.now());
        r.setIssuedBy(actor);
    }

    /**
     * The receipt number for a Student ID: its "LSA" becomes "LSR", so both carry the same number
     * (LSA/2026/0025 -> LSR/2026/0025). A Student ID with an office-chosen prefix gets "LSR/" in
     * front of it (ABC-0007 -> LSR/ABC-0007), which keeps every receipt number unique.
     */
    public static String receiptNoFor(String studentId) {
        String id = studentId == null ? "" : studentId.trim().toUpperCase(Locale.ROOT);
        return id.startsWith("LSA") ? "LSR" + id.substring(3) : "LSR/" + id;
    }

    // ---- discount ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public BigDecimal totalPaid(AcadStudent s) {
        return payments.totalPaidByStudent(s.getId());
    }

    /**
     * Gives (or changes) one student's discount — a rupee amount or a percentage of their course
     * fee. It can be at most what the student still owes, so the balance never goes below zero.
     * Every receipt of the student is refreshed under the same number so it shows Course Fee,
     * Discount and Fee after Discount.
     */
    @Transactional
    public FeeSummary setDiscount(Long studentId, DiscountRequest req, User actor, String ip) {
        AcadStudent s = requireStudent(studentId);
        if ((req.amount() == null) == (req.percent() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the discount either in rupees or as a percentage.");
        }
        BigDecimal amount = (req.amount() != null ? req.amount()
                : s.getCourseFee().multiply(req.percent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The discount must be more than zero.");
        }
        BigDecimal paid = totalPaid(s);
        BigDecimal owed = s.getCourseFee().subtract(paid);
        if (amount.compareTo(owed) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The discount can be at most ₹" + AcadReceiptPdfService.indian(owed.max(BigDecimal.ZERO))
                    + " — what " + s.getFullName() + " still owes (course fee ₹" + AcadReceiptPdfService.indian(s.getCourseFee())
                    + " less ₹" + AcadReceiptPdfService.indian(paid) + " already paid).");
        }
        BigDecimal before = s.getDiscountAmount();
        s.setDiscountAmount(amount);
        s.setDiscountReason(req.reason().trim());
        s.setDiscountAt(Instant.now());
        s.setDiscountBy(actor);
        students.save(s);
        refreshReceiptFees(s);
        auditService.record(actor, before.signum() > 0 ? "ACAD_DISCOUNT_CHANGED" : "ACAD_DISCOUNT_GIVEN", "AcadStudent", s.getId(),
                s.getStudentId() + ": discount " + (before.signum() > 0 ? "₹" + AcadReceiptPdfService.indian(before) + " -> " : "")
                        + "₹" + AcadReceiptPdfService.indian(amount)
                        + (req.percent() != null ? " (" + req.percent().stripTrailingZeros().toPlainString() + "% of ₹"
                        + AcadReceiptPdfService.indian(s.getCourseFee()) + ")" : "")
                        + ", fee after discount ₹" + AcadReceiptPdfService.indian(s.netFee()) + " — reason: " + s.getDiscountReason(), ip);
        return feeSummary(s);
    }

    @Transactional
    public FeeSummary removeDiscount(Long studentId, User actor, String ip) {
        AcadStudent s = requireStudent(studentId);
        BigDecimal before = s.getDiscountAmount();
        if (before == null || before.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, s.getFullName() + " has no discount.");
        }
        s.setDiscountAmount(BigDecimal.ZERO);
        s.setDiscountReason(null);
        s.setDiscountAt(Instant.now());
        s.setDiscountBy(actor);
        students.save(s);
        refreshReceiptFees(s);
        auditService.record(actor, "ACAD_DISCOUNT_REMOVED", "AcadStudent", s.getId(),
                s.getStudentId() + ": discount of ₹" + AcadReceiptPdfService.indian(before) + " removed, fee ₹"
                        + AcadReceiptPdfService.indian(s.netFee()), ip);
        return feeSummary(s);
    }

    /** Every receipt of the student shows the current fee and discount; each keeps its own "total paid as of then". */
    private void refreshReceiptFees(AcadStudent s) {
        for (AcadReceipt r : receipts.findByStudentIdOrderByIssuedAtDesc(s.getId())) {
            r.setTotalFee(s.getCourseFee());
            r.setDiscount(s.getDiscountAmount());
            r.setBalance(s.netFee().subtract(r.getTotalPaid()));
            receipts.save(r);
        }
    }

    @Transactional(readOnly = true)
    public ReceiptDto receipt(Long id) {
        return toDto(receipts.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Receipt", id)));
    }

    @Transactional(readOnly = true)
    public Page<ReceiptDto> receipts(Long studentId, LocalDate from, LocalDate to, String q, int page, int size) {
        String term = q == null || q.isBlank() ? null : q.trim().toUpperCase();
        return receipts.search(studentId, from, to, term, PageRequest.of(page, Math.min(Math.max(size, 1), 200))).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<ReceiptDto> receiptsFor(AcadStudent s) {
        return receipts.findByStudentIdOrderByIssuedAtDesc(s.getId()).stream().map(this::toDto).toList();
    }

    /** Emails the receipt to the student's registered address through the configured SMTP settings. */
    @Transactional
    public String emailReceipt(Long id, User actor, String ip) {
        AcadReceipt r = receipts.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Receipt", id));
        String to = r.getStudent().getEmail();
        if (to == null || to.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This student has no email address on record. Add one on the student profile first.");
        }
        Map<String, Object> model = new HashMap<>();
        ReceiptDto d = toDto(r);
        model.put("receipt", d);
        model.put("name", d.studentName());
        model.put("paymentDateText", d.paymentDate() == null ? "—" : d.paymentDate().format(DMY));
        // The signed receipt (same as the on-screen one, with Vaibhav S. Pawar's signature) travels as a PDF.
        AcadReceiptPdfService.ReceiptFile file = receiptPdf.getObject().render(d);
        EmailDelivery delivery = emailService.sendFeeReceipt(to, model, EmailAttachment.pdf(file.filename(), file.content()));
        if (!delivery.delivered()) {
            auditService.record(actor, "ACAD_RECEIPT_EMAIL_FAILED", "AcadReceipt", id, to + " — " + delivery.reason(), ip);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The receipt could not be emailed. " + delivery.reason());
        }
        r.setEmailedAt(Instant.now());
        receipts.save(r);
        auditService.record(actor, "ACAD_RECEIPT_EMAILED", "AcadReceipt", id, r.getReceiptNo() + " to " + to, ip);
        return "Receipt " + r.getReceiptNo() + " emailed to " + to + ".";
    }

    // ---- listing -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PaymentDto> paymentsFor(AcadStudent s) {
        return payments.findByStudentIdOrderByInstallmentNoAsc(s.getId()).stream().map(p -> toDto(p, receipts.findByPaymentId(p.getId()).orElse(null))).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> report(Long studentId, Long batchId, LocalDate from, LocalDate to, String mode) {
        return payments.report(studentId, batchId, from, to, mode == null || mode.isBlank() ? null : mode).stream()
                .map(p -> toDto(p, receipts.findByPaymentId(p.getId()).orElse(null))).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> recent(int n) {
        return payments.recent(PageRequest.of(0, n)).stream().map(p -> toDto(p, receipts.findByPaymentId(p.getId()).orElse(null))).toList();
    }

    @Transactional(readOnly = true)
    public PaymentDto get(Long id) {
        AcadPayment p = requirePayment(id);
        return toDto(p, receipts.findByPaymentId(id).orElse(null));
    }

    // ---- helpers -----------------------------------------------------------------------------

    AcadStudent requireStudent(Long id) {
        return students.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Student", id));
    }

    AcadPayment requirePayment(Long id) {
        return payments.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Payment", id));
    }

    public PaymentDto toDto(AcadPayment p, AcadReceipt r) {
        AcadStudent s = p.getStudent();
        return new PaymentDto(p.getId(), p.getPaymentNo(), s.getId(), s.getStudentId(), s.getFullName(),
                s.getBatch() == null ? null : s.getBatch().getName(), s.getCourse() == null ? null : s.getCourse().getName(),
                p.getInstallmentNo(), master.installmentLabel(p.getInstallmentNo()), p.getPaymentDate(), p.getAmount(), p.getPaymentMode(),
                p.getReferenceNo(), p.getNotes(), p.getReviewNote(), p.getSource(),
                r == null ? null : r.getId(), r == null ? null : r.getReceiptNo(),
                p.getRecordedBy() == null ? "import" : p.getRecordedBy().getFullName(), p.getCreatedAt());
    }

    public ReceiptDto toDto(AcadReceipt r) {
        AcadStudent s = r.getStudent();
        AcadPayment p = r.getPayment();
        return new ReceiptDto(r.getId(), r.getReceiptNo(), p.getId(), p.getPaymentNo(), s.getId(), r.getStudentCode(), r.getStudentName(),
                s.getMobile(), s.getEmail(), r.getCourseName(), r.getBatchName(), r.getBatchSchedule(),
                r.getTotalFee(), r.getDiscount(), r.getTotalFee().subtract(r.getDiscount()),
                r.getAmountPaid(), r.getTotalPaid(), r.getBalance(), r.getPaymentDate(), r.getPaymentMode(),
                r.getReferenceNo(), p.getInstallmentNo(), r.getAmountInWords(), r.getIssuedAt(),
                r.getIssuedBy() == null ? "import" : r.getIssuedBy().getFullName(), r.getEmailedAt(),
                ACADEMY_NAME, ACADEMY_TAGLINE, ACADEMY_ADDRESS, ACADEMY_EMAIL, ACADEMY_PHONE, SIGNATORY, history(r));
    }

    /**
     * Every installment paid up to and including this receipt's one (a later installment never
     * appears on an earlier receipt). The receipt's own line shows its snapshot, like the rest of it.
     */
    private List<ReceiptPaymentLine> history(AcadReceipt r) {
        AcadPayment own = r.getPayment();
        return payments.findByStudentIdOrderByInstallmentNoAsc(r.getStudent().getId()).stream()
                .filter(x -> x.getInstallmentNo() <= own.getInstallmentNo())
                .map(x -> x.getId().equals(own.getId())
                        ? new ReceiptPaymentLine(x.getInstallmentNo(), x.getPaymentNo(), r.getPaymentDate(), r.getPaymentMode(), r.getAmountPaid(), true)
                        : new ReceiptPaymentLine(x.getInstallmentNo(), x.getPaymentNo(), x.getPaymentDate(), x.getPaymentMode(), x.getAmount(), false))
                .toList();
    }

    Optional<AcadReceipt> receiptFor(Long paymentId) {
        return receipts.findByPaymentId(paymentId);
    }
}
