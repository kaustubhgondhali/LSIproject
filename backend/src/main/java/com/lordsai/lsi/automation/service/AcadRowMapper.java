package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.dto.AutomationDtos.StudentRow;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadPaymentRepository;
import com.lordsai.lsi.automation.repository.AcadRecordRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link StudentRow}s with fee totals and attendance figures computed from the ledger,
 * using two grouped queries for the whole list rather than one query per student.
 */
@Component
public class AcadRowMapper {

    public record Fees(BigDecimal paid, LocalDate lastPayment) {
    }

    public record Attendance(int total, int present, int excused, LocalDate last) {
        Double percent() {
            int counted = total - excused;
            return counted == 0 ? null : Math.round(present * 1000.0 / counted) / 10.0;
        }
    }

    private final AcadPaymentRepository payments;
    private final AcadRecordRepository records;

    public AcadRowMapper(AcadPaymentRepository payments, AcadRecordRepository records) {
        this.payments = payments;
        this.records = records;
    }

    public Map<Long, Fees> feesByStudent() {
        Map<Long, Fees> out = new HashMap<>();
        for (Object[] r : payments.totalsByStudent()) {
            out.put((Long) r[0], new Fees((BigDecimal) r[1], (LocalDate) r[2]));
        }
        return out;
    }

    public Map<Long, Attendance> attendanceByStudent() {
        Map<Long, Attendance> out = new HashMap<>();
        for (Object[] r : records.summaryByStudent()) {
            out.put((Long) r[0], new Attendance(((Number) r[1]).intValue(), ((Number) r[2]).intValue(),
                    ((Number) r[3]).intValue(), (LocalDate) r[4]));
        }
        return out;
    }

    public List<StudentRow> rows(Collection<AcadStudent> students) {
        Map<Long, Fees> fees = feesByStudent();
        Map<Long, Attendance> att = attendanceByStudent();
        return students.stream().map(s -> row(s, fees.get(s.getId()), att.get(s.getId()))).toList();
    }

    public StudentRow row(AcadStudent s) {
        return row(s, feesByStudent().get(s.getId()), attendanceByStudent().get(s.getId()));
    }

    public StudentRow row(AcadStudent s, Fees f, Attendance a) {
        BigDecimal paid = f == null ? BigDecimal.ZERO : f.paid();
        BigDecimal balance = s.getCourseFee().subtract(paid);
        return new StudentRow(s.getId(), s.getStudentId(), s.getFullName(),
                s.getBatch() == null ? null : s.getBatch().getId(), s.getBatch() == null ? null : s.getBatch().getName(),
                s.getCourse() == null ? null : s.getCourse().getId(), s.getCourse() == null ? null : s.getCourse().getName(),
                s.getAdmissionDate(), s.getAdmissionDateRaw(), s.getMobile(), s.getEmail(), s.getAddress(),
                s.getCourseFee(), paid, balance, paymentStatus(s.getCourseFee(), paid),
                a == null ? 0 : a.total(), a == null ? 0 : a.present(), a == null ? null : a.percent(),
                f == null ? null : f.lastPayment(), a == null ? null : a.last(),
                s.getStatus(), s.getReviewNote(), s.getSource(), s.getCreatedAt(), s.getUpdatedAt());
    }

    /** PAID when the fee is fully covered, PENDING when nothing has been paid, otherwise PARTIAL. */
    public static String paymentStatus(BigDecimal fee, BigDecimal paid) {
        if (paid == null || paid.signum() <= 0) {
            return fee == null || fee.signum() == 0 ? "PAID" : "PENDING";
        }
        return paid.compareTo(fee) >= 0 ? "PAID" : "PARTIAL";
    }
}
