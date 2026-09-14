package com.lordsai.lsi.service;

import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.UserSession;
import com.lordsai.lsi.entity.enums.Role;
import com.lordsai.lsi.entity.enums.SessionRevokeReason;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.repository.UserSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    public static final String ALREADY_LOGGED_IN_MESSAGE =
            "This account is already logged in on another device. Please logout from the other device first.";

    /** How often last_activity_at is written back, to avoid a DB write on every request. */
    private static final Duration ACTIVITY_WRITE_INTERVAL = Duration.ofMinutes(1);

    private final UserSessionRepository sessionRepository;

    public SessionService(UserSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Opens a session for a successful login.
     * Students are limited to one active device. Admins are allowed to log in
     * again, which quietly closes their previous session.
     */
    // noRollbackFor: the 409 below is a normal outcome. Without it the joined login transaction
    // would be marked rollback-only and the caller's commit would fail with a 500.
    @Transactional(noRollbackFor = ApiException.class)
    public UserSession open(User user, Duration ttl, String deviceInfo, String ipAddress) {
        Instant now = Instant.now();
        Optional<UserSession> existing =
                sessionRepository.findFirstByUserIdAndActiveTrueAndExpiresAtAfter(user.getId(), now);

        if (existing.isPresent()) {
            if (user.getRole() == Role.STUDENT) {
                throw new ApiException(HttpStatus.CONFLICT, ALREADY_LOGGED_IN_MESSAGE);
            }
            revokeAll(user.getId(), SessionRevokeReason.NEW_LOGIN);
        }

        UserSession session = new UserSession();
        session.setUser(user);
        session.setTokenId(UUID.randomUUID().toString());
        session.setDeviceInfo(truncate(deviceInfo, 300));
        session.setIpAddress(truncate(ipAddress, 45));
        session.setLastActivityAt(now);
        session.setExpiresAt(now.plus(ttl));
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public Optional<UserSession> findActive(String tokenId) {
        return sessionRepository.findByTokenIdAndActiveTrue(tokenId)
                .filter(s -> !s.isExpired());
    }

    @Transactional(readOnly = true)
    public Optional<UserSession> findActiveById(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .filter(UserSession::isActive)
                .filter(s -> !s.isExpired());
    }

    @Transactional
    public void touch(UserSession session) {
        Instant now = Instant.now();
        if (Duration.between(session.getLastActivityAt(), now).compareTo(ACTIVITY_WRITE_INTERVAL) > 0) {
            session.setLastActivityAt(now);
            sessionRepository.save(session);
        }
    }

    @Transactional
    public void revoke(Long sessionId, SessionRevokeReason reason) {
        sessionRepository.findById(sessionId).ifPresent(s -> {
            if (s.isActive()) {
                s.revoke(reason);
                sessionRepository.save(s);
            }
        });
    }

    @Transactional
    public int revokeAll(Long userId, SessionRevokeReason reason) {
        List<UserSession> active = sessionRepository.findByUserIdAndActiveTrue(userId);
        active.forEach(s -> s.revoke(reason));
        sessionRepository.saveAll(active);
        return active.size();
    }

    /** Rotates the token id so a refreshed JWT cannot be replayed with the old id. */
    @Transactional
    public UserSession rotate(UserSession session, Duration ttl) {
        Instant now = Instant.now();
        session.setTokenId(UUID.randomUUID().toString());
        session.setLastActivityAt(now);
        session.setExpiresAt(now.plus(ttl));
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<UserSession> history(Long userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
