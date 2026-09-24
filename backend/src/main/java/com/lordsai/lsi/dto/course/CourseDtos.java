package com.lordsai.lsi.dto.course;

import com.lordsai.lsi.entity.enums.CourseStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CourseDtos {

    private CourseDtos() {
    }

    // ---- Requests --------------------------------------------------------------------------

    public record CourseRequest(
            @NotBlank @Size(max = 40)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9-]*$", message = "Course code may contain letters, numbers and hyphens only.")
            String courseCode,
            @NotBlank @Size(max = 200) String courseName,
            @Size(max = 500) String shortDescription,
            @Size(max = 10000) String description,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
            @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal discountedPrice,
            @Size(max = 100) String duration,
            @Size(max = 255) String thumbnailPath,
            Integer displayOrder
    ) {
    }

    public record CourseRenameRequest(@NotBlank @Size(max = 200) String courseName) {
    }

    public record CoursePriceRequest(
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
            @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal discountedPrice
    ) {
    }

    public record StatusRequest(@NotNull CourseStatus status) {
    }

    public record ModuleRequest(
            @NotBlank @Size(max = 200) String moduleName,
            @Size(max = 5000) String description
    ) {
    }

    public record LessonRequest(
            @NotBlank @Size(max = 200) String lessonTitle,
            @Size(max = 5000) String description,
            Integer videoDurationSeconds
    ) {
    }

    public record ActiveRequest(boolean active) {
    }

    /** Full ordered list of ids; the position in the list becomes the display order. */
    public record ReorderRequest(@NotEmpty List<Long> orderedIds) {
    }

    // ---- Responses -------------------------------------------------------------------------

    /** What visitors see on courses.html. Never includes lesson file paths. */
    public record PublicCourse(
            Long id,
            String courseCode,
            String courseName,
            String shortDescription,
            String description,
            BigDecimal price,
            BigDecimal discountedPrice,
            BigDecimal effectivePrice,
            String duration,
            String thumbnailPath,
            int moduleCount,
            long lessonCount,
            List<PublicModule> modules
    ) {
    }

    public record PublicModule(Long id, String moduleName, String description, int displayOrder, List<String> lessonTitles) {
    }

    /** Admin view with counts and status. */
    public record AdminCourse(
            Long id,
            String courseCode,
            String courseName,
            String shortDescription,
            String description,
            BigDecimal price,
            BigDecimal discountedPrice,
            BigDecimal effectivePrice,
            String duration,
            String thumbnailPath,
            CourseStatus status,
            int displayOrder,
            int moduleCount,
            long lessonCount,
            long activeStudents,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ModuleResponse(
            Long id,
            Long courseId,
            String moduleName,
            String description,
            int displayOrder,
            boolean active,
            List<LessonResponse> lessons
    ) {
    }

    public record LessonResponse(
            Long id,
            Long moduleId,
            String lessonTitle,
            String description,
            int displayOrder,
            boolean active,
            boolean hasVideo,
            Integer videoDurationSeconds,
            boolean hasMaterial,
            String materialOriginalName
    ) {
    }
}
