package com.lordsai.lsi.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force throttle: after MAX_FAILURES failed attempts for the same
 * identifier the login is locked for LOCK_DURATION. Suitable for a single-instance deployment;
 * move the counters to Redis/DB if the backend is ever scaled horizontally.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private record Attempt(int failures, Instant lockedUntil) {
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String identifier) {
        Attempt attempt = attempts.get(key(identifier));
        if (attempt == null || attempt.lockedUntil() == null) {
            return false;
        }
        if (Instant.now().isAfter(attempt.lockedUntil())) {
            attempts.remove(key(identifier));
            return false;
        }
        return true;
    }

    public void recordFailure(String identifier) {
        attempts.compute(key(identifier), (k, existing) -> {
            int failures = existing == null ? 1 : existing.failures() + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? Instant.now().plus(LOCK_DURATION) : null;
            return new Attempt(failures, lockedUntil);
        });
    }

    public void recordSuccess(String identifier) {
        attempts.remove(key(identifier));
    }

    private static String key(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase();
    }
}
