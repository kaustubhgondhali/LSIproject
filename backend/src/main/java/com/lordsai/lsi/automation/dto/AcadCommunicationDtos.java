package com.lordsai.lsi.automation.dto;

import com.lordsai.lsi.communication.WhatsAppChat;
import com.lordsai.lsi.entity.enums.CommunicationChannel;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import com.lordsai.lsi.entity.enums.CommunicationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Email & WhatsApp automation of the Automation Admin. Recipients are always the academy's own
 * students (acad_students); nothing here refers to website / LMS accounts.
 */
public final class AcadCommunicationDtos {

    private AcadCommunicationDtos() {
    }

    /** Which academy students to reach. Every field is optional; an empty filter means "all active students". */
    public record RecipientFilter(
            Long batchId,
            Long courseId,
            /** ACTIVE (default) | ARCHIVED | ALL */
            String status,
            /** Free text: name, Student ID, email, mobile, batch. */
            String q
    ) {
        public static RecipientFilter none() {
            return new RecipientFilter(null, null, null, null);
        }
    }

    /** An academy student as shown on the automation screen, with how to reach them. */
    public record Recipient(
            Long id,
            String studentId,
            String fullName,
            String email,
            String mobile,
            String batch,
            String course,
            String status
    ) {
    }

    public record SendRequest(
            /** Explicit selection of acad student ids; when empty the filter decides. */
            List<Long> studentIds,
            RecipientFilter filter,
            @NotEmpty Set<CommunicationChannel> channels,
            @Size(max = 200) String subject,
            @NotBlank @Size(max = 5000) String message,
            /** attachments/<file> returned by the upload endpoint. */
            @Size(max = 255) String attachmentPath,
            @Size(max = 255) String attachmentName
    ) {
    }

    /** What the confirmation dialog shows before anything is sent. */
    public record SendPreview(int recipients, int emailRecipients, int whatsappRecipients,
                              boolean whatsappConfigured, String whatsappMessage, List<Recipient> sample) {
    }

    /**
     * Returned as soon as the messages are queued; progress is polled by batch reference.
     * {@code whatsappChats}: the WhatsApp chats prepared for the office to open (READY rows).
     */
    public record SendResult(String batchRef, int recipients, int queued, int emailQueued, int whatsappQueued,
                             int skipped, String message, List<WhatsAppChatTarget> whatsappChats) {
    }

    /** One prepared WhatsApp chat: open it on the office computer, the message is already typed. */
    public record WhatsAppChatTarget(Long logId, Long studentId, String studentCode, String studentName,
                                     String mobile, String phone, String text) {
    }

    /** The receipt's WhatsApp message, shown (and opened) before the office clicks Send. */
    public record WhatsAppChatPreview(boolean available, String mobile, String phone, String text, String reason) {
    }

    public record BatchProgress(String batchRef, long total, long pending, long sent, long failed) {
    }

    public record CommunicationLogResponse(
            Long id,
            Instant createdAt,
            CommunicationChannel channel,
            CommunicationType messageType,
            Long studentId,
            String studentCode,
            String studentName,
            String recipient,
            String subject,
            String body,
            CommunicationStatus status,
            String provider,
            String providerMessageId,
            String errorReason,
            String attachmentName,
            String batchRef,
            int attempts,
            Instant lastAttemptAt,
            Instant sentAt,
            String sentBy,
            /** The receipt of a FEE_RECEIPT row (null when its payment was deleted): its picture is pasted into WhatsApp. */
            Long receiptId,
            /** Set when a WhatsApp chat is ready to open (after "Open in WhatsApp" from the history). */
            WhatsAppChat whatsappChat
    ) {
    }

    public record ChannelStatus(boolean emailConfigured, String emailSource, String emailMessage,
                                boolean whatsappConfigured, String whatsappProvider, String whatsappMessage,
                                boolean whatsappSupportsDocuments) {
    }

    public record AttachmentUploaded(String path, String name, long sizeBytes) {
    }

    // ---- one-click payment receipt sharing (WhatsApp + Email) --------------------------------------

    /** Per-channel outcome of a receipt share. Never SENT unless the provider accepted the message. */
    public enum ShareStatus {
        SENT,
        /** WhatsApp: the student's chat was opened on the office computer with the receipt message; the office presses Send. */
        OPENED,
        FAILED,
        /** The channel (SMTP / WhatsApp provider) is not set up; nothing could be sent. */
        NOT_CONFIGURED,
        /** The student has no usable email address / mobile number; nothing was attempted. */
        MISSING_RECIPIENT
    }

    /**
     * @param recipient the student's address the receipt went to (null when missing)
     * @param logId     the acad_communication_logs row (null when nothing was attempted)
     */
    public record ShareChannelResult(CommunicationChannel channel, ShareStatus status, String recipient, String message, Long logId) {
    }

    public record ReceiptShareResult(Long receiptId, String receiptNo, String paymentNo, int installmentNo,
                                     java.math.BigDecimal amountPaid, java.time.LocalDate paymentDate,
                                     Long studentId, String studentCode, String studentName,
                                     ShareChannelResult whatsapp, ShareChannelResult email, boolean allSent, String summary,
                                     WhatsAppChat whatsappChat) {
    }
}
