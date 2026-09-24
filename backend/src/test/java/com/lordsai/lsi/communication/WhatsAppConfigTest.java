package com.lordsai.lsi.communication;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.entity.WhatsAppConfig;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.repository.AuditLogRepository;
import com.lordsai.lsi.repository.WhatsAppConfigRepository;
import com.lordsai.lsi.security.SecretCrypto;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Central WhatsApp configuration: saved once by the Main Admin (token encrypted, never returned),
 * verified with the provider before it is enabled, and used by every WhatsApp send in the
 * system — the Automation Admin's automation included — without any second copy of credentials.
 * Only the provider's network calls are stubbed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WhatsAppConfigTest {

    private static final String TOKEN = "EAAB-super-secret-meta-token-1234567890";

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired WhatsAppConfigRepository repository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired SecretCrypto crypto;
    @Autowired WhatsAppMessageService whatsApp;

    @MockitoSpyBean MetaCloudWhatsAppProvider meta;

    private String admin;
    private String office;

    @BeforeEach
    void setUp() throws Exception {
        users.admin("admin@test.local");
        admin = api.login("admin@test.local", TestUsers.PASSWORD);
        office = api.login("automation", "Automation@123");
    }

    private Map<String, Object> config(String token) {
        Map<String, Object> m = new HashMap<>();
        m.put("provider", "META_CLOUD"); m.put("phoneNumberId", "123456789012345"); m.put("businessAccountId", "98765");
        if (token != null) m.put("accessToken", token);
        return m;
    }

    private void providerAccepts() {
        doReturn(WhatsAppProvider.ValidationResult.success("+91 99202 54354 · Lord Sai")).when(meta).verify(any());
    }

    // ---- not configured by default (test profile has no WHATSAPP_* variables) ----------------------

    @Test
    void notConfiguredUntilTheMainAdminSavesItAndTheMessageNamesNoServerVariables() throws Exception {
        api.get(admin, "/api/admin/whatsapp").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.recordExists").value(false))
                .andExpect(jsonPath("$.data.message").value("WhatsApp is not configured. Configure WhatsApp from Settings → WhatsApp Settings."))
                .andExpect(jsonPath("$.data.providers[0]").value("META_CLOUD"));
        api.post(admin, "/api/admin/whatsapp/test-connection", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("WHATSAPP_"))));
        api.get(office, "/api/automation/communications/status")
                .andExpect(jsonPath("$.data.whatsappConfigured").value(false))
                .andExpect(jsonPath("$.data.whatsappMessage").value("WhatsApp is not configured. Please ask the Main Admin to configure WhatsApp in Settings."));
    }

    // ---- save: verified, encrypted, masked ----------------------------------------------------------

    @Test
    void mainAdminSavesTheConfigurationOnceAndTheTokenIsEncryptedAndNeverReturned() throws Exception {
        providerAccepts();
        String body = api.put(admin, "/api/admin/whatsapp", config(TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.credentialSource").value("ADMIN"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.provider").value("META_CLOUD"))
                .andExpect(jsonPath("$.data.phoneNumberId").value("123456789012345"))
                .andExpect(jsonPath("$.data.displayPhoneNumber").value("+91 99202 54354 · Lord Sai"))
                .andExpect(jsonPath("$.data.accessTokenMasked").value("••••••••••••••••••••••••"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(TOKEN);

        WhatsAppConfig cfg = repository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(cfg.getAccessTokenEnc()).doesNotContain(TOKEN);
        assertThat(crypto.decrypt(cfg.getAccessTokenEnc())).isEqualTo(TOKEN);
        assertThat(cfg.isActive()).isTrue();

        // Persists across reads, still masked.
        String again = api.get(admin, "/api/admin/whatsapp").andExpect(jsonPath("$.data.recordExists").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(again).doesNotContain(TOKEN);

        // The central resolver hands the decrypted token to the provider — the only place it is used.
        assertThat(whatsApp.resolve()).isPresent();
        assertThat(whatsApp.resolve().get().accessToken()).isEqualTo(TOKEN);
        assertThat(whatsApp.credentialSource()).isEqualTo("ADMIN");
        assertThat(auditLogRepository.findAll().stream().map(a -> a.getAction()).toList()).contains("WHATSAPP_CONFIG_CREATED");
    }

    @Test
    void rejectedCredentialsAreNeverStoredAndBlankTokenOnUpdateKeepsTheSavedOne() throws Exception {
        doReturn(WhatsAppProvider.ValidationResult.failure("WhatsApp API error 401: Invalid OAuth access token")).when(meta).verify(any());
        api.put(admin, "/api/admin/whatsapp", config("bad-token")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("WhatsApp configuration failed. Please verify your provider, access token and phone number ID.")));
        assertThat(repository.count()).isZero();

        providerAccepts();
        api.put(admin, "/api/admin/whatsapp", config(TOKEN)).andExpect(status().isOk());
        // Update the phone number id without re-typing the token.
        Map<String, Object> update = config(null); update.put("phoneNumberId", "555");
        api.put(admin, "/api/admin/whatsapp", update).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneNumberId").value("555"));
        assertThat(repository.count()).as("one central configuration, never duplicated").isEqualTo(1);
        assertThat(crypto.decrypt(repository.findFirstByOrderByIdAsc().orElseThrow().getAccessTokenEnc())).isEqualTo(TOKEN);

        // A brand-new configuration does require a token.
        repository.deleteAll();
        api.put(admin, "/api/admin/whatsapp", config(null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Access Token is required."));
    }

    // ---- enable / disable / remove ------------------------------------------------------------------

    @Test
    void disableEnableAndRemoveControlWhetherWhatsAppIsAvailableEverywhere() throws Exception {
        providerAccepts();
        api.put(admin, "/api/admin/whatsapp", config(TOKEN)).andExpect(status().isOk());

        api.patch(admin, "/api/admin/whatsapp/disable", null).andExpect(jsonPath("$.data.configured").value(false)).andExpect(jsonPath("$.data.enabled").value(false));
        assertThat(whatsApp.isConfigured()).isFalse();
        api.get(office, "/api/automation/communications/status").andExpect(jsonPath("$.data.whatsappConfigured").value(false));

        api.patch(admin, "/api/admin/whatsapp/enable", null).andExpect(jsonPath("$.data.configured").value(true));
        api.get(office, "/api/automation/communications/status").andExpect(jsonPath("$.data.whatsappConfigured").value(true))
                .andExpect(jsonPath("$.data.whatsappMessage").value(org.hamcrest.Matchers.containsString("managed from Main Admin")));

        api.delete(admin, "/api/admin/whatsapp").andExpect(jsonPath("$.data.recordExists").value(false));
        assertThat(repository.count()).isZero();
        assertThat(whatsApp.isConfigured()).isFalse();
    }

    // ---- test connection / test message use the central configuration ------------------------------

    @Test
    void testConnectionAndTestMessageGoThroughTheCentralConfiguration() throws Exception {
        providerAccepts();
        api.put(admin, "/api/admin/whatsapp", config(TOKEN)).andExpect(status().isOk());
        api.post(admin, "/api/admin/whatsapp/test-connection", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("WhatsApp configuration is working."));

        doReturn(WhatsAppDelivery.sent("wamid.TEST1", "META_CLOUD")).when(meta).sendText(any(), eq("919876543210"), anyString());
        api.post(admin, "/api/admin/whatsapp/test-message", Map.of("mobile", "9876543210", "message", "Hello from LSI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Test WhatsApp message sent successfully."))
                .andExpect(jsonPath("$.data.providerMessageId").value("wamid.TEST1"));
        ArgumentCaptor<WhatsAppSettings> used = ArgumentCaptor.forClass(WhatsAppSettings.class);
        verify(meta).sendText(used.capture(), eq("919876543210"), anyString());
        assertThat(used.getValue().accessToken()).isEqualTo(TOKEN);
        assertThat(used.getValue().source()).isEqualTo("ADMIN");

        doReturn(WhatsAppDelivery.failed("WhatsApp API error 400: outside the 24-hour window", "META_CLOUD")).when(meta).sendText(any(), eq("919000000000"), anyString());
        api.post(admin, "/api/admin/whatsapp/test-message", Map.of("mobile", "9000000000", "message", "Hi"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Test WhatsApp message could not be sent.")));
        assertThat(auditLogRepository.findAll().stream().map(a -> a.getAction()).toList()).contains("WHATSAPP_TEST_SENT", "WHATSAPP_TEST_FAILED");
    }

    // ---- the Automation Admin USES the same configuration but cannot see or change it -----------------

    @Test
    void automationAdminUsesTheCentralConfigurationWithoutAccessToTheSecret() throws Exception {
        providerAccepts();
        api.put(admin, "/api/admin/whatsapp", config(TOKEN)).andExpect(status().isOk());
        api.get(office, "/api/admin/whatsapp").andExpect(status().isForbidden());
        api.put(office, "/api/admin/whatsapp", config(TOKEN)).andExpect(status().isForbidden());
        api.post(office, "/api/admin/whatsapp/test-message", Map.of("mobile", "9876543210", "message", "x")).andExpect(status().isForbidden());

        // Automation Admin sends to ITS OWN student through the same central credentials.
        JsonNode l = api.data(api.get(office, "/api/automation/lookups"));
        long batchId = l.path("batches").get(0).path("id").asLong();
        long courseId = l.path("courses").get(0).path("id").asLong();
        long sid = api.data(api.post(office, "/api/automation/students", Map.of("fullName", "Ravi Patil", "batchId", batchId, "courseId", courseId,
                "admissionDate", "2026-09-01", "mobile", "9876543210"))).path("id").asLong();
        doReturn(WhatsAppDelivery.sent("wamid.AUTO", "META_CLOUD")).when(meta).sendText(any(), eq("919876543210"), anyString());
        String body = api.post(office, "/api/automation/communications/send", Map.of("studentIds", List.of(sid), "channels", List.of("WHATSAPP"), "message", "Class at 10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.whatsappQueued").value(1))
                .andExpect(jsonPath("$.data.message").value("Sent 1 message(s)."))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(TOKEN);
        String history = api.get(office, "/api/automation/communications/history?channel=WHATSAPP").andExpect(jsonPath("$.data.content[0].status").value("SENT"))
                .andExpect(jsonPath("$.data.content[0].providerMessageId").value("wamid.AUTO"))
                .andReturn().getResponse().getContentAsString();
        assertThat(history).doesNotContain(TOKEN);
        ArgumentCaptor<WhatsAppSettings> used = ArgumentCaptor.forClass(WhatsAppSettings.class);
        verify(meta).sendText(used.capture(), eq("919876543210"), anyString());
        assertThat(used.getValue().source()).isEqualTo("ADMIN");
    }

    // ---- security ---------------------------------------------------------------------------------------

    @Test
    void studentsAndAnonymousVisitorsCannotReachTheConfiguration() throws Exception {
        users.create("pupil@test.local", Role.STUDENT, AccountStatus.ACTIVE);
        String student = api.login("pupil@test.local", TestUsers.PASSWORD);
        api.get(student, "/api/admin/whatsapp").andExpect(status().isForbidden());
        api.get(null, "/api/admin/whatsapp").andExpect(status().isUnauthorized());
        api.put(null, "/api/admin/whatsapp", config(TOKEN)).andExpect(status().isUnauthorized());
        verify(meta, never()).verify(any());
    }
}
