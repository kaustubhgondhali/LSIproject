package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.Enrollment;
import com.lordsai.lsi.entity.enums.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByStudentIdAndCourseId(Long studentUserId, Long courseId);

    boolean existsByStudentIdAndCourseId(Long studentUserId, Long courseId);

    List<Enrollment> findByStudentIdOrderByEnrolledAtDesc(Long studentUserId);

    Page<Enrollment> findByCourseId(Long courseId, Pageable pageable);

    long countByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    long countByStatus(EnrollmentStatus status);

    List<Enrollment> findTop10ByOrderByEnrolledAtDesc();
}
