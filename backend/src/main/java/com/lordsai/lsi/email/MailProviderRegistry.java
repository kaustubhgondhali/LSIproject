package com.lordsai.lsi.email;

import com.lordsai.lsi.entity.enums.MailSecurityMode;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The one place that knows what each email provider's SMTP service looks like. The admin screen,
 * the detection endpoint and the save path all read from here — update a provider's host, port or
 * instructions in this file only.
 *
 * <p>Detection is deliberately conservative: a provider is reported from its public mailbox domains
 * or from the domain's MX records; anything else is CUSTOM with empty host/port rather than a guess.
 */
public final class MailProviderRegistry {

    /** How the SMTP username is derived. */
    public enum UsernameRule {
        /** The full sender address (Gmail, Outlook, Yahoo, Zoho, most custom servers). */
        FULL_EMAIL,
        /** Provider-issued credentials that are not the mailbox address (Amazon SES, Brevo). */
        PROVIDER_ISSUED
    }

    public record MailProvider(
            String code,
            String label,
            String type,
            /** Public mailbox domains that identify the provider directly. */
            List<String> domains,
            /** Substrings found in the MX hostnames of domains hosted with this provider. */
            List<String> mxPatterns,
            String smtpHost,
            Integer smtpPort,
            MailSecurityMode security,
            UsernameRule usernameRule,
            boolean authRequired,
            /** What the secret field should be called for this provider. */
            String credentialLabel,
            /** Short, actionable setup notes shown next to the credential field. */
            List<String> instructions
    ) {
        public boolean isCustom() {
            return CUSTOM.equals(code);
        }
    }

    public static final String AUTO = "AUTO";
    public static final String CUSTOM = "CUSTOM";

    private static final List<String> APP_PASSWORD_NEVER_GENERATED = List.of(
            "For security reasons this application cannot generate or retrieve your provider password — enter it yourself and it is stored encrypted.");

