package com.lordsai.lsi.security;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.util.RequestUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP throttle for the unauthenticated endpoints an attacker could hammer: login,
 * password-reset requests, and order creation. Fixed window, in-memory — adequate for a
 * single instance; back it with Redis if the API is ever scaled out.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(int maxRequests, Duration window) {
    }

    private record Counter(Instant windowStart, int count) {
    }

    private static final Map<String, Rule> RULES = Map.of(
            "/api/auth/login", new Rule(20, Duration.ofMinutes(5)),
            "/api/auth/forgot-password", new Rule(5, Duration.ofMinutes(15)),
            "/api/auth/resend-setup", new Rule(5, Duration.ofMinutes(15)),
            "/api/auth/reset-password", new Rule(10, Duration.ofMinutes(15)),
            "/api/auth/token-check", new Rule(30, Duration.ofMinutes(15)),
            "/api/payments/create-order", new Rule(10, Duration.ofMinutes(10)),
            "/api/payments/demo-complete", new Rule(10, Duration.ofMinutes(10)),
            "/api/payments/verify", new Rule(20, Duration.ofMinutes(10)),
            "/api/public/reviews", new Rule(5, Duration.ofMinutes(15)),
            "/api/public/enquiry", new Rule(5, Duration.ofMinutes(15))
    );

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RateLimitFilter(ObjectMapper objectMapper,
                           @Value("${lsi.rate-limit.enabled:true}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = enabled ? RULES.get(request.getRequestURI()) : null;
        if (rule == null || !"POST".equalsIgnoreCase(request.getMethod()) && !request.getRequestURI().endsWith("token-check")) {
            chain.doFilter(request, response);
            return;
        }
        String key = request.getRequestURI() + "|" + RequestUtil.clientIp(request);
        Instant now = Instant.now();
        Counter updated = counters.compute(key, (k, c) -> {
            if (c == null || now.isAfter(c.windowStart().plus(rule.window()))) {
                return new Counter(now, 1);
            }
            return new Counter(c.windowStart(), c.count() + 1);
        });
        if (updated.count() > rule.maxRequests()) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.error("Too many requests. Please wait a few minutes and try again.")));
            return;
        }
        if (counters.size() > 50_000) {
            counters.entrySet().removeIf(e -> now.isAfter(e.getValue().windowStart().plus(Duration.ofHours(1))));
        }
        chain.doFilter(request, response);
    }
}
