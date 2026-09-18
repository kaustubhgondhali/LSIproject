package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.dto.AutomationDtos.BatchDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.CourseDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.MasterItemDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.PaymentDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.ReceiptDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.SessionDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.StudentRow;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Report datasets as column/row tables, so one renderer draws every report and one CSV writer
 * exports any of them (CSV opens directly in Excel; PDF is the browser's print of the report view).
 */
@Service
public class AcadReportService {

    public record Table(String title, List<String> columns, List<List<Object>> rows, Map<String, Object> totals) {
    }

    /** Every filter any Automation Admin screen can apply, so exports match what is on screen. */
    public record ReportQuery(Long batchId, Long courseId, String status, Integer year, LocalDate from, LocalDate to,
                              String mode, Double threshold, String q, String paymentStatus, Long studentId) {
        public static ReportQuery of(Long batchId, Long courseId, String status, Integer year, LocalDate from, LocalDate to,
                                     String mode, Double threshold) {
            return new ReportQuery(batchId, courseId, status, year, from, to, mode, threshold, null, null, null);
        }
    }

    public static final List<String> REPORTS = List.of("students", "batch-wise", "fee-collection", "outstanding",
            "payment-history", "receipts", "attendance", "attendance-sessions", "low-attendance", "batches", "courses-master",
            "payment-modes", "session-types");

    public static boolean knows(String report) {
        return REPORTS.contains(report);
    }

    private final AcadStudentRepository students;
    private final AcadRowMapper mapper;
    private final AcadPaymentService paymentService;
    private final AcadAttendanceService attendanceService;
    private final AcadMasterService masterService;

    public AcadReportService(AcadStudentRepository students, AcadRowMapper mapper, AcadPaymentService paymentService,
                             AcadAttendanceService attendanceService, AcadMasterService masterService) {
        this.students = students;
        this.mapper = mapper;
        this.paymentService = paymentService;
        this.attendanceService = attendanceService;
        this.masterService = masterService;
    }

    @Transactional(readOnly = true)
    public Table build(String report, Long batchId, Long courseId, String status, Integer year, LocalDate from, LocalDate to,
                       String mode, Double threshold) {
        return build(report, ReportQuery.of(batchId, courseId, status, year, from, to, mode, threshold));
    }

    @Transactional(readOnly = true)
    public Table build(String report, ReportQuery f) {
        return switch (report) {
            case "students" -> students(f.batchId(), f.courseId(), f.status(), f.year(), f.paymentStatus(), f.q());
            case "batch-wise" -> batchWise(f.batchId());
            case "fee-collection" -> feeCollection(f.batchId(), f.from(), f.to(), f.mode(), f.studentId());
            case "outstanding" -> outstanding(f.batchId(), f.courseId());
            case "payment-history" -> paymentHistory(f.batchId(), f.from(), f.to(), f.mode(), f.studentId());
            case "receipts" -> receipts(f.from(), f.to(), f.q(), f.studentId());
            case "attendance", "attendance-sessions" -> attendance(f.batchId(), f.from(), f.to());
            case "low-attendance" -> lowAttendance(f.batchId(), f.threshold() == null ? AcadAttendanceService.LOW_ATTENDANCE_THRESHOLD : f.threshold());
            case "batches" -> batches();
            case "courses-master" -> coursesMaster();
            case "payment-modes" -> masterItems("PAYMENT_MODE", "Payment Modes");
            case "session-types" -> masterItems("SESSION_TYPE", "Session Types");
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown report: " + report);
        };
    }

    private List<StudentRow> rows(Long batchId, Long courseId, String status, Integer year, String q) {
        String qUpper = q == null || q.isBlank() ? null : AcadStudentService.normalizeName(q);
        List<AcadStudent> all = students.search(qUpper, batchId, courseId, status == null || status.isBlank() ? null : status, year,
                org.springframework.data.domain.PageRequest.of(0, 5000, org.springframework.data.domain.Sort.by("studentId"))).getContent();
        return mapper.rows(all);
    }

    private List<StudentRow> rows(Long batchId, Long courseId, String status, Integer year) {
        return rows(batchId, courseId, status, year, null);
    }

    private Table students(Long batchId, Long courseId, String status, Integer year, String paymentStatus) {
        return students(batchId, courseId, status, year, paymentStatus, null);
    }

    private Table students(Long batchId, Long courseId, String status, Integer year, String paymentStatus, String q) {
        List<StudentRow> rows = rows(batchId, courseId, status == null ? AcadStudent.STATUS_ACTIVE : status, year, q);
        if (paymentStatus != null && !paymentStatus.isBlank()) {
            rows = rows.stream().filter(r -> paymentStatus.equalsIgnoreCase(r.paymentStatus())).toList();
        }
        List<List<Object>> out = new ArrayList<>();
        for (StudentRow r : rows) {
            out.add(List.of(r.studentId(), r.fullName(), n(r.batchName()), n(r.courseName()), n(r.admissionDate()), n(r.mobile()), n(r.email()),
                    r.courseFee(), r.totalPaid(), r.balance(), r.paymentStatus(), pct(r.attendancePercent()), r.status()));
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("students", rows.size());
        totals.put("fees", rows.stream().map(StudentRow::courseFee).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        totals.put("paid", rows.stream().map(StudentRow::totalPaid).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        totals.put("balance", rows.stream().map(StudentRow::balance).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        return new Table("Student Report", List.of("Student ID", "Name", "Batch", "Course", "Admission", "Mobile", "Email",
                "Course Fee", "Paid", "Balance", "Payment Status", "Attendance %", "Status"), out, totals);
    }

    private Table batchWise(Long batchId) {
        Table t = students(batchId, null, AcadStudent.STATUS_ACTIVE, null, null);
        return new Table("Batch-wise Student Report", t.columns(), t.rows(), t.totals());
    }

    private Table outstanding(Long batchId, Long courseId) {
        List<StudentRow> rows = rows(batchId, courseId, AcadStudent.STATUS_ACTIVE, null).stream().filter(r -> r.balance().signum() > 0).toList();
        List<List<Object>> out = new ArrayList<>();
        for (StudentRow r : rows) {
            out.add(List.of(r.studentId(), r.fullName(), n(r.batchName()), n(r.mobile()), r.courseFee(), r.totalPaid(), r.balance(),
                    r.paymentStatus(), n(r.lastPaymentDate())));
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("students", rows.size());
        totals.put("balance", rows.stream().map(StudentRow::balance).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        return new Table("Outstanding Balance Report", List.of("Student ID", "Name", "Batch", "Mobile", "Course Fee", "Paid", "Balance",
                "Status", "Last Payment"), out, totals);
    }

    private Table feeCollection(Long batchId, LocalDate from, LocalDate to, String mode, Long studentId) {
        List<PaymentDto> list = paymentService.report(studentId, batchId, from, to, mode);
        List<List<Object>> out = new ArrayList<>();
        Map<String, java.math.BigDecimal> byMode = new LinkedHashMap<>();
        for (PaymentDto p : list) {
            out.add(List.of(n(p.paymentDate()), p.paymentNo(), p.studentCode(), p.studentName(), n(p.batchName()), p.installmentLabel(),
                    p.amount(), p.paymentMode(), n(p.referenceNo()), n(p.receiptNo())));
            byMode.merge(p.paymentMode(), p.amount(), java.math.BigDecimal::add);
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("payments", list.size());
        totals.put("collected", list.stream().map(PaymentDto::amount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        totals.put("byMode", byMode);
        return new Table("Fee Collection Report", List.of("Date", "Payment No", "Student ID", "Name", "Batch", "Installment",
                "Amount", "Mode", "Reference", "Receipt No"), out, totals);
    }

    private Table paymentHistory(Long batchId, LocalDate from, LocalDate to, String mode, Long studentId) {
        Table t = feeCollection(batchId, from, to, mode, studentId);
        return new Table("Payment History", t.columns(), t.rows(), t.totals());
    }

    private Table receipts(LocalDate from, LocalDate to, String q, Long studentId) {
        List<ReceiptDto> list = paymentService.receipts(studentId, from, to, q, 0, 5000).getContent();
        List<List<Object>> out = new ArrayList<>();
        for (ReceiptDto r : list) {
            out.add(List.of(r.receiptNo(), n(r.paymentDate()), r.studentCode(), r.studentName(), n(r.courseName()), n(r.batchName()),
                    r.totalFee(), r.amountPaid(), r.totalPaid(), r.balance(), r.paymentMode(), r.emailedAt() == null ? "No" : "Yes"));
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("receipts", list.size());
        totals.put("amount", list.stream().map(ReceiptDto::amountPaid).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        return new Table("Receipt Report", List.of("Receipt No", "Date", "Student ID", "Name", "Course", "Batch", "Total Fee",
                "Paid (this)", "Paid to Date", "Balance", "Mode", "Emailed"), out, totals);
    }

    private Table attendance(Long batchId, LocalDate from, LocalDate to) {
        List<SessionDto> list = attendanceService.sessions(batchId, from, to);
        List<List<Object>> out = new ArrayList<>();
        for (SessionDto s : list) {
            out.add(List.of(n(s.sessionDate()), s.batchName(), s.sessionType(), n(s.instructor()), s.marked(), s.present(), s.absent(),
                    s.marked() == 0 ? "" : Math.round(s.present() * 1000.0 / s.marked()) / 10.0 + "%"));
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("sessions", list.size());
        totals.put("present", list.stream().mapToLong(SessionDto::present).sum());
        totals.put("absent", list.stream().mapToLong(SessionDto::absent).sum());
        return new Table("Attendance Report", List.of("Date", "Batch", "Type", "Instructor", "Marked", "Present", "Absent", "Present %"), out, totals);
    }

    private Table lowAttendance(Long batchId, double threshold) {
        List<StudentRow> rows = rows(batchId, null, AcadStudent.STATUS_ACTIVE, null).stream()
                .filter(r -> r.attendancePercent() != null && r.attendancePercent() < threshold).toList();
        List<List<Object>> out = new ArrayList<>();
        for (StudentRow r : rows) {
            out.add(List.of(r.studentId(), r.fullName(), n(r.batchName()), n(r.mobile()), r.sessionsPresent() + " / " + r.sessionsTotal(),
                    pct(r.attendancePercent()), n(r.lastAttendanceDate())));
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("students", rows.size());
        totals.put("threshold", threshold);
        return new Table("Low Attendance Report (below " + threshold + "%)", List.of("Student ID", "Name", "Batch", "Mobile",
                "Attended", "Attendance %", "Last Present"), out, totals);
    }

    private Table batches() {
        List<BatchDto> list = masterService.listBatches();
        List<List<Object>> out = new ArrayList<>();
        for (BatchDto b : list) {
            out.add(List.of(b.name(), n(b.schedule()), n(b.startDate()), b.students(), b.active() ? "ACTIVE" : "INACTIVE"));
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("batches", list.size());
        return new Table("Batches", List.of("Batch", "Schedule", "Start Date", "Students", "Status"), out, totals);
    }

    private Table coursesMaster() {
        List<CourseDto> list = masterService.listCourses();
        List<List<Object>> out = new ArrayList<>();
        for (CourseDto c : list) {
            out.add(List.of(c.name(), c.defaultFee(), n(c.duration()), c.students(), c.active() ? "ACTIVE" : "INACTIVE"));
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("courses", list.size());
        return new Table("Courses (Academy Office)", List.of("Course", "Default Fee", "Duration", "Students", "Status"), out, totals);
    }

    private Table masterItems(String category, String title) {
        List<MasterItemDto> list = masterService.items(category);
        List<List<Object>> out = new ArrayList<>();
        for (MasterItemDto m : list) {
            out.add(List.of(m.code(), m.label(), m.active() ? "ACTIVE" : "INACTIVE"));
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("values", list.size());
        return new Table(title, List.of("Code", "Label", "Status"), out, totals);
    }

    public static String csv(Table t) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(String.join(",", t.columns().stream().map(AcadReportService::cell).toList())).append("\r\n");
        for (List<Object> row : t.rows()) {
            sb.append(String.join(",", row.stream().map(AcadReportService::cell).toList())).append("\r\n");
        }
        return sb.toString();
    }

    private static String cell(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        // Neutralise spreadsheet formula injection and quote as needed.
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !s.matches("^-?\\d+(\\.\\d+)?$")) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static Object n(Object v) {
        return v == null ? "" : v;
    }

    private static String pct(Double v) {
        return v == null ? "" : v + "%";
    }
}
