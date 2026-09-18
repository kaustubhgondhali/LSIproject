package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    Optional<LessonProgress> findByStudentIdAndLessonId(Long studentUserId, Long lessonId);

    void deleteByLessonId(Long lessonId);

    @Query("""
            select lp from LessonProgress lp
              join lp.lesson l join l.module m
             where lp.student.id = :studentId and m.course.id = :courseId
            """)
    List<LessonProgress> findByStudentAndCourse(@Param("studentId") Long studentUserId,
                                                @Param("courseId") Long courseId);

    @Query("""
            select count(lp) from LessonProgress lp
              join lp.lesson l join l.module m
             where lp.student.id = :studentId and m.course.id = :courseId
               and lp.completed = true and l.active = true and m.active = true
            """)
    long countCompletedByStudentAndCourse(@Param("studentId") Long studentUserId,
                                          @Param("courseId") Long courseId);
}
