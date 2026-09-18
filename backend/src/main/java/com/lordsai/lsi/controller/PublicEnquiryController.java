package com.lordsai.lsi.controller;

import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.email.MailSenderResolver;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * The website contact form. Delivers the enquiry to the academy inbox through the configured
 * email system (Main Admin > Email Settings). Rate-limited per IP; nothing is stored. When no
 * SMTP channel is active the visitor gets an honest message pointing at WhatsApp/phone instead
 * of a fake "sent".
 */
@RestController
@RequestMapping("/api/public")
public class PublicEnquiryController {

    private static final Logger log = LoggerFactory.getLogger(PublicEnquiryController.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    public record EnquiryRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            @NotBlank @Email(message = "Please enter a valid email address.") @Size(max = 190) String email,
            @Size(max = 20) String phone,
            @Size(max = 120) String subject,
            @NotBlank @Size(min = 5, max = 3000) String message,
            /** Which site the form was on: "Share Market Academy" or "Mutual Fund". */
            @Size(max = 60) String site,
            /** Honeypot: bots fill it, people never see it. */
            @Size(max = 100) String website
    ) {
    }

    private final EmailService emailService;
    private final MailSenderResolver resolver;
    private final AppProperties properties;

    public PublicEnquiryController(EmailService emailService, MailSenderResolver resolver, AppProperties properties) {
        this.emailService = emailService;
        this.resolver = resolver;
        this.properties = properties;
    }

    @PostMapping("/enquiry")
    public ApiResponse<Void> enquiry(@Valid @RequestBody EnquiryRequest body, HttpServletRequest request) {
        if (body.website() != null && !body.website().isBlank()) {
            // Honeypot tripped: answer as if sent so the bot learns nothing, deliver nothing.
            return ApiResponse.message("Thank you! Your enquiry has been received.");
        }
        // The academy inbox is the configured sender mailbox; before any settings exist, the support address.
        String inbox = resolver.resolve().map(MailSenderResolver.ActiveMail::fromEmail).orElse(properties.support().email());

        Map<String, Object> model = new HashMap<>();
        model.put("visitorName", body.name().trim());
        model.put("visitorEmail", body.email().trim().toLowerCase());
        model.put("visitorPhone", body.phone() == null || body.phone().isBlank() ? null : body.phone().trim());
        model.put("subject", body.subject() == null || body.subject().isBlank() ? "General enquiry" : body.subject().trim());
        model.put("message", body.message().trim());
        model.put("site", body.site() == null || body.site().isBlank() ? "Share Market Academy" : body.site().trim());
        model.put("receivedAt", ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(STAMP) + " IST");

        EmailDelivery delivery = emailService.sendEnquiry(inbox, model.get("visitorEmail").toString(), model);
        String ip = RequestUtil.clientIp(request);
        if (!delivery.delivered()) {
            log.warn("[ENQUIRY] NOT delivered from {} <{}> (ip {}): {} — {}", model.get("visitorName"), model.get("visitorEmail"), ip,
                    delivery.status(), delivery.reason());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Our email system is temporarily unavailable, so your message could not be delivered. "
                            + "Please message us on WhatsApp or call us during working hours.");
        }
        log.info("[ENQUIRY] Delivered to {} from {} <{}> (ip {})", inbox, model.get("visitorName"), model.get("visitorEmail"), ip);
        return ApiResponse.message("Thank you! Your enquiry has been sent to the academy. We will get back to you shortly.");
    }
}
