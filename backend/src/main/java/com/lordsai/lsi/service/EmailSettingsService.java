package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.admin.EmailSettingsDtos.ConnectionResult;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.DetectRequest;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.DetectionResult;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.EmailSettingsStatus;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.ProviderInfo;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.SaveEmailSettingsRequest;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.TestConnectionRequest;
import com.lordsai.lsi.email.DnsDiagnosticsService;
import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.email.MailProviderRegistry;
import com.lordsai.lsi.email.MailProviderRegistry.MailProvider;
import com.lordsai.lsi.email.MailSenderResolver;
import com.lordsai.lsi.email.MxResolver;
import com.lordsai.lsi.email.google.GmailOAuthService;
import com.lordsai.lsi.email.google.GoogleOAuthClient;
import com.lordsai.lsi.email.SmtpEmailService;
import com.lordsai.lsi.entity.EmailSettings;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.dto.admin.GoogleOAuthDtos.GoogleStatus;
import com.lordsai.lsi.entity.enums.MailAuthMode;
import com.lordsai.lsi.entity.enums.MailSecurityMode;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.repository.EmailSettingsRepository;
import com.lordsai.lsi.security.SecretCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Main Admin > Email Settings. Owns the single SMTP configuration row; the password is
 * encrypted with the same {@link SecretCrypto} as the payment gateway secrets and is never
 * returned, logged or echoed in an error.
 */
@Service
public class EmailSettingsService {

    private static final Logger log = LoggerFactory.getLogger(EmailSettingsService.class);
    private static final String MASK = "••••••••••••••••";

    private final EmailSettingsRepository repository;
    private final SecretCrypto crypto;
    private final MailSenderResolver resolver;
    private final EmailService emailService;
    private final AuditService auditService;
    private final MxResolver mxResolver;
    private final GmailOAuthService gmailOAuth;
    private final DnsDiagnosticsService dnsDiagnosticsService;

    public EmailSettingsService(EmailSettingsRepository repository,
                                SecretCrypto crypto,
                                MailSenderResolver resolver,
                                EmailService emailService,
                                AuditService auditService,
                                MxResolver mxResolver,
                                GmailOAuthService gmailOAuth,
                                DnsDiagnosticsService dnsDiagnosticsService) {
        this.repository = repository;
        this.crypto = crypto;
        this.resolver = resolver;
        this.emailService = emailService;
        this.auditService = auditService;
        this.mxResolver = mxResolver;
        this.gmailOAuth = gmailOAuth;
        this.dnsDiagnosticsService = dnsDiagnosticsService;
    }

    @Transactional(readOnly = true)
    public EmailSettingsStatus status() {
        Optional<EmailSettings> saved = repository.findFirstByOrderByIdAsc();
        String source = resolver.currentSource();
        boolean active = !MailSenderResolver.SOURCE_NONE.equals(source);
        return saved.map(s -> {
                    MailProvider p = MailProviderRegistry.byCode(s.getProvider()).orElse(MailProviderRegistry.custom());
                    return new EmailSettingsStatus(true, s.getSenderName(), s.getSenderEmail(), s.getSmtpHost(),
                            s.getSmtpPort(), s.getSmtpUsername(), s.getSmtpPasswordEnc() != null,
                            s.getSmtpPasswordEnc() != null ? MASK : null, s.getSecurityMode(), s.isEnabled(),
                            s.getTestRecipient(), source, active, s.getLastTestedAt(), s.getLastTestOk(),
                            s.getCreatedAt(), s.getUpdatedAt(),
                            s.getUpdatedBy() == null ? null : s.getUpdatedBy().getFullName(),
                            p.code(), p.label(), p.credentialLabel(), s.getConnectionVerifiedAt(),
                            credentialReadable(s), s.getAuthMode(), s.googleConnected(), s.getGoogleEmail(),
                            s.getReplyTo(), s.getSendingDomain(), s.getDkimSelector());
                })
                .orElseGet(() -> new EmailSettingsStatus(false, null, null, null, 587, null, false, null,
                        MailSecurityMode.STARTTLS, false, null, source, active, null, null, null, null, null,
                        null, null, null, null, true, MailAuthMode.SMTP, false, null, null, null, null));
    }

