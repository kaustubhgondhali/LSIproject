package com.lordsai.lsi.automation.dto;

import com.lordsai.lsi.automation.entity.AttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
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

    /**
     * @param aadhaarMasked "XXXX XXXX 1234" (the full number never leaves the server), null when not recorded yet.
     * @param netFee        what the student has to pay: courseFee - discount. Balance and payment status use it.
     */
    public record StudentRow(
            Long id, String studentId, String fullName,
            Long batchId, String batchName, Long courseId, String courseName,
            LocalDate admissionDate, String admissionDateRaw,
            String mobile, String email, String address, String aadhaarMasked,
            BigDecimal courseFee, BigDecimal discount, BigDecimal netFee, BigDecimal totalPaid, BigDecimal balance, String paymentStatus,
            int sessionsTotal, int sessionsPresent, Double attendancePercent,
            LocalDate lastPaymentDate, LocalDate lastAttendanceDate,
            String status, String reviewNote, String source, Instant createdAt, Instant updatedAt,
            AdmissionDetails admission
    ) {
    }

    /** The admission-form fields beyond the core record above; every value may be null on older records. */
    public record AdmissionDetails(
            LocalDate dateOfBirth, String gender,
            String highestQualification, String instituteName, Integer yearOfCompletion,
            String employmentStatus, String currentEmployer, String jobTitle, BigDecimal annualIncome,
            Boolean stockMarketExperience, BigDecimal stockMarketYears,
            Boolean tradingExperience, BigDecimal tradingYears,
            String tradingPlatforms, String tradingStrategies,
            LocalDate preferredStartDate, String paymentMethod
    ) {
    }

    /** Letters of any script (incl. Devanagari vowel signs), spaces, dots, apostrophes and hyphens. */
    public static final String NAME_PATTERN = "^[\\p{L}][\\p{L}\\p{M} .'-]*$";
    public static final String NAME_MESSAGE = "Full name may contain only letters, spaces, dots, apostrophes and hyphens.";

    public record SaveStudentRequest(
            @NotBlank @Size(min = 2, max = 150) @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE) String fullName,
            @NotNull Long batchId,
            @NotNull Long courseId,
            /** Null = use the course's default fee. */
            @DecimalMin("0.00") BigDecimal courseFee,
            @NotNull LocalDate admissionDate,
            @NotBlank @Pattern(regexp = MOBILE_PATTERN, message = MOBILE_MESSAGE) String mobile,
            @Email(message = "Enter a valid email address.") @Size(max = 190) String email,
            @Size(max = 500) String address,
            /**
             * 12 digits (spaces / hyphens allowed). Required for a new student; when editing, blank
             * keeps the number already saved, and a student without one must be given one.
             */
            @Size(max = 20) String aadhaarNo,
            // ---- admission form details: all optional so existing callers and records keep working ----
            @Past(message = "Date of birth must be in the past.") LocalDate dateOfBirth,
            @Pattern(regexp = "^(|MALE|FEMALE|OTHER)$", message = "Gender must be Male, Female or Other.") String gender,
            @Size(max = 100) String highestQualification,
            @Size(max = 150) String instituteName,
            @Min(value = 1950, message = "Enter a valid year of completion.") @Max(value = 2100, message = "Enter a valid year of completion.") Integer yearOfCompletion,
            @Pattern(regexp = "^(|EMPLOYED|SELF_EMPLOYED|UNEMPLOYED|STUDENT|OTHER)$", message = "Choose a valid employment status.") String employmentStatus,
            @Size(max = 150) String currentEmployer,
            @Size(max = 100) String jobTitle,
            @DecimalMin(value = "0.00", message = "Annual income cannot be negative.")
            @Digits(integer = 12, fraction = 2, message = "Enter annual income in rupees (up to 2 decimals).") BigDecimal annualIncome,
            Boolean stockMarketExperience,
            @DecimalMin(value = "0.0", message = "Years cannot be negative.") @DecimalMax(value = "60.0", message = "Years of experience cannot exceed 60.")
            @Digits(integer = 2, fraction = 1, message = "Enter years with at most one decimal.") BigDecimal stockMarketYears,
            Boolean tradingExperience,
            @DecimalMin(value = "0.0", message = "Years cannot be negative.") @DecimalMax(value = "60.0", message = "Years of experience cannot exceed 60.")
            @Digits(integer = 2, fraction = 1, message = "Enter years with at most one decimal.") BigDecimal tradingYears,
            @Size(max = 255) String tradingPlatforms,
            @Size(max = 500) String tradingStrategies,
            LocalDate preferredStartDate,
            @Pattern(regexp = "^(|CREDIT_CARD|BANK_TRANSFER|CASH)$", message = "Payment method must be Credit Card, Bank Transfer or Cash.") String paymentMethod
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
                             BigDecimal courseFee, BigDecimal discount, BigDecimal netFee, String discountReason,
                             Instant discountAt, String discountBy,
                             BigDecimal totalPaid, BigDecimal balance, String paymentStatus,
                             List<InstallmentSlot> installments, Integer nextInstallmentNo,
                             String feeReceiptNo, int installmentsRecorded) {
    }

    /**
     * Give / change one student's discount: either a rupee {@code amount} or a {@code percent} of
     * the course fee (exactly one), with the reason for the office's records.
     */
    public record DiscountRequest(
            @DecimalMin(value = "0.01", message = "The discount must be more than zero.") BigDecimal amount,
            @DecimalMin(value = "0.01", message = "The discount must be more than zero.")
            @DecimalMax(value = "100", message = "A percentage discount cannot be more than 100%.") BigDecimal percent,
            @NotBlank(message = "Enter the reason for the discount.") @Size(max = 200) String reason
    ) {
    }

    // ---- free ebook login (Student Panel, ebook only) -------------------------------------------

    /**
     * Automation Admin > student > Ebook Login.
     *
     * @param state           NO_PAYMENT (made when the first installment is paid) | NEEDS_EMAIL | READY (can be
     *                        made now) | ACTIVE | BLOCKED
     * @param password        the first / office-reset password while the student has not changed it; null afterwards
     * @param passwordChanged the student has set their own password (only Reset password shows a new one)
     * @param linkedAccount   the student's email already had a website login; the ebook was added to it
     */
    public record EbookAccess(String state, String message, String studentId, String fullName, String mobile, String email,
                              String userId, String password, boolean passwordChanged, boolean linkedAccount,
                              Instant createdAt, Instant emailedAt, Instant lastLoginAt, String deviceName,
                              List<com.lordsai.lsi.dto.ebook.EbookDtos.AcademyEbook> ebooks, String loginUrl, boolean blocked) {
    }

    // ---- pending fees ------------------------------------------------------------------------

    public record PendingFeeRow(Long id, String studentId, String fullName, String mobile, String email, String address,
                                String batchName, String courseName,
                                BigDecimal courseFee, BigDecimal discount, BigDecimal netFee, BigDecimal totalPaid, BigDecimal balance,
                                String paymentStatus, int installmentsPaid, LocalDate lastPaymentDate, Instant lastReminderAt) {
    }

    public record PendingFees(List<PendingFeeRow> rows, int students, BigDecimal totalBalance) {
    }

    // ---- course price ------------------------------------------------------------------------

    public record CoursePriceRequest(@NotNull(message = "Enter the course price.")
                                     @DecimalMin(value = "0.00", message = "The price cannot be negative.")
                                     @Digits(integer = 8, fraction = 2, message = "Enter the price in rupees (up to 2 decimals).") BigDecimal price) {
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
            /** totalFee is the course fee; netFee = totalFee - discount is what the student has to pay. */
            BigDecimal totalFee, BigDecimal discount, BigDecimal netFee, BigDecimal amountPaid, BigDecimal totalPaid, BigDecimal balance,
            LocalDate paymentDate, String paymentMode, String referenceNo, int installmentNo, String amountInWords,
            Instant issuedAt, String issuedBy, Instant emailedAt,
            String academyName, String academyTagline, String academyAddress, String academyEmail, String academyPhone, String signatory,
            /** The student's payments up to and including this receipt's installment, in installment order. */
            List<ReceiptPaymentLine> history
    ) {
    }

    /** One installment in a receipt's payment history; {@code current} marks the receipt's own payment. */
    public record ReceiptPaymentLine(int installmentNo, String paymentNo, LocalDate paymentDate, String paymentMode,
                                     BigDecimal amount, boolean current) {
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

    /**
     * Either {@code nextNumber} (Settings: move the current series forward) or {@code nextStudentId}
     * (Add Student: the full next ID, whose prefix and digit width become the series).
     */
    public record UpdateStudentIdSeriesRequest(@Min(1) @Max(999999) Integer nextNumber,
                                               @Size(max = 30, message = "A Student ID can be at most 30 characters.") String nextStudentId) {
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
