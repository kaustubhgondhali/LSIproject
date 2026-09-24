package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.Exam;
import com.lordsai.lsi.entity.enums.ExamStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findAllByOrderByCreatedAtDesc();

    List<Exam> findByCourseIdOrderByCreatedAtDesc(Long courseId);

    List<Exam> findByCourseIdAndStatusOrderByCreatedAtDesc(Long courseId, ExamStatus status);

    long countByCourseIdAndStatus(Long courseId, ExamStatus status);
}
