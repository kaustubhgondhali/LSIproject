package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.student.StudentDtos.ProtectionEventRequest;
import com.lordsai.lsi.entity.User;
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
 * best-effort signals, so they are recorded — never acted on automatically — and throttled per
 * user and type so a misbehaving client cannot flood the audit table.
 */
@Service
public class ProtectionEventService {

    private static final Logger log = LoggerFactory.getLogger(ProtectionEventService.class);
    private static final Duration MIN_GAP = Duration.ofSeconds(5);

    private final AuditService auditService;
    private final UserService userService;
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    public ProtectionEventService(AuditService auditService, UserService userService) {
        this.auditService = auditService;
        this.userService = userService;
    }

    /** Records a browser-reported event. Returns false when it was throttled (still HTTP 200). */
    public boolean recordFromBrowser(Long userId, ProtectionEventRequest req, String ip) {
        if (req.type() == ProtectionEventType.UNAUTHORIZED_MEDIA_REQUEST) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This event type is raised by the server only.");
        }
        String key = userId + "|" + req.type();
        Instant now = Instant.now();
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
