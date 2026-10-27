package com.lordsai.lsi.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

/**
 * Looks up a domain's MX records so a custom domain (e.g. admin@company.com) can be matched to
 * the provider actually hosting its mail. Uses the JDK's DNS JNDI provider — no extra library —
 * with short timeouts so an unreachable resolver cannot stall the admin screen. Any failure
 * yields an empty list, which the caller treats as "could not determine".
 */
@Component
public class MxResolver {

    private static final Logger log = LoggerFactory.getLogger(MxResolver.class);

    public List<String> lookup(String domain) {
        if (domain == null || domain.isBlank()) {
            return List.of();
        }
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        try {
            InitialDirContext ctx = new InitialDirContext(env);
            try {
                Attributes attrs = ctx.getAttributes(domain.trim().toLowerCase(Locale.ROOT), new String[]{"MX"});
                Attribute mx = attrs.get("MX");
                List<String> hosts = new ArrayList<>();
                if (mx != null) {
                    for (int i = 0; i < mx.size(); i++) {
                        // "10 aspmx.l.google.com." -> "aspmx.l.google.com"
                        String[] parts = String.valueOf(mx.get(i)).trim().split("\\s+");
                        String host = parts[parts.length - 1];
                        hosts.add(host.endsWith(".") ? host.substring(0, host.length() - 1) : host);
                    }
                }
                return hosts;
            } finally {
                ctx.close();
            }
        } catch (NamingException | RuntimeException e) {
            log.info("[EMAIL] MX lookup for {} failed: {}", domain, e.getClass().getSimpleName());
            return List.of();
        }
    }
}
