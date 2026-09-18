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

public interface AcadStudentRepository extends JpaRepository<AcadStudent, Long> {
    Optional<AcadStudent> findByStudentIdIgnoreCase(String studentId);
    boolean existsByStudentIdIgnoreCase(String studentId);
    long countByStatus(String status);
    List<AcadStudent> findByBatchIdAndStatusOrderByStudentIdAsc(Long batchId, String status);
    List<AcadStudent> findByStatusOrderByStudentIdAsc(String status);

    @Query("""
            select s from AcadStudent s
            left join s.batch b left join s.course c
            where (:q is null or s.studentId like concat('%', :q, '%')
                   or s.nameNormalized like concat('%', :q, '%')
                   or s.mobile like concat('%', :q, '%')
                   or upper(s.email) like concat('%', :q, '%')
                   or upper(b.name) like concat('%', :q, '%'))
              and (:batchId is null or b.id = :batchId)
              and (:courseId is null or c.id = :courseId)
              and (:status is null or s.status = :status)
              and (:year is null or year(s.admissionDate) = :year)
            """)
    Page<AcadStudent> search(@Param("q") String q, @Param("batchId") Long batchId, @Param("courseId") Long courseId,
                             @Param("status") String status, @Param("year") Integer year, Pageable pageable);

    @Query("select coalesce(sum(s.courseFee), 0) from AcadStudent s where s.status = 'ACTIVE'")
    BigDecimal totalExpectedFees();

    @Query("select max(s.studentId) from AcadStudent s where s.studentId like concat('LSA/', :year, '/%')")
    Optional<String> maxStudentIdForYear(@Param("year") String year);
}
