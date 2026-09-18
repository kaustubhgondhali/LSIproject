package com.lordsai.lsi.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.entity.EmailSettings;
import com.lordsai.lsi.repository.EmailSettingsRepository;
import com.lordsai.lsi.security.SecretCrypto;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import java.util.List;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Main Admin > Email Settings, exercised through the real SmtpEmailService (no mocks) so the
 * SMTP failure path, secret handling and source resolution are the production code paths.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmailSettingsTest {

    private static final String SECRET = "smtp-app-password-XYZ-987";

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired EmailSettingsRepository repository;
    @Autowired SecretCrypto crypto;
    @Autowired MailSenderResolver resolver;
    /** DNS is stubbed so custom-domain detection is deterministic offline. */
    @MockitoBean MxResolver mxResolver;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        users.admin("mailadmin@test.local");
        admin = api.login("mailadmin@test.local", TestUsers.PASSWORD);
        resolver.invalidate();
        when(mxResolver.lookup(anyString())).thenReturn(List.of());
        when(mxResolver.lookup("lordsai-workspace.in")).thenReturn(List.of("aspmx.l.google.com", "alt1.aspmx.l.google.com"));
        when(mxResolver.lookup("mycompany.co.in")).thenReturn(List.of("mycompany-co-in.mail.protection.outlook.com"));
        when(mxResolver.lookup("zohohosted.in")).thenReturn(List.of("mx.zoho.in", "mx2.zoho.in"));
        when(mxResolver.lookup("selfhosted.example")).thenReturn(List.of("mail.selfhosted.example"));
    }

    // ---- auto configure: email address -> provider -> SMTP settings ----------------------------------

    @Test
    void providersComeFromTheCentralRegistry() throws Exception {
        String body = api.get(admin, "/api/admin/email-settings/providers").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(11))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"code\":\"GMAIL\"", "smtp.gmail.com", "smtp.office365.com", "smtp.mail.yahoo.com", "smtp.zoho.com",
                "email-smtp.ap-south-1.amazonaws.com", "smtp-relay.brevo.com", "smtp.sendgrid.net", "smtp.resend.com", "smtp.mailgun.org", "smtp.postmarkapp.com", "\"code\":\"CUSTOM\"");
        api.get(null, "/api/admin/email-settings/providers").andExpect(status().isUnauthorized());
    }

    @Test
    void gmailAddressIsDetectedAndFullyConfiguredFromTheDomain() throws Exception {
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "Lordsai.Academy@Gmail.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detected").value(true))
                .andExpect(jsonPath("$.data.provider").value("GMAIL"))
                .andExpect(jsonPath("$.data.providerLabel").value("Gmail / Google Workspace"))
                .andExpect(jsonPath("$.data.method").value("DOMAIN"))
                .andExpect(jsonPath("$.data.smtpHost").value("smtp.gmail.com"))
                .andExpect(jsonPath("$.data.smtpPort").value(587))
                .andExpect(jsonPath("$.data.securityMode").value("STARTTLS"))
                .andExpect(jsonPath("$.data.smtpUsername").value("lordsai.academy@gmail.com"))
                .andExpect(jsonPath("$.data.authRequired").value(true))
                .andExpect(jsonPath("$.data.credentialLabel").value(org.hamcrest.Matchers.containsString("App Password")))
                .andExpect(jsonPath("$.data.instructions[0]").value(org.hamcrest.Matchers.containsString("App Password")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Email provider detected: Gmail")));
    }

    @Test
    void microsoftYahooAndZohoAddressesAreDetected() throws Exception {
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "office@hotmail.com"))
                .andExpect(jsonPath("$.data.provider").value("OUTLOOK")).andExpect(jsonPath("$.data.smtpHost").value("smtp.office365.com"))
                .andExpect(jsonPath("$.data.smtpPort").value(587)).andExpect(jsonPath("$.data.securityMode").value("STARTTLS"));
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "someone@outlook.com")).andExpect(jsonPath("$.data.provider").value("OUTLOOK"));
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "trader@yahoo.co.in"))
                .andExpect(jsonPath("$.data.provider").value("YAHOO")).andExpect(jsonPath("$.data.smtpHost").value("smtp.mail.yahoo.com"))
                .andExpect(jsonPath("$.data.smtpPort").value(465)).andExpect(jsonPath("$.data.securityMode").value("SSL_TLS"));
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "desk@zoho.com"))
                .andExpect(jsonPath("$.data.provider").value("ZOHO")).andExpect(jsonPath("$.data.smtpHost").value("smtp.zoho.com"));
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "desk@zoho.in"))
                .andExpect(jsonPath("$.data.smtpHost").value("smtp.zoho.in"));
    }

    @Test
    void customDomainsAreResolvedThroughMxRecordsOrHonestlyLeftAsCustom() throws Exception {
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "admin@lordsai-workspace.in"))
                .andExpect(jsonPath("$.data.detected").value(true)).andExpect(jsonPath("$.data.provider").value("GMAIL"))
                .andExpect(jsonPath("$.data.method").value("MX")).andExpect(jsonPath("$.data.smtpHost").value("smtp.gmail.com"))
                .andExpect(jsonPath("$.data.smtpUsername").value("admin@lordsai-workspace.in"))
                .andExpect(jsonPath("$.data.mxRecords[0]").value("aspmx.l.google.com"));
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "info@mycompany.co.in"))
                .andExpect(jsonPath("$.data.provider").value("OUTLOOK")).andExpect(jsonPath("$.data.method").value("MX"));
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "hello@zohohosted.in"))
                .andExpect(jsonPath("$.data.provider").value("ZOHO")).andExpect(jsonPath("$.data.smtpHost").value("smtp.zoho.in"));

        // Unknown hosting: no guessed host/port, a clear message, and the username still pre-filled.
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "me@selfhosted.example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detected").value(false))
                .andExpect(jsonPath("$.data.provider").value("CUSTOM"))
                .andExpect(jsonPath("$.data.method").value("NONE"))
                .andExpect(jsonPath("$.data.smtpHost").isEmpty())
                .andExpect(jsonPath("$.data.smtpPort").isEmpty())
                .andExpect(jsonPath("$.data.smtpUsername").value("me@selfhosted.example"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Provider could not be automatically determined")));
    }

    @Test
    void manualProviderChoiceLoadsStandardSettingsAndInvalidInputIsRejected() throws Exception {
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "noreply@lordsai.in", "provider", "AMAZON_SES"))
                .andExpect(jsonPath("$.data.method").value("MANUAL")).andExpect(jsonPath("$.data.smtpHost").value("email-smtp.ap-south-1.amazonaws.com"))
                .andExpect(jsonPath("$.data.smtpUsername").isEmpty()).andExpect(jsonPath("$.data.credentialLabel").value("SES SMTP password"));
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "noreply@lordsai.in", "provider", "BREVO"))
                .andExpect(jsonPath("$.data.smtpHost").value("smtp-relay.brevo.com"));
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "noreply@lordsai.in", "provider", "SENDGRID"))
                .andExpect(jsonPath("$.data.smtpHost").value("smtp.sendgrid.net"))
                .andExpect(jsonPath("$.data.smtpUsername").isEmpty());
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "noreply@lordsai.in", "provider", "RESEND"))
                .andExpect(jsonPath("$.data.smtpHost").value("smtp.resend.com"))
                .andExpect(jsonPath("$.data.smtpUsername").isEmpty());
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "noreply@lordsai.in", "provider", "CUSTOM"))
                .andExpect(jsonPath("$.data.detected").value(false)).andExpect(jsonPath("$.data.method").value("MANUAL"))
                .andExpect(jsonPath("$.data.smtpUsername").value("noreply@lordsai.in"));
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "not-an-email")).andExpect(status().isBadRequest());
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "a@gmail.com", "provider", "FAXMODEM")).andExpect(status().isBadRequest());
    }

    // ---- test SMTP connection --------------------------------------------------------------------------

    @Test
    void connectionTestReportsFailuresSafelyAndRequiresACredential() throws Exception {
        Map<String, Object> probe = new HashMap<>();
        probe.put("smtpHost", "127.0.0.1"); probe.put("smtpPort", 2525); probe.put("securityMode", "STARTTLS");
        probe.put("smtpUsername", "mailer@example.com"); probe.put("smtpPassword", SECRET);
        String body = api.post(admin, "/api/admin/email-settings/test-connection", probe)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.connected").value(false))
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Unable to connect to the SMTP server.")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(SECRET).doesNotContain("Exception");

        probe.put("smtpPassword", "");
        api.post(admin, "/api/admin/email-settings/test-connection", probe).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email provider credential is required to authenticate with this SMTP server."));

        // With a saved credential for the same user, a blank password reuses it (no re-typing) - still a clean failure here.
        api.put(admin, "/api/admin/email-settings", settings(false)).andExpect(status().isOk());
        api.post(admin, "/api/admin/email-settings/test-connection", probe).andExpect(status().isOk()).andExpect(jsonPath("$.data.connected").value(false));
        api.get(admin, "/api/admin/email-settings").andExpect(jsonPath("$.data.connectionVerifiedAt").isEmpty());
    }

    @Test
    void saveRecordsTheProviderAndTheStatusDescribesTheEmailSystem() throws Exception {
        Map<String, Object> gmail = settings(true);
        gmail.put("senderEmail", "lordsai.academy@gmail.com"); gmail.put("smtpHost", "smtp.gmail.com"); gmail.put("smtpUsername", "lordsai.academy@gmail.com");
        gmail.put("provider", "AUTO");
        api.put(admin, "/api/admin/email-settings", gmail).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("GMAIL"))
                .andExpect(jsonPath("$.data.providerLabel").value("Gmail / Google Workspace"))
                .andExpect(jsonPath("$.data.credentialLabel").value(org.hamcrest.Matchers.containsString("App Password")))
                .andExpect(jsonPath("$.data.smtpPasswordSet").value(true))
                .andExpect(jsonPath("$.data.smtpPassword").doesNotExist());
        assertThat(repository.findFirstByOrderByIdAsc().orElseThrow().getProvider()).isEqualTo("GMAIL");

        Map<String, Object> custom = settings(true); custom.put("provider", "CUSTOM"); custom.put("smtpPassword", "");
        api.put(admin, "/api/admin/email-settings", custom).andExpect(jsonPath("$.data.provider").value("CUSTOM")).andExpect(jsonPath("$.data.providerLabel").value("Custom SMTP"));
        Map<String, Object> bad = settings(true); bad.put("provider", "NOPE");
        api.put(admin, "/api/admin/email-settings", bad).andExpect(status().isBadRequest());
    }

    @Test
    void saveAndRetrieveDomainAndReplyToSettings() throws Exception {
        Map<String, Object> m = settings(true);
        m.put("replyTo", "helpdesk@vitc.edu");
        m.put("sendingDomain", "vitc.edu");
        m.put("dkimSelector", "k1");

        api.put(admin, "/api/admin/email-settings", m).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyTo").value("helpdesk@vitc.edu"))
                .andExpect(jsonPath("$.data.sendingDomain").value("vitc.edu"))
                .andExpect(jsonPath("$.data.dkimSelector").value("k1"));

        api.get(admin, "/api/admin/email-settings").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyTo").value("helpdesk@vitc.edu"))
                .andExpect(jsonPath("$.data.sendingDomain").value("vitc.edu"))
                .andExpect(jsonPath("$.data.dkimSelector").value("k1"));
    }

    @Test
    void domainDnsCheckEndpointReturnsDiagnostics() throws Exception {
        api.get(admin, "/api/admin/email-settings/domain-check?domain=example.com&selector=default")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.domain").value("example.com"))
                .andExpect(jsonPath("$.data.dkimSelector").value("default"))
                .andExpect(jsonPath("$.data.checks").isArray())
                .andExpect(jsonPath("$.data.summary").exists());

        api.get(null, "/api/admin/email-settings/domain-check?domain=example.com")
                .andExpect(status().isUnauthorized());
    }

    private Map<String, Object> settings(boolean enabled) {
        Map<String, Object> m = new HashMap<>();
        m.put("senderName", "LSI VITC Administration");
        m.put("senderEmail", "No-Reply@Example.com");
        m.put("smtpHost", "127.0.0.1");
        m.put("smtpPort", 2525);
        m.put("smtpUsername", "mailer@example.com");
        m.put("smtpPassword", SECRET);
        m.put("securityMode", "STARTTLS");
        m.put("enabled", enabled);
        m.put("testRecipient", "ops@example.com");
        return m;
    }

    // ---- access control ---------------------------------------------------------------------

    @Test
    void onlyAdminsCanReachEmailSettings() throws Exception {
        users.student("s@test.local");
        String student = api.login("s@test.local", TestUsers.PASSWORD);

        api.get(null, "/api/admin/email-settings").andExpect(status().isUnauthorized());
        api.get(student, "/api/admin/email-settings").andExpect(status().isForbidden());
        api.put(student, "/api/admin/email-settings", settings(true)).andExpect(status().isForbidden());
        api.post(student, "/api/admin/email-settings/test", Map.of("recipient", "x@example.com")).andExpect(status().isForbidden());
        assertThat(repository.count()).isZero();
    }

    // ---- empty state ------------------------------------------------------------------------

    @Test
    void beforeAnythingIsSavedTheFormIsEmptyAndSendingIsOff() throws Exception {
        api.get(admin, "/api/admin/email-settings").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordExists").value(false))
                .andExpect(jsonPath("$.data.smtpPort").value(587))
                .andExpect(jsonPath("$.data.securityMode").value("STARTTLS"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.effectiveSource").value("NONE"))
                .andExpect(jsonPath("$.data.sendingActive").value(false));

        api.post(admin, "/api/admin/email-settings/test", Map.of("recipient", "x@example.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No SMTP configuration exists. Save the email settings first."));
    }

    // ---- save / persist / mask ---------------------------------------------------------------

    @Test
    void savedSettingsPersistWithTheEncryptedPasswordNeverExposed() throws Exception {
        String saved = api.put(admin, "/api/admin/email-settings", settings(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordExists").value(true))
                .andExpect(jsonPath("$.data.senderEmail").value("no-reply@example.com"))
                .andExpect(jsonPath("$.data.smtpHost").value("127.0.0.1"))
                .andExpect(jsonPath("$.data.smtpUsername").value("mailer@example.com"))
                .andExpect(jsonPath("$.data.smtpPasswordSet").value(true))
                .andExpect(jsonPath("$.data.smtpPassword").doesNotExist())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.effectiveSource").value("ADMIN"))
                .andExpect(jsonPath("$.data.sendingActive").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(saved).doesNotContain(SECRET);

        String reloaded = api.get(admin, "/api/admin/email-settings").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smtpPasswordMasked").value("••••••••••••••••"))
                .andReturn().getResponse().getContentAsString();
        assertThat(reloaded).doesNotContain(SECRET);

        EmailSettings row = repository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(row.getSmtpPasswordEnc()).isNotBlank().doesNotContain(SECRET);
        assertThat(crypto.decrypt(row.getSmtpPasswordEnc())).isEqualTo(SECRET);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void blankPasswordOnUpdateKeepsTheStoredOneAndANewOneReplacesIt() throws Exception {
        api.put(admin, "/api/admin/email-settings", settings(true)).andExpect(status().isOk());
        String firstEnc = repository.findFirstByOrderByIdAsc().orElseThrow().getSmtpPasswordEnc();

        Map<String, Object> update = settings(false);
        update.put("smtpPassword", "");
        update.put("smtpPort", 465);
        update.put("securityMode", "SSL_TLS");
        api.put(admin, "/api/admin/email-settings", update).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smtpPort").value(465))
                .andExpect(jsonPath("$.data.securityMode").value("SSL_TLS"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.smtpPasswordSet").value(true));
        EmailSettings row = repository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(crypto.decrypt(row.getSmtpPasswordEnc())).isEqualTo(SECRET);
        assertThat(repository.count()).as("still a single row").isEqualTo(1);

        update.put("smtpPassword", "brand-new-secret");
        api.put(admin, "/api/admin/email-settings", update).andExpect(status().isOk());
        assertThat(crypto.decrypt(repository.findFirstByOrderByIdAsc().orElseThrow().getSmtpPasswordEnc()))
                .isEqualTo("brand-new-secret");
        assertThat(repository.findFirstByOrderByIdAsc().orElseThrow().getSmtpPasswordEnc()).isNotEqualTo(firstEnc);
    }

    @Test
    void invalidValuesAreRejected() throws Exception {
        Map<String, Object> badPort = settings(true); badPort.put("smtpPort", 70000);
        api.put(admin, "/api/admin/email-settings", badPort).andExpect(status().isBadRequest());

        Map<String, Object> badEmail = settings(true); badEmail.put("senderEmail", "not-an-email");
        api.put(admin, "/api/admin/email-settings", badEmail).andExpect(status().isBadRequest());

        Map<String, Object> badHost = settings(true); badHost.put("smtpHost", "smtp host with spaces");
        api.put(admin, "/api/admin/email-settings", badHost).andExpect(status().isBadRequest());

        Map<String, Object> badMode = settings(true); badMode.put("securityMode", "PLAIN");
        api.put(admin, "/api/admin/email-settings", badMode).andExpect(status().isBadRequest());

        Map<String, Object> noPassword = settings(true); noPassword.put("smtpPassword", "");
        api.put(admin, "/api/admin/email-settings", noPassword).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SMTP password is required when a username is set."));

        assertThat(repository.count()).isZero();
    }

    // ---- source resolution --------------------------------------------------------------------

    @Test
    void disablingSwitchesSendingOffEvenThoughSettingsRemainSaved() throws Exception {
        api.put(admin, "/api/admin/email-settings", settings(true)).andExpect(status().isOk());
        assertThat(resolver.resolve()).isPresent();
        assertThat(resolver.currentSource()).isEqualTo("ADMIN");

        api.put(admin, "/api/admin/email-settings", settings(false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effectiveSource").value("NONE"))
                .andExpect(jsonPath("$.data.sendingActive").value(false));
        assertThat(resolver.resolve()).isEmpty();
        // The test channel still works while disabled, so the admin can verify before enabling.
        assertThat(resolver.resolveForTest()).isPresent();
    }

    @Test
    void resolverBuildsTheSenderFromTheSavedSecurityMode() throws Exception {
        api.put(admin, "/api/admin/email-settings", settings(true)).andExpect(status().isOk());
        var sender = resolver.build(repository.findFirstByOrderByIdAsc().orElseThrow());
        assertThat(sender.getHost()).isEqualTo("127.0.0.1");
        assertThat(sender.getPort()).isEqualTo(2525);
        assertThat(sender.getUsername()).isEqualTo("mailer@example.com");
        assertThat(sender.getPassword()).isEqualTo(SECRET);
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("true");
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.connectiontimeout")).isEqualTo("10000");
    }

    // ---- test email ---------------------------------------------------------------------------

    @Test
    void testEmailAgainstAnUnreachableServerFailsCleanlyWithoutLeakingSecrets() throws Exception {
        api.put(admin, "/api/admin/email-settings", settings(false)).andExpect(status().isOk());

        String body = api.post(admin, "/api/admin/email-settings/test", Map.of("recipient", "someone@example.com"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Unable to send test email.")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "verify SMTP host, port, username, password and security settings")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(SECRET).doesNotContain("Exception").doesNotContain("at com.");

        // The failed attempt is recorded so the screen can show it — the failure did not roll it back.
        EmailSettings row = repository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(row.getLastTestedAt()).isNotNull();
        assertThat(row.getLastTestOk()).isFalse();
        api.get(admin, "/api/admin/email-settings").andExpect(jsonPath("$.data.lastTestOk").value(false));
    }

    @Test
    void testEmailFallsBackToTheSavedRecipientAndRequiresOne() throws Exception {
        Map<String, Object> noRecipient = settings(false); noRecipient.put("testRecipient", "");
        api.put(admin, "/api/admin/email-settings", noRecipient).andExpect(status().isOk());

        api.post(admin, "/api/admin/email-settings/test", Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter a test recipient email address."));
        api.post(admin, "/api/admin/email-settings/test", Map.of("recipient", "nope"))
                .andExpect(status().isBadRequest());

        api.put(admin, "/api/admin/email-settings", settings(false)).andExpect(status().isOk());
        // Saved recipient is used; the dead host makes it a 502 rather than a 400.
        api.post(admin, "/api/admin/email-settings/test", Map.of()).andExpect(status().isBadGateway());
    }

    // ---- helpers under test -----------------------------------------------------------------

    @Test
    void failureDescriptionsAreActionableAndSecretFree() {
        assertThat(SmtpEmailService.describe(new org.springframework.mail.MailAuthenticationException("535 5.7.8 bad creds for mailer")))
                .isEqualTo("Authentication failed. Check the SMTP username and password.");
        assertThat(SmtpEmailService.describe(new org.springframework.mail.MailSendException("x",
                new java.net.UnknownHostException("smtp.nowhere.invalid"))))
                .isEqualTo("SMTP host not found. Check the host name.");
        assertThat(SmtpEmailService.describe(new org.springframework.mail.MailSendException("x",
                new javax.net.ssl.SSLHandshakeException("handshake"))))
                .startsWith("TLS/SSL negotiation failed");
        assertThat(SmtpEmailService.describe(new org.springframework.mail.MailSendException("x",
                new java.net.SocketTimeoutException("read timed out"))))
                .startsWith("Connection timed out");
    }

    @Test
    void plainTextFallbackKeepsTheEssentials() {
        String text = SmtpEmailService.plainText(
                "<html><body><h1>Hi</h1><p>User ID: <strong>LSI-1</strong></p><p><a href=\"http://x/login\">Sign in</a></p></body></html>");
        assertThat(text).contains("Hi", "User ID: LSI-1", "Sign in (http://x/login)").doesNotContain("<");
    }

    /**
     * The exact failure seen in production: a credential encrypted under a different JWT_SECRET.
     * It must be visible on the screen, must NOT survive a save, and re-entering must heal it.
     */
    @Test
    void anUnreadableCredentialIsSurfacedCannotBeSilentlyKeptAndIsHealedByReEntry() throws Exception {
        api.put(admin, "/api/admin/email-settings", settings(true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialReadable").value(true));

        EmailSettings row = repository.findFirstByOrderByIdAsc().orElseThrow();
        row.setSmtpPasswordEnc("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");  // another secret's blob
        repository.saveAndFlush(row);
        resolver.invalidate();

        // 1. The screen shows it: a credential is stored, but it cannot be read.
        api.get(admin, "/api/admin/email-settings").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smtpPasswordSet").value(true))
                .andExpect(jsonPath("$.data.credentialReadable").value(false));

        // 2. Saving with the password field left blank must NOT keep the broken credential.
        Map<String, Object> blank = settings(true);
        blank.put("smtpPassword", "");
        api.put(admin, "/api/admin/email-settings", blank).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("can no longer be decrypted")));
        assertThat(crypto.encrypt("x")).isNotBlank();

        // 3. Re-entering the credential heals it, and it decrypts to exactly what was typed.
        Map<String, Object> reentered = settings(true);
        reentered.put("smtpPassword", "brand-new-app-password");
        api.put(admin, "/api/admin/email-settings", reentered).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialReadable").value(true))
                .andExpect(jsonPath("$.data.smtpPasswordSet").value(true))
                .andExpect(jsonPath("$.data.smtpPassword").doesNotExist());
        assertThat(crypto.decrypt(repository.findFirstByOrderByIdAsc().orElseThrow().getSmtpPasswordEnc()))
                .isEqualTo("brand-new-app-password");

        // 4. And the connection test now uses the healed credential (fails only because the host is dead).
        Map<String, Object> probe = new HashMap<>();
        probe.put("smtpHost", "127.0.0.1"); probe.put("smtpPort", 2525); probe.put("securityMode", "STARTTLS");
        probe.put("smtpUsername", "mailer@example.com"); probe.put("smtpPassword", "");
        api.post(admin, "/api/admin/email-settings/test-connection", probe).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Unable to connect")));
    }

    /** Every button on the screen must be routable - a missing mapping is what produced the 404. */
    @Test
    void everyEmailSettingsEndpointTheScreenCallsIsMapped() throws Exception {
        api.get(admin, "/api/admin/email-settings").andExpect(status().isOk());
        api.get(admin, "/api/admin/email-settings/providers").andExpect(status().isOk());
        api.post(admin, "/api/admin/email-settings/detect", Map.of("email", "a@gmail.com")).andExpect(status().isOk());
        api.put(admin, "/api/admin/email-settings", settings(false)).andExpect(status().isOk());
        Map<String, Object> probe = new HashMap<>();
        probe.put("smtpHost", "127.0.0.1"); probe.put("smtpPort", 2525); probe.put("securityMode", "STARTTLS");
        api.post(admin, "/api/admin/email-settings/test-connection", probe).andExpect(status().isOk());
        api.post(admin, "/api/admin/email-settings/test", Map.of("recipient", "x@example.com")).andExpect(status().isBadGateway());
        api.delete(admin, "/api/admin/email-settings").andExpect(status().isOk());
    }

    @Test
    void anUndecryptablePasswordIsReportedNotThrown() throws Exception {
        api.put(admin, "/api/admin/email-settings", settings(false)).andExpect(status().isOk());
        EmailSettings row = repository.findFirstByOrderByIdAsc().orElseThrow();
        // Simulate a blob written under a different JWT_SECRET.
        row.setSmtpPasswordEnc("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        repository.saveAndFlush(row);
        resolver.invalidate();

        assertThat(resolver.resolveForTest()).isPresent();
        assertThat(resolver.resolveForTest().get().error()).isEqualTo(MailSenderResolver.UNDECRYPTABLE);
        api.post(admin, "/api/admin/email-settings/test", Map.of("recipient", "someone@example.com"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cannot be decrypted")));
    }

    @Test
    void removingTheConfigurationFallsBackToTheEnvironment() throws Exception {
        api.delete(admin, "/api/admin/email-settings").andExpect(status().isNotFound());
        api.put(admin, "/api/admin/email-settings", settings(true)).andExpect(status().isOk());

        api.delete(admin, "/api/admin/email-settings").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordExists").value(false))
                .andExpect(jsonPath("$.data.effectiveSource").value("NONE"));
        assertThat(repository.count()).isZero();
        assertThat(resolver.resolve()).isEmpty();
    }

    @Test
    void auditTrailNeverContainsTheSmtpPassword() throws Exception {
        api.put(admin, "/api/admin/email-settings", settings(true)).andExpect(status().isOk());
        JsonNode audit = api.data(api.get(admin, "/api/admin/audit-logs?size=20"));
        String all = audit.path("content").toString();
        assertThat(all).contains("EMAIL_SETTINGS_CREATED").doesNotContain(SECRET);
    }
}
