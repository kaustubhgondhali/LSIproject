package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.dto.AutomationDtos.BatchDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.CourseDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.Lookups;
import com.lordsai.lsi.automation.dto.AutomationDtos.MasterItemDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.SaveBatchRequest;
import com.lordsai.lsi.automation.dto.AutomationDtos.SaveCourseRequest;
import com.lordsai.lsi.automation.dto.AutomationDtos.SaveMasterItemRequest;
import com.lordsai.lsi.automation.entity.AcadBatch;
import com.lordsai.lsi.automation.entity.AcadCourse;
import com.lordsai.lsi.automation.entity.AcadMasterData;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.entity.AttendanceStatus;
import com.lordsai.lsi.automation.repository.AcadBatchRepository;
import com.lordsai.lsi.automation.repository.AcadCourseRepository;
import com.lordsai.lsi.automation.repository.AcadMasterDataRepository;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Batches, courses and dropdown values — the "enter once, reuse everywhere" master data. */
@Service
public class AcadMasterService {

    private final AcadBatchRepository batches;
    private final AcadCourseRepository courses;
    private final AcadMasterDataRepository masterData;
    private final AcadStudentRepository students;
    private final AcadSequenceService sequences;
    private final AuditService auditService;

    public AcadMasterService(AcadBatchRepository batches, AcadCourseRepository courses, AcadMasterDataRepository masterData,
                             AcadStudentRepository students, AcadSequenceService sequences, AuditService auditService) {
        this.batches = batches;
        this.courses = courses;
        this.masterData = masterData;
        this.students = students;
        this.sequences = sequences;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Lookups lookups() {
        int year = LocalDate.now(ZoneId.of("Asia/Kolkata")).getYear();
        List<Integer> years = students.findAll().stream().map(AcadStudent::getAdmissionDate).filter(d -> d != null)
                .map(LocalDate::getYear).distinct().sorted().collect(Collectors.toList());
        if (!years.contains(year)) {
            years.add(year);
        }
        return new Lookups(listBatches(), listCourses(), items(AcadMasterData.PAYMENT_MODE), items(AcadMasterData.INSTALLMENT),
                items(AcadMasterData.SESSION_TYPE), Arrays.stream(AttendanceStatus.values()).map(Enum::name).toList(),
                years, sequences.peek(AcadSequenceService.STUDENT));
    }

    // ---- batches ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BatchDto> listBatches() {
        Map<Long, Long> counts = studentCountsBy(s -> s.getBatch() == null ? null : s.getBatch().getId());
        return batches.findAllByOrderByDisplayOrderAscNameAsc().stream().map(b -> toDto(b, counts.getOrDefault(b.getId(), 0L))).toList();
    }

    @Transactional
    public BatchDto saveBatch(Long id, SaveBatchRequest req, User actor, String ip) {
        String name = req.name().trim().toUpperCase();
        batches.findByNameIgnoreCase(name).filter(b -> id == null || !b.getId().equals(id)).ifPresent(b -> {
            throw new ApiException(HttpStatus.CONFLICT, "A batch named " + name + " already exists.");
        });
        AcadBatch b = id == null ? new AcadBatch() : requireBatch(id);
        b.setName(name);
        b.setSchedule(blank(req.schedule()));
        b.setStartDate(req.startDate());
        if (req.active() != null) {
            b.setActive(req.active());
        }
        if (req.displayOrder() != null) {
            b.setDisplayOrder(req.displayOrder());
        }
        b = batches.save(b);
        auditService.record(actor, id == null ? "ACAD_BATCH_CREATED" : "ACAD_BATCH_UPDATED", "AcadBatch", b.getId(), name, ip);
        return toDto(b, 0);
    }

    @Transactional
    public BatchDto setBatchActive(Long id, boolean active, User actor, String ip) {
        AcadBatch b = requireBatch(id);
        b.setActive(active);
        batches.save(b);
        auditService.record(actor, active ? "ACAD_BATCH_ACTIVATED" : "ACAD_BATCH_DEACTIVATED", "AcadBatch", id, b.getName(), ip);
        return toDto(b, 0);
    }

    @Transactional(readOnly = true)
    public AcadBatch requireBatch(Long id) {
        return batches.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Batch", id));
    }

