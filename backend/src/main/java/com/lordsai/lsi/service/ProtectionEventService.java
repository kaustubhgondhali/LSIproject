package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.student.StudentDtos.ProtectionEventRequest;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.CaptureScope;
import com.lordsai.lsi.entity.enums.ProtectionEventType;
import com.lordsai.lsi.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Audit trail for Student Portal content-protection events. Browser-reported events are
 * best-effort signals, so they are recorded and throttled per user and type so a misbehaving
 * client cannot flood the audit table. A SCREEN_CAPTURE_ATTEMPT also starts an in-memory
 * capture lock for its scope (course or ebook, counted separately) that grows by 5 minutes per
 * attempt within 24 hours; the portal enforces it by keeping that content blurred.
 */
@Service
public class ProtectionEventService {

    private static final Logger log = LoggerFactory.getLogger(ProtectionEventService.class);
    private static final Duration MIN_GAP = Duration.ofSeconds(5);
    /** Each capture attempt adds this much: 5, 10, 15 … minutes. */
    public static final Duration CAPTURE_LOCK_STEP = Duration.ofMinutes(5);
    /** Strikes reset this long after the first attempt in the window. */
    private static final Duration STRIKE_WINDOW = Duration.ofHours(24);

    private final AuditService auditService;
    private final UserService userService;
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();
    private final Map<LockKey, Instant> captureLockUntil = new ConcurrentHashMap<>();
    private final Map<LockKey, Strikes> captureStrikes = new ConcurrentHashMap<>();

    private record LockKey(Long userId, CaptureScope scope) {
    }

    private record Strikes(int count, Instant windowStart) {
    }

    public ProtectionEventService(AuditService auditService, UserService userService) {
        this.auditService = auditService;
        this.userService = userService;
    }

    /** Records a browser-reported event. Returns false when it was throttled (still HTTP 200). */
    public boolean recordFromBrowser(Long userId, ProtectionEventRequest req, String ip) {
        if (req.type() == ProtectionEventType.UNAUTHORIZED_MEDIA_REQUEST) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This event type is raised by the server only.");
        }
        Instant now = Instant.now();
        if (req.type() == ProtectionEventType.SCREEN_CAPTURE_ATTEMPT && req.scope() != null) {
            startCaptureLock(new LockKey(userId, req.scope()), now);
        }
        String key = userId + "|" + req.type();
        Instant previous = lastSeen.put(key, now);
        if (previous != null && Duration.between(previous, now).compareTo(MIN_GAP) < 0) {
            return false;
        }
        if (lastSeen.size() > 20_000) {
            lastSeen.entrySet().removeIf(e -> Duration.between(e.getValue(), now).toHours() >= 1);
        }

        User user = userService.requireUser(userId);
        String entityType = req.lessonId() != null ? "Lesson" : req.courseId() != null ? "Course" : "User";
        Long entityId = req.lessonId() != null ? req.lessonId() : req.courseId() != null ? req.courseId() : userId;
        String description = describe(req);
        auditService.record(user, "PROTECTION_" + req.type().name(), entityType, entityId, description, ip);
        log.info("[PROTECTION] {} by userId={} {}", req.type(), userId, description);
        return true;
    }

    /**
     * The Nth attempt within 24 hours of the first locks that scope for N x 5 minutes.
     * Attempts during a running lock add no strike.
     */
    private void startCaptureLock(LockKey key, Instant now) {
        if (remainingSeconds(key) > 0) {
            return;
        }
        Strikes strikes = captureStrikes.compute(key, (k, prev) ->
                prev == null || Duration.between(prev.windowStart(), now).compareTo(STRIKE_WINDOW) >= 0
                        ? new Strikes(1, now)
                        : new Strikes(prev.count() + 1, prev.windowStart()));
        captureLockUntil.put(key, now.plus(CAPTURE_LOCK_STEP.multipliedBy(strikes.count())));
    }

    /** Seconds left on the student's capture lock for one scope (0 when that content may be shown). */
    public long captureLockRemainingSeconds(Long userId, CaptureScope scope) {
        return remainingSeconds(new LockKey(userId, scope));
    }

    private long remainingSeconds(LockKey key) {
        Instant until = captureLockUntil.get(key);
        if (until == null) {
            return 0;
        }
        long left = Duration.between(Instant.now(), until).getSeconds();
        if (left <= 0) {
            captureLockUntil.remove(key, until);
            return 0;
        }
        return left;
    }

    /** Server-side: a media request that failed authorization. */
    public void recordUnauthorizedMedia(User user, Long lessonId, String reason, String ip) {
        auditService.record(user, "PROTECTION_" + ProtectionEventType.UNAUTHORIZED_MEDIA_REQUEST.name(),
                "Lesson", lessonId, reason, ip);
        log.warn("[PROTECTION] UNAUTHORIZED_MEDIA_REQUEST lesson={} user={} — {}", lessonId,
                user == null ? "anonymous" : user.getId(), reason);
    }

    private static String describe(ProtectionEventRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.courseId() != null) {
            sb.append("course=").append(req.courseId()).append(' ');
        }
        if (req.lessonId() != null) {
            sb.append("lesson=").append(req.lessonId()).append(' ');
        }
        if (req.detail() != null && !req.detail().isBlank()) {
            // Free text from the browser: keep it short and strip control characters.
            sb.append(req.detail().replaceAll("[\\p{Cntrl}]", " ").trim());
        }
        return sb.length() == 0 ? req.type().name() : sb.toString().trim();
    }
}
