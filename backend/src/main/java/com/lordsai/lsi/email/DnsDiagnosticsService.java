package com.lordsai.lsi.email;

import com.lordsai.lsi.dto.admin.EmailSettingsDtos.DnsCheckResult;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.DomainDnsStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

/**
 * Performs safe, read-only DNS checks for SPF, DKIM, DMARC, and MX records using the JDK's
 * built-in JNDI DNS context (no third-party DNS library required).
 *
 * <p>Never attempts to create or modify DNS records (which require registrar/DNS zone access).
 * Returns clear diagnostic statuses: PASS, FAIL, NOT_CONFIGURED, or CHECK_FAILED.
 */
@Service
public class DnsDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(DnsDiagnosticsService.class);

    private static final String STATUS_PASS = "PASS";
    private static final String STATUS_FAIL = "FAIL";
    private static final String STATUS_NOT_CONFIGURED = "NOT_CONFIGURED";
    private static final String STATUS_CHECK_FAILED = "CHECK_FAILED";

    private final MxResolver mxResolver;

    public DnsDiagnosticsService(MxResolver mxResolver) {
        this.mxResolver = mxResolver;
    }

    /**
     * Runs full DNS diagnostics for the specified domain and optional DKIM selector.
     */
    public DomainDnsStatus checkDomain(String domain, String dkimSelector) {
        String cleanDomain = normalizeDomain(domain);
        String selector = dkimSelector == null ? "" : dkimSelector.trim();

        if (cleanDomain == null || cleanDomain.isBlank()) {
            return new DomainDnsStatus(
                    "",
                    selector,
                    List.of(),
                    false,
                    "No sending domain specified. Enter your organization domain (e.g. lordsai.com) to verify DNS."
            );
        }

        List<DnsCheckResult> checks = new ArrayList<>();

        // 1. SPF Check
        checks.add(checkSpf(cleanDomain));

        // 2. DKIM Check
        checks.add(checkDkim(cleanDomain, selector));

        // 3. DMARC Check
        checks.add(checkDmarc(cleanDomain));

        // 4. MX Check
        checks.add(checkMx(cleanDomain));

        boolean allPassed = checks.stream()
                .allMatch(c -> STATUS_PASS.equals(c.status()));

        String summary = allPassed
                ? "All domain authentication records (SPF, DKIM, DMARC, MX) are active and verified for @" + cleanDomain + "."
                : "One or more email authentication records are missing or need attention on @" + cleanDomain + ". Review the status below.";

        return new DomainDnsStatus(cleanDomain, selector, checks, allPassed, summary);
    }

    private DnsCheckResult checkSpf(String domain) {
        try {
            List<String> txtRecords = queryTxt(domain);
            List<String> spfRecords = txtRecords.stream()
                    .filter(r -> r.toLowerCase(Locale.ROOT).startsWith("v=spf1"))
                    .toList();

            if (spfRecords.isEmpty()) {
                return new DnsCheckResult(
                        "SPF",
                        domain,
                        STATUS_NOT_CONFIGURED,
                        "No SPF TXT record found. Add a TXT record for '" + domain + "' with 'v=spf1 ... ~all' authorizing your mail server or provider.",
                        txtRecords
                );
            }

            String record = spfRecords.get(0);
            boolean strict = record.contains("-all");
            boolean softFail = record.contains("~all");
            String qualifier = strict ? "strict policy (-all)" : softFail ? "softfail policy (~all)" : "neutral policy";

            return new DnsCheckResult(
                    "SPF",
                    domain,
                    STATUS_PASS,
                    "Valid SPF record found with " + qualifier + ": " + record,
                    spfRecords
            );
        } catch (NamingException e) {
            log.warn("[DNS] SPF lookup error for {}: {}", domain, e.getMessage());
            return new DnsCheckResult("SPF", domain, STATUS_CHECK_FAILED, "DNS query timed out or failed for SPF.", List.of());
        }
    }

    private DnsCheckResult checkDkim(String domain, String selector) {
        if (selector.isBlank()) {
            return new DnsCheckResult(
                    "DKIM",
                    "<selector>._domainkey." + domain,
                    STATUS_NOT_CONFIGURED,
                    "DKIM selector not specified. Enter the selector (e.g. 'default', 's1', 'k1', or 'resend') provided by your email provider.",
                    List.of()
            );
        }

        String dkimHost = selector + "._domainkey." + domain;
        try {
            List<String> txtRecords = queryTxt(dkimHost);
            List<String> dkimRecords = txtRecords.stream()
                    .filter(r -> r.toLowerCase(Locale.ROOT).contains("v=dkim1") || r.toLowerCase(Locale.ROOT).contains("p="))
                    .toList();

            if (dkimRecords.isEmpty()) {
                return new DnsCheckResult(
                        "DKIM",
                        dkimHost,
                        STATUS_FAIL,
                        "No DKIM TXT record found at '" + dkimHost + "'. Verify that the selector matches your provider's DKIM settings.",
                        txtRecords
                );
            }

            return new DnsCheckResult(
                    "DKIM",
                    dkimHost,
                    STATUS_PASS,
                    "Valid DKIM key record found for selector '" + selector + "'.",
                    dkimRecords
            );
        } catch (NamingException e) {
            log.warn("[DNS] DKIM lookup error for {}: {}", dkimHost, e.getMessage());
            return new DnsCheckResult("DKIM", dkimHost, STATUS_FAIL,
                    "No DKIM record resolved at '" + dkimHost + "'. Please check DNS propagation and selector name.", List.of());
        }
    }

    private DnsCheckResult checkDmarc(String domain) {
        String dmarcHost = "_dmarc." + domain;
        try {
            List<String> txtRecords = queryTxt(dmarcHost);
            List<String> dmarcRecords = txtRecords.stream()
                    .filter(r -> r.toLowerCase(Locale.ROOT).startsWith("v=dmarc1"))
                    .toList();

            if (dmarcRecords.isEmpty()) {
                return new DnsCheckResult(
                        "DMARC",
                        dmarcHost,
                        STATUS_NOT_CONFIGURED,
                        "No DMARC TXT record found at '" + dmarcHost + "'. Create a TXT record with at least 'v=DMARC1; p=none;' to monitor deliverability.",
                        txtRecords
                );
            }

            String record = dmarcRecords.get(0);
            return new DnsCheckResult(
                    "DMARC",
                    dmarcHost,
                    STATUS_PASS,
                    "Valid DMARC policy found: " + record,
                    dmarcRecords
            );
        } catch (NamingException e) {
            log.warn("[DNS] DMARC lookup error for {}: {}", dmarcHost, e.getMessage());
            return new DnsCheckResult("DMARC", dmarcHost, STATUS_NOT_CONFIGURED,
                    "No DMARC record found at '" + dmarcHost + "'.", List.of());
        }
    }

    private DnsCheckResult checkMx(String domain) {
        List<String> mxHosts = mxResolver.lookup(domain);
        if (mxHosts.isEmpty()) {
            return new DnsCheckResult(
                    "MX",
                    domain,
                    STATUS_FAIL,
                    "No MX records found for '" + domain + "'. Receiving mail servers might reject messages if the domain has no inbound mail configuration.",
                    List.of()
            );
        }
        return new DnsCheckResult(
                "MX",
                domain,
                STATUS_PASS,
                "MX records active (" + mxHosts.size() + " host(s) found): " + String.join(", ", mxHosts),
                mxHosts
        );
    }

    private List<String> queryTxt(String host) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2500");
        env.put("com.sun.jndi.dns.timeout.retries", "1");

        InitialDirContext ctx = new InitialDirContext(env);
        try {
            Attributes attrs = ctx.getAttributes(host.trim().toLowerCase(Locale.ROOT), new String[]{"TXT"});
            Attribute txt = attrs.get("TXT");
            List<String> results = new ArrayList<>();
            if (txt != null) {
                NamingEnumeration<?> e = txt.getAll();
                while (e.hasMore()) {
                    Object val = e.next();
                    if (val != null) {
                        String s = val.toString().trim();
                        // Strip wrapping quotes if returned by JNDI
                        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                            s = s.substring(1, s.length() - 1);
                        }
                        results.add(s);
                    }
                }
            }
            return results;
        } finally {
            try {
                ctx.close();
            } catch (Exception ignored) {
            }
        }
    }

    private String normalizeDomain(String input) {
        if (input == null) {
            return null;
        }
        String d = input.trim().toLowerCase(Locale.ROOT);
        if (d.contains("@")) {
            d = d.substring(d.indexOf('@') + 1);
        }
        if (d.endsWith(".")) {
            d = d.substring(0, d.length() - 1);
        }
        return d;
    }
}
