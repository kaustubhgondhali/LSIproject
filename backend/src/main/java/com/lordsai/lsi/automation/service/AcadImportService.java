package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.dto.AutomationDtos.ImportMark;
import com.lordsai.lsi.automation.dto.AutomationDtos.ImportPayload;
import com.lordsai.lsi.automation.dto.AutomationDtos.ImportPayment;
import com.lordsai.lsi.automation.dto.AutomationDtos.ImportResult;
import com.lordsai.lsi.automation.dto.AutomationDtos.ImportSession;
import com.lordsai.lsi.automation.dto.AutomationDtos.ImportStudent;
import com.lordsai.lsi.automation.entity.AcadAttendanceRecord;
import com.lordsai.lsi.automation.entity.AcadAttendanceSession;
import com.lordsai.lsi.automation.entity.AcadBatch;
import com.lordsai.lsi.automation.entity.AcadCourse;
import com.lordsai.lsi.automation.entity.AcadMasterData;
import com.lordsai.lsi.automation.entity.AcadPayment;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.entity.AttendanceStatus;
import com.lordsai.lsi.automation.repository.AcadBatchRepository;
import com.lordsai.lsi.automation.repository.AcadCourseRepository;
import com.lordsai.lsi.automation.repository.AcadMasterDataRepository;
import com.lordsai.lsi.automation.repository.AcadPaymentRepository;
import com.lordsai.lsi.automation.repository.AcadRecordRepository;
import com.lordsai.lsi.automation.repository.AcadSessionRepository;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Loads the normalised workbook payload (produced by backend/db/import/convert_academy_workbook.py).
 * Idempotent: a student, installment or attendance mark that already exists is skipped, never
 * overwritten, so the import can be re-run safely. Placeholder rows without a name are refused.
 */
@Service
public class AcadImportService {

    private static final Logger log = LoggerFactory.getLogger(AcadImportService.class);

    private final AcadStudentRepository students;
    private final AcadBatchRepository batches;
    private final AcadCourseRepository courses;
    private final AcadMasterDataRepository masterData;
    private final AcadPaymentRepository payments;
    private final AcadSessionRepository sessions;
    private final AcadRecordRepository records;
    private final AcadSequenceService sequences;
    private final AcadPaymentService paymentService;
    private final AuditService auditService;

    public AcadImportService(AcadStudentRepository students, AcadBatchRepository batches, AcadCourseRepository courses,
                             AcadMasterDataRepository masterData, AcadPaymentRepository payments, AcadSessionRepository sessions,
                             AcadRecordRepository records, AcadSequenceService sequences, AcadPaymentService paymentService,
                             AuditService auditService) {
        this.students = students;
        this.batches = batches;
        this.courses = courses;
        this.masterData = masterData;
        this.payments = payments;
        this.sessions = sessions;
        this.records = records;
        this.sequences = sequences;
        this.paymentService = paymentService;
        this.auditService = auditService;
    }

