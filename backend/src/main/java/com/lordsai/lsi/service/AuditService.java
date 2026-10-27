package com.lordsai.lsi.service;

import com.lordsai.lsi.entity.AuditLog;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Joins the caller's transaction so audit rows can reference entities created in the same
     * unit of work (e.g. "student created" during purchase processing). Callers that must keep
     * an audit row despite a business failure declare noRollbackFor on their own transaction.
     */
    @Transactional
    public void record(User actor, String action, String entityType, Long entityId,
                       String description, String ipAddress) {
        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDescription(description == null ? action : truncate(description, 1000));
        log.setIpAddress(ipAddress);
        auditLogRepository.save(log);
    }

    public void record(User actor, String action, String description, String ipAddress) {
        record(actor, action, null, null, description, ipAddress);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
