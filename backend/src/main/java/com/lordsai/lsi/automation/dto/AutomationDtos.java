package com.lordsai.lsi.automation.dto;

import com.lordsai.lsi.automation.entity.AttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request/response records for the Automation Admin API (/api/automation/**). */
public final class AutomationDtos {

    private AutomationDtos() {
    }

    public static final String MOBILE_PATTERN = "^[6-9][0-9]{9}$";
    public static final String MOBILE_MESSAGE = "Mobile must be a 10-digit Indian number.";

    // ---- master data ------------------------------------------------------------------------

    public record BatchDto(Long id, String name, String schedule, LocalDate startDate, boolean active, int displayOrder, long students) {
    }

    public record SaveBatchRequest(
            @NotBlank @Size(max = 50) String name,
            @Size(max = 100) String schedule,
            LocalDate startDate,
            Boolean active,
            Integer displayOrder
    ) {
    }

    public record CourseDto(Long id, String name, BigDecimal defaultFee, String duration, boolean active, int displayOrder, long students) {
    }

    public record SaveCourseRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin("0.00") BigDecimal defaultFee,
            @Size(max = 100) String duration,
            Boolean active,
            Integer displayOrder
    ) {
    }

    public record MasterItemDto(Long id, String category, String code, String label, boolean active, int displayOrder) {
    }

    public record SaveMasterItemRequest(
            @NotBlank @Pattern(regexp = "^(PAYMENT_MODE|SESSION_TYPE|INSTALLMENT)$", message = "Unknown category.") String category,
            @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Z0-9_]+$", message = "Code must be UPPER_CASE letters, digits or underscores.") String code,
            @NotBlank @Size(max = 100) String label,
            Boolean active,
            Integer displayOrder
    ) {
    }

    /** Everything the forms need for their dropdowns, fetched once. */
    public record Lookups(List<BatchDto> batches, List<CourseDto> courses, List<MasterItemDto> paymentModes,
                          List<MasterItemDto> installments, List<MasterItemDto> sessionTypes,
                          List<String> attendanceStatuses, List<Integer> admissionYears, String nextStudentId) {
    }

    // ---- students ---------------------------------------------------------------------------

    public record StudentRow(
            Long id, String studentId, String fullName,
            Long batchId, String batchName, Long courseId, String courseName,
            LocalDate admissionDate, String admissionDateRaw,
            String mobile, String email, String address,
            BigDecimal courseFee, BigDecimal totalPaid, BigDecimal balance, String paymentStatus,
            int sessionsTotal, int sessionsPresent, Double attendancePercent,
            LocalDate lastPaymentDate, LocalDate lastAttendanceDate,
            String status, String reviewNote, String source, Instant createdAt, Instant updatedAt
    ) {
    }

    public record SaveStudentRequest(
            @NotBlank @Size(min = 2, max = 150) String fullName,
            @NotNull Long batchId,
            @NotNull Long courseId,
            /** Null = use the course's default fee. */
            @DecimalMin("0.00") BigDecimal courseFee,
            @NotNull LocalDate admissionDate,
            @NotBlank @Pattern(regexp = MOBILE_PATTERN, message = MOBILE_MESSAGE) String mobile,
            @Email(message = "Enter a valid email address.") @Size(max = 190) String email,
            @Size(max = 500) String address
    ) {
    }

    public record SearchHit(Long id, String studentId, String fullName, String batchName, String mobile,
                            String status, String paymentStatus, BigDecimal balance) {
    }

    // ---- fees / payments --------------------------------------------------------------------

    public record InstallmentSlot(int installmentNo, String label, boolean paid, BigDecimal amount, LocalDate paymentDate,
                                  String paymentNo, Long receiptId, String receiptNo) {
    }

    /**
     * @param feeReceiptNo the ONE permanent receipt/reference number for this student's fee plan
     *                     (same on every installment's receipt); null until the first payment is recorded.
     * @param installmentsRecorded how many payments have actually been recorded so far.
     */
    public record FeeSummary(Long studentId, String studentCode, String fullName, String batchName, String courseName,
                             BigDecimal courseFee, BigDecimal totalPaid, BigDecimal balance, String paymentStatus,
                             List<InstallmentSlot> installments, Integer nextInstallmentNo,
                             String feeReceiptNo, int installmentsRecorded) {
    }

    public record PaymentDto(
            Long id, String paymentNo, Long studentId, String studentCode, String studentName, String batchName, String courseName,
            int installmentNo, String installmentLabel, LocalDate paymentDate, BigDecimal amount, String paymentMode,
            String referenceNo, String notes, String reviewNote, String source,
            Long receiptId, String receiptNo, String recordedBy, Instant createdAt
    ) {
    }

    public record RecordPaymentRequest(
            @NotNull Long studentId,
            @NotNull @Min(1) @Max(20) Integer installmentNo,
            @NotNull LocalDate paymentDate,
            @NotNull @DecimalMin(value = "0.01", message = "Amount must be greater than zero.") BigDecimal amount,
            @NotBlank @Size(max = 40) String paymentMode,
            @Size(max = 100) String referenceNo,
            @Size(max = 500) String notes
    ) {
    }

    public record UpdatePaymentRequest(
            @NotNull @Min(1) @Max(20) Integer installmentNo,
            @NotNull LocalDate paymentDate,
            @NotNull @DecimalMin(value = "0.01", message = "Amount must be greater than zero.") BigDecimal amount,
            @NotBlank @Size(max = 40) String paymentMode,
            @Size(max = 100) String referenceNo,
            @Size(max = 500) String notes
    ) {
    }

    // ---- receipts ---------------------------------------------------------------------------

    public record ReceiptDto(
            Long id, String receiptNo, Long paymentId, String paymentNo, Long studentId, String studentCode, String studentName,
            String studentMobile, String studentEmail, String courseName, String batchName, String batchSchedule,
            BigDecimal totalFee, BigDecimal amountPaid, BigDecimal totalPaid, BigDecimal balance,
            LocalDate paymentDate, String paymentMode, String referenceNo, int installmentNo, String amountInWords,
            Instant issuedAt, String issuedBy, Instant emailedAt,
            String academyName, String academyTagline, String academyAddress, String academyEmail, String academyPhone, String signatory
    ) {
    }

    // ---- attendance -------------------------------------------------------------------------

    public record SessionDto(Long id, LocalDate sessionDate, Long batchId, String batchName, Long courseId, String courseName,
                             String sessionType, String instructor, String notes, long marked, long present, long absent, Instant createdAt) {
    }

    public record SaveSessionRequest(
            @NotNull Long batchId,
            @NotNull LocalDate sessionDate,
            Long courseId,
            @Size(max = 40) String sessionType,
            @Size(max = 100) String instructor,
            @Size(max = 500) String notes
    ) {
    }

    public record SheetRow(Long studentId, String studentCode, String fullName, AttendanceStatus status,
                           int sessionsTotal, int sessionsPresent, Double attendancePercent) {
    }

    public record AttendanceSheet(SessionDto session, List<SheetRow> rows) {
    }

    public record Mark(@NotNull Long studentId, AttendanceStatus status) {
    }

    public record SaveAttendanceRequest(@NotNull Long sessionId, @NotEmpty @Valid List<Mark> marks) {
    }

    public record AttendanceSummary(int sessionsTotal, int present, int absent, int late, int excused,
                                    Double percent, LocalDate lastAttendance, int consecutiveAbsences) {
    }

    public record AttendanceHistoryRow(Long sessionId, LocalDate sessionDate, String batchName, String sessionType, AttendanceStatus status) {
    }

    // ---- student profile (everything in one call) --------------------------------------------

    public record StudentProfile(StudentRow student, FeeSummary fees, List<PaymentDto> payments, List<ReceiptDto> receipts,
                                 AttendanceSummary attendance, List<AttendanceHistoryRow> attendanceHistory, List<ActivityRow> activity) {
    }

    public record ActivityRow(String action, String description, String actor, Instant at) {
    }

    // ---- settings: Student ID series -------------------------------------------------------

    /** Mirrors {@link com.lordsai.lsi.automation.service.AcadSequenceService.StudentIdSeriesInfo}. */
    public record StudentIdSeriesDto(int year, int nextNumber, String nextStudentId,
                                     int highestExistingNumber, String highestExistingStudentId) {
    }

    public record UpdateStudentIdSeriesRequest(@NotNull @Min(1) @Max(999999) Integer nextNumber) {
    }

    // ---- dashboard --------------------------------------------------------------------------

    public record DashboardStats(
            long totalStudents, long activeStudents, long archivedStudents,
            BigDecimal totalFees, BigDecimal totalPaid, BigDecimal totalBalance,
            long paidStudents, long partialStudents, long pendingStudents,
            long totalSessions, long attendanceMarked, Double attendanceRate, long lowAttendanceStudents,
            BigDecimal collectedToday, BigDecimal collectedThisMonth, long receiptsIssued, long studentsNeedingReview,
            List<PaymentDto> recentPayments, List<StudentRow> lowAttendance, List<StudentRow> pendingBalances
    ) {
    }

    // ---- import -----------------------------------------------------------------------------

    public record ImportStudent(String studentId, String fullName, String batch, String course, BigDecimal courseFee,
                                LocalDate admissionDate, String admissionDateRaw, String mobile, String email, String address,
                                String reviewNote) {
    }

    public record ImportPayment(String studentId, int installmentNo, LocalDate paymentDate, BigDecimal amount,
                                String paymentMode, String notes, String reviewNote) {
    }

    public record ImportSession(String batch, LocalDate sessionDate, String sessionType, List<ImportMark> marks) {
    }

    public record ImportMark(String studentId, String status) {
    }

    public record ImportPayload(List<ImportStudent> students, List<ImportPayment> payments, List<ImportSession> sessions) {
    }

    public record ImportResult(int studentsCreated, int studentsUpdated, int studentsSkipped, int paymentsCreated, int paymentsSkipped,
                               int receiptsCreated, int sessionsCreated, int recordsCreated, int recordsSkipped,
                               List<String> reviewItems, List<String> errors) {
    }
}
