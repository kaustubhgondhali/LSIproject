package com.lordsai.lsi.email;

import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.email.google.GmailOAuthService;
import com.lordsai.lsi.entity.EmailSettings;
import com.lordsai.lsi.entity.enums.MailSecurityMode;
import com.lordsai.lsi.repository.EmailSettingsRepository;
import com.lordsai.lsi.security.SecretCrypto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

/**
 * Decides which channel outgoing mail uses, in this order:
 * <ol>
 *   <li>The Main Admin's saved Email Settings (when the row exists). Its "enabled" switch is
 *       authoritative — switching it off silences all system email even if the server
 *       environment is configured. Within that row the authentication mode decides the transport:
 *       a connected Google account sends through the Gmail API (no SMTP socket, no password),
 *       otherwise the saved SMTP host/port/credential is used.</li>
 *   <li>Otherwise the server environment (MAIL_ENABLED=true + spring.mail.*).</li>
 *   <li>Otherwise nothing: email is written to the log instead.</li>
 * </ol>
 * The admin-configured sender is built once and reused until the settings row changes.
 */
@Component
public class MailSenderResolver {

    public static final String SOURCE_ADMIN = "ADMIN";
    public static final String SOURCE_ENVIRONMENT = "ENVIRONMENT";
    public static final String SOURCE_NONE = "NONE";
    /** The academy's Google account, authorised once via OAuth and sending through the Gmail API. */
    public static final String SOURCE_GOOGLE = "GOOGLE_OAUTH";

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int IO_TIMEOUT_MS = 20_000;

    /**
     * A configured outgoing channel: the sender plus the From and Reply-To identity to stamp on messages.
     * {@code error} is set instead of {@code sender} when the saved settings exist but cannot be
     * used (e.g. the stored password no longer decrypts because JWT_SECRET changed).
     */
    public record ActiveMail(JavaMailSender sender, String fromEmail, String fromName, String replyTo, String source, String error) {
        public ActiveMail(JavaMailSender sender, String fromEmail, String fromName, String source) {
            this(sender, fromEmail, fromName, null, source, null);
        }
        public ActiveMail(JavaMailSender sender, String fromEmail, String fromName, String replyTo, String source) {
            this(sender, fromEmail, fromName, replyTo, source, null);
        }
    }

    public static final String UNDECRYPTABLE =
            "The saved SMTP password cannot be decrypted (was the server's JWT_SECRET changed?)."
                    + " Re-enter the password in Email Settings.";

    /** Shown when Gmail OAuth is the saved mode but the server is missing its Google client settings. */
    public static final String GOOGLE_NOT_CONFIGURED =
            "A Google account is connected but this server is missing its Google OAuth settings"
                    + " (GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REDIRECT_URI). Set them and restart the backend.";

    private final EmailSettingsRepository repository;
    private final SecretCrypto crypto;
    private final ObjectProvider<JavaMailSender> environmentSender;
    private final AppProperties properties;
    private final GmailOAuthService gmailOAuth;

    private volatile JavaMailSenderImpl cachedSender;
    private volatile Long cachedId;
    private volatile Instant cachedVersion;

    public MailSenderResolver(EmailSettingsRepository repository,
                              SecretCrypto crypto,
                              ObjectProvider<JavaMailSender> environmentSender,
                              AppProperties properties,
                              GmailOAuthService gmailOAuth) {
        this.repository = repository;
        this.crypto = crypto;
        this.environmentSender = environmentSender;
        this.properties = properties;
        this.gmailOAuth = gmailOAuth;
    }

    /** The channel system emails go through right now, if any. */
    public Optional<ActiveMail> resolve() {
        Optional<EmailSettings> saved = repository.findFirstByOrderByIdAsc();
        if (saved.isPresent()) {
            EmailSettings s = saved.get();
            return s.isEnabled() ? Optional.of(fromSettings(s)) : Optional.empty();
        }
        return environment();
    }

    /**
     * The channel a test email should use: the saved settings even while disabled (the admin
     * tests before switching on), else the environment.
     */
    public Optional<ActiveMail> resolveForTest() {
        return repository.findFirstByOrderByIdAsc().map(this::fromSettings).or(this::environment);
    }

    /** Where mail is coming from, for the admin screen. */
    public String currentSource() {
        return resolve().map(ActiveMail::source).orElse(SOURCE_NONE);
    }

    /** Why nothing is being sent right now — shown to admins when an email could not go out. */
    public String disabledReason() {
        Optional<EmailSettings> saved = repository.findFirstByOrderByIdAsc();
        if (saved.isPresent() && !saved.get().isEnabled()) {
            return "Email sending is switched off in Email Settings (Main Admin > Email Settings > Enable Email Sending).";
        }
        if (saved.isPresent() && saved.get().getAuthMode() == com.lordsai.lsi.entity.enums.MailAuthMode.GMAIL_OAUTH
                && !saved.get().googleConnected()) {
            return "No Google account is connected. Connect one in Main Admin > Email Settings.";
        }
        return EmailDelivery.disabled().reason();
    }

