package com.lordsai.lsi.email;

import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Sends branded HTML email over SMTP. Active when lsi.mail.enabled=true.
 * A delivery failure is logged but never propagated: a paid enrollment must not be rolled
 * back because the mail server hiccupped — the admin can resend from the portal.
 */
@Service
@ConditionalOnProperty(prefix = "lsi.mail", name = "enabled", havingValue = "true")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final AppProperties properties;

    public SmtpEmailService(JavaMailSender mailSender, SpringTemplateEngine templateEngine, AppProperties properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    @Override
    public void sendEnrollmentEmail(User student, String studentId, String courseName,
                                    String orderRef, String amountDisplay, String setupLink) {
        Map<String, Object> model = base();
        model.put("name", student.getFullName());
        model.put("email", student.getEmail());
        model.put("studentId", studentId);
        model.put("courseName", courseName);
        model.put("orderRef", orderRef);
        model.put("amount", amountDisplay);
        model.put("setupLink", setupLink);
        send(student.getEmail(), "Welcome to Lord Sai Academy — Enrollment Confirmed (" + studentId + ")",
                "email/enrollment", model);
    }

    @Override
    public void sendWelcomeEmail(User user, String setupLink) {
        Map<String, Object> model = base();
        model.put("name", user.getFullName());
        model.put("email", user.getEmail());
        model.put("setupLink", setupLink);
        model.put("role", user.getRole().name().charAt(0) + user.getRole().name().substring(1).toLowerCase());
        send(user.getEmail(), "Your Lord Sai Academy account", "email/welcome", model);
    }

    @Override
    public void sendPasswordResetEmail(User user, String resetLink) {
        Map<String, Object> model = base();
        model.put("name", user.getFullName());
        model.put("resetLink", resetLink);
        send(user.getEmail(), "Reset your Lord Sai Academy password", "email/password-reset", model);
    }

    @Override
    public void sendPaymentConfirmation(String toEmail, String customerName, String courseName,
                                        String orderRef, String amountDisplay) {
        Map<String, Object> model = base();
        model.put("name", customerName);
        model.put("courseName", courseName);
        model.put("orderRef", orderRef);
        model.put("amount", amountDisplay);
        send(toEmail, "Payment received — " + orderRef, "email/payment-confirmation", model);
    }

    @Override
    public void sendReviewReceived(String toEmail, String name) {
        Map<String, Object> model = base();
        model.put("name", name);
        send(toEmail, "We received your review — Lord Sai Academy", "email/review-received", model);
    }

    @Override
    public void sendReviewApproved(String toEmail, String name) {
        Map<String, Object> model = base();
        model.put("name", name);
        model.put("testimonialsUrl", properties.publicBaseUrl().replaceAll("/+$", "") + "/testimonials.html");
        send(toEmail, "Your review is now live — Lord Sai Academy", "email/review-approved", model);
    }

    private Map<String, Object> base() {
        Map<String, Object> model = new HashMap<>();
        model.put("portalUrl", properties.publicBaseUrl().replaceAll("/+$", "") + "/student-login.html");
        model.put("siteUrl", properties.publicBaseUrl());
        model.put("supportEmail", properties.support().email());
        model.put("supportPhone", properties.support().phone());
        return model;
    }

    private void send(String to, String subject, String template, Map<String, Object> model) {
        try {
            Context context = new Context();
            context.setVariables(model);
            String html = templateEngine.process(template, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email '{}' sent to {}", subject, to);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send email '{}' to {}: {}", subject, to, e.getMessage());
        }
    }
}
