package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    Optional<StudentProfile> findByStudentIdIgnoreCase(String studentId);

    Optional<StudentProfile> findByUserId(Long userId);

    boolean existsByStudentId(String studentId);
}
