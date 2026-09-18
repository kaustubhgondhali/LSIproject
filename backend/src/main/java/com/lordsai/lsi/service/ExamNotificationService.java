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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Exam workflow emails (application received, scheduled / rescheduled, result, certificate) sent
 * through the existing centralized {@link EmailService} and recorded in communication_logs like
 * every other student communication. Delivery is best effort: a failure is logged and recorded,
 * never thrown, so email can never block exam access.
 */
@Service
public class ExamNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ExamNotificationService.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    private final EmailService emailService;
    private final CommunicationLogRepository logRepository;

    public ExamNotificationService(EmailService emailService, CommunicationLogRepository logRepository) {
        this.emailService = emailService;
        this.logRepository = logRepository;
    }

    public void applicationReceived(User student, String studentCode, String courseName) {
        send(student, studentCode, "Exam Application Received — " + courseName,
                "We have received your application for the final examination of \"" + courseName + "\".\n\n"
                        + "Student ID: " + code(studentCode) + "\n"
                        + "Status: Under review\n\n"
                        + "The academy will review your application and schedule your exam. You will be notified "
                        + "by email and the schedule will appear under My Exams in your Student Portal.",
                null);
    }

    public void applicationApproved(User student, String studentCode, String courseName) {
        send(student, studentCode, "Exam Application Approved — " + courseName,
                "Your application for the final examination of \"" + courseName + "\" has been approved.\n\n"
                        + "Your exam date and time will be scheduled by the academy shortly. Watch My Exams in "
                        + "your Student Portal for the schedule.",
                null);
    }

    public void applicationRejected(User student, String studentCode, String courseName, String remarks) {
        send(student, studentCode, "Exam Application Update — " + courseName,
                "Your application for the final examination of \"" + courseName + "\" could not be approved at this time."
                        + (remarks == null || remarks.isBlank() ? "" : "\n\nRemarks from the academy: " + remarks)
                        + "\n\nPlease contact the academy for further assistance.",
                null);
    }

    public void examScheduled(User student, String studentCode, String courseName, String examTitle,
                              Instant startsAt, Instant endsAt, boolean rescheduled, Long courseId) {
        send(student, studentCode, (rescheduled ? "Exam Rescheduled — " : "Exam Scheduled — ") + courseName,
                "Your final examination has been " + (rescheduled ? "rescheduled" : "scheduled") + ".\n\n"
                        + "Course: " + courseName + "\n"
                        + "Exam: " + examTitle + "\n"
                        + "Window opens: " + when(startsAt) + "\n"
                        + "Window closes: " + when(endsAt) + "\n\n"
                        + "Log in to your Student Portal and open My Exams during this window to start the exam. "
                        + "The exam cannot be started before the window opens or after it closes.",
                courseId);
    }

    public void examCancelled(User student, String studentCode, String courseName, String examTitle, Long courseId) {
        send(student, studentCode, "Exam Schedule Cancelled — " + courseName,
                "The scheduled window for \"" + examTitle + "\" (" + courseName + ") has been cancelled by the academy. "
                        + "A new schedule will be communicated to you.",
                courseId);
    }

    public void examResult(User student, String studentCode, String courseName, String examTitle, int attemptNumber,
                           int score, int totalMarks, int passingMarks, boolean passed, int attemptsRemaining,
                           String certificateNumber, Long courseId) {
        StringBuilder body = new StringBuilder();
        body.append("Your exam has been evaluated.\n\n")
                .append("Course: ").append(courseName).append('\n')
                .append("Exam: ").append(examTitle).append('\n')
                .append("Attempt: ").append(attemptNumber).append('\n')
                .append("Score: ").append(score).append(" / ").append(totalMarks).append('\n')
                .append("Passing marks: ").append(passingMarks).append('\n')
                .append("Result: ").append(passed ? "PASS" : "FAIL").append("\n\n");
        if (passed) {
            body.append("Congratulations! You have cleared the examination. Your course completion certificate");
            if (certificateNumber != null) {
                body.append(" (").append(certificateNumber).append(')');
            }
            body.append(" is now available under Certificates in your Student Portal.");
        } else if (attemptsRemaining > 0) {
            body.append("You have ").append(attemptsRemaining).append(" attempt(s) remaining. You may attempt the exam again "
                    + "from My Exams in your Student Portal while your exam window is open.");
        } else {
            body.append("You have used all allowed attempts for this exam. Please contact the academy for guidance.");
        }
        send(student, studentCode, (passed ? "Exam Result: PASS — " : "Exam Result — ") + courseName, body.toString(), courseId);
    }

    // ---- internals -------------------------------------------------------------------------

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
            log.info("[EXAM EMAIL] '{}' sent to {}", subject, student.getEmail());
        } else {
            log.warn("[EXAM EMAIL] '{}' NOT delivered to {} — {}: {}", subject, student.getEmail(), delivery.status(), delivery.reason());
        }
        try {
            CommunicationLog row = new CommunicationLog();
            row.setChannel(CommunicationChannel.EMAIL);
            row.setMessageType(CommunicationType.EXAM_NOTIFICATION);
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
            log.warn("[EXAM EMAIL] Could not record communication log: {}", e.getMessage());
        }
    }

    private static String when(Instant instant) {
        return DATE_TIME.format(instant.atZone(INDIA)) + " IST";
    }

    private static String code(String studentCode) {
        return studentCode == null ? "—" : studentCode;
    }
}
