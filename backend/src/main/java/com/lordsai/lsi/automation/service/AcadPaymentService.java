package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.dto.AutomationDtos.FeeSummary;
import com.lordsai.lsi.automation.dto.AutomationDtos.InstallmentSlot;
import com.lordsai.lsi.automation.dto.AutomationDtos.PaymentDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.ReceiptDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.RecordPaymentRequest;
import com.lordsai.lsi.automation.dto.AutomationDtos.UpdatePaymentRequest;
import com.lordsai.lsi.automation.entity.AcadPayment;
import com.lordsai.lsi.automation.entity.AcadReceipt;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadPaymentRepository;
import com.lordsai.lsi.automation.repository.AcadReceiptRepository;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
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
    public static final String ACADEMY_ADDRESS = "Shreeraj Nagar, Shop No. 37, 1st Floor, Near Dr. Gade Hospital, Kamtha Road, Uran, Navi Mumbai - 400702";
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

    public AcadPaymentService(AcadPaymentRepository payments, AcadReceiptRepository receipts, AcadStudentRepository students,
                              AcadMasterService master, AcadSequenceService sequences, AuditService auditService,
                              EmailService emailService, AppProperties properties) {
        this.payments = payments;
        this.receipts = receipts;
        this.students = students;
        this.master = master;
        this.sequences = sequences;
        this.auditService = auditService;
        this.emailService = emailService;
        this.properties = properties;
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
        int maxSlots = Math.max(5, list.stream().mapToInt(AcadPayment::getInstallmentNo).max().orElse(0));
        List<InstallmentSlot> slots = new java.util.ArrayList<>();
        Integer next = null;
        for (int n = 1; n <= maxSlots; n++) {
            AcadPayment p = byNo.get(n);
            slots.add(new InstallmentSlot(n, master.installmentLabel(n), p != null, p == null ? null : p.getAmount(),
                    p == null ? null : p.getPaymentDate(), p == null ? null : p.getPaymentNo()));
            if (next == null && p == null) {
                next = n;
            }
        }
        BigDecimal balance = s.getCourseFee().subtract(paid);
        return new FeeSummary(s.getId(), s.getStudentId(), s.getFullName(),
                s.getBatch() == null ? null : s.getBatch().getName(), s.getCourse() == null ? null : s.getCourse().getName(),
                s.getCourseFee(), paid, balance, AcadRowMapper.paymentStatus(s.getCourseFee(), paid), slots,
                balance.signum() > 0 ? (next == null ? maxSlots + 1 : next) : null);
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
        BigDecimal remaining = s.getCourseFee().subtract(paidOthers);
        if (amount.compareTo(remaining) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount ₹" + amount.toPlainString() + " exceeds the outstanding balance of ₹"
                    + remaining.toPlainString() + " for " + s.getStudentId() + ".");
        }
        if (date != null && date.isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payment date cannot be in the future.");
        }
    }

    // ---- receipts ----------------------------------------------------------------------------

    /** Creates the receipt for a payment (also used by the importer, which passes null for actor). */
    @Transactional
    public AcadReceipt issueReceipt(AcadPayment p, User actor) {
        AcadReceipt r = new AcadReceipt();
        int year = p.getPaymentDate() != null ? p.getPaymentDate().getYear() : LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).getYear();
        r.setReceiptNo(sequences.next(AcadSequenceService.RECEIPT, year));
        r.setPayment(p);
        r.setStudent(p.getStudent());
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
        r.setAmountPaid(p.getAmount());
        r.setTotalPaid(totalPaid);
        r.setBalance(s.getCourseFee().subtract(totalPaid));
        r.setPaymentDate(p.getPaymentDate());
        r.setPaymentMode(p.getPaymentMode());
        r.setReferenceNo(p.getReferenceNo());
        r.setAmountInWords(AmountInWords.rupees(p.getAmount()));
        r.setIssuedAt(Instant.now());
        r.setIssuedBy(actor);
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
        EmailDelivery delivery = emailService.sendFeeReceipt(to, model);
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
                r.getTotalFee(), r.getAmountPaid(), r.getTotalPaid(), r.getBalance(), r.getPaymentDate(), r.getPaymentMode(),
                r.getReferenceNo(), p.getInstallmentNo(), r.getAmountInWords(), r.getIssuedAt(),
                r.getIssuedBy() == null ? "import" : r.getIssuedBy().getFullName(), r.getEmailedAt(),
                ACADEMY_NAME, ACADEMY_TAGLINE, ACADEMY_ADDRESS, ACADEMY_EMAIL, ACADEMY_PHONE, SIGNATORY);
    }

    Optional<AcadReceipt> receiptFor(Long paymentId) {
        return receipts.findByPaymentId(paymentId);
    }
}
