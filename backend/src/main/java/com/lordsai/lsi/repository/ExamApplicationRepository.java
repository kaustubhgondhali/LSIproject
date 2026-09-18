package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.ExamApplication;
import com.lordsai.lsi.entity.enums.ExamApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamApplicationRepository extends JpaRepository<ExamApplication, Long> {

    List<ExamApplication> findByStudentIdOrderByAppliedAtDesc(Long studentUserId);

    /** Ownership-scoped lookup: a student can only ever resolve their own application. */
    Optional<ExamApplication> findByIdAndStudentId(Long id, Long studentUserId);

    Optional<ExamApplication> findFirstByStudentIdAndCourseIdAndStatusInOrderByAppliedAtDesc(
            Long studentUserId, Long courseId, Collection<ExamApplicationStatus> statuses);

    List<ExamApplication> findByStudentIdAndCourseIdOrderByAppliedAtDesc(Long studentUserId, Long courseId);

    long countByStatus(ExamApplicationStatus status);

    long countByExamId(Long examId);

    @Query("""
            select a from ExamApplication a
             where (:status is null or a.status = :status)
               and (:courseId is null or a.course.id = :courseId)
               and (:q is null
                    or lower(a.student.fullName) like lower(concat('%', :q, '%'))
                    or lower(a.student.email) like lower(concat('%', :q, '%')))
             order by a.appliedAt desc
            """)
    Page<ExamApplication> search(@Param("status") ExamApplicationStatus status,
                                 @Param("courseId") Long courseId,
                                 @Param("q") String q,
                                 Pageable pageable);
}