    /** Creates or updates the configuration. A blank password on update keeps the stored one. */
    @Transactional
    public EmailSettingsStatus save(SaveEmailSettingsRequest req, User actor, String ip) {
        Optional<EmailSettings> existing = repository.findFirstByOrderByIdAsc();
        EmailSettings s = existing.orElseGet(EmailSettings::new);
        boolean created = s.getId() == null;

        String username = blankToNull(req.smtpUsername());
        String password = req.smtpPassword() == null ? "" : req.smtpPassword();
        // With a Google account connected there is no SMTP credential to demand: the row keeps its
        // SMTP columns as a dormant fallback and authentication happens over OAuth instead.
        boolean google = s.googleConnected();
        if (!google && username != null && password.isBlank() && s.getSmtpPasswordEnc() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SMTP password is required when a username is set.");
        }
        // A stored credential that no longer decrypts must not be kept by a save that leaves the
        // password field blank — otherwise the admin "saves" and the broken credential silently survives.
        if (!google && username != null && password.isBlank() && !credentialReadable(s)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "The saved SMTP password can no longer be decrypted, so it cannot be reused. "
                            + "Please enter the " + MailProviderRegistry.byCode(s.getProvider())
                            .map(MailProvider::credentialLabel).orElse("SMTP password").toLowerCase(Locale.ROOT)
                            + " again and save.");
        }

