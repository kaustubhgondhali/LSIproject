package com.lordsai.lsi.dto.exam;

import com.lordsai.lsi.entity.enums.ExamApplicationStatus;
import com.lordsai.lsi.entity.enums.ExamAttemptStatus;
import com.lordsai.lsi.entity.enums.ExamResult;
import com.lordsai.lsi.entity.enums.ExamScheduleStatus;
import com.lordsai.lsi.entity.enums.ExamStatus;
import com.lordsai.lsi.entity.enums.McqOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Exam workflow DTOs. Student-facing records ({@link ExamPaper}, {@link PaperQuestion},
 * {@link AttemptResult}) deliberately have no field for the correct option — the backend
 * scores every attempt from the database.
 */
public final class ExamDtos {

    private ExamDtos() {
    }

    // ---- Admin requests --------------------------------------------------------------------

    public record ExamRequest(
            @NotNull Long courseId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull @Min(1) @Max(10000) Integer totalMarks,
            @NotNull @Min(0) @Max(10000) Integer passingMarks,
            @NotNull @Min(1) @Max(100) Integer maxAttempts,
            @Min(1) @Max(1440) Integer durationMinutes
    ) {
    }

    public record ExamStatusRequest(@NotNull ExamStatus status) {
    }

    public record QuestionRequest(
            @NotBlank @Size(max = 5000) String questionText,
            @NotBlank @Size(max = 1000) String optionA,
            @NotBlank @Size(max = 1000) String optionB,
            @NotBlank @Size(max = 1000) String optionC,
            @NotBlank @Size(max = 1000) String optionD,
            @NotNull McqOption correctOption,
            @NotNull @Min(1) @Max(1000) Integer marks
    ) {
    }

    public record ReviewRequest(@Size(max = 500) String remarks) {
    }

    /** Date and times are entered in India Standard Time; the backend converts them to instants. */
    public record ScheduleRequest(
            @NotNull Long examId,
            @NotNull LocalDate examDate,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @Size(max = 5000) String instructions
    ) {
    }

    // ---- Student requests ------------------------------------------------------------------

    public record ApplyRequest(@NotNull Long courseId) {
    }

    public record AnswerRequest(@NotNull Long questionId, McqOption selectedOption) {
    }

    public record SubmitRequest(@NotNull @Valid List<AnswerRequest> answers) {
    }

    // ---- Shared responses ------------------------------------------------------------------

    public record AdminExam(
            Long id,
            Long courseId,
            String courseCode,
            String courseName,
            String title,
            String description,
            int totalMarks,
            int passingMarks,
            int maxAttempts,
            Integer durationMinutes,
            ExamStatus status,
            long questionCount,
            long questionMarksTotal,
            /** True when the active questions add up exactly to totalMarks (required before scheduling). */
            boolean marksConsistent,
            long applicationCount,
            long attemptCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    /** Admin-only view of a question — the only DTO that carries the correct option. */
    public record AdminQuestion(
            Long id,
            Long examId,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            McqOption correctOption,
            int marks,
            int displayOrder,
            boolean active,
            long answerCount
    ) {
    }

    public record ScheduleResponse(
            Long id,
            Long applicationId,
            Long examId,
            String examTitle,
            Instant startsAt,
            Instant endsAt,
            String examDate,
            String startTime,
            String endTime,
            String instructions,
            ExamScheduleStatus status,
            int rescheduleCount,
            long attemptCount,
            /** The server's view of the window: UPCOMING, OPEN, CLOSED (or the schedule status). */
            String window
    ) {
    }

    public record AttemptSummary(
            Long id,
            int attemptNumber,
            ExamAttemptStatus status,
            Instant startedAt,
            Instant deadlineAt,
            Instant submittedAt,
            int score,
            int totalMarks,
            int passingMarks,
            boolean passed,
            int questionCount,
            int correctCount
    ) {
    }

    public record ApplicationResponse(
            Long id,
            Long studentUserId,
            String studentId,
            String studentName,
            String studentEmail,
            Long courseId,
            String courseName,
            Long enrollmentId,
            Long examId,
            String examTitle,
            Integer totalMarks,
            Integer passingMarks,
            Integer maxAttempts,
            ExamApplicationStatus status,
            ExamResult result,
            Instant appliedAt,
            Instant courseCompletedAt,
            Instant reviewedAt,
            String reviewedBy,
            String adminRemarks,
            Instant completedAt,
            ScheduleResponse schedule,
            long attemptsUsed,
            Integer attemptsRemaining,
            List<AttemptSummary> attempts,
            Long certificateId,
            String certificateNumber,
            /** What the student may do right now: APPLY_PENDING, START, RESUME, WAIT, CLOSED, DONE ... */
            String action,
            String message
    ) {
    }

    public record ApplicationCounters(long pending, long approved, long scheduled, long completed, long rejected) {
    }

    public record ResultRow(
            Long attemptId,
            Long studentUserId,
            String studentId,
            String studentName,
            String studentEmail,
            Long courseId,
            String courseName,
            Long examId,
            String examTitle,
            int attemptNumber,
            int maxAttempts,
            int score,
            int totalMarks,
            int passingMarks,
            boolean passed,
            ExamAttemptStatus status,
            Instant startedAt,
            Instant submittedAt,
            boolean certificateEligible,
            Long certificateId,
            String certificateNumber
    ) {
    }

    // ---- Student responses -----------------------------------------------------------------

    /** One card in the student's "My Exams" view. */
    public record MyExamCourse(
            Long courseId,
            String courseCode,
            String courseName,
            String enrollmentStatus,
            long totalLessons,
            long completedLessons,
            int progressPercent,
            boolean courseCompleted,
            boolean canApply,
            String eligibilityMessage,
            boolean examAvailable,
            ApplicationResponse application,
            CertificateDtos.MyCertificate certificate
    ) {
    }

    /** The question as the student sees it — no correct option, ever. */
    public record PaperQuestion(
            Long id,
            int number,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            int marks
    ) {
    }

    public record ExamPaper(
            Long attemptId,
            Long examId,
            String examTitle,
            String courseName,
            String description,
            String instructions,
            int attemptNumber,
            int maxAttempts,
            int totalMarks,
            int passingMarks,
            Instant startedAt,
            Instant deadlineAt,
            long secondsRemaining,
            List<PaperQuestion> questions
    ) {
    }

    public record AttemptResult(
            Long attemptId,
            Long applicationId,
            String studentName,
            String studentId,
            String examTitle,
            String courseName,
            int attemptNumber,
            int maxAttempts,
            int attemptsRemaining,
            int questionCount,
            int correctCount,
            int score,
            int totalMarks,
            int passingMarks,
            boolean passed,
            String result,
            ExamAttemptStatus status,
            Instant submittedAt,
            boolean certificateEligible,
            Long certificateId,
            String certificateNumber,
            String message
    ) {
    }
}
