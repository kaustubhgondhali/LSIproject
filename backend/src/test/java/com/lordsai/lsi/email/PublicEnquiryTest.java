package com.lordsai.lsi.email;

import com.lordsai.lsi.support.ApiClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The website enquiry forms deliver through the configured email system and never fake success. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicEnquiryTest {

    @Autowired ApiClient api;
    @Autowired SpringTemplateEngine templateEngine;
    @MockitoBean EmailService emailService;

    /** The Contact page form: every field filled in. */
    private Map<String, Object> enquiry() {
        Map<String, Object> m = new HashMap<>();
        m.put("name", "Rahul Patil"); m.put("email", "Rahul@Example.com"); m.put("phone", "9920254354");
        m.put("subject", "Technical Analysis"); m.put("message", "I want to know about SIP & tax-saving options?");
        m.put("site", "Share Market Academy"); m.put("page", "Contact");
        return m;
    }

    /** The "Enquire Now" pop-up: no message box, and email is optional. */
    private Map<String, Object> popupEnquiry() {
        Map<String, Object> m = new HashMap<>();
        m.put("name", "Asha Mhatre"); m.put("email", ""); m.put("phone", "+91 99202-54354");
        m.put("subject", "Mutual Fund SIP Consultation"); m.put("site", "Mutual Fund"); m.put("page", "SIP");
        return m;
    }

    @Test
    void enquiryIsEmailedToTheAcademyInboxWithReplyToTheVisitor() throws Exception {
        when(emailService.sendEnquiry(anyString(), anyString(), any())).thenReturn(EmailDelivery.sent());
        api.post(null, "/api/public/enquiry", enquiry()).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Thank you! Your enquiry has been sent")));

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> replyTo = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendEnquiry(to.capture(), replyTo.capture(), model.capture());
        assertThat(to.getValue()).isEqualTo("test@lordsai.local");          // support inbox until Email Settings are saved
        assertThat(replyTo.getValue()).isEqualTo("rahul@example.com");
        assertThat(model.getValue()).containsEntry("visitorName", "Rahul Patil").containsEntry("subject", "Technical Analysis")
                .containsEntry("site", "Share Market Academy").containsEntry("page", "Contact")
                .containsEntry("visitorPhone", "+91 9920254354")
                .containsEntry("message", "I want to know about SIP & tax-saving options?");
    }

    @Test
    void popupEnquiryWithoutEmailOrMessageIsDelivered() throws Exception {
        when(emailService.sendEnquiry(anyString(), isNull(), any())).thenReturn(EmailDelivery.sent());
        api.post(null, "/api/public/enquiry", popupEnquiry()).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendEnquiry(anyString(), isNull(), model.capture());
        assertThat(model.getValue()).containsEntry("visitorPhone", "+91 9920254354").containsEntry("visitorEmail", null)
                .containsEntry("message", null).containsEntry("site", "Mutual Fund");
    }

    @Test
    void whenEmailIsNotConfiguredTheVisitorGetsAnHonestMessage() throws Exception {
        when(emailService.sendEnquiry(anyString(), anyString(), any())).thenReturn(EmailDelivery.disabled());
        api.post(null, "/api/public/enquiry", enquiry()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("WhatsApp")));
    }

    @Test
    void validationAndHoneypot() throws Exception {
        Map<String, Object> badEmail = enquiry(); badEmail.put("email", "nope@x");
        api.post(null, "/api/public/enquiry", badEmail).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Please enter a valid email address."));
        Map<String, Object> noName = enquiry(); noName.put("name", " ");
        api.post(null, "/api/public/enquiry", noName).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        Map<String, Object> noPhone = enquiry(); noPhone.remove("phone");
        api.post(null, "/api/public/enquiry", noPhone).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").value("Please enter a valid 10-digit mobile number."));
        for (String phone : new String[]{"12345", "5920254354", "99202543541", "9920 25a 354"}) {
            Map<String, Object> badPhone = enquiry(); badPhone.put("phone", phone);
            api.post(null, "/api/public/enquiry", badPhone).andExpect(status().isBadRequest());
        }
        Map<String, Object> longMessage = enquiry(); longMessage.put("message", "x".repeat(3001));
        api.post(null, "/api/public/enquiry", longMessage).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").exists());
        Map<String, Object> bot = enquiry(); bot.put("website", "http://spam.example");
        api.post(null, "/api/public/enquiry", bot).andExpect(status().isOk());
        verify(emailService, never()).sendEnquiry(anyString(), any(), any());
    }

    @Test
    void acceptedMobileFormatsAreNormalised() throws Exception {
        when(emailService.sendEnquiry(anyString(), any(), any())).thenReturn(EmailDelivery.sent());
        for (String phone : new String[]{"9920 254 354", "+919920254354", "919920254354", "09920254354", "99202-54354"}) {
            Map<String, Object> m = popupEnquiry(); m.put("phone", phone);
            api.post(null, "/api/public/enquiry", m).andExpect(status().isOk());
        }
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);
        verify(emailService, org.mockito.Mockito.times(5)).sendEnquiry(anyString(), any(), model.capture());
        assertThat(model.getAllValues()).allSatisfy(v -> assertThat(v).containsEntry("visitorPhone", "+91 9920254354"));
    }

    @Test
    void enquiryEmailRendersWithAndWithoutTheOptionalFields() {
        Map<String, Object> full = new HashMap<>(Map.of("visitorName", "Rahul Patil", "visitorEmail", "rahul@example.com",
                "visitorPhone", "+91 9920254354", "subject", "Technical Analysis", "message", "SIP & tax <questions>",
                "site", "Share Market Academy", "page", "Contact", "receivedAt", "30 Sep 2026, 02:30 PM IST"));
        String html = render(full);
        assertThat(html).contains("New Enquiry Received", "Rahul Patil", "mailto:rahul@example.com", "tel:+919920254354",
                "Technical Analysis", "30 Sep 2026, 02:30 PM IST", "(Contact page)", "SIP &amp; tax &lt;questions&gt;");

        Map<String, Object> popup = new HashMap<>(full);
        popup.put("visitorEmail", null); popup.put("message", null); popup.put("page", null);
        String html2 = render(popup);
        // (the shared layout's footer always links the support address, so only the visitor's mailto must be gone)
        assertThat(html2).contains("Rahul Patil", "+91 9920254354", "No email was given")
                .doesNotContain("mailto:rahul@example.com", ">Message<", "page)");
    }

    private String render(Map<String, Object> model) {
        Context context = new Context();
        context.setVariables(model);
        context.setVariable("supportEmail", "test@lordsai.local");
        context.setVariable("supportPhone", "+91 99202 54354");
        return templateEngine.process("email/enquiry", context);
    }
}