    @Transactional
    public ImportResult run(ImportPayload payload, User actor, String ip) {
        int sc = 0, su = 0, ss = 0, pc = 0, ps = 0, rc = 0, sec = 0, recc = 0, recs = 0;
        List<String> review = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (ImportStudent in : nz(payload.students())) {
            String code = AcadSequenceService.canonicalStudentId(in.studentId());
            if (code == null) { errors.add("Student row with unreadable ID '" + in.studentId() + "' skipped."); ss++; continue; }
            if (in.fullName() == null || in.fullName().isBlank()) { errors.add(code + ": no name — placeholder row not imported."); ss++; continue; }
            Optional<AcadStudent> existing = students.findByStudentIdIgnoreCase(code);
            if (existing.isPresent()) { ss++; continue; }

            AcadStudent s = new AcadStudent();
            s.setStudentId(code);
            s.setFullName(in.fullName().trim().replaceAll("\\s+", " "));
            s.setNameNormalized(AcadStudentService.normalizeName(in.fullName()));
            s.setAdmissionDate(in.admissionDate());
            s.setAdmissionDateRaw(AcadMasterService.blank(in.admissionDateRaw()));
            s.setMobile(AcadMasterService.blank(in.mobile()));
            s.setEmail(in.email() == null || in.email().isBlank() ? null : in.email().trim().toLowerCase(Locale.ROOT));
            s.setAddress(AcadMasterService.blank(in.address()));
            s.setBatch(in.batch() == null || in.batch().isBlank() ? null : batch(in.batch()));
            AcadCourse course = in.course() == null || in.course().isBlank() ? null : course(in.course(), in.courseFee());
            s.setCourse(course);
            s.setCourseFee(in.courseFee() != null ? in.courseFee() : course != null ? course.getDefaultFee() : BigDecimal.ZERO);
            s.setReviewNote(AcadMasterService.blank(in.reviewNote()));
            s.setSource("IMPORT");
            s.setCreatedBy(actor);
            s.setUpdatedBy(actor);
            students.save(s);
            sc++;
            if (s.getReviewNote() != null) {
                review.add(code + " " + s.getFullName() + ": " + s.getReviewNote());
            }
        }
        // Keep the counter ahead of whatever was imported (LSA/2026/0021 is next after 0020).
        students.flush();

        for (ImportPayment in : nz(payload.payments())) {
            String code = AcadSequenceService.canonicalStudentId(in.studentId());
            Optional<AcadStudent> st = code == null ? Optional.empty() : students.findByStudentIdIgnoreCase(code);
            if (st.isEmpty()) { errors.add("Payment for unknown student '" + in.studentId() + "' skipped."); ps++; continue; }
            if (in.amount() == null || in.amount().signum() <= 0) { ps++; continue; }
            if (payments.existsByStudentIdAndInstallmentNo(st.get().getId(), in.installmentNo())) { ps++; continue; }

            AcadPayment p = new AcadPayment();
            p.setPaymentNo(sequences.next(AcadSequenceService.PAYMENT, in.paymentDate() != null ? in.paymentDate().getYear()
                    : st.get().getAdmissionDate() != null ? st.get().getAdmissionDate().getYear() : 2026));
            p.setStudent(st.get());
            p.setInstallmentNo(in.installmentNo());
            p.setPaymentDate(in.paymentDate());
            p.setAmount(in.amount());
            p.setPaymentMode(mode(in.paymentMode()));
            p.setNotes(AcadMasterService.blank(in.notes()));
            p.setReviewNote(AcadMasterService.blank(in.reviewNote()));
            p.setSource("IMPORT");
            p.setRecordedBy(actor);
            p = payments.save(p);
            pc++;
            paymentService.issueReceipt(p, actor);
            rc++;
            if (p.getReviewNote() != null) {
                review.add(code + " installment " + in.installmentNo() + ": " + p.getReviewNote());
            }
        }

        for (ImportSession in : nz(payload.sessions())) {
            if (in.batch() == null || in.sessionDate() == null) { errors.add("Attendance block without batch/date skipped."); continue; }
            AcadBatch batch = batch(in.batch());
            String type = in.sessionType() == null || in.sessionType().isBlank() ? "CLASS" : in.sessionType();
            Optional<AcadAttendanceSession> existing = sessions.findByBatchIdAndSessionDateAndSessionType(batch.getId(), in.sessionDate(), type);
            AcadAttendanceSession session = existing.orElseGet(() -> {
                AcadAttendanceSession n = new AcadAttendanceSession();
                n.setBatch(batch);
                n.setSessionDate(in.sessionDate());
                n.setSessionType(type);
                n.setCreatedBy(actor);
                return sessions.save(n);
            });
            if (existing.isEmpty()) {
                sec++;
            }
            for (ImportMark m : nz(in.marks())) {
                String code = AcadSequenceService.canonicalStudentId(m.studentId());
                Optional<AcadStudent> st = code == null ? Optional.empty() : students.findByStudentIdIgnoreCase(code);
                if (st.isEmpty()) { errors.add("Attendance for unknown student '" + m.studentId() + "' on " + in.sessionDate() + " skipped."); recs++; continue; }
                AttendanceStatus status = status(m.status());
                if (status == null) { recs++; continue; }
                if (records.findBySessionIdAndStudentId(session.getId(), st.get().getId()).isPresent()) { recs++; continue; }
                AcadAttendanceRecord r = new AcadAttendanceRecord();
                r.setSession(session);
                r.setStudent(st.get());
                r.setStatus(status);
                r.setMarkedBy(actor);
                records.save(r);
                recc++;
            }
        }

        ImportResult result = new ImportResult(sc, su, ss, pc, ps, rc, sec, recc, recs, review, errors);
        auditService.record(actor, "ACAD_IMPORT_RUN", "AcadImport", null,
                "students +" + sc + " (skipped " + ss + "), payments +" + pc + " (skipped " + ps + "), receipts +" + rc
                        + ", sessions +" + sec + ", attendance +" + recc + " (skipped " + recs + "), review items " + review.size()
                        + ", errors " + errors.size(), ip);
        log.info("[AUTOMATION] Import: {}", result);
        return result;
    }

    private AcadBatch batch(String name) {
        String n = name.trim().toUpperCase(Locale.ROOT);
        return batches.findByNameIgnoreCase(n).orElseGet(() -> {
            AcadBatch b = new AcadBatch();
            b.setName(n);
            b.setDisplayOrder((int) batches.count() + 1);
            return batches.save(b);
        });
    }

    private AcadCourse course(String name, BigDecimal fee) {
        return courses.findByNameIgnoreCase(name.trim()).orElseGet(() -> {
            AcadCourse c = new AcadCourse();
            c.setName(name.trim());
            c.setDefaultFee(fee == null ? BigDecimal.ZERO : fee);
            c.setDisplayOrder((int) courses.count() + 1);
            return courses.save(c);
        });
    }

    private String mode(String raw) {
        String code = raw == null || raw.isBlank() ? "CASH" : raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return masterData.findByCategoryAndCodeIgnoreCase(AcadMasterData.PAYMENT_MODE, code).map(AcadMasterData::getCode).orElse("OTHER");
    }

    private static AttendanceStatus status(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "P", "PRESENT" -> AttendanceStatus.PRESENT;
            case "A", "ABSENT" -> AttendanceStatus.ABSENT;
            case "L", "LATE" -> AttendanceStatus.LATE;
            case "E", "EXCUSED" -> AttendanceStatus.EXCUSED;
            default -> null;
        };
    }

    private static <T> List<T> nz(List<T> list) {
        return list == null ? List.of() : list;
    }
}