    private static final List<MailProvider> PROVIDERS = List.of(
            new MailProvider("GMAIL", "Gmail / Google Workspace", "Consumer & Workspace mailbox",
                    List.of("gmail.com", "googlemail.com"),
                    List.of("google.com", "googlemail.com", "aspmx.l.google.com"),
                    "smtp.gmail.com", 587, MailSecurityMode.STARTTLS, UsernameRule.FULL_EMAIL, true,
                    "Google App Password (16 characters)",
                    List.of("Gmail SMTP does not accept your normal Google password when 2-Step Verification is on — create an App Password: Google Account → Security → 2-Step Verification → App passwords.",
                            "Choose app \"Mail\", copy the 16-character password and paste it here (spaces are ignored).",
                            "Google Workspace domains: the same steps apply; the administrator may need to allow \"less secure app\" style SMTP access or use App Passwords.",
                            APP_PASSWORD_NEVER_GENERATED.get(0))),
            new MailProvider("OUTLOOK", "Microsoft 365 / Outlook / Hotmail", "Consumer & business mailbox",
                    List.of("outlook.com", "hotmail.com", "live.com", "msn.com", "outlook.in", "hotmail.co.uk", "hotmail.co.in", "live.in"),
                    List.of("mail.protection.outlook.com", "olc.protection.outlook.com", "outlook.com"),
                    "smtp.office365.com", 587, MailSecurityMode.STARTTLS, UsernameRule.FULL_EMAIL, true,
                    "Mailbox password or app password",
                    List.of("Microsoft 365: \"Authenticated SMTP\" must be enabled for the mailbox (Microsoft 365 admin center → Users → Mail → Manage email apps).",
                            "Personal Outlook/Hotmail accounts with two-step verification need an app password: Microsoft account → Security → Advanced security options → App passwords.",
                            "Microsoft is retiring basic SMTP authentication for some tenants; if login keeps failing, ask your Microsoft 365 admin to confirm SMTP AUTH is allowed.",
                            APP_PASSWORD_NEVER_GENERATED.get(0))),
            new MailProvider("YAHOO", "Yahoo Mail", "Consumer mailbox",
                    List.of("yahoo.com", "yahoo.co.in", "yahoo.in", "ymail.com", "rocketmail.com", "yahoo.co.uk"),
                    List.of("yahoodns.net", "yahoo.com"),
                    "smtp.mail.yahoo.com", 465, MailSecurityMode.SSL_TLS, UsernameRule.FULL_EMAIL, true,
                    "Yahoo app password",
                    List.of("Yahoo requires an app password for SMTP: Yahoo Account → Security → Generate app password.",
                            "Port 465 (SSL/TLS) is used; port 587 with STARTTLS also works if your network blocks 465.",
                            APP_PASSWORD_NEVER_GENERATED.get(0))),
            new MailProvider("ZOHO", "Zoho Mail", "Business mailbox",
                    List.of("zoho.com", "zohomail.com", "zoho.in", "zohomail.in", "zoho.eu"),
                    List.of("zoho.com", "zoho.in", "zoho.eu", "zohomail"),
                    "smtp.zoho.com", 587, MailSecurityMode.STARTTLS, UsernameRule.FULL_EMAIL, true,
                    "Zoho password or app-specific password",
                    List.of("If two-factor authentication is on, create an application-specific password: Zoho Accounts → Security → App Passwords.",
                            "Accounts on the India data centre use smtp.zoho.in (detected automatically for zoho.in addresses / MX records); Europe uses smtp.zoho.eu.",
                            "IMAP/SMTP access must be enabled in Zoho Mail → Settings → Mail Accounts.",
                            APP_PASSWORD_NEVER_GENERATED.get(0))),
            new MailProvider("AMAZON_SES", "Amazon SES", "Transactional sending service",
                    List.of(), List.of(),
                    "email-smtp.ap-south-1.amazonaws.com", 587, MailSecurityMode.STARTTLS, UsernameRule.PROVIDER_ISSUED, true,
                    "SES SMTP password",
                    List.of("The SMTP username and password are the SMTP credentials created in the SES console (they are NOT your AWS login or the mailbox password).",
                            "The host is region specific — ap-south-1 (Mumbai) is pre-filled; change the region part if your SES identity lives elsewhere.",
                            "The sender address or domain must be verified in SES, and a new account must be moved out of the SES sandbox to email arbitrary recipients.",
                            APP_PASSWORD_NEVER_GENERATED.get(0))),
            new MailProvider("SENDGRID", "SendGrid", "Transactional sending service",
                    List.of(), List.of("sendgrid.net"),
                    "smtp.sendgrid.net", 587, MailSecurityMode.STARTTLS, UsernameRule.PROVIDER_ISSUED, true,
                    "SendGrid API key",
                    List.of("The SMTP username is always the literal string 'apikey'.",
                            "The password is a SendGrid API Key generated with 'Mail Send' permissions at SendGrid → Settings → API Keys.",
                            "The sender email address or domain must be verified in SendGrid Settings → Sender Authentication.",
                            APP_PASSWORD_NEVER_GENERATED.get(0))),
            new MailProvider("RESEND", "Resend", "Transactional sending service",
                    List.of(), List.of("resend.com"),
                    "smtp.resend.com", 587, MailSecurityMode.STARTTLS, UsernameRule.PROVIDER_ISSUED, true,
                    "Resend API key",
                    List.of("The SMTP username is always the literal string 'resend'.",
                            "The password is an API key starting with 're_' generated in the Resend Dashboard (API Keys section).",
                            "The sending domain must be verified in Resend with SPF and DKIM DNS records.",
                            APP_PASSWORD_NEVER_GENERATED.get(0))),
            new MailProvider("MAILGUN", "Mailgun", "Transactional sending service",
                    List.of(), List.of("mailgun.org"),
                    "smtp.mailgun.org", 587, MailSecurityMode.STARTTLS, UsernameRule.PROVIDER_ISSUED, true,
                    "Mailgun SMTP password",
                    List.of("The SMTP username is the postmaster or domain user issued in Mailgun → Sending → Domains → Domain settings → SMTP credentials.",
                            "The password is the SMTP password generated for that credential in Mailgun.",
                            "The sending domain must be verified with DNS records (SPF, DKIM, MX).",
                            APP_PASSWORD_NEVER_GENERATED.get(0))),
            new MailProvider("POSTMARK", "Postmark", "Transactional sending service",
                    List.of(), List.of("postmarkapp.com"),
                    "smtp.postmarkapp.com", 587, MailSecurityMode.STARTTLS, UsernameRule.PROVIDER_ISSUED, true,
                    "Postmark Server API token",
                    List.of("Both the SMTP username and password are the Postmark Server API token for your Message Stream.",
                            "The sender signature or domain must be verified in Postmark.",
                            APP_PASSWORD_NEVER_GENERATED.get(0))),
            new MailProvider("BREVO", "Brevo (Sendinblue)", "Transactional sending service",
                    List.of(), List.of(),
                    "smtp-relay.brevo.com", 587, MailSecurityMode.STARTTLS, UsernameRule.PROVIDER_ISSUED, true,
                    "Brevo SMTP key",
                    List.of("The SMTP username is your Brevo account login email; the password is an SMTP key generated at Brevo → SMTP & API → SMTP.",
                            "The sender address must be a verified sender in Brevo.",
                            APP_PASSWORD_NEVER_GENERATED.get(0))),
            new MailProvider(CUSTOM, "Custom SMTP", "Any other mail server",
                    List.of(), List.of(),
                    null, null, MailSecurityMode.STARTTLS, UsernameRule.FULL_EMAIL, true,
                    "SMTP password",
                    List.of("Enter the SMTP host, port and security exactly as given by your email host (typically port 587 with STARTTLS or 465 with SSL/TLS).",
                            "The username is usually the full email address; leave it blank only for relays that need no authentication.",
                            APP_PASSWORD_NEVER_GENERATED.get(0)))
    );

