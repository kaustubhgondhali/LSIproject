package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.Course;
import com.lordsai.lsi.entity.enums.CourseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCourseCodeIgnoreCase(String courseCode);

    boolean existsByCourseCodeIgnoreCase(String courseCode);

    List<Course> findByStatusOrderByDisplayOrderAscCourseNameAsc(CourseStatus status);

    List<Course> findAllByOrderByDisplayOrderAscCourseNameAsc();

    long countByStatus(CourseStatus status);
}
