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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The website contact form delivers through the configured email system and never fakes success. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicEnquiryTest {

    @Autowired ApiClient api;
    @MockitoBean EmailService emailService;

    private Map<String, Object> enquiry() {
        Map<String, Object> m = new HashMap<>();
        m.put("name", "Rahul Patil"); m.put("email", "Rahul@Example.com"); m.put("phone", "9920254354");
        m.put("subject", "Technical Analysis"); m.put("message", "I would like to join the next batch."); m.put("site", "Share Market Academy");
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
                .containsEntry("site", "Share Market Academy");
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
        Map<String, Object> bad = enquiry(); bad.put("email", "nope");
        api.post(null, "/api/public/enquiry", bad).andExpect(status().isBadRequest());
        Map<String, Object> empty = enquiry(); empty.put("message", "");
        api.post(null, "/api/public/enquiry", empty).andExpect(status().isBadRequest());
        Map<String, Object> bot = enquiry(); bot.put("website", "http://spam.example");
        api.post(null, "/api/public/enquiry", bot).andExpect(status().isOk());
        verify(emailService, never()).sendEnquiry(anyString(), anyString(), any());
    }
}
