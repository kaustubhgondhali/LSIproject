package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.dto.AutomationDtos.AttendanceHistoryRow;
import com.lordsai.lsi.automation.dto.AutomationDtos.AttendanceSheet;
import com.lordsai.lsi.automation.dto.AutomationDtos.AttendanceSummary;
import com.lordsai.lsi.automation.dto.AutomationDtos.Mark;
import com.lordsai.lsi.automation.dto.AutomationDtos.SaveAttendanceRequest;
import com.lordsai.lsi.automation.dto.AutomationDtos.SaveSessionRequest;
import com.lordsai.lsi.automation.dto.AutomationDtos.SessionDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.SheetRow;
import com.lordsai.lsi.automation.entity.AcadAttendanceRecord;
import com.lordsai.lsi.automation.entity.AcadAttendanceSession;
import com.lordsai.lsi.automation.entity.AcadBatch;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.entity.AttendanceStatus;
import com.lordsai.lsi.automation.repository.AcadRecordRepository;
import com.lordsai.lsi.automation.repository.AcadSessionRepository;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Attendance sessions per batch, the marking sheet, and the per-student statistics. */
@Service
public class AcadAttendanceService {

    public static final double LOW_ATTENDANCE_THRESHOLD = 75.0;

    private final AcadSessionRepository sessions;
    private final AcadRecordRepository records;
    private final AcadStudentRepository students;
    private final AcadMasterService master;
    private final AcadRowMapper mapper;
    private final AuditService auditService;

