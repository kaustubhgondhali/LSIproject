package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.dto.AutomationDtos.PendingFeeRow;
import com.lordsai.lsi.automation.dto.AutomationDtos.PendingFees;
import com.lordsai.lsi.automation.entity.AcadCommunicationLog;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadCommunicationLogRepository;
import com.lordsai.lsi.automation.repository.AcadPaymentRepository;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.communication.WhatsAppChat;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.CommunicationChannel;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import com.lordsai.lsi.entity.enums.CommunicationType;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.service.AuditService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automation Admin > Pending Fees: every active academy student who still owes money (fee after
 * any discount, less what was paid), with their contact details, and a one-click fee reminder by
 * email or WhatsApp. Reminders are acad_communication_logs rows (FEE_REMINDER), so they appear in
 * Message History and the list shows when each student was last reminded.
 */
@Service
public class AcadPendingFeesService {

    static final String SUBJECT = "Fee reminder";

    /** The outcome of one reminder; {@code whatsappChat} is the chat the click opens (WhatsApp only). */
    public record ReminderResult(CommunicationChannel channel, String status, String recipient, String message, Long logId,
                                 WhatsAppChat whatsappChat) {
    }

    private final AcadStudentRepository students;
    private final AcadPaymentRepository payments;
    private final AcadRowMapper mapper;
    private final AcadCommunicationLogRepository logs;
    private final AcadCommunicationSender sender;
    private final AuditService auditService;

    public AcadPendingFeesService(AcadStudentRepository students, AcadPaymentRepository payments, AcadRowMapper mapper,
                                  AcadCommunicationLogRepository logs, AcadCommunicationSender sender, AuditService auditService) {
        this.students = students;
        this.payments = payments;
        this.mapper = mapper;
        this.logs = logs;
        this.sender = sender;
        this.auditService = auditService;
    }

    /**
     * @param paymentStatus PENDING (nothing paid yet) or PARTIAL, blank for both
     * @param sort          balance (highest first, the default) | student | name | lastPayment (longest without paying first)
     */
    @Transactional(readOnly = true)
    public PendingFees list(Long batchId, Long courseId, String q, String paymentStatus, String sort) {
        String term = q == null || q.isBlank() ? null : AcadStudentService.normalizeName(q);
        List<AcadStudent> active = students.search(term, batchId, courseId, AcadStudent.STATUS_ACTIVE, null,
                PageRequest.of(0, 5000, Sort.by("studentId"))).getContent();
        Map<Long, AcadRowMapper.Fees> fees = mapper.feesByStudent();
        Map<Long, Instant> reminded = lastReminders();
        List<PendingFeeRow> rows = new ArrayList<>();
        for (AcadStudent s : active) {
            AcadRowMapper.Fees f = fees.get(s.getId());
            BigDecimal paid = f == null ? BigDecimal.ZERO : f.paid();
            BigDecimal netFee = s.netFee();
            BigDecimal balance = netFee.subtract(paid);
            if (balance.signum() <= 0) {
                continue;
            }
            String status = AcadRowMapper.paymentStatus(netFee, paid);
            if (paymentStatus != null && !paymentStatus.isBlank() && !paymentStatus.equalsIgnoreCase(status)) {
                continue;
            }
            rows.add(new PendingFeeRow(s.getId(), s.getStudentId(), s.getFullName(), s.getMobile(), s.getEmail(), s.getAddress(),
                    s.getBatch() == null ? null : s.getBatch().getName(), s.getCourse() == null ? null : s.getCourse().getName(),
                    s.getCourseFee(), s.getDiscountAmount(), netFee, paid, balance, status,
                    f == null ? 0 : f.payments(), f == null ? null : f.lastPayment(), reminded.get(s.getId())));
        }
        rows.sort(order(sort));
        BigDecimal total = rows.stream().map(PendingFeeRow::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PendingFees(rows, rows.size(), total);
    }

    private static Comparator<PendingFeeRow> order(String sort) {
        Comparator<PendingFeeRow> byId = Comparator.comparing(PendingFeeRow::studentId);
        if ("student".equalsIgnoreCase(sort)) {
            return byId;
        }
        if ("name".equalsIgnoreCase(sort)) {
            return Comparator.comparing((PendingFeeRow r) -> r.fullName().toUpperCase()).thenComparing(byId);
        }
        if ("lastPayment".equalsIgnoreCase(sort)) {
            return Comparator.comparing(PendingFeeRow::lastPaymentDate, Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
                    .thenComparing(byId);
        }
        return Comparator.comparing(PendingFeeRow::balance).reversed().thenComparing(byId);
    }

    private Map<Long, Instant> lastReminders() {
        Map<Long, Instant> out = new HashMap<>();
        for (Object[] r : logs.lastByType(CommunicationType.FEE_REMINDER)) {
            out.put((Long) r[0], (Instant) r[1]);
        }
        return out;
    }

    /**
     * One fee reminder to one student. Email is sent by the server now; WhatsApp is recorded as
     * opened and the click opens the student's chat with the reminder typed (the office presses Send).
     */
    @Transactional
    public ReminderResult remind(Long studentId, CommunicationChannel channel, User actor, String ip) {
        if (channel == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose Email or WhatsApp.");
        }
        AcadStudent s = students.findById(studentId).orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));
        BigDecimal paid = payments.totalPaidByStudent(s.getId());
        BigDecimal netFee = s.netFee();
        BigDecimal balance = netFee.subtract(paid);
        if (balance.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, s.getFullName() + " has no pending fee.");
        }
        String pending = "₹" + AcadReceiptPdfService.indian(balance) + " of your course fee"
                + (s.getCourse() == null ? "" : " for " + s.getCourse().getName()) + " is pending (fee ₹"
                + AcadReceiptPdfService.indian(netFee) + ", paid so far ₹" + AcadReceiptPdfService.indian(paid) + ").";
        String request = "Please pay at the academy office at your earliest convenience. If you have already paid, kindly ignore this message.";
        String signOff = AcadWhatsAppChats.SIGN_OFF + "\n" + AcadPaymentService.ACADEMY_PHONE;

