package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {

    List<ExamQuestion> findByExamIdOrderByDisplayOrderAscIdAsc(Long examId);

    List<ExamQuestion> findByExamIdAndActiveTrueOrderByDisplayOrderAscIdAsc(Long examId);

    Optional<ExamQuestion> findByIdAndExamId(Long id, Long examId);

    long countByExamIdAndActiveTrue(Long examId);

    @Query("select coalesce(sum(q.marks), 0) from ExamQuestion q where q.exam.id = :examId and q.active = true")
    long sumActiveMarks(@Param("examId") Long examId);

    @Query("select coalesce(max(q.displayOrder), 0) from ExamQuestion q where q.exam.id = :examId")
    int maxDisplayOrder(@Param("examId") Long examId);
}
