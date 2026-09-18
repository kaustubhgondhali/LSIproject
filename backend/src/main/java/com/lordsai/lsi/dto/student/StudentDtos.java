package com.lordsai.lsi.dto.student;

import com.lordsai.lsi.entity.enums.ProtectionEventType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class StudentDtos {

    private StudentDtos() {
    }

    /** Rules for a student-chosen sign-in User ID (student_profiles.student_id is VARCHAR(20)). */
    public static final String USER_ID_RULE_MESSAGE =
            "User ID must be 4-20 characters: letters, numbers, dot, underscore or hyphen, starting with a letter or number.";
    private static final String USER_ID_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._-]{3,19}$";

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

    /** A content-protection event observed in the browser (best-effort; server validates the type). */
    public record ProtectionEventRequest(
            @jakarta.validation.constraints.NotNull ProtectionEventType type,
            Long courseId,
            Long lessonId,
            @jakarta.validation.constraints.Size(max = 200) String detail
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

    /** Student changes the User ID (Student ID) they sign in with; the account is taken from the session, never from here. */
    public record ChangeUserIdRequest(
            @NotBlank(message = "Enter your current User ID.") String currentUserId,
            @NotBlank(message = "Enter a new User ID.")
            @Size(min = 4, max = 20, message = USER_ID_RULE_MESSAGE)
            @Pattern(regexp = USER_ID_PATTERN, message = USER_ID_RULE_MESSAGE)
            String newUserId,
            @NotBlank(message = "Confirm the new User ID.") String confirmUserId,
            @NotBlank(message = "Enter your current password.") String currentPassword
    ) {
    }

    public record ChangeUserIdResponse(String userId) {
    }
}
