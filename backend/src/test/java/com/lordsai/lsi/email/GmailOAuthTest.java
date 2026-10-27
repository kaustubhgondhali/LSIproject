package com.lordsai.lsi.email;

import com.lordsai.lsi.email.google.GmailApiMailSender;
import com.lordsai.lsi.email.google.GoogleAuthException;
import com.lordsai.lsi.email.google.GoogleOAuthClient;
import com.lordsai.lsi.email.google.GoogleOAuthStateService;
import com.lordsai.lsi.email.google.GoogleTokens;
import com.lordsai.lsi.entity.EmailSettings;
import com.lordsai.lsi.entity.enums.MailAuthMode;
import com.lordsai.lsi.entity.enums.MailSecurityMode;
import com.lordsai.lsi.repository.EmailSettingsRepository;
import com.lordsai.lsi.security.SecretCrypto;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gmail via Google OAuth 2.0.
 *
 * <p>Google itself is the one thing that cannot be exercised here, so {@link GoogleOAuthClient} is
 * mocked at the HTTP boundary. Everything on this side of that boundary is the real code: the state
 * signing, the callback controller, the encrypted token storage, the channel resolution and the
 * message that actually goes to the Gmail API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GmailOAuthTest {

    /** Stand-in for a real refresh token. Never a live credential — this is a fixture. */
    private static final String REFRESH_TOKEN = "1//test-refresh-token-value";
    private static final String ACCESS_TOKEN = "ya29.test-access-token-value";
    private static final String GOOGLE_ACCOUNT = "academy@gmail.com";

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired MockMvc mockMvc;
    @Autowired EmailSettingsRepository repository;
    @Autowired SecretCrypto crypto;
    @Autowired MailSenderResolver resolver;
    @Autowired GoogleOAuthStateService stateService;
    @Autowired com.lordsai.lsi.email.google.GmailOAuthService gmailOAuth;

    /** The only seam to Google. Every other layer under test is production code. */
    @MockitoBean GoogleOAuthClient googleClient;
    @MockitoBean MxResolver mxResolver;

    private String admin;
    private Long adminId;

    @BeforeEach
    void setUp() throws Exception {
        com.lordsai.lsi.entity.User a = users.admin("oauthadmin@test.local");
        adminId = a.getId();
        admin = api.login("oauthadmin@test.local", TestUsers.PASSWORD);
        resolver.invalidate();
        gmailOAuth.invalidate();
        when(mxResolver.lookup(anyString())).thenReturn(java.util.List.of());
        when(googleClient.authorizationUrl(anyString()))
                .thenAnswer(inv -> "https://accounts.google.com/o/oauth2/v2/auth?state=" + inv.getArgument(0));
        when(googleClient.revoke(anyString())).thenReturn(true);
    }

    // ---- the state parameter: the only thing protecting a necessarily public callback ---------

    @Test
    void aStateValueIsSingleUseAndCannotBeForgedOrReplayed() {
        String state = stateService.issue(adminId);

        assertThat(stateService.consume(state)).contains(adminId);
        // Replay of the very same value must not work a second time.
        assertThat(stateService.consume(state)).isEmpty();

        String fresh = stateService.issue(adminId);
        // Tamper with the signature, and with the payload, and with the shape.
        assertThat(stateService.consume(fresh.substring(0, fresh.length() - 2) + "xy")).isEmpty();
        assertThat(stateService.consume("bm90LWEtcmVhbC1zdGF0ZQ.signature")).isEmpty();
        assertThat(stateService.consume("garbage")).isEmpty();
        assertThat(stateService.consume(null)).isEmpty();
        assertThat(stateService.consume("")).isEmpty();
        // The untampered original still works, proving the rejections were about the tampering.
        assertThat(stateService.consume(fresh)).contains(adminId);
    }

    // ---- connect -------------------------------------------------------------------------------

    @Test
    void theAuthorizationUrlAsksOnlyForPermissionToSendAndNeverCarriesTheClientSecret() {
        // The real client is used here, not the mock, because the URL itself is what is under test.
        GoogleOAuthClient real = new GoogleOAuthClient(new com.lordsai.lsi.email.google.GoogleOAuthProperties(
                "client-id-123.apps.googleusercontent.com", "super-secret-value",
                "http://localhost:8080/api/public/email/google/callback"));
        String url = real.authorizationUrl("state-abc");

        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
        assertThat(url).contains("client_id=client-id-123.apps.googleusercontent.com");
        assertThat(url).contains("gmail.send");
        // A refresh token only comes back with these two, and without it the connection dies in an hour.
        assertThat(url).contains("access_type=offline").contains("prompt=consent");
        assertThat(url).contains("state=state-abc");
        // The blast radius of the scope: send only, never full mailbox access.
        assertThat(url).doesNotContain("mail.google.com");
        assertThat(url).doesNotContain("gmail.readonly").doesNotContain("gmail.modify");
        // The secret belongs in the token POST body, never in a URL the browser will hold.
        assertThat(url).doesNotContain("super-secret-value").doesNotContain("client_secret");
    }

    @Test
    void connectReturnsAnAuthorizationUrlAndIsMainAdminOnly() throws Exception {
        api.get(admin, "/api/admin/email-settings/google/connect")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizationUrl").value(org.hamcrest.Matchers.startsWith("https://accounts.google.com/")));

        // Same endpoint without a token is refused by the /api/admin/** rule.
        mockMvc.perform(get("/api/admin/email-settings/google/connect")).andExpect(status().isUnauthorized());
    }

    @Test
    void aSuccessfulCallbackStoresTheRefreshTokenEncryptedAndSwitchesSendingToGmail() throws Exception {
        when(googleClient.exchangeCode("auth-code-xyz")).thenReturn(tokens(REFRESH_TOKEN));
        String state = stateService.issue(adminId);

        mockMvc.perform(get("/api/public/email/google/callback").param("code", "auth-code-xyz").param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5500/admin-dashboard.html?google=connected#email"));

        EmailSettings saved = repository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(saved.getAuthMode()).isEqualTo(MailAuthMode.GMAIL_OAUTH);
        assertThat(saved.getGoogleEmail()).isEqualTo(GOOGLE_ACCOUNT);
        assertThat(saved.googleConnected()).isTrue();

        // Encrypted at rest, and it really is the token underneath.
        assertThat(saved.getGoogleRefreshTokenEnc()).isNotNull().doesNotContain(REFRESH_TOKEN);
        assertThat(crypto.decrypt(saved.getGoogleRefreshTokenEnc())).isEqualTo(REFRESH_TOKEN);

        // Gmail rejects a From the account does not own, so the sender identity follows the account.
        assertThat(saved.getSenderEmail()).isEqualTo(GOOGLE_ACCOUNT);

        resolver.invalidate();
        assertThat(resolver.currentSource()).isEqualTo(MailSenderResolver.SOURCE_GOOGLE);
    }

    @Test
    void aCallbackWithoutAValidStateChangesNothingAtAll() throws Exception {
        mockMvc.perform(get("/api/public/email/google/callback").param("code", "attacker-code").param("state", "forged"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5500/admin-dashboard.html?google=invalid_state#email"));

        // The decisive part: an unverified callback must not reach Google with the code.
        verify(googleClient, never()).exchangeCode(anyString());
        assertThat(repository.findFirstByOrderByIdAsc()).isEmpty();
    }

    @Test
    void refusingConsentIsReportedWithoutTouchingTheConfiguration() throws Exception {
        mockMvc.perform(get("/api/public/email/google/callback").param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5500/admin-dashboard.html?google=denied#email"));

        verify(googleClient, never()).exchangeCode(anyString());
        assertThat(repository.findFirstByOrderByIdAsc()).isEmpty();
    }

    @Test
    void anAuthorizationWithoutARefreshTokenOrWithoutSendPermissionIsRejected() throws Exception {
        when(googleClient.exchangeCode("no-refresh")).thenReturn(
                new GoogleTokens(ACCESS_TOKEN, null, Instant.now().plusSeconds(3600), GoogleOAuthClient.SCOPE, GOOGLE_ACCOUNT));
        mockMvc.perform(get("/api/public/email/google/callback").param("code", "no-refresh")
                        .param("state", stateService.issue(adminId)))
                .andExpect(header().string("Location", "http://localhost:5500/admin-dashboard.html?google=failed#email"));
        assertThat(repository.findFirstByOrderByIdAsc()).isEmpty();

        when(googleClient.exchangeCode("no-send")).thenReturn(
                new GoogleTokens(ACCESS_TOKEN, REFRESH_TOKEN, Instant.now().plusSeconds(3600), "openid email", GOOGLE_ACCOUNT));
        mockMvc.perform(get("/api/public/email/google/callback").param("code", "no-send")
                        .param("state", stateService.issue(adminId)))
                .andExpect(header().string("Location", "http://localhost:5500/admin-dashboard.html?google=failed#email"));
        assertThat(repository.findFirstByOrderByIdAsc()).isEmpty();
    }

    // ---- status: what the browser is allowed to see -------------------------------------------

    @Test
    void theStatusEndpointRevealsTheAccountButNeverATokenOrTheClientSecret() throws Exception {
        connect();

        String body = api.get(admin, "/api/admin/email-settings/google").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.email").value(GOOGLE_ACCOUNT))
                .andExpect(jsonPath("$.data.sendPermissionGranted").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(REFRESH_TOKEN).doesNotContain(ACCESS_TOKEN);
        assertThat(body).doesNotContain("refreshToken").doesNotContain("accessToken")
                .doesNotContain("refresh_token").doesNotContain("access_token")
                .doesNotContain("clientSecret").doesNotContain("client_secret")
                .doesNotContain("test-google-client-secret");

        // The main settings payload must be equally clean.
        String settings = api.get(admin, "/api/admin/email-settings").andReturn().getResponse().getContentAsString();
        assertThat(settings).doesNotContain(REFRESH_TOKEN).doesNotContain(ACCESS_TOKEN)
                .doesNotContain("refreshToken").doesNotContain("RefreshTokenEnc");
        assertThat(settings).contains("\"googleConnected\":true").contains(GOOGLE_ACCOUNT);
    }

    @Test
    void everyGoogleEndpointTheScreenCallsIsMapped() throws Exception {
        connect();
        api.get(admin, "/api/admin/email-settings/google").andExpect(status().isOk());
        api.get(admin, "/api/admin/email-settings/google/connect").andExpect(status().isOk());
        api.post(admin, "/api/admin/email-settings/google/disconnect", null).andExpect(status().isOk());
        // The callback is public on purpose: Google navigates the browser there with no token.
        mockMvc.perform(get("/api/public/email/google/callback").param("error", "access_denied"))
                .andExpect(status().isFound());
    }

    // ---- disconnect ----------------------------------------------------------------------------

    @Test
    void disconnectRevokesAtGoogleClearsTheTokenAndFallsBackToSmtp() throws Exception {
        connect();

        api.post(admin, "/api/admin/email-settings/google/disconnect", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false));

        // Revoked with the real token, not with something else.
        ArgumentCaptor<String> revoked = ArgumentCaptor.forClass(String.class);
        verify(googleClient).revoke(revoked.capture());
        assertThat(revoked.getValue()).isEqualTo(REFRESH_TOKEN);

        EmailSettings after = repository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(after.getGoogleRefreshTokenEnc()).isNull();
        assertThat(after.getGoogleEmail()).isNull();
        assertThat(after.getAuthMode()).isEqualTo(MailAuthMode.SMTP);
        // The SMTP configuration it had before is still there to fall back to.
        assertThat(after.getSmtpHost()).isEqualTo("smtp.gmail.com");
        assertThat(after.getSmtpPort()).isEqualTo(587);

        resolver.invalidate();
        assertThat(resolver.currentSource()).isNotEqualTo(MailSenderResolver.SOURCE_GOOGLE);
    }

    @Test
    void sendingFailsWithAClearMessageOnceTheAccountIsDisconnected() throws Exception {
        connect();
        api.post(admin, "/api/admin/email-settings/google/disconnect", null).andExpect(status().isOk());
        resolver.invalidate();

        // Gracefully, and immediately: no 30-second SMTP timeout, and the message says what to do.
        api.post(admin, "/api/admin/email-settings/test", Map.of("recipient", "someone@example.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "No Google account is connected and no SMTP credential is saved")));
    }

    @Test
    void removingTheWholeConfigurationAlsoWithdrawsTheGoogleGrant() throws Exception {
        connect();

        api.delete(admin, "/api/admin/email-settings").andExpect(status().isOk());

        // The row is gone and, just as importantly, the academy's Google account is no longer
        // authorised for an application that no longer holds the grant.
        assertThat(repository.findFirstByOrderByIdAsc()).isEmpty();
        verify(googleClient).revoke(REFRESH_TOKEN);
    }

    // ---- sending --------------------------------------------------------------------------------

    @Test
    void theMessageHandedToGmailIsTheSameMimeMessageTheTemplatesProduce() throws Exception {
        GmailApiMailSender sender = new GmailApiMailSender(googleClient, () -> ACCESS_TOKEN);

        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(GOOGLE_ACCOUNT, "Lord Sai Academy");
        helper.setTo("student@example.com");
        helper.setSubject("Welcome to Lord Sai Academy");
        helper.setText("plain body", "<p>html body</p>");
        sender.send(message);

        ArgumentCaptor<byte[]> raw = ArgumentCaptor.forClass(byte[].class);
        verify(googleClient).sendRaw(org.mockito.ArgumentMatchers.eq(ACCESS_TOKEN), raw.capture());
        String mime = new String(raw.getValue(), StandardCharsets.UTF_8);
        assertThat(mime).contains("To: student@example.com");
        assertThat(mime).contains("Welcome to Lord Sai Academy");
        assertThat(mime).contains(GOOGLE_ACCOUNT);
        assertThat(mime).contains("html body");
    }

    @Test
    void aRevokedGrantSurfacesTheReconnectMessageRatherThanAnSmtpOne() {
        GmailApiMailSender sender = new GmailApiMailSender(googleClient, () -> {
            throw GoogleAuthException.revoked();
        });

        assertThatThrownBy(() -> sender.send(sender.createMimeMessage()))
                .isInstanceOf(MailAuthenticationException.class)
                .hasMessageContaining("Google account authorization has expired or been revoked");

        // And the message the admin finally sees is the Google one, not "check the SMTP username".
        String described = SmtpEmailService.describe(
                new MailAuthenticationException("wrapped", GoogleAuthException.revoked()));
        assertThat(described).isEqualTo(
                "Google account authorization has expired or been revoked. Please reconnect your Google account.");
    }

    @Test
    void aGmailApiFailureIsReportedWithoutLeakingTheAccessToken() {
        doThrow(new GoogleAuthException("Gmail is temporarily unavailable or the sending limit was reached. "
                + "Please try again shortly.", false))
                .when(googleClient).sendRaw(anyString(), any(byte[].class));
        GmailApiMailSender sender = new GmailApiMailSender(googleClient, () -> ACCESS_TOKEN);

        assertThatThrownBy(() -> sender.send(sender.createMimeMessage()))
                .isInstanceOf(org.springframework.mail.MailSendException.class)
                .hasMessageNotContaining(ACCESS_TOKEN);
    }

    @Test
    void tokensAreNeverPrintableEvenByAccident() {
        GoogleTokens t = tokens(REFRESH_TOKEN);
        assertThat(t.toString()).doesNotContain(REFRESH_TOKEN).doesNotContain(ACCESS_TOKEN).contains("***");
    }

    // ---- SMTP keeps working ---------------------------------------------------------------------

    @Test
    void savingTheFormWhileGoogleIsConnectedDoesNotDemandAnSmtpPassword() throws Exception {
        connect();

        // The screen posts the whole form back, SMTP fields and all, with no credential to give.
        api.put(admin, "/api/admin/email-settings", Map.of(
                        "provider", "GMAIL", "senderName", "Lord Sai Academy", "senderEmail", GOOGLE_ACCOUNT,
                        "smtpHost", "smtp.gmail.com", "smtpPort", 587, "smtpUsername", GOOGLE_ACCOUNT,
                        "smtpPassword", "", "securityMode", "STARTTLS", "enabled", true,
                        "testRecipient", "ops@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.googleConnected").value(true));

        // The saved connection survived the save untouched.
        EmailSettings after = repository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(crypto.decrypt(after.getGoogleRefreshTokenEnc())).isEqualTo(REFRESH_TOKEN);
        assertThat(after.getAuthMode()).isEqualTo(MailAuthMode.GMAIL_OAUTH);
    }

    @Test
    void anSmtpOnlyConfigurationIsCompletelyUnaffectedByTheOAuthFeature() {
        EmailSettings s = new EmailSettings();
        s.setSenderName("Lord Sai Academy");
        s.setSenderEmail("smtp@example.com");
        s.setSmtpHost("smtp.example.com");
        s.setSmtpPort(587);
        s.setSecurityMode(MailSecurityMode.STARTTLS);
        s.setSmtpUsername("smtp@example.com");
        s.setSmtpPasswordEnc(crypto.encrypt("an-smtp-password"));
        s.setEnabled(true);
        repository.save(s);
        resolver.invalidate();

        // New rows default to SMTP, and the resolver still builds an ordinary SMTP sender.
        assertThat(s.getAuthMode()).isEqualTo(MailAuthMode.SMTP);
        assertThat(s.googleConnected()).isFalse();
        Optional<MailSenderResolver.ActiveMail> channel = resolver.resolve();
        assertThat(channel).isPresent();
        assertThat(channel.get().source()).isEqualTo(MailSenderResolver.SOURCE_ADMIN);
        assertThat(channel.get().error()).isNull();
        assertThat(channel.get().fromEmail()).isEqualTo("smtp@example.com");
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private GoogleTokens tokens(String refreshToken) {
        return new GoogleTokens(ACCESS_TOKEN, refreshToken, Instant.now().plusSeconds(3600),
                GoogleOAuthClient.SCOPE, GOOGLE_ACCOUNT);
    }

    /** Runs a full, valid authorisation round trip so a test can start from "connected". */
    private void connect() throws Exception {
        when(googleClient.exchangeCode("valid-code")).thenReturn(tokens(REFRESH_TOKEN));
        mockMvc.perform(get("/api/public/email/google/callback")
                        .param("code", "valid-code").param("state", stateService.issue(adminId)))
                .andExpect(status().isFound());
        resolver.invalidate();
    }
}
