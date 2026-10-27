package com.lordsai.lsi.email;

import com.lordsai.lsi.entity.User;

/**
 * All outbound email goes through here. The SMTP implementation (Phase 8) and the
 * development logging implementation are interchangeable.
 */
public interface EmailService {

    /**
     * Sent after a verified purchase, with the User ID (for login), the Student ID (reference only)
     * and the one-time password setup link.
     * Returns the delivery outcome: this email is the only carrier of the setup link, so a failure
     * has to reach the checkout screen instead of being swallowed.
     */
    EmailDelivery sendEnrollmentEmail(User student, String studentId, String userId, String courseName,
                                      String orderRef, String amountDisplay, String setupLink);

    /** Sent when an admin creates an account by hand. {@code userId} is the student's User ID, null for staff. */
    EmailDelivery sendWelcomeEmail(User user, String userId, String setupLink);

    void sendPasswordResetEmail(User user, String resetLink);

    void sendPaymentConfirmation(String toEmail, String customerName, String courseName,
                                 String orderRef, String amountDisplay);

    /** Sent to a visitor right after they submit a testimonial (it is awaiting approval). */
    void sendReviewReceived(String toEmail, String name);

    /** Sent when an admin approves the visitor's testimonial. */
    void sendReviewApproved(String toEmail, String name);

    /** Main Admin "Send Test Email": proves the configured SMTP settings work. */
    EmailDelivery sendTestEmail(String toEmail, String requestedBy);

    /**
     * Sends a freshly generated password. Only ever called with a password created in the same
     * operation — an existing password is never recoverable from the database.
     */
    EmailDelivery sendLoginCredentials(User user, String userId, String temporaryPassword, String loginUrl);

    /**
     * Automation Admin: the free ebook login of an academy student — the same login details email,
     * saying that this login opens the ebook (My Ebooks) in the Student Panel.
     */
    default EmailDelivery sendAcademyEbookLogin(User user, String userId, String temporaryPassword, String loginUrl) {
        return sendLoginCredentials(user, userId, temporaryPassword, loginUrl);
    }

    /** Sent after a successful password change or reset. Never includes the old or new password. */
    EmailDelivery sendPasswordChanged(User user);

    /** Automation Admin: emails a fee receipt. The model carries a ReceiptDto under "receipt"; the signed PDF is attached. */
    EmailDelivery sendFeeReceipt(String toEmail, java.util.Map<String, Object> model, EmailAttachment receiptPdf);

    /** Subject line of every website enquiry email. */
    String ENQUIRY_SUBJECT = "New Enquiry - LSI Website";

    /** Website contact/enquiry form -> the academy inbox, with Reply-To set to the visitor when they gave an email. */
    EmailDelivery sendEnquiry(String toEmail, String replyTo, java.util.Map<String, Object> model);

    /**
     * Purchase-related transactional email (course / ebook confirmation, new purchase on an
     * existing account, invoice resend) with the invoice PDF attached. Never throws: a failed
     * delivery is returned so the purchase stays successful and the admin can retry.
     */
    EmailDelivery sendPurchaseEmail(PurchaseEmail email);

    /**
     * Admin-composed message to a student (individual or bulk communication), rendered in the
     * Lord Sai layout with an optional attachment.
     */
    EmailDelivery sendMessage(String toEmail, String recipientName, String subject, String message,
                              EmailAttachment attachment);

    /** Device binding / OTP verification email for registration, linking secondary browsers, or device recovery. */
    EmailDelivery sendDeviceOtp(User user, String otp, String actionDescription, String ipAddress);
}
