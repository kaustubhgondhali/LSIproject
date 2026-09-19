package com.lordsai.lsi.email;

import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.Role;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The application's single outbound email service. Sends branded HTML (with a plain-text part)
 * through whichever channel {@link MailSenderResolver} reports as active — the Main Admin's saved
 * Email Settings first (a connected Google account via the Gmail API, otherwise SMTP), then the
 * server environment. When neither is configured it falls back to {@link LoggingEmailService},
 * which writes the message to the log.
 *
 * <p>A delivery failure is never thrown — a paid enrollment must not be rolled back because the
 * mail server hiccupped — but it is returned as {@link EmailDelivery#failed(String)} so the
 * caller can report it and offer a resend, instead of claiming an email was sent.
 * Credentials are never logged.
 */
@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final MailSenderResolver resolver;
    private final SpringTemplateEngine templateEngine;
    private final AppProperties properties;
    private final LoggingEmailService fallback = new LoggingEmailService();

    public SmtpEmailService(MailSenderResolver resolver, SpringTemplateEngine templateEngine, AppProperties properties) {
        this.resolver = resolver;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    @Override
    public EmailDelivery sendEnrollmentEmail(User student, String studentId, String courseName,
                                             String orderRef, String amountDisplay, String setupLink) {
        Optional<MailSenderResolver.ActiveMail> channel = resolver.resolve();
        if (channel.isEmpty()) {
            return notSent(fallback.sendEnrollmentEmail(student, studentId, courseName, orderRef, amountDisplay, setupLink));
        }
        Map<String, Object> model = base();
        model.put("name", student.getFullName());
        model.put("email", student.getEmail());
        model.put("studentId", studentId);
        model.put("courseName", courseName);
        model.put("orderRef", orderRef);
        model.put("amount", amountDisplay);
        model.put("setupLink", setupLink);
        return send(channel.get(), student.getEmail(), "Welcome to Lord Sai Academy — Enrollment Confirmed (" + studentId + ")",
                "email/enrollment", model);
    }

    @Override
    public EmailDelivery sendWelcomeEmail(User user, String setupLink) {
        Optional<MailSenderResolver.ActiveMail> channel = resolver.resolve();
        if (channel.isEmpty()) {
            return notSent(fallback.sendWelcomeEmail(user, setupLink));
        }
        Map<String, Object> model = base();
        model.put("name", user.getFullName());
        model.put("email", user.getEmail());
        model.put("setupLink", setupLink);
        model.put("role", user.getRole().name().charAt(0) + user.getRole().name().substring(1).toLowerCase());
        return send(channel.get(), user.getEmail(), "Your Lord Sai Academy account", "email/welcome", model);
    }

    @Override
    public void sendPasswordResetEmail(User user, String resetLink) {
        Optional<MailSenderResolver.ActiveMail> channel = resolver.resolve();
        if (channel.isEmpty()) {
            fallback.sendPasswordResetEmail(user, resetLink);
            return;
        }
        Map<String, Object> model = base();
        model.put("name", user.getFullName());
        model.put("resetLink", resetLink);
        send(channel.get(), user.getEmail(), "Reset your Lord Sai Academy password", "email/password-reset", model);
    }

    @Override
    public void sendPaymentConfirmation(String toEmail, String customerName, String courseName,
                                        String orderRef, String amountDisplay) {
        Optional<MailSenderResolver.ActiveMail> channel = resolver.resolve();
        if (channel.isEmpty()) {
            fallback.sendPaymentConfirmation(toEmail, customerName, courseName, orderRef, amountDisplay);
            return;
        }
        Map<String, Object> model = base();
        model.put("name", customerName);
        model.put("courseName", courseName);
        model.put("orderRef", orderRef);
        model.put("amount", amountDisplay);
        send(channel.get(), toEmail, "Payment received — " + orderRef, "email/payment-confirmation", model);
    }

    @Override
    public void sendReviewReceived(String toEmail, String name) {
        Optional<MailSenderResolver.ActiveMail> channel = resolver.resolve();
        if (channel.isEmpty()) {
            fallback.sendReviewReceived(toEmail, name);
            return;
        }
        Map<String, Object> model = base();
        model.put("name", name);
        send(channel.get(), toEmail, "We received your review — Lord Sai Academy", "email/review-received", model);
    }

    @Override
    public void sendReviewApproved(String toEmail, String name) {
        Optional<MailSenderResolver.ActiveMail> channel = resolver.resolve();
        if (channel.isEmpty()) {
            fallback.sendReviewApproved(toEmail, name);
            return;
        }
        Map<String, Object> model = base();
        model.put("name", name);
        model.put("testimonialsUrl", properties.publicBaseUrl().replaceAll("/+$", "") + "/testimonials.html");
        send(channel.get(), toEmail, "Your review is now live — Lord Sai Academy", "email/review-approved", model);
    }

    // ---- Email Settings / credentials --------------------------------------------------------

    @Override
    public EmailDelivery sendTestEmail(String toEmail, String requestedBy) {
        // Uses the saved settings even while "enabled" is off: the admin tests before switching on.
        Optional<MailSenderResolver.ActiveMail> channel = resolver.resolveForTest();
        if (channel.isEmpty()) {
            return notSent(fallback.sendTestEmail(toEmail, requestedBy));
        }
        Map<String, Object> model = base();
        model.put("requestedBy", requestedBy);
        model.put("source", channel.get().source());
        return send(channel.get(), toEmail, "VITC Email Configuration Test", "email/test", model);
    }

    @Override
    public EmailDelivery sendLoginCredentials(User user, String userId, String temporaryPassword, String loginUrl) {
        Optional<MailSenderResolver.ActiveMail> channel = resolver.resolve();
        if (channel.isEmpty()) {
            return notSent(fallback.sendLoginCredentials(user, userId, temporaryPassword, loginUrl));
        }
        Map<String, Object> model = base();
        model.put("name", user.getFullName());
        model.put("email", user.getEmail());
        model.put("userId", userId);
        if (temporaryPassword != null && (temporaryPassword.startsWith("http://") || temporaryPassword.startsWith("https://") || temporaryPassword.contains("token="))) {
            model.put("setupLink", temporaryPassword);
        } else {
            model.put("password", temporaryPassword);
        }
        model.put("loginUrl", loginUrl);
        return send(channel.get(), user.getEmail(), "LSI VITC - Your Login Credentials", "email/credentials", model);
    }

    @Override
    public EmailDelivery sendPasswordChanged(User user) {
        Optional<MailSenderResolver.ActiveMail> channel = resolver.resolve();
        if (channel.isEmpty()) {
            return notSent(fallback.sendPasswordChanged(user));
        }
        Map<String, Object> model = base();
        model.put("name", user.getFullName());
        model.put("loginUrl", loginUrlFor(user.getRole()));
        return send(channel.get(), user.getEmail(), "LSI VITC - Password Changed Successfully", "email/password-changed", model);
    }

    @Override
    public EmailDelivery sendFeeReceipt(String toEmail, Map<String, Object> receiptModel) {
        Optional<MailSenderResolver.ActiveMail> channel = resolver.resolve();
        if (channel.isEmpty()) {
            return notSent(fallback.sendFeeReceipt(toEmail, receiptModel));
        }
        Map<String, Object> model = base();
        model.putAll(receiptModel);
        Object receipt = receiptModel.get("receipt");
        String receiptNo = receipt instanceof com.lordsai.lsi.automation.dto.AutomationDtos.ReceiptDto r ? r.receiptNo() : "";
        return send(channel.get(), toEmail, "LSI VITC - Fee Receipt " + receiptNo, "email/receipt", model);
    }

    @Override
    public EmailDelivery sendEnquiry(String toEmail, String replyTo, Map<String, Object> enquiryModel) {
        Optional<MailSenderResolver.ActiveMail> channel = resolver.resolve();
        if (channel.isEmpty()) {
            return notSent(fallback.sendEnquiry(toEmail, replyTo, enquiryModel));
        }
        Map<String, Object> model = base();
        model.putAll(enquiryModel);
        String subject = "Website enquiry: " + enquiryModel.getOrDefault("subject", "General") + " - " + enquiryModel.getOrDefault("visitorName", "");
        return send(channel.get(), toEmail, replyTo, subject, "email/enquiry", model);
    }

    @Override
    public EmailDelivery sendPurchaseEmail(PurchaseEmail email) {
        Optional<MailSenderResolver.ActiveMail> channel = resolver.resolve();
        if (channel.isEmpty()) {
            return notSent(fallback.sendPurchaseEmail(email));
        }
        Map<String, Object> model = base();
        model.putAll(email.model());
        return send(channel.get(), email.to(), null, email.subject(), email.template(), model, email.attachment());
    }

    @Override
    public EmailDelivery sendMessage(String toEmail, String recipientName, String subject, String message,
                                     EmailAttachment attachment) {
        Optional<MailSenderResolver.ActiveMail> channel = resolver.resolve();
        if (channel.isEmpty()) {
            return notSent(fallback.sendMessage(toEmail, recipientName, subject, message, attachment));
        }
        Map<String, Object> model = base();
        model.put("name", recipientName);
        model.put("subject", subject);
        model.put("message", message == null ? "" : message);
        return send(channel.get(), toEmail, null, subject, "email/communication", model, attachment);
    }

    @Override
    public EmailDelivery sendDeviceOtp(User user, String otp, String actionDescription, String ipAddress) {
        Optional<MailSenderResolver.ActiveMail> channel = resolver.resolve();
        if (channel.isEmpty()) {
            return notSent(fallback.sendDeviceOtp(user, otp, actionDescription, ipAddress));
        }
        Map<String, Object> model = base();
        model.put("name", user.getFullName());
        model.put("subject", "Device Verification Code - Lord Sai Academy");
        model.put("message", "Your verification code for " + actionDescription + " is: " + otp + "\n\nThis code will expire in 10 minutes.\nRequest IP: " + ipAddress + "\nIf you did not initiate this request, please change your password immediately.");
        return send(channel.get(), user.getEmail(), null, "Device Verification Code - Lord Sai Academy", "email/communication", model, null);
    }

    /** Where a user of this role signs in; used in credential and notification emails. */
    public String loginUrlFor(Role role) {
        String base = properties.publicBaseUrl().replaceAll("/+$", "") + "/student-login.html";
        return role == Role.ADMIN ? base + "?portal=admin" : base;
    }

    // ---- internals ----------------------------------------------------------------------------

    /** The dev sink has logged the message; report the real reason nothing was delivered. */
    private EmailDelivery notSent(EmailDelivery sinkResult) {
        return EmailDelivery.disabled(resolver.disabledReason());
    }

    private Map<String, Object> base() {
        Map<String, Object> model = new HashMap<>();
        model.put("portalUrl", properties.publicBaseUrl().replaceAll("/+$", "") + "/student-login.html");
        model.put("siteUrl", properties.publicBaseUrl());
        model.put("supportEmail", properties.support().email());
        model.put("supportPhone", properties.support().phone());
        return model;
    }

    private EmailDelivery send(MailSenderResolver.ActiveMail channel, String to, String subject,
                               String template, Map<String, Object> model) {
        return send(channel, to, null, subject, template, model);
    }

    private EmailDelivery send(MailSenderResolver.ActiveMail channel, String to, String replyTo, String subject,
                               String template, Map<String, Object> model) {
        return send(channel, to, replyTo, subject, template, model, null);
    }

    private EmailDelivery send(MailSenderResolver.ActiveMail channel, String to, String replyTo, String subject,
                               String template, Map<String, Object> model, EmailAttachment attachment) {
        if (channel.error() != null) {
            log.error("[EMAIL] FAILED '{}' to {} via {}: {}", subject, to, channel.source(), channel.error());
            return EmailDelivery.failed(channel.error());
        }

        String effectiveReplyTo = (replyTo != null && !replyTo.isBlank())
                ? replyTo
                : (channel.replyTo() != null && !channel.replyTo().isBlank()
                        ? channel.replyTo()
                        : (properties.support() != null ? properties.support().email() : null));

        int maxAttempts = 3;
        long backoffMs = 500;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Context context = new Context();
                context.setVariables(model);
                String html = templateEngine.process(template, context);

                MimeMessage message = channel.sender().createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                if (channel.fromName() != null && !channel.fromName().isBlank()) {
                    helper.setFrom(channel.fromEmail(), channel.fromName());
                } else {
                    helper.setFrom(channel.fromEmail());
                }
                helper.setTo(to);
                if (effectiveReplyTo != null && !effectiveReplyTo.isBlank()) {
                    helper.setReplyTo(effectiveReplyTo);
                }
                helper.setSubject(subject);
                helper.setText(plainText(html), html);
                if (attachment != null && attachment.content() != null && attachment.content().length > 0) {
                    helper.addAttachment(attachment.filename(),
                            new org.springframework.core.io.ByteArrayResource(attachment.content()),
                            attachment.contentType() == null ? "application/octet-stream" : attachment.contentType());
                }
                channel.sender().send(message);
                log.info("[EMAIL] Sent '{}' to {} via {}", subject, to, channel.source());
                return EmailDelivery.sent(channel.source());
            } catch (Exception e) {
                lastException = e;
                if (!isTransient(e) || attempt == maxAttempts) {
                    break;
                }
                log.warn("[EMAIL] Attempt {} failed for '{}' to {}: {}. Retrying in {} ms...",
                        attempt, subject, to, describe(e), backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                backoffMs *= 2;
            }
        }

        String reason = describe(lastException);
        // Never log the exception's toString: JavaMail can echo the AUTH exchange in it.
        log.error("[EMAIL] FAILED '{}' to {} via {}: {}", subject, to, channel.source(), reason);
        return EmailDelivery.failed(reason);
    }

    /** True if the failure is transient (network timeout / connection drop) and worth retrying. */
    public static boolean isTransient(Exception e) {
        if (e == null) {
            return false;
        }
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof MailAuthenticationException || t.getClass().getSimpleName().contains("AuthenticationFailed")) {
                return false;
            }
            if (t instanceof com.lordsai.lsi.email.google.GoogleAuthException g && g.isReconnectRequired()) {
                return false;
            }
            String name = t.getClass().getSimpleName();
            if (name.contains("SocketTimeout") || name.contains("TimeoutException")
                    || name.contains("ConnectException") || name.contains("SocketException")) {
                return true;
            }
        }
        return false;
    }

    /**
     * A short, secret-free explanation of why SMTP refused the message. JavaMail messages can
     * contain the AUTH dialogue and the server banner, so only the exception chain's *types* and
     * a few safe phrases are used.
     */
    public static String describe(Exception e) {
        java.util.List<Throwable> chain = new java.util.ArrayList<>();
        for (Throwable t = e; t != null && chain.size() < 10; t = t.getCause()) {
            chain.add(t);
        }
        // Pass 0: the Gmail API path builds its own administrator-ready message (and deliberately
        // never includes a token), so it is used verbatim rather than translated into SMTP wording.
        for (Throwable t : chain) {
            if (t instanceof com.lordsai.lsi.email.google.GoogleAuthException g) {
                return g.getMessage();
            }
        }
        // Pass 1: exception types, deepest cause first — JavaMail wraps a refused connection in a
        // MailConnectException whose text mentions the timeout value, so text alone misleads.
        for (int i = chain.size() - 1; i >= 0; i--) {
            Throwable t = chain.get(i);
            String type = t.getClass().getSimpleName();
            if (t instanceof MailAuthenticationException || type.contains("AuthenticationFailed")) {
                return "Authentication failed. Check the SMTP username and password.";
            }
            if (type.contains("SSLHandshake") || type.contains("SSLException")) {
                return "TLS/SSL negotiation failed. Check the security mode (STARTTLS vs SSL/TLS) and the port.";
            }
            if (type.contains("UnknownHost")) {
                return "SMTP host not found. Check the host name.";
            }
            if (type.contains("SocketTimeout") || type.contains("TimeoutException")) {
                return "Connection timed out. Check the host, port and that outbound SMTP is allowed from this server.";
            }
            if (type.equals("ConnectException")) {
                return "Could not connect to the SMTP server. Check the host and port.";
            }
            if (type.contains("SendFailed") || type.contains("AddressException")) {
                return "The mail server rejected the sender or recipient address.";
            }
        }
        // Pass 2: message text, for servers that only report a status line.
        for (Throwable t : chain) {
            String msg = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
            if (msg.contains("authentication") || msg.contains("535 ")) {
                return "Authentication failed. Check the SMTP username and password.";
            }
            if (msg.contains("certificate") || msg.contains("handshake") || msg.contains("starttls is required")) {
                return "TLS/SSL negotiation failed. Check the security mode (STARTTLS vs SSL/TLS) and the port.";
            }
            if (msg.contains("timed out")) {
                return "Connection timed out. Check the host, port and that outbound SMTP is allowed from this server.";
            }
            if (msg.contains("couldn't connect") || msg.contains("connection refused")) {
                return "Could not connect to the SMTP server. Check the host and port.";
            }
            if (msg.contains("invalid address") || msg.contains("recipient") || msg.contains("sender address")) {
                return "The mail server rejected the sender or recipient address.";
            }
        }
        return "The mail server rejected the message (" + e.getClass().getSimpleName() + ").";
    }

    /** Rough text/plain alternative for clients that do not render HTML. */
    static String plainText(String html) {
        String text = html
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "")
                .replaceAll("(?i)<a\\s[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", "$2 ($1)")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|tr|h[1-6]|li)>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("(?m)^\\s+", "")
                .replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }
}
