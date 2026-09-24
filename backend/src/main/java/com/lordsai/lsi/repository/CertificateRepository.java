package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.Certificate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    List<Certificate> findByStudentIdOrderByIssuedAtDesc(Long studentUserId);

    /** Ownership-scoped lookup: a student can only ever resolve their own certificate. */
    Optional<Certificate> findByIdAndStudentId(Long id, Long studentUserId);

    Optional<Certificate> findByAttemptId(Long attemptId);

    Optional<Certificate> findFirstByStudentIdAndCourseIdOrderByIssuedAtDesc(Long studentUserId, Long courseId);

    Optional<Certificate> findByCertificateNumberIgnoreCase(String certificateNumber);

    long countByTemplateId(Long templateId);

    @Query("""
            select c from Certificate c
             where (:courseId is null or c.course.id = :courseId)
               and (:q is null
                    or lower(c.certificateNumber) like lower(concat('%', :q, '%'))
                    or lower(c.studentName) like lower(concat('%', :q, '%'))
                    or lower(coalesce(c.studentCode, '')) like lower(concat('%', :q, '%')))
             order by c.issuedAt desc
            """)
    Page<Certificate> search(@Param("courseId") Long courseId, @Param("q") String q, Pageable pageable);
}
