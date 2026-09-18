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

public interface AcadRecordRepository extends JpaRepository<AcadAttendanceRecord, Long> {
    List<AcadAttendanceRecord> findBySessionId(Long sessionId);
    Optional<AcadAttendanceRecord> findBySessionIdAndStudentId(Long sessionId, Long studentId);
    long countBySessionId(Long sessionId);

    @Query("select r from AcadAttendanceRecord r join fetch r.session s where r.student.id = :studentId order by s.sessionDate desc")
    List<AcadAttendanceRecord> historyForStudent(@Param("studentId") Long studentId);

    /** student id, sessions attended-or-not, present count, last session date — for every student at once. */
    @Query("""
            select r.student.id, count(r), sum(case when r.status in ('PRESENT','LATE') then 1 else 0 end),
                   sum(case when r.status = 'EXCUSED' then 1 else 0 end), max(s.sessionDate)
            from AcadAttendanceRecord r join r.session s group by r.student.id
            """)
    List<Object[]> summaryByStudent();

    @Query("select count(r) from AcadAttendanceRecord r where r.status in ('PRESENT','LATE')")
    long totalPresent();

    @Query("select count(r) from AcadAttendanceRecord r where r.status <> 'EXCUSED'")
    long totalCounted();
}
