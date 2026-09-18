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

public interface AcadPaymentRepository extends JpaRepository<AcadPayment, Long> {
    List<AcadPayment> findByStudentIdOrderByInstallmentNoAsc(Long studentId);
    boolean existsByStudentIdAndInstallmentNo(Long studentId, int installmentNo);
    Optional<AcadPayment> findByPaymentNo(String paymentNo);

    @Query("select coalesce(sum(p.amount), 0) from AcadPayment p where p.student.id = :studentId")
    BigDecimal totalPaidByStudent(@Param("studentId") Long studentId);

    @Query("select coalesce(sum(p.amount), 0) from AcadPayment p join p.student s where s.status = 'ACTIVE'")
    BigDecimal totalCollected();

    @Query("select coalesce(sum(p.amount), 0) from AcadPayment p where p.paymentDate between :from and :to")
    BigDecimal collectedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Per-student totals in one query, so lists and dashboard cards never loop over students. */
    @Query("select p.student.id, sum(p.amount), max(p.paymentDate) from AcadPayment p group by p.student.id")
    List<Object[]> totalsByStudent();

    @Query("""
            select p from AcadPayment p join fetch p.student s left join fetch s.batch left join fetch s.course
            where (:studentId is null or s.id = :studentId)
              and (:batchId is null or s.batch.id = :batchId)
              and (:from is null or p.paymentDate >= :from)
              and (:to is null or p.paymentDate <= :to)
              and (:mode is null or p.paymentMode = :mode)
            order by p.paymentDate desc, p.id desc
            """)
    List<AcadPayment> report(@Param("studentId") Long studentId, @Param("batchId") Long batchId,
                             @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("mode") String mode);

    @Query("select p from AcadPayment p join fetch p.student s order by p.createdAt desc")
    List<AcadPayment> recent(Pageable pageable);
}
