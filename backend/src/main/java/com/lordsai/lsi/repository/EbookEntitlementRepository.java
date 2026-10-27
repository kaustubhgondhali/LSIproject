package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.EbookEntitlement;
import com.lordsai.lsi.entity.enums.EntitlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EbookEntitlementRepository extends JpaRepository<EbookEntitlement, Long> {

    Optional<EbookEntitlement> findByStudentIdAndEbookId(Long studentUserId, Long ebookId);

    @EntityGraph(attributePaths = "ebook")
    Optional<EbookEntitlement> findWithEbookByStudentIdAndEbookId(Long studentUserId, Long ebookId);

    boolean existsByStudentIdAndEbookId(Long studentUserId, Long ebookId);

    List<EbookEntitlement> findByStudentIdOrderByGrantedAtDesc(Long studentUserId);

    Page<EbookEntitlement> findByEbookId(Long ebookId, Pageable pageable);

    List<EbookEntitlement> findByEbookId(Long ebookId);

    long countByEbookId(Long ebookId);

    long countByEbookIdAndStatus(Long ebookId, EntitlementStatus status);

    long countByStatus(EntitlementStatus status);

    List<EbookEntitlement> findTop10ByOrderByGrantedAtDesc();
}
