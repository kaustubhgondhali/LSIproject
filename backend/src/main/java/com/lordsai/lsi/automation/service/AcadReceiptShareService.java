package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.ReceiptShareResult;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.ShareChannelResult;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.ShareStatus;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.WhatsAppChatPreview;
import com.lordsai.lsi.automation.dto.AutomationDtos.ReceiptDto;
import com.lordsai.lsi.automation.entity.AcadCommunicationLog;
import com.lordsai.lsi.automation.entity.AcadReceipt;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadCommunicationLogRepository;
import com.lordsai.lsi.automation.repository.AcadReceiptRepository;
import com.lordsai.lsi.communication.WhatsAppChat;
import com.lordsai.lsi.email.MailSenderResolver;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.CommunicationChannel;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import com.lordsai.lsi.entity.enums.CommunicationType;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automation Admin > Email & WhatsApp: shares ONE selected payment receipt with its student in a
 * single click. Email is sent by the server with the receipt PDF attached, as always. WhatsApp
 * uses WhatsApp's own "click to chat" (no WhatsApp Business API), which can only type text: the
 * click copies the signed receipt picture ({@link #picture}) and opens the student's chat on the
 * office computer with one short line typed; the office pastes the picture (Ctrl + V) and presses
 * Send. The addresses always come from the receipt's own student; each channel is an
 * acad_communication_logs row (Message History), with its own honest outcome.
 */
@Service
public class AcadReceiptShareService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SIGN_OFF = AcadWhatsAppChats.SIGN_OFF;
    static final String NO_MOBILE = "WhatsApp cannot be sent — mobile number is missing.";

    private final AcadReceiptRepository receipts;
    private final AcadCommunicationLogRepository logRepository;
    private final AcadCommunicationSender sender;
    private final AcadPaymentService paymentService;
    private final AcadReceiptPdfService receiptPdf;
    private final MailSenderResolver mailResolver;
    private final AcadWhatsAppChats chats;
    private final AuditService auditService;

    /** Receipts currently being sent: a second simultaneous click on the same receipt is refused. */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public AcadReceiptShareService(AcadReceiptRepository receipts, AcadCommunicationLogRepository logRepository,
                                   AcadCommunicationSender sender, AcadPaymentService paymentService,
                                   AcadReceiptPdfService receiptPdf, MailSenderResolver mailResolver,
                                   AcadWhatsAppChats chats, AuditService auditService) {
        this.receipts = receipts;
        this.logRepository = logRepository;
        this.sender = sender;
        this.paymentService = paymentService;
        this.receiptPdf = receiptPdf;
        this.mailResolver = mailResolver;
        this.chats = chats;
        this.auditService = auditService;
    }

    /** The PDF exactly as it will be sent (for the admin's preview). */
    @Transactional(readOnly = true)
    public AcadReceiptPdfService.ReceiptFile pdf(Long receiptId) {
        return receiptPdf.render(require(receiptId));
    }

    /** The same receipt as a picture (PNG), which the Send click copies for pasting into the WhatsApp chat. */
    @Transactional(readOnly = true)
    public AcadReceiptPdfService.ReceiptFile picture(Long receiptId) {
        return receiptPdf.picture(require(receiptId));
    }

    /** The receipt's WhatsApp line — shown before sending, and opened by the Send click itself. */
    @Transactional(readOnly = true)
    public WhatsAppChatPreview whatsappPreview(Long receiptId) {
        AcadReceipt receipt = require(receiptId);
        String mobile = receipt.getStudent().getMobile();
        if (mobile == null || mobile.isBlank()) {
            return new WhatsAppChatPreview(false, null, null, null, NO_MOBILE);
        }
        WhatsAppChat chat = chats.receipt(receipt);
        if (chat == null) {
            return new WhatsAppChatPreview(false, mobile.trim(), null, null, invalidMobile(mobile));
        }
        return new WhatsAppChatPreview(true, mobile.trim(), chat.phone(), chat.text(), null);
    }

    @Transactional
    public ReceiptShareResult share(Long receiptId, User actor, String ip) {
        if (!inFlight.add(receiptId)) {
            throw new ApiException(HttpStatus.CONFLICT, "This receipt is already being sent. Please wait for the result.");
        }
        try {
            return doShare(require(receiptId), actor, ip);
        } finally {
            inFlight.remove(receiptId);
        }
    }

    private ReceiptShareResult doShare(AcadReceipt receipt, User actor, String ip) {
        AcadStudent student = receipt.getStudent();
        ReceiptDto r = paymentService.toDto(receipt);
        String subject = "Payment Receipt - " + student.getFullName();
        String details = "Receipt No.: " + r.receiptNo()
                + "\nPayment No.: " + r.paymentNo()
                + "\nInstallment: " + AcadReceiptPdfService.ordinal(r.installmentNo()) + " Installment"
                + "\nAmount Paid: ₹" + AcadReceiptPdfService.indian(r.amountPaid())
                + (r.paymentDate() == null ? "" : "\nPayment Date: " + r.paymentDate().format(DMY));
        // The email layout already opens with "Dear <name>,".
        String emailBody = "Please find your payment receipt attached.\n\n" + details + "\n\n" + SIGN_OFF;
        String batchRef = "RCPT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
        String filename = AcadReceiptPdfService.fileName(r);

        WhatsAppChat chat = chats.receipt(receipt);
        ShareChannelResult wa = recordWhatsAppOpened(student, receipt, subject, chat, AcadReceiptPdfService.pictureName(r), batchRef, actor);
        ShareChannelResult email = deliver(CommunicationChannel.EMAIL,
                !MailSenderResolver.SOURCE_NONE.equals(mailResolver.currentSource()), student, receipt, subject,
                emailBody, filename, batchRef, actor);

        boolean allSent = wa.status() == ShareStatus.OPENED && email.status() == ShareStatus.SENT;
        String summary = "WhatsApp: " + label(wa.status()) + " · Email: " + label(email.status());
        auditService.record(actor, "AUTOMATION_RECEIPT_SHARED", "AcadReceipt", receipt.getId(),
                r.receiptNo() + " / " + r.paymentNo() + " for " + student.getStudentId() + " — WhatsApp: " + wa.status()
                        + (wa.status() == ShareStatus.SENT ? "" : " (" + wa.message() + ")")
                        + ", Email: " + email.status() + (email.status() == ShareStatus.SENT ? "" : " (" + email.message() + ")"), ip);
        return new ReceiptShareResult(receipt.getId(), r.receiptNo(), r.paymentNo(), r.installmentNo(), r.amountPaid(), r.paymentDate(),
                student.getId(), student.getStudentId(), student.getFullName(), wa, email, allSent, summary,
                wa.status() == ShareStatus.OPENED ? chat : null);
    }

    /**
     * WhatsApp needs no server-side sending: the Send click copied the receipt picture and opened the
     * student's chat on the office computer. The row records exactly that (OPENED, never SENT), with
     * the picture's file name as its attachment.
     */
    private ShareChannelResult recordWhatsAppOpened(AcadStudent student, AcadReceipt receipt, String subject, WhatsAppChat chat,
                                                    String pictureName, String batchRef, User actor) {
        String mobile = student.getMobile();
        if (mobile == null || mobile.isBlank()) {
            return new ShareChannelResult(CommunicationChannel.WHATSAPP, ShareStatus.MISSING_RECIPIENT, null, NO_MOBILE, null);
        }
        if (chat == null) {
            return new ShareChannelResult(CommunicationChannel.WHATSAPP, ShareStatus.MISSING_RECIPIENT, mobile, invalidMobile(mobile), null);
        }
        AcadCommunicationLog row = newRow(CommunicationChannel.WHATSAPP, student, receipt, mobile.trim(), subject, chat.text(),
                pictureName, batchRef, actor);
        row.setStatus(CommunicationStatus.OPENED);
        row.setProvider(WhatsAppChat.PROVIDER);
        row.setAttempts(1);
        row.setLastAttemptAt(Instant.now());
        row = logRepository.saveAndFlush(row);
        return new ShareChannelResult(CommunicationChannel.WHATSAPP, ShareStatus.OPENED, row.getRecipient(),
                "WhatsApp opened on " + row.getRecipient() + " — press Ctrl + V to paste the receipt picture, then Send.", row.getId());
    }

    private static String invalidMobile(String mobile) {
        return "WhatsApp cannot be sent — mobile number '" + mobile.trim() + "' is not valid.";
    }

    /** Email: sent by the server with the receipt PDF attached (unchanged). */
    private ShareChannelResult deliver(CommunicationChannel channel, boolean configured, AcadStudent student, AcadReceipt receipt,
                                       String subject, String body, String filename, String batchRef, User actor) {
        String address = student.getEmail();
        if (address == null || address.isBlank()) {
            return new ShareChannelResult(channel, ShareStatus.MISSING_RECIPIENT, null, "Email cannot be sent — email address is missing.", null);
        }
        AcadCommunicationLog row = newRow(channel, student, receipt, address.trim(), subject, body, filename, batchRef, actor);
        row = logRepository.saveAndFlush(row);

        AcadCommunicationLog result = sender.sendOne(row.getId());
        if (result.getStatus() == CommunicationStatus.SENT) {
            return new ShareChannelResult(channel, ShareStatus.SENT, result.getRecipient(), "Sent to " + result.getRecipient() + ".", result.getId());
        }
        String reason = result.getErrorReason() == null ? "The message could not be sent." : result.getErrorReason();
        return new ShareChannelResult(channel, configured ? ShareStatus.FAILED : ShareStatus.NOT_CONFIGURED,
                result.getRecipient(), reason, result.getId());
    }

    private static AcadCommunicationLog newRow(CommunicationChannel channel, AcadStudent student, AcadReceipt receipt, String address,
                                               String subject, String body, String filename, String batchRef, User actor) {
        AcadCommunicationLog row = new AcadCommunicationLog();
        row.setChannel(channel);
        row.setMessageType(CommunicationType.FEE_RECEIPT);
        row.setStudent(student);
        row.setStudentCode(student.getStudentId());
        row.setStudentName(student.getFullName());
        row.setRecipient(address);
        row.setSubject(subject);
        row.setBody(body);
        row.setStatus(CommunicationStatus.PENDING);
        row.setAttachmentName(filename);
        row.setReceipt(receipt);
        row.setBatchRef(batchRef);
        row.setSentBy(actor);
        return row;
    }

    private static String label(ShareStatus s) {
        return switch (s) {
            case SENT -> "Sent";
            case OPENED -> "Opened (press Send)";
            case FAILED -> "Failed";
            case NOT_CONFIGURED -> "Not configured";
            case MISSING_RECIPIENT -> "Not sent (no address)";
        };
    }

    private AcadReceipt require(Long receiptId) {
        return receipts.findById(receiptId).orElseThrow(() -> ResourceNotFoundException.of("Receipt", receiptId));
    }
}
