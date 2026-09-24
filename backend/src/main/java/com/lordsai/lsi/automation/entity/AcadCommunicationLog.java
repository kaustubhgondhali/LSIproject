package com.lordsai.lsi.automation.entity;

import com.lordsai.lsi.entity.BaseEntity;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.CommunicationChannel;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import com.lordsai.lsi.entity.enums.CommunicationType;
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
 * Delivery record for an email / WhatsApp message the Automation Admin sent to one of ITS OWN
 * students (acad_students). A row is written before the send attempt and updated with the
 * outcome, so a failure is never lost and can be retried. This log belongs to the Automation
 * Admin only: it never references website / LMS users, purchases or invoices.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "acad_communication_logs", indexes = {
        @Index(name = "idx_acad_comm_logs_status", columnList = "status"),
        @Index(name = "idx_acad_comm_logs_channel", columnList = "channel, status"),
        @Index(name = "idx_acad_comm_logs_student", columnList = "acad_student_id"),
        @Index(name = "idx_acad_comm_logs_created", columnList = "created_at"),
        @Index(name = "idx_acad_comm_logs_batch", columnList = "batch_ref")
})
public class AcadCommunicationLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunicationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 40)
    private CommunicationType messageType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "acad_student_id", nullable = false)
    private AcadStudent student;

    /** Student ID and name as they were when the message was sent, so history stays readable. */
    @Column(name = "student_code", length = 30)
    private String studentCode;

    @Column(name = "student_name", length = 150)
    private String studentName;

    /** Email address or mobile number the message went to. */
    @Column(nullable = false, length = 190)
    private String recipient;

    @Column(length = 255)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunicationStatus status = CommunicationStatus.PENDING;

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

    /** Groups the rows of one bulk send. */
    @Column(name = "batch_ref", length = 40)
    private String batchRef;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by_user_id")
    private User sentBy;
}
