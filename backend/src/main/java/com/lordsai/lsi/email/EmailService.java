package com.lordsai.lsi.email;

import com.lordsai.lsi.entity.User;

/**
 * All outbound email goes through here. The SMTP implementation (Phase 8) and the
 * development logging implementation are interchangeable.
 */
public interface EmailService {

    /**
     * Sent after a verified purchase, with the student ID and the one-time password setup link.
     * Returns the delivery outcome: this email is the only carrier of the setup link, so a failure
     * has to reach the checkout screen instead of being swallowed.
     */
    EmailDelivery sendEnrollmentEmail(User student, String studentId, String courseName,
                                      String orderRef, String amountDisplay, String setupLink);

    /** Sent when an admin creates an account by hand. */
    EmailDelivery sendWelcomeEmail(User user, String setupLink);

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

    /** Sent after a successful password change or reset. Never includes the old or new password. */
    EmailDelivery sendPasswordChanged(User user);

    /** Automation Admin: emails a fee receipt. The model carries a ReceiptDto under "receipt". */
    EmailDelivery sendFeeReceipt(String toEmail, java.util.Map<String, Object> model);

    /** Website contact/enquiry form -> the academy inbox, with Reply-To set to the visitor. */
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
}
