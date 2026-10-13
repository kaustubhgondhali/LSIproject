package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.CertificateRequest;
import com.lordsai.lsi.entity.enums.CertificateRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CertificateRequestRepository extends JpaRepository<CertificateRequest, Long> {

    List<CertificateRequest> findByStudentIdOrderByRequestedAtDesc(Long studentUserId);

    /** The student's active (not rejected) request for a course, if any. */
    Optional<CertificateRequest> findFirstByStudentIdAndCourseIdAndActiveSlotIsNotNull(Long studentUserId, Long courseId);

    /** Row-locked read so two admins (or a double click) cannot approve / reject the same request twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CertificateRequest r where r.id = :id")
    Optional<CertificateRequest> findForUpdate(@Param("id") Long id);

    long countByStatus(CertificateRequestStatus status);

    @Query("""
            select r from CertificateRequest r
             where (:status is null or r.status = :status)
               and (:q is null
                    or lower(r.studentName) like lower(concat('%', :q, '%'))
                    or lower(coalesce(r.studentCode, '')) like lower(concat('%', :q, '%'))
                    or lower(r.courseName) like lower(concat('%', :q, '%')))
             order by r.requestedAt desc
            """)
    Page<CertificateRequest> search(@Param("status") CertificateRequestStatus status, @Param("q") String q, Pageable pageable);
}
