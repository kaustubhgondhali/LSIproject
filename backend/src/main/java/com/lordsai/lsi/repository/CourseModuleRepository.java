package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.CourseModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseModuleRepository extends JpaRepository<CourseModule, Long> {

    List<CourseModule> findByCourseIdOrderByDisplayOrderAsc(Long courseId);

    List<CourseModule> findByCourseIdAndActiveTrueOrderByDisplayOrderAsc(Long courseId);

    int countByCourseId(Long courseId);
}
