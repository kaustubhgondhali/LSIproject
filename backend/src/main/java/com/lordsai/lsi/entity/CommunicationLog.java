package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.CommunicationChannel;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import com.lordsai.lsi.entity.enums.CommunicationType;
import com.lordsai.lsi.entity.enums.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Delivery record for every outbound student communication (email or WhatsApp): automatic
 * purchase confirmations, invoice resends and admin-initiated individual / bulk messages. A row
 * is written before the send attempt and updated with the outcome, so a failure is never lost and
 * can be retried from the admin panels. Passwords and secrets are never stored here.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "communication_logs", indexes = {
        @Index(name = "idx_comm_logs_status", columnList = "status"),
        @Index(name = "idx_comm_logs_channel", columnList = "channel, status"),
        @Index(name = "idx_comm_logs_student", columnList = "student_user_id"),
        @Index(name = "idx_comm_logs_created", columnList = "created_at"),
        @Index(name = "idx_comm_logs_batch", columnList = "batch_ref"),
        @Index(name = "idx_comm_logs_product", columnList = "product_type, product_id")
})
public class CommunicationLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunicationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 40)
    private CommunicationType messageType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_user_id")
    private User student;

    @Column(name = "student_code", length = 20)
    private String studentCode;

    /** Email address or mobile number the message was addressed to. */
    @Column(nullable = false, length = 190)
    private String recipient;

    @Column(length = 255)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunicationStatus status = CommunicationStatus.PENDING;

    /** Which channel implementation handled it (ADMIN / GOOGLE_OAUTH / ENVIRONMENT SMTP, META_CLOUD, NONE). */
    @Column(length = 40)
    private String provider;

    @Column(name = "provider_message_id", length = 190)
    private String providerMessageId;

    @Column(name = "error_reason", length = 1000)
    private String errorReason;

    @Column(name = "attachment_path", length = 255)
    private String attachmentPath;

    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", length = 20)
    private ProductType productType;

    @Column(name = "product_id")
    private Long productId;

    /** Groups the rows of one bulk send so its progress can be reported. */
    @Column(name = "batch_ref", length = 40)
    private String batchRef;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    /** Null for system-triggered messages (purchase confirmations). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by_user_id")
    private User sentBy;
}
