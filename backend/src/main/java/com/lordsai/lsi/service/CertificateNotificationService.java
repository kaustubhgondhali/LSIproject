package com.lordsai.lsi.service;

import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.entity.CommunicationLog;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.CommunicationChannel;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import com.lordsai.lsi.entity.enums.CommunicationType;
import com.lordsai.lsi.entity.enums.ProductType;
import com.lordsai.lsi.repository.CommunicationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Certificate emails (approved / rejected) sent through the existing centralized {@link EmailService}
 * and recorded in communication_logs like every other student communication. Best effort: a failed
 * email is logged and recorded, never thrown, so it can never undo an approval.
 */
@Service
public class CertificateNotificationService {

    private static final Logger log = LoggerFactory.getLogger(CertificateNotificationService.class);

    private final EmailService emailService;
    private final CommunicationLogRepository logRepository;

    public CertificateNotificationService(EmailService emailService, CommunicationLogRepository logRepository) {
        this.emailService = emailService;
        this.logRepository = logRepository;
    }

    public void certificateAvailable(User student, String studentCode, String courseName, String certificateNumber, Long courseId) {
        send(student, studentCode, "Your Certificate of Completion is Ready — " + courseName,
                "Congratulations! Your certificate request for \"" + courseName + "\" has been approved.\n\n"
                        + "Certificate No.: " + certificateNumber + "\n"
                        + "Student ID: " + code(studentCode) + "\n\n"
                        + "Log in to your Student Portal and open Certificate to view or download your "
                        + "Certificate of Completion.",
                courseId);
    }

    public void requestRejected(User student, String studentCode, String courseName, String reason, Long courseId) {
        send(student, studentCode, "Certificate Request Update — " + courseName,
                "Your certificate request for \"" + courseName + "\" could not be approved at this time."
                        + (reason == null || reason.isBlank() ? "" : "\n\nReason from the academy: " + reason)
                        + "\n\nYou can see the status under Certificate in your Student Portal and apply again, "
                        + "or contact the academy for assistance.",
                courseId);
    }

    /** Joins the caller's transaction, like the purchase emails; the log write is best effort. */
    @Transactional
    public void send(User student, String studentCode, String subject, String message, Long courseId) {
        EmailDelivery delivery;
        try {
            delivery = emailService.sendMessage(student.getEmail(), student.getFullName(), subject, message, null);
        } catch (RuntimeException e) {
            delivery = EmailDelivery.failed(e.getMessage() == null ? "Email could not be sent." : e.getMessage());
        }
        if (delivery.delivered()) {
            log.info("[CERTIFICATE EMAIL] '{}' sent to {}", subject, student.getEmail());
        } else {
            log.warn("[CERTIFICATE EMAIL] '{}' NOT delivered to {} — {}: {}", subject, student.getEmail(), delivery.status(), delivery.reason());
        }
        try {
            CommunicationLog row = new CommunicationLog();
            row.setChannel(CommunicationChannel.EMAIL);
            row.setMessageType(CommunicationType.CERTIFICATE_NOTIFICATION);
            row.setStudent(student);
            row.setStudentCode(studentCode);
            row.setRecipient(student.getEmail());
            row.setSubject(subject);
            row.setBody(message);
            if (courseId != null) {
                row.setProductType(ProductType.COURSE);
                row.setProductId(courseId);
            }
            row.setAttempts(1);
            row.setLastAttemptAt(Instant.now());
            row.setProvider(delivery.provider() != null ? delivery.provider() : (delivery.delivered() ? "SMTP" : "NONE"));
            if (delivery.delivered()) {
                row.setStatus(CommunicationStatus.SENT);
                row.setSentAt(Instant.now());
            } else {
                row.setStatus(CommunicationStatus.FAILED);
                row.setErrorReason(delivery.reason());
            }
            logRepository.save(row);
        } catch (RuntimeException e) {
            log.warn("[CERTIFICATE EMAIL] Could not record communication log: {}", e.getMessage());
        }
    }

    private static String code(String studentCode) {
        return studentCode == null ? "—" : studentCode;
    }
}
