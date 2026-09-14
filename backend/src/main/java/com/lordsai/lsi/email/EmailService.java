package com.lordsai.lsi.email;

import com.lordsai.lsi.entity.User;

/**
 * All outbound email goes through here. The SMTP implementation (Phase 8) and the
 * development logging implementation are interchangeable.
 */
public interface EmailService {

    /** Sent after a verified purchase, with the student ID and the one-time password setup link. */
    void sendEnrollmentEmail(User student, String studentId, String courseName,
                             String orderRef, String amountDisplay, String setupLink);

    /** Sent when an admin creates an account by hand. */
    void sendWelcomeEmail(User user, String setupLink);

    void sendPasswordResetEmail(User user, String resetLink);

    void sendPaymentConfirmation(String toEmail, String customerName, String courseName,
                                 String orderRef, String amountDisplay);

    /** Sent to a visitor right after they submit a testimonial (it is awaiting approval). */
    void sendReviewReceived(String toEmail, String name);

    /** Sent when an admin approves the visitor's testimonial. */
    void sendReviewApproved(String toEmail, String name);
}
