package com.lordsai.lsi.automation.repository;

import com.lordsai.lsi.automation.entity.AcadCommunicationLog;
import com.lordsai.lsi.entity.enums.CommunicationChannel;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AcadCommunicationLogRepository extends JpaRepository<AcadCommunicationLog, Long> {

    List<AcadCommunicationLog> findByBatchRefOrderByIdAsc(String batchRef);

    long countByBatchRef(String batchRef);

    long countByBatchRefAndStatus(String batchRef, CommunicationStatus status);

    long countByStatus(CommunicationStatus status);

    long countByChannelAndStatus(CommunicationChannel channel, CommunicationStatus status);

    List<AcadCommunicationLog> findByStudentIdOrderByCreatedAtDesc(Long acadStudentId);

    @Query("""
            select c from AcadCommunicationLog c
            where (:channel is null or c.channel = :channel)
              and (:status is null or c.status = :status)
              and (:studentId is null or c.student.id = :studentId)
              and (:from is null or c.createdAt >= :from)
              and (:to is null or c.createdAt < :to)
              and (:q is null
                   or lower(c.recipient) like lower(concat('%', :q, '%'))
                   or lower(coalesce(c.subject, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(c.studentCode, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(c.studentName, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(c.providerMessageId, '')) like lower(concat('%', :q, '%')))
            order by c.createdAt desc, c.id desc
            """)
    Page<AcadCommunicationLog> search(@Param("channel") CommunicationChannel channel,
                                      @Param("status") CommunicationStatus status,
                                      @Param("studentId") Long studentId,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to,
                                      @Param("q") String q,
                                      Pageable pageable);
}
