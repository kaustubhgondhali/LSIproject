package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.dto.AutomationDtos.ActivityRow;
import com.lordsai.lsi.automation.dto.AutomationDtos.SaveStudentRequest;
import com.lordsai.lsi.automation.dto.AutomationDtos.SearchHit;
import com.lordsai.lsi.automation.dto.AutomationDtos.StudentProfile;
import com.lordsai.lsi.automation.dto.AutomationDtos.StudentRow;
import com.lordsai.lsi.automation.entity.AcadBatch;
import com.lordsai.lsi.automation.entity.AcadCourse;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.AuditLogRepository;
import com.lordsai.lsi.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Locale;

/** Student records: the single source every other Automation Admin screen reads from. */
@Service
public class AcadStudentService {

    private final AcadStudentRepository students;
    private final AcadMasterService master;
    private final AcadSequenceService sequences;
    private final AcadRowMapper mapper;
    private final AcadPaymentService paymentService;
    private final AcadAttendanceService attendanceService;
    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;

    public AcadStudentService(AcadStudentRepository students, AcadMasterService master, AcadSequenceService sequences,
                              AcadRowMapper mapper, AcadPaymentService paymentService, AcadAttendanceService attendanceService,
                              AuditService auditService, AuditLogRepository auditLogRepository) {
        this.students = students;
        this.master = master;
        this.sequences = sequences;
        this.mapper = mapper;
        this.paymentService = paymentService;
        this.attendanceService = attendanceService;
        this.auditService = auditService;
        this.auditLogRepository = auditLogRepository;
    }

