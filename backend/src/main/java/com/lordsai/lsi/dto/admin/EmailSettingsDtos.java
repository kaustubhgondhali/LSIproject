package com.lordsai.lsi.dto.admin;

import com.lordsai.lsi.entity.enums.MailAuthMode;
import com.lordsai.lsi.entity.enums.MailSecurityMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class EmailSettingsDtos {

    private EmailSettingsDtos() {
    }

    /** Admin: the saved configuration with the password masked, plus what is actually in effect. */
    public record EmailSettingsStatus(
            boolean recordExists,
            String senderName,
            String senderEmail,
            String smtpHost,
            Integer smtpPort,
            String smtpUsername,
            boolean smtpPasswordSet,
            String smtpPasswordMasked,
            MailSecurityMode securityMode,
            boolean enabled,
            String testRecipient,
            /** ADMIN (this screen), ENVIRONMENT (server variables) or NONE — where outgoing mail comes from right now. */
            String effectiveSource,
            /** True when the system will actually send email at this moment. */
            boolean sendingActive,
            Instant lastTestedAt,
            Boolean lastTestOk,
            Instant createdAt,
            Instant updatedAt,
            String updatedBy,
            String provider,
            String providerLabel,
            String credentialLabel,
            Instant connectionVerifiedAt,
            /** False when a credential is stored but can no longer be decrypted (the encryption
             *  secret changed) — the screen then asks for it to be entered again. */
            boolean credentialReadable,
            /** SMTP or GMAIL_OAUTH — which authentication mode this configuration uses. */
            MailAuthMode authMode,
            /** True when a Google account is authorised and its refresh token is stored. */
            boolean googleConnected,
            /** The connected Gmail address, or null. Safe to show; it is the visible From identity. */
            String googleEmail,
            String replyTo,
            String sendingDomain,
            String dkimSelector
    ) {
    }

    /** One entry of the provider dropdown, straight from MailProviderRegistry. */
    public record ProviderInfo(String code, String label, String type, String smtpHost, Integer smtpPort, MailSecurityMode securityMode,
                               String usernameRule, boolean authRequired, String credentialLabel, List<String> instructions) {
    }

    public record DetectRequest(
            @NotBlank @Email(message = "Please enter a valid email address.") @Size(max = 190) String email,
            /** AUTO (default), a provider code, or CUSTOM. */
            @Size(max = 40) String provider
    ) {
    }

    /** What "Auto Configure Email" fills in. Host/port are null when nothing could be determined reliably. */
    public record DetectionResult(
            boolean detected,
            String provider,
            String providerLabel,
            String providerType,
            /** DOMAIN (public mailbox domain), MX (custom domain's mail records), MANUAL (chosen in dropdown), NONE. */
            String method,
            String senderEmail,
            String smtpHost,
            Integer smtpPort,
            MailSecurityMode securityMode,
            String smtpUsername,
            boolean authRequired,
            String credentialLabel,
            List<String> instructions,
            List<String> mxRecords,
            String message
    ) {
    }

    /** "Test SMTP Connection" runs against the values currently in the form; a blank password means the saved one. */
    public record TestConnectionRequest(
            @NotBlank @Size(max = 255)
            @Pattern(regexp = "^[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?$", message = "SMTP host must be a hostname or IP address.")
            String smtpHost,
            @NotNull @Min(value = 1, message = "SMTP port must be between 1 and 65535.")
            @Max(value = 65535, message = "SMTP port must be between 1 and 65535.") Integer smtpPort,
            @NotNull(message = "Choose a security mode.") MailSecurityMode securityMode,
            @Size(max = 190) String smtpUsername,
            @Size(max = 200) String smtpPassword
    ) {
    }

    public record ConnectionResult(boolean connected, boolean authenticated, String message, long elapsedMs) {
    }

    public record SaveEmailSettingsRequest(
            /** Provider code from the dropdown; AUTO/blank = detect from the sender address. */
            @Size(max = 40) String provider,
            @NotBlank @Size(max = 100) String senderName,
            @NotBlank @Email(message = "Sender email must be a valid email address.") @Size(max = 190) String senderEmail,
            @NotBlank @Size(max = 255)
            @Pattern(regexp = "^[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?$", message = "SMTP host must be a hostname or IP address.")
            String smtpHost,
            @NotNull @Min(value = 1, message = "SMTP port must be between 1 and 65535.")
            @Max(value = 65535, message = "SMTP port must be between 1 and 65535.") Integer smtpPort,
            @Size(max = 190) String smtpUsername,
            /** Blank on update means "keep the existing password". */
            @Size(max = 200) String smtpPassword,
            @NotNull(message = "Choose a security mode.") MailSecurityMode securityMode,
            boolean enabled,
            @Email(message = "Test recipient must be a valid email address.") @Size(max = 190) String testRecipient,
            @Email(message = "Reply-To must be a valid email address.") @Size(max = 190) String replyTo,
            @Size(max = 190) String sendingDomain,
            @Size(max = 100) String dkimSelector
    ) {
    }

    public record TestEmailRequest(
            /** Optional — falls back to the saved test recipient. */
            @Email(message = "Recipient must be a valid email address.") @Size(max = 190) String recipient
    ) {
    }

    /** DNS diagnostic result for a single record check (SPF, DKIM, DMARC, MX). */
    public record DnsCheckResult(
            String recordType,
            String queryHost,
            /** PASS, FAIL, NOT_CONFIGURED, CHECK_FAILED */
            String status,
            String message,
            List<String> recordsFound
    ) {
    }

    /** Overall domain readiness status for transactional deliverability. */
    public record DomainDnsStatus(
            String domain,
            String dkimSelector,
            List<DnsCheckResult> checks,
            boolean allPassed,
            String summary
    ) {
    }
}
