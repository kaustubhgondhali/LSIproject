package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(Long actorUserId, Pageable pageable);

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId, Pageable pageable);

    /** Activity for one academy student: rows targeting the record plus any row that names its Student ID. */
    @org.springframework.data.jpa.repository.Query("""
            select a from AuditLog a
            where (a.entityType = :entityType and a.entityId = :entityId) or a.description like concat('%', :code, '%')
            order by a.createdAt desc
            """)
    Page<AuditLog> activityFor(@org.springframework.data.repository.query.Param("entityType") String entityType,
                               @org.springframework.data.repository.query.Param("entityId") Long entityId,
                               @org.springframework.data.repository.query.Param("code") String code, Pageable pageable);
}
