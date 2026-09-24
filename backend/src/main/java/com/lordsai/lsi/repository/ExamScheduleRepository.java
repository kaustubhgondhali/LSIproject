package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.ExamSchedule;
import com.lordsai.lsi.entity.enums.ExamScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, Long> {

    Optional<ExamSchedule> findByApplicationId(Long applicationId);

    Optional<ExamSchedule> findByIdAndStudentId(Long id, Long studentUserId);

    List<ExamSchedule> findByStudentIdOrderByStartsAtDesc(Long studentUserId);

    long countByExamIdAndStatus(Long examId, ExamScheduleStatus status);

    @Query("""
            select s from ExamSchedule s
             where (:status is null or s.status = :status)
               and (:examId is null or s.exam.id = :examId)
               and (:courseId is null or s.exam.course.id = :courseId)
             order by s.startsAt desc
            """)
    Page<ExamSchedule> search(@Param("status") ExamScheduleStatus status,
                              @Param("examId") Long examId,
                              @Param("courseId") Long courseId,
                              Pageable pageable);
}
