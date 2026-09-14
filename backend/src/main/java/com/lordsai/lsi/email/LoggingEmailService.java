package com.lordsai.lsi.email;

import com.lordsai.lsi.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Active when lsi.mail.enabled=false (the default until SMTP is configured).
 * Writes the email to the log instead of sending it, so flows can be exercised
 * locally without an SMTP account.
 */
@Service
@ConditionalOnProperty(prefix = "lsi.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void sendEnrollmentEmail(User student, String studentId, String courseName,
                                    String orderRef, String amountDisplay, String setupLink) {
        log.info("[EMAIL:ENROLLMENT] to={} studentId={} course='{}' order={} amount={} setupLink={}",
                student.getEmail(), studentId, courseName, orderRef, amountDisplay, setupLink);
    }

    @Override
    public void sendWelcomeEmail(User user, String setupLink) {
        log.info("[EMAIL:WELCOME] to={} setupLink={}", user.getEmail(), setupLink);
    }

    @Override
    public void sendPasswordResetEmail(User user, String resetLink) {
        log.info("[EMAIL:PASSWORD_RESET] to={} resetLink={}", user.getEmail(), resetLink);
    }

    @Override
    public void sendReviewReceived(String toEmail, String name) {
        log.info("[EMAIL:REVIEW_RECEIVED] to={} name='{}'", toEmail, name);
    }

    @Override
    public void sendReviewApproved(String toEmail, String name) {
        log.info("[EMAIL:REVIEW_APPROVED] to={} name='{}'", toEmail, name);
    }

    @Override
    public void sendPaymentConfirmation(String toEmail, String customerName, String courseName,
                                        String orderRef, String amountDisplay) {
        log.info("[EMAIL:PAYMENT_CONFIRMATION] to={} name='{}' course='{}' order={} amount={}",
                toEmail, customerName, courseName, orderRef, amountDisplay);
    }
}
