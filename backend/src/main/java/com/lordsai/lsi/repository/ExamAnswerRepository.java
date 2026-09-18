package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.ExamAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamAnswerRepository extends JpaRepository<ExamAnswer, Long> {

    List<ExamAnswer> findByAttemptIdOrderByIdAsc(Long attemptId);

    long countByQuestionId(Long questionId);
}