    /** "ANJALI  THAKUR " -> "ANJALI THAKUR": what search and duplicate checks compare. */
    public static String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public Page<StudentRow> search(String q, Long batchId, Long courseId, String status, Integer year, String paymentStatus,
                                   int page, int size) {
        String qUpper = q == null || q.isBlank() ? null : normalizeName(q);
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 200), Sort.by("studentId"));
        Page<AcadStudent> found = students.search(qUpper, batchId, courseId,
                status == null || status.isBlank() ? null : status, year, pageable);
        List<StudentRow> rows = mapper.rows(found.getContent());
        if (paymentStatus != null && !paymentStatus.isBlank()) {
            rows = rows.stream().filter(r -> paymentStatus.equalsIgnoreCase(r.paymentStatus())).toList();
        }
        return new org.springframework.data.domain.PageImpl<>(rows, pageable, found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<SearchHit> quickSearch(String q) {
        if (q == null || q.trim().length() < 2) {
            return List.of();
        }
        Page<StudentRow> page = search(q, null, null, null, null, null, 0, 8);
        return page.getContent().stream().map(r -> new SearchHit(r.id(), r.studentId(), r.fullName(), r.batchName(),
                r.mobile(), r.status(), r.paymentStatus(), r.balance())).toList();
    }

    @Transactional(readOnly = true)
    public StudentRow get(Long id) {
        return mapper.row(require(id));
    }

    @Transactional(readOnly = true)
    public AcadStudent require(Long id) {
        return students.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Student", id));
    }

    @Transactional
    public StudentRow create(SaveStudentRequest req, User actor, String ip) {
        // Compulsory for every NEW admission. Editing keeps the old rule so older / imported
        // records that have no email or address can still be opened and saved.
        if (req.email() == null || req.email().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "E-mail ID is required.");
        }
        if (req.address() == null || req.address().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Address is required.");
        }
        AcadBatch batch = master.requireBatch(req.batchId());
        AcadCourse course = master.requireCourse(req.courseId());
        String normalized = normalizeName(req.fullName());

        AcadStudent s = new AcadStudent();
        applyAadhaar(s, req.aadhaarNo());
        s.setStudentId(sequences.next(AcadSequenceService.STUDENT));
        apply(s, req, batch, course, normalized);
        s.setCreatedBy(actor);
        s.setUpdatedBy(actor);
        s = students.save(s);
        auditService.record(actor, "ACAD_STUDENT_CREATED", "AcadStudent", s.getId(),
                s.getStudentId() + " " + s.getFullName() + " (" + batch.getName() + ", " + course.getName() + ")", ip);
        return mapper.row(s);
    }

    @Transactional
    public StudentRow update(Long id, SaveStudentRequest req, User actor, String ip) {
        AcadStudent s = require(id);
        AcadBatch batch = master.requireBatch(req.batchId());
        AcadCourse course = master.requireCourse(req.courseId());
        String before = s.getFullName() + " / " + (s.getBatch() == null ? "-" : s.getBatch().getName()) + " / fee " + s.getCourseFee();
        String aadhaarBefore = s.getAadhaarNo();
        checkFeeCoversDiscount(s, req.courseFee() != null ? req.courseFee() : course.getDefaultFee());
        applyAadhaar(s, req.aadhaarNo());
        apply(s, req, batch, course, normalizeName(req.fullName()));
        s.setUpdatedBy(actor);
        // The admin has looked at this record; a review flag from the import is cleared on save.
        s.setReviewNote(null);
        s.setAdmissionDateRaw(null);
        students.save(s);
        auditService.record(actor, "ACAD_STUDENT_UPDATED", "AcadStudent", id, before + " -> " + s.getFullName() + " / "
                + batch.getName() + " / fee " + s.getCourseFee()
                + (java.util.Objects.equals(aadhaarBefore, s.getAadhaarNo()) ? "" : " / Aadhaar " + Aadhaar.mask(s.getAadhaarNo())), ip);
        return mapper.row(s);
    }

    @Transactional
    public StudentRow setStatus(Long id, boolean archive, User actor, String ip) {
        AcadStudent s = require(id);
        s.setStatus(archive ? AcadStudent.STATUS_ARCHIVED : AcadStudent.STATUS_ACTIVE);
        s.setUpdatedBy(actor);
        students.save(s);
        auditService.record(actor, archive ? "ACAD_STUDENT_ARCHIVED" : "ACAD_STUDENT_RESTORED", "AcadStudent", id, s.getStudentId(), ip);
        return mapper.row(s);
    }

    @Transactional(readOnly = true)
    public StudentProfile profile(Long id) {
        AcadStudent s = require(id);
        List<ActivityRow> activity = auditLogRepository.activityFor("AcadStudent", id, s.getStudentId(), PageRequest.of(0, 30))
                .getContent().stream()
                .map(a -> new ActivityRow(a.getAction(), a.getDescription(),
                        a.getActor() == null ? "system" : a.getActor().getFullName(), a.getCreatedAt()))
                .toList();
        return new StudentProfile(mapper.row(s), paymentService.feeSummary(s), paymentService.paymentsFor(s),
                paymentService.receiptsFor(s), attendanceService.summaryFor(s), attendanceService.historyFor(s), activity);
    }

    /**
     * Required for a new student. When editing, a blank field keeps the number already saved; a
     * student saved before Aadhaar was required must be given one. Always 12 valid digits, and
     * never the number of another academy student.
     */
    private void applyAadhaar(AcadStudent s, String raw) {
        String digits = Aadhaar.normalize(raw);
        if (digits == null) {
            if (s.getAadhaarNo() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Aadhaar number is required.");
            }
            return;
        }
        if (!Aadhaar.isValid(digits)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, Aadhaar.INVALID);
        }
        students.findByAadhaarNo(digits).filter(other -> !other.getId().equals(s.getId())).ifPresent(other -> {
            throw new ApiException(HttpStatus.CONFLICT, "Aadhaar number " + Aadhaar.mask(digits) + " is already recorded for "
                    + other.getStudentId() + " (" + other.getFullName() + ").");
        });
        s.setAadhaarNo(digits);
    }

    /**
     * A student with a discount: the new fee, less the discount, must still cover what was already
     * paid. Checked before anything on the record changes.
     */
    private void checkFeeCoversDiscount(AcadStudent s, BigDecimal newFee) {
        if (s.getDiscountAmount() == null || s.getDiscountAmount().signum() <= 0 || newFee == null) {
            return;
        }
        BigDecimal paid = paymentService.totalPaid(s);
        BigDecimal net = newFee.subtract(s.getDiscountAmount());
        if (net.compareTo(paid) < 0 || net.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "With the discount of ₹" + AcadReceiptPdfService.indian(s.getDiscountAmount())
                    + ", a course fee of ₹" + AcadReceiptPdfService.indian(newFee) + " would be less than what the student has already paid (₹"
                    + AcadReceiptPdfService.indian(paid) + "). Change or remove the discount first.");
        }
    }

    private void apply(AcadStudent s, SaveStudentRequest req, AcadBatch batch, AcadCourse course, String normalized) {
        String mobile = req.mobile().trim();
        if (req.email() != null && !req.email().isBlank()) {
            s.setEmail(req.email().trim().toLowerCase(Locale.ROOT));
        } else {
            s.setEmail(null);
        }
        s.setFullName(req.fullName().trim().replaceAll("\\s+", " "));
        s.setNameNormalized(normalized);
        s.setAdmissionDate(req.admissionDate());
        s.setMobile(mobile);
        s.setAddress(AcadMasterService.blank(req.address()));
        s.setBatch(batch);
        s.setCourse(course);
        BigDecimal fee = req.courseFee() != null ? req.courseFee() : course.getDefaultFee();
        if (fee.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Course fee cannot be negative.");
        }
        s.setCourseFee(fee);
        applyAdmissionDetails(s, req);
    }

    /** The admission-form fields. All optional: a null/blank value is stored as null, as for older records. */
    private static void applyAdmissionDetails(AcadStudent s, SaveStudentRequest req) {
        if (req.dateOfBirth() != null && req.dateOfBirth().isBefore(LocalDate.of(1900, 1, 1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid date of birth.");
        }
        int latestYear = Year.now().getValue() + 10;
        if (req.yearOfCompletion() != null && req.yearOfCompletion() > latestYear) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Year of completion cannot be later than " + latestYear + ".");
        }
        s.setDateOfBirth(req.dateOfBirth());
        s.setGender(AcadMasterService.blank(req.gender()));
        s.setHighestQualification(AcadMasterService.blank(req.highestQualification()));
        s.setInstituteName(AcadMasterService.blank(req.instituteName()));
        s.setYearOfCompletion(req.yearOfCompletion());
        s.setEmploymentStatus(AcadMasterService.blank(req.employmentStatus()));
        s.setCurrentEmployer(AcadMasterService.blank(req.currentEmployer()));
        s.setJobTitle(AcadMasterService.blank(req.jobTitle()));
        s.setAnnualIncome(req.annualIncome());
        // Years only mean something when the answer is "Yes".
        s.setStockMarketExperience(req.stockMarketExperience());
        s.setStockMarketYears(Boolean.TRUE.equals(req.stockMarketExperience()) ? req.stockMarketYears() : null);
        s.setTradingExperience(req.tradingExperience());
        s.setTradingYears(Boolean.TRUE.equals(req.tradingExperience()) ? req.tradingYears() : null);
        s.setTradingPlatforms(AcadMasterService.blank(req.tradingPlatforms()));
        s.setTradingStrategies(AcadMasterService.blank(req.tradingStrategies()));
        s.setPreferredStartDate(req.preferredStartDate());
        s.setPaymentMethod(AcadMasterService.blank(req.paymentMethod()));
    }
}
