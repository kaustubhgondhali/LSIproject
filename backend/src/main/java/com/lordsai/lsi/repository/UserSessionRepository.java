package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByTokenId(String tokenId);

    Optional<UserSession> findByTokenIdAndActiveTrue(String tokenId);

    /** The one query behind "already logged in on another device". */
    Optional<UserSession> findFirstByUserIdAndActiveTrueAndExpiresAtAfter(Long userId, Instant now);

    List<UserSession> findByUserIdAndActiveTrue(Long userId);

    List<UserSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("""
            update UserSession s
               set s.active = false,
                   s.revokeReason = com.lordsai.lsi.entity.enums.SessionRevokeReason.EXPIRED,
                   s.revokedAt = :now
             where s.active = true and s.expiresAt < :now
            """)
    int expireStaleSessions(@Param("now") Instant now);
}