    /** Forces the next resolve() to rebuild the admin sender (called after a save). */
    public void invalidate() {
        cachedSender = null;
        cachedId = null;
        cachedVersion = null;
        gmailOAuth.invalidate();
    }

    private Optional<ActiveMail> environment() {
        if (properties.mail() == null || !properties.mail().enabled()) {
            return Optional.empty();
        }
        JavaMailSender sender = environmentSender.getIfAvailable();
        if (sender == null) {
            return Optional.empty();
        }
        String supportReplyTo = (properties.mail().replyTo() != null && !properties.mail().replyTo().isBlank())
                ? properties.mail().replyTo()
                : (properties.support() != null ? properties.support().email() : null);
        return Optional.of(new ActiveMail(sender, properties.mail().from(), null, supportReplyTo, SOURCE_ENVIRONMENT));
    }

    private ActiveMail fromSettings(EmailSettings s) {
        String replyTo = s.getReplyTo() != null && !s.getReplyTo().isBlank()
                ? s.getReplyTo()
                : (properties.support() != null ? properties.support().email() : null);

        // Gmail OAuth is a property of the same row: when the admin has connected a Google account,
        // mail goes through the Gmail API and the SMTP columns are simply not consulted.
        if (s.googleConnected()) {
            return gmailOAuth.senderFor(s)
                    .<ActiveMail>map(g -> new ActiveMail(g, s.getGoogleEmail(), s.getSenderName(), replyTo, SOURCE_GOOGLE))
                    .orElseGet(() -> new ActiveMail(null, s.getGoogleEmail(), s.getSenderName(), replyTo, SOURCE_GOOGLE,
                            GOOGLE_NOT_CONFIGURED));
        }
        JavaMailSenderImpl sender = cachedSender;
        if (sender == null || !s.getId().equals(cachedId) || !s.getUpdatedAt().equals(cachedVersion)) {
            try {
                sender = build(s);
            } catch (IllegalStateException e) {
                // SecretCrypto: the stored blob does not decrypt under the current JWT_SECRET.
                return new ActiveMail(null, s.getSenderEmail(), s.getSenderName(), replyTo, SOURCE_ADMIN, UNDECRYPTABLE);
            }
            cachedSender = sender;
            cachedId = s.getId();
            cachedVersion = s.getUpdatedAt();
        }
        return new ActiveMail(sender, s.getSenderEmail(), s.getSenderName(), replyTo, SOURCE_ADMIN);
    }

    /** Builds a sender from the saved settings. Public so a test can be run against unsaved-but-validated values. */
    public JavaMailSenderImpl build(EmailSettings s) {
        return build(s, s.getSmtpPasswordEnc() == null ? "" : crypto.decrypt(s.getSmtpPasswordEnc()));
    }

    /** Same, with the password supplied in plain text (never stored) — used by "Test SMTP Connection". */
    public JavaMailSenderImpl build(EmailSettings s, String plainPassword) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(s.getSmtpHost());
        sender.setPort(s.getSmtpPort());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        sender.setProtocol("smtp");

        boolean auth = s.getSmtpUsername() != null && !s.getSmtpUsername().isBlank();
        if (auth) {
            sender.setUsername(s.getSmtpUsername());
            sender.setPassword(plainPassword == null ? "" : plainPassword);
        }

        Properties p = sender.getJavaMailProperties();
        p.put("mail.smtp.auth", String.valueOf(auth));
        // JavaMail's defaults are infinite; a wrong host must fail in seconds, not hang a request.
        p.put("mail.smtp.connectiontimeout", String.valueOf(CONNECT_TIMEOUT_MS));
        p.put("mail.smtp.timeout", String.valueOf(IO_TIMEOUT_MS));
        p.put("mail.smtp.writetimeout", String.valueOf(IO_TIMEOUT_MS));
        MailSecurityMode mode = s.getSecurityMode() == null ? MailSecurityMode.STARTTLS : s.getSecurityMode();
        switch (mode) {
            case STARTTLS -> {
                p.put("mail.smtp.starttls.enable", "true");
                p.put("mail.smtp.starttls.required", "true");
                p.put("mail.smtp.ssl.enable", "false");
            }
            case SSL_TLS -> {
                p.put("mail.smtp.ssl.enable", "true");
                p.put("mail.smtp.starttls.enable", "false");
            }
            case NONE -> {
                p.put("mail.smtp.starttls.enable", "false");
                p.put("mail.smtp.ssl.enable", "false");
            }
        }
        return sender;
    }
}