    public AcadAttendanceService(AcadSessionRepository sessions, AcadRecordRepository records, AcadStudentRepository students,
                                 AcadMasterService master, AcadRowMapper mapper, AuditService auditService) {
        this.sessions = sessions;
        this.records = records;
        this.students = students;
        this.master = master;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    // ---- sessions ------------------------------------------------------------------------------

    /** Finds or creates the session for (batch, date, type) so "select batch + date" is enough to start marking. */
    @Transactional
    public AttendanceSheet openSheet(SaveSessionRequest req, User actor, String ip) {
        AcadBatch batch = master.requireBatch(req.batchId());
        String type = req.sessionType() == null || req.sessionType().isBlank() ? "CLASS" : req.sessionType().trim().toUpperCase();
        if (req.sessionDate().isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Session date cannot be in the future.");
        }
        Optional<AcadAttendanceSession> existing = sessions.findByBatchIdAndSessionDateAndSessionType(batch.getId(), req.sessionDate(), type);
        AcadAttendanceSession s = existing.orElseGet(() -> {
            AcadAttendanceSession n = new AcadAttendanceSession();
            n.setBatch(batch);
            n.setSessionDate(req.sessionDate());
            n.setSessionType(type);
            n.setCreatedBy(actor);
            return n;
        });
        if (req.courseId() != null) {
            s.setCourse(master.requireCourse(req.courseId()));
        }
        if (req.instructor() != null) {
            s.setInstructor(AcadMasterService.blank(req.instructor()));
        }
        if (req.notes() != null) {
            s.setNotes(AcadMasterService.blank(req.notes()));
        }
        s = sessions.save(s);
        if (existing.isEmpty()) {
            auditService.record(actor, "ACAD_SESSION_CREATED", "AcadAttendanceSession", s.getId(),
                    batch.getName() + " " + req.sessionDate() + " " + type, ip);
        }
        return sheet(s);
    }

    @Transactional(readOnly = true)
    public AttendanceSheet sheet(Long sessionId) {
        return sheet(requireSession(sessionId));
    }

    private AttendanceSheet sheet(AcadAttendanceSession s) {
        Map<Long, AcadAttendanceRecord> marked = new HashMap<>();
        records.findBySessionId(s.getId()).forEach(r -> marked.put(r.getStudent().getId(), r));
        Map<Long, AcadRowMapper.Attendance> stats = mapper.attendanceByStudent();
        List<AcadStudent> roster = students.findByBatchIdAndStatusOrderByStudentIdAsc(s.getBatch().getId(), AcadStudent.STATUS_ACTIVE);
        // Students who were marked in this session but have since moved batch / been archived stay visible.
        marked.values().forEach(r -> { if (roster.stream().noneMatch(x -> x.getId().equals(r.getStudent().getId()))) roster.add(r.getStudent()); });
        List<SheetRow> rows = roster.stream().map(st -> {
            AcadAttendanceRecord r = marked.get(st.getId());
            AcadRowMapper.Attendance a = stats.get(st.getId());
            return new SheetRow(st.getId(), st.getStudentId(), st.getFullName(), r == null ? null : r.getStatus(),
                    a == null ? 0 : a.total(), a == null ? 0 : a.present(), a == null ? null : a.percent());
        }).toList();
        return new AttendanceSheet(toDto(s, marked.values()), rows);
    }

    @Transactional
    public AttendanceSheet save(SaveAttendanceRequest req, User actor, String ip) {
        AcadAttendanceSession s = requireSession(req.sessionId());
        int created = 0, updated = 0, cleared = 0;
        for (Mark m : req.marks()) {
            AcadStudent st = students.findById(m.studentId()).orElseThrow(() -> ResourceNotFoundException.of("Student", m.studentId()));
            Optional<AcadAttendanceRecord> existing = records.findBySessionIdAndStudentId(s.getId(), st.getId());
            if (m.status() == null) {
                if (existing.isPresent()) { records.delete(existing.get()); cleared++; }
                continue;
            }
            AcadAttendanceRecord r = existing.orElseGet(AcadAttendanceRecord::new);
            boolean isNew = r.getId() == null;
            r.setSession(s);
            r.setStudent(st);
            r.setStatus(m.status());
            r.setMarkedBy(actor);
            records.save(r);
            if (isNew) created++; else updated++;
        }
        auditService.record(actor, "ACAD_ATTENDANCE_SAVED", "AcadAttendanceSession", s.getId(),
                s.getBatch().getName() + " " + s.getSessionDate() + ": " + created + " marked, " + updated + " changed, " + cleared + " cleared", ip);
        return sheet(s);
    }

    @Transactional
    public void deleteSession(Long id, User actor, String ip) {
        AcadAttendanceSession s = requireSession(id);
        long n = records.countBySessionId(id);
        records.deleteAll(records.findBySessionId(id));
        sessions.delete(s);
        auditService.record(actor, "ACAD_SESSION_DELETED", "AcadAttendanceSession", id,
                s.getBatch().getName() + " " + s.getSessionDate() + " (" + n + " records)", ip);
    }

    @Transactional(readOnly = true)
    public List<SessionDto> sessions(Long batchId, LocalDate from, LocalDate to) {
        return sessions.report(batchId, from, to).stream().map(s -> toDto(s, records.findBySessionId(s.getId()))).toList();
    }

    // ---- analytics ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AttendanceSummary summaryFor(AcadStudent s) {
        List<AcadAttendanceRecord> history = records.historyForStudent(s.getId());
        int present = 0, absent = 0, late = 0, excused = 0, consecutive = 0;
        boolean streakOpen = true;
        LocalDate last = null;
        for (AcadAttendanceRecord r : history) { // newest first
            switch (r.getStatus()) {
                case PRESENT -> present++;
                case LATE -> late++;
                case ABSENT -> absent++;
                case EXCUSED -> excused++;
            }
            if (streakOpen) {
                if (r.getStatus() == AttendanceStatus.ABSENT) consecutive++;
                else if (r.getStatus() != AttendanceStatus.EXCUSED) streakOpen = false;
            }
            if (r.getStatus().countsAsPresent() && (last == null || r.getSession().getSessionDate().isAfter(last))) {
                last = r.getSession().getSessionDate();
            }
        }
        int counted = history.size() - excused;
        Double pct = counted == 0 ? null : Math.round((present + late) * 1000.0 / counted) / 10.0;
        return new AttendanceSummary(history.size(), present, absent, late, excused, pct, last, consecutive);
    }

    @Transactional(readOnly = true)
    public List<AttendanceHistoryRow> historyFor(AcadStudent s) {
        return records.historyForStudent(s.getId()).stream().map(r -> new AttendanceHistoryRow(r.getSession().getId(),
                r.getSession().getSessionDate(), r.getSession().getBatch().getName(), r.getSession().getSessionType(), r.getStatus())).toList();
    }

    // ---- helpers -----------------------------------------------------------------------------

    public AcadAttendanceSession requireSession(Long id) {
        return sessions.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Attendance session", id));
    }

    private SessionDto toDto(AcadAttendanceSession s, java.util.Collection<AcadAttendanceRecord> recs) {
        long present = recs.stream().filter(r -> r.getStatus().countsAsPresent()).count();
        long absent = recs.stream().filter(r -> r.getStatus() == AttendanceStatus.ABSENT).count();
        return new SessionDto(s.getId(), s.getSessionDate(), s.getBatch().getId(), s.getBatch().getName(),
                s.getCourse() == null ? null : s.getCourse().getId(), s.getCourse() == null ? null : s.getCourse().getName(),
                s.getSessionType(), s.getInstructor(), s.getNotes(), recs.size(), present, absent, s.getCreatedAt());
    }
}
