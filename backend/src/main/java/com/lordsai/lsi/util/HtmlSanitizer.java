package com.lordsai.lsi.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, safe HTML sanitizer for blog article content.
 * Disallows script execution, iframes, embeds, form submissions, and inline event handlers
 * while preserving rich text formatting (headings, lists, quotes, images, links, tables).
 */
public final class HtmlSanitizer {

    private static final Pattern SCRIPT_TAGS = Pattern.compile("(?is)<script.*?>.*?</script>");
    private static final Pattern STYLE_TAGS = Pattern.compile("(?is)<style.*?>.*?</style>");
    private static final Pattern DANGEROUS_TAGS = Pattern.compile("(?i)</?(iframe|object|embed|applet|form|input|button|select|textarea|meta|link|base|frame|frameset|svg)[^>]*>");
    private static final Pattern EVENT_HANDLERS = Pattern.compile("(?i)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern JAVASCRIPT_URLS = Pattern.compile("(?i)(href|src)\\s*=\\s*(\"\\s*javascript:[^\"]*\"|'\\s*javascript:[^']*'|javascript:[^\\s>]+)");
    private static final Pattern VBSCRIPT_URLS = Pattern.compile("(?i)(href|src)\\s*=\\s*(\"\\s*vbscript:[^\"]*\"|'\\s*vbscript:[^']*'|vbscript:[^\\s>]+)");

    private HtmlSanitizer() { }

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String cleaned = SCRIPT_TAGS.matcher(html).replaceAll("");
        cleaned = STYLE_TAGS.matcher(cleaned).replaceAll("");
        cleaned = DANGEROUS_TAGS.matcher(cleaned).replaceAll("");
        cleaned = EVENT_HANDLERS.matcher(cleaned).replaceAll("");
        cleaned = JAVASCRIPT_URLS.matcher(cleaned).replaceAll("$1=\"#\"");
        cleaned = VBSCRIPT_URLS.matcher(cleaned).replaceAll("$1=\"#\"");
        return cleaned.trim();
    }
}