        ReminderResult result;
        if (channel == CommunicationChannel.EMAIL) {
            String to = s.getEmail();
            if (to == null || to.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, s.getFullName() + " has no email address. Add one with Edit on the student's page.");
            }
            // The email layout already opens with "Dear <name>,".
            AcadCommunicationLog row = logs.saveAndFlush(newRow(s, CommunicationChannel.EMAIL, to.trim(),
                    "This is a gentle reminder that " + pending + "\n\n" + request + "\n\n" + signOff, actor));
            AcadCommunicationLog sent = sender.sendOne(row.getId());
            result = sent.getStatus() == CommunicationStatus.SENT
                    ? new ReminderResult(channel, "SENT", sent.getRecipient(), "Reminder emailed to " + sent.getRecipient() + ".", sent.getId(), null)
                    : new ReminderResult(channel, "FAILED", sent.getRecipient(),
                    sent.getErrorReason() == null ? "The email could not be sent." : sent.getErrorReason(), sent.getId(), null);
        } else {
            String text = "Dear " + s.getFullName() + ",\nThis is a gentle reminder that " + pending + "\n" + request + "\n\n" + signOff;
            WhatsAppChat chat = WhatsAppChat.of(s.getMobile(), text);
            if (chat == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "WhatsApp cannot be used for " + s.getFullName() + " — the mobile number '"
                        + (s.getMobile() == null ? "" : s.getMobile()) + "' is missing or not valid.");
            }
            AcadCommunicationLog row = newRow(s, CommunicationChannel.WHATSAPP, s.getMobile().trim(), text, actor);
            row.setStatus(CommunicationStatus.OPENED);
            row.setProvider(WhatsAppChat.PROVIDER);
            row.setAttempts(1);
            row.setLastAttemptAt(Instant.now());
            row = logs.saveAndFlush(row);
            result = new ReminderResult(channel, "OPENED", row.getRecipient(),
                    "WhatsApp opened on " + row.getRecipient() + " with the reminder — press Send there.", row.getId(), chat);
        }
        auditService.record(actor, "ACAD_FEE_REMINDER", "AcadStudent", s.getId(), s.getStudentId() + ": fee reminder by " + channel
                + " (balance ₹" + AcadReceiptPdfService.indian(balance) + ") — " + result.status(), ip);
        return result;
    }

    private static AcadCommunicationLog newRow(AcadStudent s, CommunicationChannel channel, String to, String body, User actor) {
        AcadCommunicationLog row = new AcadCommunicationLog();
        row.setChannel(channel);
        row.setMessageType(CommunicationType.FEE_REMINDER);
        row.setStudent(s);
        row.setStudentCode(s.getStudentId());
        row.setStudentName(s.getFullName());
        row.setRecipient(to);
        row.setSubject(SUBJECT);
        row.setBody(body);
        row.setStatus(CommunicationStatus.PENDING);
        row.setBatchRef("REMIND-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase(java.util.Locale.ROOT));
        row.setSentBy(actor);
        return row;
    }
}
