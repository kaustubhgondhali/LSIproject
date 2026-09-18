package com.lordsai.lsi.automation.repository;

import com.lordsai.lsi.automation.entity.AcadAttendanceRecord;
import com.lordsai.lsi.automation.entity.AcadAttendanceSession;
import com.lordsai.lsi.automation.entity.AcadBatch;
import com.lordsai.lsi.automation.entity.AcadCourse;
import com.lordsai.lsi.automation.entity.AcadMasterData;
import com.lordsai.lsi.automation.entity.AcadPayment;
import com.lordsai.lsi.automation.entity.AcadReceipt;
import com.lordsai.lsi.automation.entity.AcadSequence;
import com.lordsai.lsi.automation.entity.AcadStudent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AcadSessionRepository extends JpaRepository<AcadAttendanceSession, Long> {
    Optional<AcadAttendanceSession> findByBatchIdAndSessionDateAndSessionType(Long batchId, LocalDate date, String type);
    List<AcadAttendanceSession> findByBatchIdOrderBySessionDateDesc(Long batchId);
    long countByBatchId(Long batchId);

    @Query("""
            select s from AcadAttendanceSession s join fetch s.batch
            where (:batchId is null or s.batch.id = :batchId)
              and (:from is null or s.sessionDate >= :from)
              and (:to is null or s.sessionDate <= :to)
            order by s.sessionDate desc, s.id desc
            """)
    List<AcadAttendanceSession> report(@Param("batchId") Long batchId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
