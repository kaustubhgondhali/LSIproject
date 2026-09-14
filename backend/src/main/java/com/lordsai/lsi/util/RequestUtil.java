package com.lordsai.lsi.util;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestUtil {

    private RequestUtil() {
    }

    /** Honours X-Forwarded-For when the app sits behind a reverse proxy. */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String deviceInfo(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua == null ? "unknown" : ua;
    }
}
