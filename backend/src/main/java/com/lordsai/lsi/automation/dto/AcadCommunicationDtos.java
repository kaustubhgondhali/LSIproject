package com.lordsai.lsi.automation.dto;

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

    /** Returned as soon as the messages are queued; progress is polled by batch reference. */
    public record SendResult(String batchRef, int recipients, int queued, int emailQueued, int whatsappQueued,
                             int skipped, String message) {
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
            String sentBy
    ) {
    }

    public record ChannelStatus(boolean emailConfigured, String emailSource, String emailMessage,
                                boolean whatsappConfigured, String whatsappProvider, String whatsappMessage,
                                boolean whatsappSupportsDocuments) {
    }

    public record AttachmentUploaded(String path, String name, long sizeBytes) {
    }
}
