package com.lordsai.lsi.email;

import com.lordsai.lsi.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The development sink used by {@link SmtpEmailService} whenever no SMTP channel is in effect
 * (no Email Settings saved / sending disabled, and MAIL_ENABLED=false). Writes the email to the
 * log instead of sending it, so flows can be exercised locally without an SMTP account.
 * Nothing reaches the recipient, so every method reports {@link EmailDelivery#disabled()}.
 *
 * <p>The setup link printed below contains a live one-time token. That is deliberate and is the
 * whole point of this development sink — it is the stand-in for the email body while delivery is
 * off. Configure Email Settings (or MAIL_ENABLED=true) on any server where the log is not private.
 * Generated passwords are never printed, even here.
 */
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public EmailDelivery sendEnrollmentEmail(User student, String studentId, String courseName,
                                             String orderRef, String amountDisplay, String setupLink) {
        log.warn("[EMAIL:DEV-SINK] Enrollment email NOT sent (no SMTP configured). to={} studentId={} course='{}' order={} amount={}",
                student.getEmail(), studentId, courseName, orderRef, amountDisplay);
        if (setupLink != null) {
            log.warn("[EMAIL:DEV-SINK] Password setup link for {} (development only): {}", student.getEmail(), setupLink);
        }
        return EmailDelivery.disabled();
    }

    @Override
    public EmailDelivery sendWelcomeEmail(User user, String setupLink) {
        log.warn("[EMAIL:DEV-SINK] Welcome email NOT sent (no SMTP configured). to={}", user.getEmail());
        log.warn("[EMAIL:DEV-SINK] Password setup link for {} (development only): {}", user.getEmail(), setupLink);
        return EmailDelivery.disabled();
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

    @Override
    public EmailDelivery sendTestEmail(String toEmail, String requestedBy) {
        log.warn("[EMAIL:DEV-SINK] Test email NOT sent (no SMTP configured). to={} requestedBy={}", toEmail, requestedBy);
        return EmailDelivery.disabled();
    }

    @Override
    public EmailDelivery sendLoginCredentials(User user, String userId, String temporaryPassword, String loginUrl) {
        log.warn("[EMAIL:DEV-SINK] Login credentials email NOT sent (no SMTP configured). to={} userId={} (password withheld from log)",
                user.getEmail(), userId);
        return EmailDelivery.disabled();
    }

    @Override
    public EmailDelivery sendPasswordChanged(User user) {
        log.info("[EMAIL:DEV-SINK] Password-changed notice NOT sent (no SMTP configured). to={}", user.getEmail());
        return EmailDelivery.disabled();
    }

    @Override
    public EmailDelivery sendFeeReceipt(String toEmail, java.util.Map<String, Object> model) {
        log.warn("[EMAIL:DEV-SINK] Fee receipt NOT sent (no SMTP configured). to={}", toEmail);
        return EmailDelivery.disabled();
    }

    @Override
    public EmailDelivery sendEnquiry(String toEmail, String replyTo, java.util.Map<String, Object> model) {
        log.warn("[EMAIL:DEV-SINK] Website enquiry NOT sent (no SMTP configured). to={} from visitor={} subject='{}'",
                toEmail, replyTo, model.get("subject"));
        return EmailDelivery.disabled();
    }

    @Override
    public EmailDelivery sendPurchaseEmail(PurchaseEmail email) {
        log.warn("[EMAIL:DEV-SINK] {} email NOT sent (no SMTP configured). to={} subject='{}' invoice={} attachment={}",
                email.kind(), email.to(), email.subject(), email.model().get("invoiceNumber"),
                email.attachment() == null ? "none" : email.attachment().filename());
        Object setupLink = email.model().get("setupLink");
        if (setupLink != null) {
            log.warn("[EMAIL:DEV-SINK] Password setup link for {} (development only): {}", email.to(), setupLink);
        }
        return EmailDelivery.disabled();
    }

    @Override
    public EmailDelivery sendMessage(String toEmail, String recipientName, String subject, String message,
                                     EmailAttachment attachment) {
        log.warn("[EMAIL:DEV-SINK] Message NOT sent (no SMTP configured). to={} subject='{}' attachment={}",
                toEmail, subject, attachment == null ? "none" : attachment.filename());
        return EmailDelivery.disabled();
    }

    @Override
    public EmailDelivery sendDeviceOtp(User user, String otp, String actionDescription, String ipAddress) {
        log.warn("[EMAIL:DEV-SINK] Device OTP for {} ({}): OTP={} ip={}",
                user.getEmail(), actionDescription, otp, ipAddress);
        return EmailDelivery.disabled();
    }
}
