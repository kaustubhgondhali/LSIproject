package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.dto.AutomationDtos.DashboardStats;
import com.lordsai.lsi.automation.dto.AutomationDtos.StudentRow;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadPaymentRepository;
import com.lordsai.lsi.automation.repository.AcadReceiptRepository;
import com.lordsai.lsi.automation.repository.AcadRecordRepository;
import com.lordsai.lsi.automation.repository.AcadSessionRepository;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/** Live figures for the dashboard cards — all computed from the ledger, nothing stored. */
@Service
public class AcadDashboardService {

    private final AcadStudentRepository students;
    private final AcadPaymentRepository payments;
    private final AcadReceiptRepository receipts;
    private final AcadSessionRepository sessions;
    private final AcadRecordRepository records;
    private final AcadRowMapper mapper;
    private final AcadPaymentService paymentService;

    public AcadDashboardService(AcadStudentRepository students, AcadPaymentRepository payments, AcadReceiptRepository receipts,
                                AcadSessionRepository sessions, AcadRecordRepository records, AcadRowMapper mapper,
                                AcadPaymentService paymentService) {
        this.students = students;
        this.payments = payments;
        this.receipts = receipts;
        this.sessions = sessions;
        this.records = records;
        this.mapper = mapper;
        this.paymentService = paymentService;
    }

    @Transactional(readOnly = true)
    public DashboardStats stats() {
        List<StudentRow> active = mapper.rows(students.findByStatusOrderByStudentIdAsc(AcadStudent.STATUS_ACTIVE));
        long paid = active.stream().filter(r -> "PAID".equals(r.paymentStatus())).count();
        long partial = active.stream().filter(r -> "PARTIAL".equals(r.paymentStatus())).count();
        long pending = active.stream().filter(r -> "PENDING".equals(r.paymentStatus())).count();
        BigDecimal fees = active.stream().map(StudentRow::courseFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = active.stream().map(StudentRow::totalPaid).reduce(BigDecimal.ZERO, BigDecimal::add);

        long counted = records.totalCounted();
        Double rate = counted == 0 ? null : Math.round(records.totalPresent() * 1000.0 / counted) / 10.0;
        List<StudentRow> low = active.stream()
                .filter(r -> r.attendancePercent() != null && r.attendancePercent() < AcadAttendanceService.LOW_ATTENDANCE_THRESHOLD)
                .sorted(Comparator.comparing(StudentRow::attendancePercent)).limit(8).toList();
        List<StudentRow> due = active.stream().filter(r -> r.balance().signum() > 0)
                .sorted(Comparator.comparing(StudentRow::balance).reversed()).limit(8).toList();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        return new DashboardStats(
                students.count(), active.size(), students.countByStatus(AcadStudent.STATUS_ARCHIVED),
                fees, collected, fees.subtract(collected),
                paid, partial, pending,
                sessions.count(), records.count(), rate, low.size(),
                payments.collectedBetween(today, today), payments.collectedBetween(today.withDayOfMonth(1), today),
                receipts.count(), active.stream().filter(r -> r.reviewNote() != null).count(),
                paymentService.recent(8), low, due);
    }
}
