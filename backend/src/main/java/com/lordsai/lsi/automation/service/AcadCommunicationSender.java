package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.entity.AcadCommunicationLog;
import com.lordsai.lsi.automation.repository.AcadCommunicationLogRepository;
import com.lordsai.lsi.communication.WhatsAppDelivery;
import com.lordsai.lsi.communication.WhatsAppMessageService;
import com.lordsai.lsi.email.EmailAttachment;
import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.entity.enums.CommunicationChannel;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.service.AuditService;
import com.lordsai.lsi.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Delivers ONE queued acad_communication_logs row over its channel and records the outcome.
 * Email and WhatsApp are only the delivery mechanism (the configurable SMTP / WhatsApp
 * provider); the recipient always comes from the Automation Admin's own student record.
 * Provider failures are written to the row so one bad recipient never affects the rest.
 */
@Service
public class AcadCommunicationSender {

    private static final Logger log = LoggerFactory.getLogger(AcadCommunicationSender.class);

    private final AcadCommunicationLogRepository logRepository;
    private final EmailService emailService;
    private final WhatsAppMessageService whatsApp;
    private final FileStorageService storage;
    private final AuditService auditService;

    public AcadCommunicationSender(AcadCommunicationLogRepository logRepository,
                                   EmailService emailService,
                                   WhatsAppMessageService whatsApp,
                                   FileStorageService storage,
                                   AuditService auditService) {
        this.logRepository = logRepository;
        this.emailService = emailService;
        this.whatsApp = whatsApp;
        this.storage = storage;
        this.auditService = auditService;
    }

    @Transactional
    public AcadCommunicationLog sendOne(Long logId) {
        AcadCommunicationLog row = logRepository.findById(logId)
                .orElseThrow(() -> ResourceNotFoundException.of("Automation message", logId));
        if (row.getStatus() == CommunicationStatus.SENT) {
            return row;
        }
        String recipientName = row.getStudentName() == null ? "Student" : row.getStudentName();
        try {
            if (row.getChannel() == CommunicationChannel.EMAIL) {
                EmailDelivery d = emailService.sendMessage(row.getRecipient(), recipientName, row.getSubject(), row.getBody(), attachment(row));
                applyEmailOutcome(row, d);
            } else {
                WhatsAppDelivery d = whatsApp.sendText(row.getRecipient(), whatsAppText(row));
                if (d.sent() && row.getAttachmentPath() != null && whatsApp.supportsDocuments()) {
                    // Best effort: the text went through; a document failure is recorded but does not fail the row.
                    WhatsAppDelivery doc = whatsApp.sendDocument(row.getRecipient(), null, attachmentName(row),
                            contentTypeOf(row.getAttachmentName()), storage.readBytes(row.getAttachmentPath()));
                    if (!doc.sent()) {
                        row.setErrorReason("Text delivered; attachment not delivered: " + doc.reason());
                    }
                }
                applyWhatsAppOutcome(row, d);
            }
        } catch (RuntimeException e) {
            log.error("[AUTOMATION] Unexpected failure sending message {}: {}", logId, e.getMessage());
            row.setAttempts(row.getAttempts() + 1);
            row.setLastAttemptAt(Instant.now());
            row.setStatus(CommunicationStatus.FAILED);
            row.setErrorReason("Unexpected error: " + e.getClass().getSimpleName());
        }
        row = logRepository.save(row);
        auditService.record(row.getSentBy(), "AUTOMATION_" + (row.getChannel() == CommunicationChannel.EMAIL ? "EMAIL_" : "WHATSAPP_")
                        + (row.getStatus() == CommunicationStatus.SENT ? "SENT" : "FAILED"),
                "AcadCommunicationLog", row.getId(), row.getMessageType() + " to " + row.getRecipient()
                        + (row.getStatus() == CommunicationStatus.SENT ? " via " + row.getProvider() : " — " + row.getErrorReason()), null);
        return row;
    }

    static void applyEmailOutcome(AcadCommunicationLog row, EmailDelivery delivery) {
        row.setAttempts(row.getAttempts() + 1);
        row.setLastAttemptAt(Instant.now());
        row.setProvider(delivery.provider() != null ? delivery.provider() : (delivery.delivered() ? "SMTP" : "NONE"));
        if (delivery.delivered()) {
            row.setStatus(CommunicationStatus.SENT);
            row.setSentAt(Instant.now());
            row.setErrorReason(null);
        } else {
            // DISABLED (no mail channel) is a failure from the office's point of view and is retryable.
            row.setStatus(CommunicationStatus.FAILED);
            row.setErrorReason(delivery.reason());
        }
    }

    static void applyWhatsAppOutcome(AcadCommunicationLog row, WhatsAppDelivery delivery) {
        row.setAttempts(row.getAttempts() + 1);
        row.setLastAttemptAt(Instant.now());
        row.setProvider(delivery.provider());
        if (delivery.sent()) {
            row.setStatus(CommunicationStatus.SENT);
            row.setSentAt(Instant.now());
            row.setProviderMessageId(delivery.providerMessageId());
            row.setErrorReason(null);
        } else {
            row.setStatus(CommunicationStatus.FAILED);
            row.setErrorReason(delivery.reason());
        }
    }

    private EmailAttachment attachment(AcadCommunicationLog row) {
        if (row.getAttachmentPath() == null || !storage.exists(row.getAttachmentPath())) {
            return null;
        }
        return new EmailAttachment(attachmentName(row), contentTypeOf(row.getAttachmentName()), storage.readBytes(row.getAttachmentPath()));
    }

    private static String attachmentName(AcadCommunicationLog row) {
        return row.getAttachmentName() == null ? "attachment" : row.getAttachmentName();
    }

    private static String whatsAppText(AcadCommunicationLog row) {
        String subject = row.getSubject() == null || row.getSubject().isBlank() ? "" : "*" + row.getSubject() + "*\n\n";
        return subject + (row.getBody() == null ? "" : row.getBody()) + "\n\n— Lord Sai Investment & Share Market Academy";
    }

    static String contentTypeOf(String filename) {
        String n = filename == null ? "" : filename.toLowerCase();
        if (n.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".webp")) {
            return "image/webp";
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }
}
