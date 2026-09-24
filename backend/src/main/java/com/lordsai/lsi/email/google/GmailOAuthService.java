package com.lordsai.lsi.email.google;

import com.lordsai.lsi.entity.EmailSettings;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.MailAuthMode;
import com.lordsai.lsi.entity.enums.MailSecurityMode;
import com.lordsai.lsi.repository.EmailSettingsRepository;
import com.lordsai.lsi.security.SecretCrypto;
import com.lordsai.lsi.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Owns the academy's Google connection: the authorisation round trip, the encrypted refresh token,
 * and the supply of short-lived access tokens to {@link GmailApiMailSender}.
 *
 * <p>Token handling rules enforced here:
 * <ul>
 *   <li>the refresh token is encrypted at rest with the same {@code SecretCrypto} as the SMTP
 *       password and the payment gateway secrets;</li>
 *   <li>access tokens are held in memory only, never written to the database;</li>
 *   <li>no token, code or client secret is ever logged or returned from a method that feeds an
 *       API response.</li>
 * </ul>
 */
@Service
public class GmailOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GmailOAuthService.class);

    private final EmailSettingsRepository repository;
    private final SecretCrypto crypto;
    private final GoogleOAuthClient client;
    private final GoogleOAuthStateService stateService;
    private final GoogleOAuthProperties properties;
    private final AuditService auditService;

    /** Current access token. In memory only, and dropped whenever the connection changes. */
    private volatile GoogleTokens cachedAccess;
    private volatile GmailApiMailSender cachedSender;

    public GmailOAuthService(EmailSettingsRepository repository,
                             SecretCrypto crypto,
                             GoogleOAuthClient client,
                             GoogleOAuthStateService stateService,
                             GoogleOAuthProperties properties,
                             AuditService auditService) {
        this.repository = repository;
        this.crypto = crypto;
        this.client = client;
        this.stateService = stateService;
        this.properties = properties;
        this.auditService = auditService;
    }

    public boolean configured() {
        return properties.isConfigured();
    }

    /** The exact redirect URI this server will use, so the admin screen can show what to register. */
    public String redirectUri() {
        return properties.redirectUri();
    }

    // ---- connect ------------------------------------------------------------------------------

    /** Step 1: where to send the admin's browser. Carries a signed, single-use state value. */
    public String authorizationUrl(User actor) {
        if (!configured()) {
            throw new GoogleAuthException("Google sign-in is not configured on this server. Set GOOGLE_CLIENT_ID, "
                    + "GOOGLE_CLIENT_SECRET and GOOGLE_REDIRECT_URI, then restart the backend.", false);
        }
        return client.authorizationUrl(stateService.issue(actor.getId()));
    }

    /** Validates the state Google echoed back, returning the admin who started the flow. */
    public Optional<Long> consumeState(String state) {
        return stateService.consume(state);
    }

    /**
     * Step 2: turn the authorization code into a stored connection.
     *
     * <p>Creates the {@code email_settings} row if the academy had none. When a row already exists
     * its SMTP columns are left exactly as they are, so the previous configuration survives and the
     * academy can switch back to SMTP without re-entering anything.
     *
     * @return the connected Google address
     */
    @Transactional
    public String completeConnection(String code, User actor, String ip) {
        GoogleTokens tokens = client.exchangeCode(code);
        if (!tokens.hasRefreshToken()) {
            // Without a refresh token the connection would die within the hour.
            throw new GoogleAuthException("Google did not return a long-lived authorization. Remove this application "
                    + "from your Google account's third-party access list and connect again.", false);
        }
        if (!tokens.grantsSend()) {
            throw new GoogleAuthException("Permission to send email was not granted. Please connect again and leave "
                    + "the \"Send email on your behalf\" permission ticked.", false);
        }

        EmailSettings s = repository.findFirstByOrderByIdAsc().orElseGet(() -> {
            EmailSettings fresh = new EmailSettings();
            fresh.setSenderName("Lord Sai Academy");
            // The SMTP columns are NOT NULL; the standard Gmail values keep the row valid and give
            // a working starting point if the academy ever switches back to SMTP.
            fresh.setSmtpHost("smtp.gmail.com");
            fresh.setSmtpPort(587);
            fresh.setSecurityMode(MailSecurityMode.STARTTLS);
            fresh.setProvider("GMAIL");
            fresh.setCreatedBy(actor);
            // A connection made from scratch is meant to be used.
            fresh.setEnabled(true);
            return fresh;
        });

        String email = tokens.email();
        if (email == null && s.getGoogleEmail() != null) {
            email = s.getGoogleEmail();
        }

        s.setAuthMode(MailAuthMode.GMAIL_OAUTH);
        s.setGoogleEmail(email);
        s.setGoogleRefreshTokenEnc(crypto.encrypt(tokens.refreshToken()));
        s.setGoogleScope(tokens.scope());
        s.setGoogleConnectedAt(Instant.now());
        s.setGoogleConnectedBy(actor);
        // Gmail rejects a From address the authorised account does not own, so the sender identity
        // follows the connected account. This is exactly the mismatch App Password setups hit.
        if (email != null) {
            s.setSenderEmail(email);
        }
        s.setUpdatedBy(actor);
        repository.save(s);
        invalidate();
        // Hold the access token we already have rather than immediately asking for another.
        cachedAccess = tokens;

        auditService.record(actor, "EMAIL_GOOGLE_CONNECTED", "EmailSettings", s.getId(),
                "Gmail OAuth connected for " + email, ip);
        log.info("[GMAIL OAUTH] Connected account {} (authorised by {})", email, actor.getEmail());
        return email;
    }

    // ---- disconnect ---------------------------------------------------------------------------

    /**
     * Withdraws the connection: asks Google to revoke the grant, then deletes the stored token and
     * returns the row to SMTP mode. The SMTP settings are untouched, so sending falls back to them
     * if they are configured.
     */
    @Transactional
    public String disconnect(User actor, String ip) {
        EmailSettings s = repository.findFirstByOrderByIdAsc()
                .filter(e -> e.getGoogleRefreshTokenEnc() != null)
                .orElseThrow(() -> new GoogleAuthException("No Google account is connected.", false));

        String email = s.getGoogleEmail();
        boolean revoked = false;
        try {
            revoked = client.revoke(crypto.decrypt(s.getGoogleRefreshTokenEnc()));
        } catch (IllegalStateException e) {
            // The stored token no longer decrypts; nothing to revoke, but it must still be cleared.
            log.warn("[GMAIL OAUTH] Stored refresh token could not be read for revocation; clearing it anyway.");
        }

        s.setAuthMode(MailAuthMode.SMTP);
        s.setGoogleRefreshTokenEnc(null);
        s.setGoogleEmail(null);
        s.setGoogleScope(null);
        s.setGoogleConnectedAt(null);
        s.setGoogleConnectedBy(null);
        s.setUpdatedBy(actor);
        // Without a Google account and without an SMTP credential there is nothing to send with;
        // leaving "enabled" on would silently drop mail into the log.
        if (s.getSmtpPasswordEnc() == null) {
            s.setEnabled(false);
        }
        repository.save(s);
        invalidate();

        auditService.record(actor, "EMAIL_GOOGLE_DISCONNECTED", "EmailSettings", s.getId(),
                "Gmail OAuth disconnected" + (email == null ? "" : " for " + email)
                        + (revoked ? " (access revoked at Google)" : " (local credentials removed)"), ip);
        log.info("[GMAIL OAUTH] Disconnected account {} by {}", email, actor.getEmail());
        return revoked
                ? "Google account disconnected and access revoked at Google."
                : "Google account disconnected. The stored credentials have been removed.";
    }

    // ---- sending ------------------------------------------------------------------------------

    /** The mail sender for the connected account, or empty when no Google account is connected. */
    public Optional<GmailApiMailSender> senderFor(EmailSettings settings) {
        if (settings == null || !settings.googleConnected() || !configured()) {
            return Optional.empty();
        }
        GmailApiMailSender sender = cachedSender;
        if (sender == null) {
            sender = new GmailApiMailSender(client, this::accessToken);
            cachedSender = sender;
        }
        return Optional.of(sender);
    }

    /**
     * A valid access token, refreshed from the stored refresh token when the cached one has aged
     * out. Called on every send, so the common path is a cache hit with no network round trip.
     */
    public String accessToken() {
        GoogleTokens current = cachedAccess;
        if (current != null && current.accessToken() != null && !current.expired()) {
            return current.accessToken();
        }
        synchronized (this) {
            current = cachedAccess;
            if (current != null && current.accessToken() != null && !current.expired()) {
                return current.accessToken();
            }
            EmailSettings s = repository.findFirstByOrderByIdAsc()
                    .filter(EmailSettings::googleConnected)
                    .orElseThrow(() -> new GoogleAuthException(
                            "No Google account is connected. Connect one in Main Admin > Email Settings.", false));
            String refreshToken;
            try {
                refreshToken = crypto.decrypt(s.getGoogleRefreshTokenEnc());
            } catch (IllegalStateException e) {
                throw new GoogleAuthException("The stored Google authorization can no longer be decrypted "
                        + "(the server's JWT_SECRET changed). Please reconnect your Google account.", true, e);
            }
            GoogleTokens refreshed = client.refreshAccessToken(refreshToken);
            if (refreshed.accessToken() == null) {
                throw GoogleAuthException.revoked();
            }
            cachedAccess = refreshed;
            return refreshed.accessToken();
        }
    }

    /** Drops the in-memory access token and sender so the next send re-reads the saved connection. */
    public void invalidate() {
        cachedAccess = null;
        cachedSender = null;
    }
}
