package com.lordsai.lsi.dto.student;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class StudentDtos {

    private StudentDtos() {
    }

    public record Overview(
            Long userId,
            String studentId,
            String fullName,
            String email,
            String mobile,
            String batch,
            String location,
            LocalDate registrationDate,
            Instant lastLoginAt,
            int enrolledCourses,
            long totalLessons,
            long completedLessons,
            int overallProgressPercent,
            long journalEntries,
            long openDoubts
    ) {
    }

    public record MyCourse(
            Long enrollmentId,
            Long courseId,
            String courseCode,
            String courseName,
            String shortDescription,
            String thumbnailPath,
            String duration,
            String enrollmentStatus,
            Instant enrolledAt,
            LocalDate expiryDate,
            int moduleCount,
            long totalLessons,
            long completedLessons,
            int progressPercent,
            Long nextLessonId,
            String nextLessonTitle
    ) {
    }

    public record CourseContent(
            Long courseId,
            String courseCode,
            String courseName,
            String description,
            int progressPercent,
            long totalLessons,
            long completedLessons,
            List<ModuleContent> modules
    ) {
    }

    public record ModuleContent(
            Long id,
            String moduleName,
            String description,
            int displayOrder,
            long lessonCount,
            long completedCount,
            List<LessonSummary> lessons
    ) {
    }

    public record LessonSummary(
            Long id,
            String lessonTitle,
            int displayOrder,
            boolean hasVideo,
            Integer videoDurationSeconds,
            boolean hasMaterial,
            String materialOriginalName,
            boolean completed,
            int watchedPercentage
    ) {
    }

    public record LessonDetail(
            Long id,
            Long courseId,
            Long moduleId,
            String moduleName,
            String lessonTitle,
            String description,
            boolean hasVideo,
            Integer videoDurationSeconds,
            boolean hasMaterial,
            String materialOriginalName,
            boolean completed,
            int watchedPercentage,
            Instant lastWatchedAt,
            Long previousLessonId,
            String previousLessonTitle,
            Long nextLessonId,
            String nextLessonTitle
    ) {
    }

    public record ProgressRequest(
            @Min(0) @Max(100) Integer watchedPercentage,
            Boolean completed
    ) {
    }

    public record ProgressResponse(
            Long lessonId,
            boolean completed,
            int watchedPercentage,
            int courseProgressPercent,
            long courseCompletedLessons,
            long courseTotalLessons
    ) {
    }
}