        s.setSenderName(req.senderName().trim());
        if (!google) {
            s.setSenderEmail(req.senderEmail().trim().toLowerCase());
        }
        s.setReplyTo(blankToNull(req.replyTo()) == null ? null : req.replyTo().trim().toLowerCase(Locale.ROOT));
        s.setSendingDomain(blankToNull(req.sendingDomain()) == null ? null : req.sendingDomain().trim().toLowerCase(Locale.ROOT));
        s.setDkimSelector(blankToNull(req.dkimSelector()) == null ? null : req.dkimSelector().trim());
        s.setSmtpHost(req.smtpHost().trim());
        s.setSmtpPort(req.smtpPort());
        s.setSmtpUsername(username);
        if (username == null && !google) {
            s.setSmtpPasswordEnc(null);
        } else if (!password.isBlank()) {
            s.setSmtpPasswordEnc(crypto.encrypt(password));
        }
        s.setSecurityMode(req.securityMode());
        s.setEnabled(req.enabled());
        s.setTestRecipient(blankToNull(req.testRecipient()) == null ? null : req.testRecipient().trim().toLowerCase());
        String resolvedProvider = resolveProviderCode(req.provider(), s.getSenderEmail(), s.getSmtpHost());
        if (!google && "GMAIL".equalsIgnoreCase(resolvedProvider) && username != null && username.toLowerCase(Locale.ROOT).endsWith("@gmail.com")) {
            if (s.getSenderEmail().endsWith("@gmail.com") && !s.getSenderEmail().equalsIgnoreCase(username)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Gmail SMTP does not permit sending as an unrelated Gmail address. "
                                + "Sender email must match the authenticated Gmail username (" + username + ").");
            }
        }
        s.setProvider(resolvedProvider);
        if (created) {
            s.setCreatedBy(actor);
        }
        s.setUpdatedBy(actor);
        // Connection details changed: previous test results no longer prove anything, unless this
        // save carries exactly the settings that were verified moments ago (same host/port/user/secret).
        boolean sameConnection = !created && existing.get().getConnectionVerifiedAt() != null
                && s.getSmtpHost().equalsIgnoreCase(req.smtpHost().trim()) && s.getSmtpPort() == req.smtpPort()
                && java.util.Objects.equals(existing.get().getSmtpUsername(), username) && password.isBlank();
        if (!sameConnection) {
            s.setConnectionVerifiedAt(null);
        }
        s.setLastTestedAt(null);
        s.setLastTestOk(null);
        s = repository.save(s);
        resolver.invalidate();

        auditService.record(actor, created ? "EMAIL_SETTINGS_CREATED" : "EMAIL_SETTINGS_UPDATED", "EmailSettings", s.getId(),
                s.getSmtpHost() + ":" + s.getSmtpPort() + " " + s.getSecurityMode() + " as " + s.getSenderEmail()
                        + " — sending " + (s.isEnabled() ? "ENABLED" : "DISABLED"), ip);
        log.info("[EMAIL SETTINGS] {} by {}: host={} port={} security={} enabled={}",
                created ? "Created" : "Updated", actor.getEmail(), s.getSmtpHost(), s.getSmtpPort(), s.getSecurityMode(), s.isEnabled());
        return status();
    }

    /**
     * Sends the test message through the saved settings (or the server environment when nothing
     * is saved). The outcome is recorded on the row so the screen can show "last test: OK/failed" —
     * including a failure, hence the failure exception must not roll the record back.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public String sendTest(String recipient, User actor, String ip) {
        Optional<EmailSettings> saved = repository.findFirstByOrderByIdAsc();
        String to = blankToNull(recipient);
        if (to == null) {
            to = saved.map(EmailSettings::getTestRecipient).orElse(null);
        }
        if (to == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a test recipient email address.");
        }
        if (saved.isEmpty() && MailSenderResolver.SOURCE_NONE.equals(resolver.currentSource())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "No SMTP configuration exists. Save the email settings first.");
        }
        // A configuration whose provider needs authentication but holds no credential of either kind
        // cannot deliver anything. Saying so immediately beats a 30-second SMTP timeout — this is the
        // state the screen is in right after a Google account is disconnected.
        if (saved.isPresent() && !saved.get().googleConnected()
                && saved.get().getSmtpUsername() == null && saved.get().getSmtpPasswordEnc() == null
                && MailProviderRegistry.byCode(saved.get().getProvider()).map(MailProvider::authRequired).orElse(false)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "No Google account is connected and no SMTP credential is saved, so there is nothing to send with. "
                            + "Connect a Google account, or fill in the SMTP settings and save them.");
        }

        EmailDelivery result = emailService.sendTestEmail(to.trim().toLowerCase(), actor.getEmail());
        Instant now = Instant.now();
        saved.ifPresent(s -> {
            s.setLastTestedAt(now);
            s.setLastTestOk(result.delivered());
            repository.save(s);
        });
        auditService.record(actor, result.delivered() ? "EMAIL_TEST_SENT" : "EMAIL_TEST_FAILED", "EmailSettings",
                saved.map(EmailSettings::getId).orElse(null), "to " + to + (result.delivered() ? "" : " — " + result.reason()), ip);

        if (result.delivered()) {
            return "Test email sent successfully to " + to + ".";
        }
        String reason = result.status() == EmailDelivery.Status.DISABLED
                ? "No SMTP configuration is available."
                : result.reason();
        throw new ApiException(HttpStatus.BAD_GATEWAY,
                "Unable to send test email. " + reason
                        + " Please verify SMTP host, port, username, password and security settings.");
    }

    /** Deletes the saved configuration; outgoing mail falls back to the server environment (if any). */
    @Transactional
    public EmailSettingsStatus remove(User actor, String ip) {
        EmailSettings s = repository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No email settings are saved."));
        Long id = s.getId();
        String summary = s.getSmtpHost() + ":" + s.getSmtpPort();
        if (s.googleConnected()) {
            // Best effort: the row is going away regardless, but leaving a live grant behind at
            // Google would be worse than a failed revocation call.
            gmailOAuth.disconnect(actor, ip);
            s = repository.findFirstByOrderByIdAsc().orElse(s);
        }
        repository.delete(s);
        resolver.invalidate();
        auditService.record(actor, "EMAIL_SETTINGS_REMOVED", "EmailSettings", id, "Removed " + summary, ip);
        log.info("[EMAIL SETTINGS] Removed by {} ({})", actor.getEmail(), summary);
        return status();
    }

    // ---- Gmail via Google OAuth ----------------------------------------------------------------

    /**
     * Safe connection information for the screen. By construction this returns no access token,
     * no refresh token, no authorization code and no client secret — only whether a connection
     * exists and which Google address it belongs to.
     */
    @Transactional(readOnly = true)
    public GoogleStatus googleStatus() {
        boolean configured = gmailOAuth.configured();
        Optional<EmailSettings> saved = repository.findFirstByOrderByIdAsc();
        boolean connected = saved.map(EmailSettings::googleConnected).orElse(false);
        String scope = saved.map(EmailSettings::getGoogleScope).orElse(null);
        boolean canSend = scope != null && scope.contains(GoogleOAuthClient.SEND_SCOPE);
        boolean sendingThroughGmail = connected
                && MailSenderResolver.SOURCE_GOOGLE.equals(resolver.currentSource());

        String message;
        if (!configured) {
            message = "Google sign-in is not configured on this server. Set GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET "
                    + "and GOOGLE_REDIRECT_URI in the backend environment and restart it.";
        } else if (!connected) {
            message = "No Google account is connected. Click Connect Google Account to authorise the academy's Gmail account.";
        } else if (!canSend) {
            message = "The connected Google account did not grant permission to send email. Please reconnect and "
                    + "leave the send permission ticked.";
        } else if (!sendingThroughGmail) {
            message = saved.map(EmailSettings::isEnabled).orElse(false)
                    ? "Google account connected."
                    : "Google account connected. Switch on Enable Email Sending to start sending through it.";
        } else {
            message = "Google account connected. All system email is being sent through Gmail.";
        }

        return new GoogleStatus(configured, connected,
                saved.map(EmailSettings::getGoogleEmail).orElse(null),
                saved.map(EmailSettings::getGoogleConnectedAt).orElse(null),
                saved.map(EmailSettings::getGoogleConnectedBy).map(User::getFullName).orElse(null),
                canSend, gmailOAuth.redirectUri(), sendingThroughGmail, message);
    }

    // ---- provider detection / connection test --------------------------------------------------

    @Transactional(readOnly = true)
    public List<ProviderInfo> providers() {
        return MailProviderRegistry.all().stream().map(p -> new ProviderInfo(p.code(), p.label(), p.type(), p.smtpHost(), p.smtpPort(),
                p.security(), p.usernameRule().name(), p.authRequired(), p.credentialLabel(), p.instructions())).toList();
    }

    /**
     * Checks DNS records (SPF, DKIM, DMARC, MX) for the configured or requested sending domain.
     */
    @Transactional(readOnly = true)
    public com.lordsai.lsi.dto.admin.EmailSettingsDtos.DomainDnsStatus checkDomainDns(String domainOverride, String selectorOverride) {
        Optional<EmailSettings> saved = repository.findFirstByOrderByIdAsc();
        String domain = domainOverride != null && !domainOverride.isBlank()
                ? domainOverride
                : saved.map(EmailSettings::getSendingDomain)
                    .filter(d -> !d.isBlank())
                    .orElseGet(() -> saved.map(EmailSettings::getSenderEmail)
                        .filter(e -> e != null && e.contains("@"))
                        .map(e -> e.substring(e.indexOf('@') + 1))
                        .orElse(""));

        String selector = selectorOverride != null && !selectorOverride.isBlank()
                ? selectorOverride
                : saved.map(EmailSettings::getDkimSelector).orElse("");

        return dnsDiagnosticsService.checkDomain(domain, selector);
    }

    /**
     * "Auto Configure Email": the sender address alone decides the provider — by its public domain,
     * or for a custom domain by the MX records that show who hosts its mail. Nothing is guessed:
     * an unknown domain comes back as CUSTOM with empty host/port and a clear message.
     */
    public DetectionResult detect(DetectRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String domain = email.substring(email.indexOf('@') + 1);
        String wanted = req.provider() == null || req.provider().isBlank() ? MailProviderRegistry.AUTO : req.provider().trim().toUpperCase(Locale.ROOT);
        List<String> mx = List.of();
        MailProvider p;
        String method;

        if (!MailProviderRegistry.AUTO.equals(wanted)) {
            p = MailProviderRegistry.byCode(wanted).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown email provider: " + wanted));
            method = "MANUAL";
        } else {
            Optional<MailProvider> byDomain = MailProviderRegistry.byDomain(domain);
            if (byDomain.isPresent()) {
                p = byDomain.get();
                method = "DOMAIN";
            } else {
                mx = mxResolver.lookup(domain);
                Optional<MailProvider> byMx = MailProviderRegistry.byMx(mx);
                p = byMx.orElse(MailProviderRegistry.custom());
                method = byMx.isPresent() ? "MX" : "NONE";
            }
        }

        boolean detected = !p.isCustom();
        String username = p.usernameRule() == MailProviderRegistry.UsernameRule.FULL_EMAIL ? email : null;
        String host = detected ? MailProviderRegistry.hostFor(p, domain, mx) : null;
        String credential = p.credentialLabel().toLowerCase(Locale.ROOT);
        String message;
        if ("MANUAL".equals(method)) {
            message = p.isCustom() ? "Custom SMTP selected - enter the host, port and security given by your email host."
                    : "Standard " + p.label() + " SMTP settings loaded. " + (p.usernameRule() == MailProviderRegistry.UsernameRule.FULL_EMAIL
                    ? "Enter your " + credential + " to authenticate."
                    : "Enter the SMTP username and " + credential + " issued by " + p.label() + ".");
        } else if (detected) {
            message = "Email provider detected: " + p.label() + ("MX".equals(method) ? " (from the mail records of " + domain + ")" : "")
                    + ". SMTP configuration loaded automatically - only your " + credential + " is still needed.";
        } else {
            message = "Provider could not be automatically determined for @" + domain
                    + ". Please select your provider, or choose Custom SMTP and enter the settings from your email host.";
        }
        log.info("[EMAIL SETTINGS] Provider detection for @{}: {} via {}", domain, p.code(), method);
        return new DetectionResult(detected, p.code(), p.label(), p.type(), method, email, host, detected ? p.smtpPort() : null,
                p.security(), username, p.authRequired(), p.credentialLabel(), p.instructions(), mx, message);
    }

    /**
     * "Test SMTP Connection": opens an authenticated SMTP session with the values in the form
     * (nothing is saved). A blank password reuses the stored one, so the admin can re-verify
     * without retyping the secret. A success against the saved host/port/user is remembered.
     */
    @Transactional
    public ConnectionResult testConnection(TestConnectionRequest req, User actor, String ip) {
        Optional<EmailSettings> saved = repository.findFirstByOrderByIdAsc();
        String username = blankToNull(req.smtpUsername());
        String password = req.smtpPassword() == null ? "" : req.smtpPassword();
        if (username != null && password.isBlank()) {
            if (saved.isPresent() && saved.get().getSmtpPasswordEnc() != null && username.equalsIgnoreCase(saved.get().getSmtpUsername())) {
                try {
                    password = crypto.decrypt(saved.get().getSmtpPasswordEnc());
                } catch (IllegalStateException e) {
                    return new ConnectionResult(false, false, MailSenderResolver.UNDECRYPTABLE, 0);
                }
            } else {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Email provider credential is required to authenticate with this SMTP server.");
            }
        }

        EmailSettings probe = new EmailSettings();
        probe.setSmtpHost(req.smtpHost().trim());
        probe.setSmtpPort(req.smtpPort());
        probe.setSecurityMode(req.securityMode());
        probe.setSmtpUsername(username);
        long started = System.currentTimeMillis();
        try {
            resolver.build(probe, password).testConnection();
        } catch (Exception e) {
            String reason = SmtpEmailService.describe(e);
            log.warn("[EMAIL SETTINGS] SMTP connection test to {}:{} failed: {}", probe.getSmtpHost(), probe.getSmtpPort(), reason);
            auditService.record(actor, "EMAIL_CONNECTION_TEST_FAILED", "EmailSettings", saved.map(EmailSettings::getId).orElse(null),
                    probe.getSmtpHost() + ":" + probe.getSmtpPort() + " - " + reason, ip);
            boolean authProblem = reason.startsWith("Authentication failed");
            return new ConnectionResult(authProblem, false, authProblem
                    ? "SMTP connection succeeded but authentication failed. Please verify the email credential / App Password."
                    : "Unable to connect to the SMTP server. " + reason, System.currentTimeMillis() - started);
        }
        long elapsed = System.currentTimeMillis() - started;
        saved.filter(s -> s.getSmtpHost().equalsIgnoreCase(probe.getSmtpHost()) && s.getSmtpPort() == probe.getSmtpPort()
                        && java.util.Objects.equals(s.getSmtpUsername(), username))
                .ifPresent(s -> { s.setConnectionVerifiedAt(Instant.now()); repository.save(s); });
        auditService.record(actor, "EMAIL_CONNECTION_TEST_OK", "EmailSettings", saved.map(EmailSettings::getId).orElse(null),
                probe.getSmtpHost() + ":" + probe.getSmtpPort() + " " + probe.getSecurityMode() + (username == null ? " (no auth)" : " as " + username), ip);
        log.info("[EMAIL SETTINGS] SMTP connection test to {}:{} OK in {} ms", probe.getSmtpHost(), probe.getSmtpPort(), elapsed);
        return new ConnectionResult(true, username != null, username != null
                ? "SMTP connection successful. Authentication successful." : "SMTP connection successful (no authentication configured).", elapsed);
    }

    /** True when there is no stored credential, or there is one and it still decrypts. */
    private boolean credentialReadable(EmailSettings s) {
        if (s.getSmtpPasswordEnc() == null) {
            return true;
        }
        try {
            crypto.decrypt(s.getSmtpPasswordEnc());
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private String resolveProviderCode(String requested, String senderEmail, String host) {
        if (requested != null && !requested.isBlank() && !MailProviderRegistry.AUTO.equalsIgnoreCase(requested.trim())) {
            return MailProviderRegistry.byCode(requested).map(MailProvider::code)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown email provider: " + requested));
        }
        String domain = senderEmail.substring(senderEmail.indexOf('@') + 1);
        Optional<MailProvider> byDomain = MailProviderRegistry.byDomain(domain);
        if (byDomain.isPresent()) {
            return byDomain.get().code();
        }
        // Recognise a standard host typed by hand (e.g. smtp.gmail.com) without a DNS round-trip on every save.
        String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
        return MailProviderRegistry.all().stream()
                .filter(p -> p.smtpHost() != null && (h.equals(p.smtpHost()) || ("ZOHO".equals(p.code()) && h.startsWith("smtp.zoho."))
                        || ("AMAZON_SES".equals(p.code()) && h.startsWith("email-smtp.") && h.endsWith(".amazonaws.com"))))
                .map(MailProvider::code).findFirst().orElse(MailProviderRegistry.CUSTOM);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
