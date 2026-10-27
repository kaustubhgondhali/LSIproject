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

public interface AcadReceiptRepository extends JpaRepository<AcadReceipt, Long> {
    Optional<AcadReceipt> findByReceiptNo(String receiptNo);
    Optional<AcadReceipt> findByPaymentId(Long paymentId);
    List<AcadReceipt> findByStudentIdOrderByIssuedAtDesc(Long studentId);

    @Query("""
            select r from AcadReceipt r join fetch r.student s
            where (:studentId is null or s.id = :studentId)
              and (:from is null or r.paymentDate >= :from)
              and (:to is null or r.paymentDate <= :to)
              and (:q is null or r.receiptNo like concat('%', :q, '%') or upper(r.studentName) like concat('%', :q, '%')
                   or r.studentCode like concat('%', :q, '%'))
            order by r.issuedAt desc
            """)
    Page<AcadReceipt> search(@Param("studentId") Long studentId, @Param("from") LocalDate from,
                             @Param("to") LocalDate to, @Param("q") String q, Pageable pageable);
}
