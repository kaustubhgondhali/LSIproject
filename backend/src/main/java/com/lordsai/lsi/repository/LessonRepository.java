package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByModuleIdOrderByDisplayOrderAsc(Long moduleId);

    List<Lesson> findByModuleIdAndActiveTrueOrderByDisplayOrderAsc(Long moduleId);

    @Query("""
            select l from Lesson l
              join l.module m
             where m.course.id = :courseId and l.active = true and m.active = true
             order by m.displayOrder, l.displayOrder
            """)
    List<Lesson> findActiveByCourseId(@Param("courseId") Long courseId);

    @Query("""
            select count(l) from Lesson l
              join l.module m
             where m.course.id = :courseId and l.active = true and m.active = true
            """)
    long countActiveByCourseId(@Param("courseId") Long courseId);
}
