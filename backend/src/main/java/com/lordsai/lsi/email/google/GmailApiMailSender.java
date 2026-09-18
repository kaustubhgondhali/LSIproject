package com.lordsai.lsi.email.google;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A {@link org.springframework.mail.javamail.JavaMailSender} that delivers through the Gmail API
 * instead of an SMTP socket.
 *
 * <p>This is the whole point of the integration: because it extends {@link JavaMailSenderImpl},
 * the message is still built by the same {@code MimeMessageHelper} with the same Thymeleaf
 * templates, subjects, recipients and headers as before — only the final hop changes. Every
 * existing caller of {@code EmailService} (enrollment, password reset, login credentials, fee
 * receipts, website enquiries, testimonials, test email) works unchanged.
 *
 * <p>No SMTP connection is ever opened, so no password or App Password exists to store.
 */
public class GmailApiMailSender extends JavaMailSenderImpl {

    private static final Logger log = LoggerFactory.getLogger(GmailApiMailSender.class);

    /** Gmail's published cap for a single message, including Base64 overhead. */
    private static final int MAX_MESSAGE_BYTES = 35 * 1024 * 1024;

    private final GoogleOAuthClient client;
    /** Supplies a currently-valid access token, refreshing it when needed. Never caches it here. */
    private final Supplier<String> accessTokenSupplier;

    public GmailApiMailSender(GoogleOAuthClient client, Supplier<String> accessTokenSupplier) {
        this.client = client;
        this.accessTokenSupplier = accessTokenSupplier;
    }

    /**
     * Hooks into the one place {@link JavaMailSenderImpl} funnels all of its overloads through, so
     * {@code send(MimeMessage)}, {@code send(SimpleMailMessage)} and {@code send(MimeMessagePreparator)}
     * are all covered without reimplementing them.
     */
    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) throws MailException {
        String accessToken;
        try {
            accessToken = accessTokenSupplier.get();
        } catch (GoogleAuthException e) {
            // Mapped onto Spring's mail exceptions so existing catch blocks keep working.
            throw e.isReconnectRequired()
                    ? new MailAuthenticationException(e.getMessage(), e)
                    : new MailSendException(e.getMessage(), e);
        }

        Map<Object, Exception> failures = new LinkedHashMap<>();
        for (int i = 0; i < mimeMessages.length; i++) {
            MimeMessage message = mimeMessages[i];
            Object original = originalMessages == null || i >= originalMessages.length ? message : originalMessages[i];
            try {
                byte[] raw = serialise(message);
                if (raw.length > MAX_MESSAGE_BYTES) {
                    throw new GoogleAuthException("The message is too large for Gmail to send.", false);
                }
                client.sendRaw(accessToken, raw);
            } catch (GoogleAuthException e) {
                if (e.isReconnectRequired()) {
                    // An expired grant applies to every message; failing fast beats retrying each one.
                    throw new MailAuthenticationException(e.getMessage(), e);
                }
                failures.put(original, e);
            } catch (Exception e) {
                failures.put(original, e);
            }
        }
        if (!failures.isEmpty()) {
            throw new MailSendException(failures);
        }
    }

    /** Renders the built MimeMessage to the RFC 5322 bytes the Gmail API expects. */
    private byte[] serialise(MimeMessage message) {
        try {
            // Gmail assigns the Message-ID; saveChanges() finalises the headers either way.
            message.saveChanges();
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            message.writeTo(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new MailParseException("The email could not be assembled for sending.", e);
        }
    }

    /**
     * "Is this connection usable right now?" — for Gmail that means: can we still obtain an access
     * token from the stored refresh token? No SMTP socket is involved.
     */
    @Override
    public void testConnection() throws jakarta.mail.MessagingException {
        try {
            accessTokenSupplier.get();
        } catch (GoogleAuthException e) {
            log.warn("[GMAIL API] Connection check failed: {}", e.getMessage());
            throw new jakarta.mail.AuthenticationFailedException(e.getMessage());
        }
    }
}
