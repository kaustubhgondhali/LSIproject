package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.ExamAttempt;
import com.lordsai.lsi.entity.enums.ExamAttemptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    /** Every attempt counts against the limit, including expired ones. */
    long countByStudentIdAndExamId(Long studentUserId, Long examId);

    List<ExamAttempt> findByStudentIdAndExamIdOrderByAttemptNumberAsc(Long studentUserId, Long examId);

    List<ExamAttempt> findByApplicationIdOrderByAttemptNumberAsc(Long applicationId);

    Optional<ExamAttempt> findFirstByStudentIdAndExamIdAndStatus(Long studentUserId, Long examId, ExamAttemptStatus status);

    Optional<ExamAttempt> findByIdAndStudentId(Long id, Long studentUserId);

    boolean existsByStudentIdAndExamIdAndPassedTrue(Long studentUserId, Long examId);

    boolean existsByStudentIdAndApplicationCourseIdAndPassedTrue(Long studentUserId, Long courseId);

    long countByExamId(Long examId);

    long countByScheduleId(Long scheduleId);

    @Query("""
            select a from ExamAttempt a
             where a.status <> com.lordsai.lsi.entity.enums.ExamAttemptStatus.IN_PROGRESS
               and (:examId is null or a.exam.id = :examId)
               and (:courseId is null or a.exam.course.id = :courseId)
               and (:passed is null or a.passed = :passed)
               and (:q is null
                    or lower(a.student.fullName) like lower(concat('%', :q, '%'))
                    or lower(a.student.email) like lower(concat('%', :q, '%')))
             order by a.submittedAt desc, a.id desc
            """)
    Page<ExamAttempt> results(@Param("examId") Long examId,
                              @Param("courseId") Long courseId,
                              @Param("passed") Boolean passed,
                              @Param("q") String q,
                              Pageable pageable);
}
