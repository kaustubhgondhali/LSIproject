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
import jakarta.validation.constraints.Pattern;
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
 * The website enquiry forms: the "Enquire Now" pop-up on the public pages and the Contact page
 * form (js/enquiry.js). Delivers the enquiry to the academy inbox through the configured email
 * system (Main Admin > Email Settings); the page then opens WhatsApp with the same details for the
 * visitor to send. Rate-limited per IP; nothing is stored. When no email channel is active the
 * visitor gets an honest message instead of a fake "sent".
 */
@RestController
@RequestMapping("/api/public")
public class PublicEnquiryController {

    private static final Logger log = LoggerFactory.getLogger(PublicEnquiryController.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    /** Empty (the email is optional on the forms) or a real-looking address. Same rule as js/enquiry.js. */
    static final String EMAIL_PATTERN = "^$|^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$";
    /** An Indian mobile: 10 digits starting 6-9, optionally written with spaces / hyphens and a +91, 91 or 0 prefix. */
    static final String MOBILE_PATTERN = "^(?:\\+?91[\\s-]?|0)?[6-9](?:[\\s-]?\\d){9}$";

    public record EnquiryRequest(
            @NotBlank(message = "Please enter your full name.")
            @Size(min = 2, max = 120, message = "Please enter your full name (2 to 120 characters).") String name,
            /** Optional; when given it becomes the email's Reply-To. */
            @Email(regexp = EMAIL_PATTERN, message = "Please enter a valid email address.")
            @Size(max = 190, message = "Please enter a valid email address.") String email,
            @NotBlank(message = "Please enter a valid 10-digit mobile number.")
            @Pattern(regexp = MOBILE_PATTERN, message = "Please enter a valid 10-digit mobile number.") String phone,
            /** The programme / service the visitor is interested in. */
            @Size(max = 160) String subject,
            /** Optional: the pop-up form has no message box. */
            @Size(max = 3000, message = "Your message is too long (maximum 3000 characters).") String message,
            /** Which site the form was on: "Share Market Academy" or "Mutual Fund". */
            @Size(max = 60) String site,
            /** Which page the form was on, e.g. "Contact" or "SIP". */
            @Size(max = 60) String page,
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
        String visitorEmail = blankToNull(body.email()) == null ? null : body.email().trim().toLowerCase();

        Map<String, Object> model = new HashMap<>();
        model.put("visitorName", body.name().trim());
        model.put("visitorEmail", visitorEmail);
        model.put("visitorPhone", normaliseMobile(body.phone()));
        model.put("subject", blankToNull(body.subject()) == null ? "General enquiry" : body.subject().trim());
        model.put("message", blankToNull(body.message()));
        model.put("site", blankToNull(body.site()) == null ? "Share Market Academy" : body.site().trim());
        model.put("page", blankToNull(body.page()));
        model.put("receivedAt", ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(STAMP) + " IST");

        EmailDelivery delivery = emailService.sendEnquiry(inbox, visitorEmail, model);
        String ip = RequestUtil.clientIp(request);
        if (!delivery.delivered()) {
            log.warn("[ENQUIRY] NOT delivered from {} ({}) (ip {}): {} — {}", model.get("visitorName"), model.get("visitorPhone"), ip,
                    delivery.status(), delivery.reason());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Our email system is temporarily unavailable, so your message could not be delivered. "
                            + "Please message us on WhatsApp or call us during working hours.");
        }
        log.info("[ENQUIRY] Delivered to {} from {} ({}) (ip {})", inbox, model.get("visitorName"), model.get("visitorPhone"), ip);
        return ApiResponse.message("Thank you! Your enquiry has been sent to the academy. We will get back to you shortly.");
    }

    /** "+91 99202 54354", "09920254354", "99202-54354" ... -> "+91 9920254354" (already validated by MOBILE_PATTERN). */
    static String normaliseMobile(String phone) {
        String digits = phone.replaceAll("\\D", "");
        return "+91 " + digits.substring(digits.length() - 10);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