    // ---- courses ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CourseDto> listCourses() {
        Map<Long, Long> counts = studentCountsBy(s -> s.getCourse() == null ? null : s.getCourse().getId());
        return courses.findAllByOrderByDisplayOrderAscNameAsc().stream().map(c -> toDto(c, counts.getOrDefault(c.getId(), 0L))).toList();
    }

    @Transactional
    public CourseDto saveCourse(Long id, SaveCourseRequest req, User actor, String ip) {
        String name = req.name().trim();
        courses.findByNameIgnoreCase(name).filter(c -> id == null || !c.getId().equals(id)).ifPresent(c -> {
            throw new ApiException(HttpStatus.CONFLICT, "A course named " + name + " already exists.");
        });
        AcadCourse c = id == null ? new AcadCourse() : requireCourse(id);
        c.setName(name);
        c.setDefaultFee(req.defaultFee());
        c.setDuration(blank(req.duration()));
        if (req.active() != null) {
            c.setActive(req.active());
        }
        if (req.displayOrder() != null) {
            c.setDisplayOrder(req.displayOrder());
        }
        c = courses.save(c);
        auditService.record(actor, id == null ? "ACAD_COURSE_CREATED" : "ACAD_COURSE_UPDATED", "AcadCourse", c.getId(),
                name + " (fee " + req.defaultFee() + ")", ip);
        return toDto(c, 0);
    }

    @Transactional(readOnly = true)
    public AcadCourse requireCourse(Long id) {
        return courses.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Course", id));
    }

    // ---- dropdown values ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MasterItemDto> items(String category) {
        return masterData.findByCategoryOrderByDisplayOrderAscLabelAsc(category).stream().map(this::toDto).toList();
    }

    @Transactional
    public MasterItemDto saveItem(Long id, SaveMasterItemRequest req, User actor, String ip) {
        masterData.findByCategoryAndCodeIgnoreCase(req.category(), req.code()).filter(m -> id == null || !m.getId().equals(id))
                .ifPresent(m -> { throw new ApiException(HttpStatus.CONFLICT, req.code() + " already exists in " + req.category() + "."); });
        AcadMasterData m = id == null ? new AcadMasterData()
                : masterData.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Master value", id));
        m.setCategory(req.category());
        m.setCode(req.code().trim().toUpperCase());
        m.setLabel(req.label().trim());
        if (req.active() != null) {
            m.setActive(req.active());
        }
        if (req.displayOrder() != null) {
            m.setDisplayOrder(req.displayOrder());
        }
        m = masterData.save(m);
        auditService.record(actor, id == null ? "ACAD_MASTER_CREATED" : "ACAD_MASTER_UPDATED", "AcadMasterData", m.getId(),
                req.category() + ":" + m.getCode(), ip);
        return toDto(m);
    }

    /** Validates a payment mode code against the master list (active values only). */
    @Transactional(readOnly = true)
    public String requirePaymentMode(String code) {
        return masterData.findByCategoryAndCodeIgnoreCase(AcadMasterData.PAYMENT_MODE, code == null ? "" : code.trim())
                .filter(AcadMasterData::isActive).map(AcadMasterData::getCode)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Choose a valid payment mode."));
    }

    @Transactional(readOnly = true)
    public String installmentLabel(int n) {
        return masterData.findByCategoryAndCodeIgnoreCase(AcadMasterData.INSTALLMENT, String.valueOf(n))
                .map(AcadMasterData::getLabel).orElse(ordinal(n) + " Installment");
    }

    public static String ordinal(int n) {
        int mod100 = n % 100;
        String suffix = (mod100 >= 11 && mod100 <= 13) ? "th" : switch (n % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; };
        return n + suffix;
    }

    // ---- helpers -----------------------------------------------------------------------------

    private Map<Long, Long> studentCountsBy(Function<AcadStudent, Long> key) {
        return students.findByStatusOrderByStudentIdAsc(AcadStudent.STATUS_ACTIVE).stream()
                .filter(s -> key.apply(s) != null)
                .collect(Collectors.groupingBy(key, Collectors.counting()));
    }

    static BatchDto toDto(AcadBatch b, long count) {
        return new BatchDto(b.getId(), b.getName(), b.getSchedule(), b.getStartDate(), b.isActive(), b.getDisplayOrder(), count);
    }

    static CourseDto toDto(AcadCourse c, long count) {
        return new CourseDto(c.getId(), c.getName(), c.getDefaultFee(), c.getDuration(), c.isActive(), c.getDisplayOrder(), count);
    }

    private MasterItemDto toDto(AcadMasterData m) {
        return new MasterItemDto(m.getId(), m.getCategory(), m.getCode(), m.getLabel(), m.isActive(), m.getDisplayOrder());
    }

    static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
