package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.CommunicationLog;
import com.lordsai.lsi.entity.enums.CommunicationChannel;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import com.lordsai.lsi.entity.enums.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CommunicationLogRepository extends JpaRepository<CommunicationLog, Long> {

    List<CommunicationLog> findByBatchRefOrderByIdAsc(String batchRef);

    long countByBatchRef(String batchRef);

    long countByBatchRefAndStatus(String batchRef, CommunicationStatus status);

    long countByStatus(CommunicationStatus status);

    long countByChannelAndStatus(CommunicationChannel channel, CommunicationStatus status);

    List<CommunicationLog> findByStudentIdOrderByCreatedAtDesc(Long studentUserId);

    @Query("""
            select c from CommunicationLog c
            where (:channel is null or c.channel = :channel)
              and (:status is null or c.status = :status)
              and (:studentUserId is null or c.student.id = :studentUserId)
              and (:productType is null or c.productType = :productType)
              and (:productId is null or c.productId = :productId)
              and (:from is null or c.createdAt >= :from)
              and (:to is null or c.createdAt < :to)
              and (:q is null
                   or lower(c.recipient) like lower(concat('%', :q, '%'))
                   or lower(coalesce(c.subject, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(c.studentCode, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(c.providerMessageId, '')) like lower(concat('%', :q, '%')))
            order by c.createdAt desc, c.id desc
            """)
    Page<CommunicationLog> search(@Param("channel") CommunicationChannel channel,
                                  @Param("status") CommunicationStatus status,
                                  @Param("studentUserId") Long studentUserId,
                                  @Param("productType") ProductType productType,
                                  @Param("productId") Long productId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to,
                                  @Param("q") String q,
                                  Pageable pageable);
}
