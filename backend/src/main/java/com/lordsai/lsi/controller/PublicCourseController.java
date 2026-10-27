package com.lordsai.lsi.controller;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.course.CourseDtos.PublicCourse;
import com.lordsai.lsi.service.CourseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only course catalogue for visitors (courses.html). */
@RestController
@RequestMapping("/api/public/courses")
public class PublicCourseController {

    private final CourseService courseService;

    public PublicCourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public ApiResponse<List<PublicCourse>> list() {
        return ApiResponse.ok(courseService.listPublic());
    }

    @GetMapping("/{courseCode}")
    public ApiResponse<PublicCourse> get(@PathVariable String courseCode) {
        return ApiResponse.ok(courseService.getPublic(courseCode));
    }
}