    private MailProviderRegistry() {
    }

    public static List<MailProvider> all() {
        return PROVIDERS;
    }

    public static Optional<MailProvider> byCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String c = code.trim().toUpperCase(Locale.ROOT);
        return PROVIDERS.stream().filter(p -> p.code().equals(c)).findFirst();
    }

    public static MailProvider custom() {
        return byCode(CUSTOM).orElseThrow();
    }

    /** Direct match on a public mailbox domain, e.g. "gmail.com" -> GMAIL. */
    public static Optional<MailProvider> byDomain(String domain) {
        if (domain == null) {
            return Optional.empty();
        }
        String d = domain.trim().toLowerCase(Locale.ROOT);
        return PROVIDERS.stream().filter(p -> p.domains().contains(d)).findFirst();
    }

    /** Match on the MX hostnames of a custom domain, e.g. "aspmx.l.google.com" -> GMAIL (Google Workspace). */
    public static Optional<MailProvider> byMx(List<String> mxHosts) {
        if (mxHosts == null) {
            return Optional.empty();
        }
        for (String mx : mxHosts) {
            String h = mx.toLowerCase(Locale.ROOT);
            for (MailProvider p : PROVIDERS) {
                if (p.mxPatterns().stream().anyMatch(h::contains)) {
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    /** Provider-specific host variants (Zoho regional data centres). */
    public static String hostFor(MailProvider p, String domain, List<String> mxHosts) {
        if ("ZOHO".equals(p.code())) {
            String all = (domain == null ? "" : domain) + " " + String.join(" ", mxHosts == null ? List.of() : mxHosts);
            if (all.contains("zoho.in") || all.contains("zohomail.in")) {
                return "smtp.zoho.in";
            }
            if (all.contains("zoho.eu")) {
                return "smtp.zoho.eu";
            }
        }
        return p.smtpHost();
    }
}
