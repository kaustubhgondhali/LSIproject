package com.lordsai.lsi.automation.web;

import com.lordsai.lsi.automation.dto.AutomationDtos.BatchDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.CourseDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.DashboardStats;
import com.lordsai.lsi.automation.dto.AutomationDtos.ImportPayload;
import com.lordsai.lsi.automation.dto.AutomationDtos.ImportResult;
import com.lordsai.lsi.automation.dto.AutomationDtos.Lookups;
import com.lordsai.lsi.automation.dto.AutomationDtos.MasterItemDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.SaveBatchRequest;
import com.lordsai.lsi.automation.dto.AutomationDtos.SaveCourseRequest;
import com.lordsai.lsi.automation.dto.AutomationDtos.SaveMasterItemRequest;
import com.lordsai.lsi.automation.dto.AutomationDtos.SaveStudentRequest;
import com.lordsai.lsi.automation.dto.AutomationDtos.SearchHit;
import com.lordsai.lsi.automation.dto.AutomationDtos.StudentIdSeriesDto;
import com.lordsai.lsi.automation.dto.AutomationDtos.StudentProfile;
import com.lordsai.lsi.automation.dto.AutomationDtos.StudentRow;
import com.lordsai.lsi.automation.dto.AutomationDtos.UpdateStudentIdSeriesRequest;
import com.lordsai.lsi.automation.service.AcadDashboardService;
import com.lordsai.lsi.automation.service.AcadImportService;
import com.lordsai.lsi.automation.service.AcadMasterService;
import com.lordsai.lsi.automation.service.AcadSequenceService;
import com.lordsai.lsi.automation.service.AcadStudentService;
import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Automation Admin: dashboard, students, master data, import.
 * Access is restricted to AUTOMATION_ADMIN and ADMIN by the /api/automation/** rule in SecurityConfig.
 */
@RestController
@RequestMapping("/api/automation")
public class AutomationController {

    private final AcadDashboardService dashboard;
    private final AcadStudentService studentService;
    private final AcadMasterService master;
    private final AcadImportService importService;
    private final AcadSequenceService sequenceService;
    private final UserService userService;

    public AutomationController(AcadDashboardService dashboard, AcadStudentService studentService, AcadMasterService master,
                                AcadImportService importService, AcadSequenceService sequenceService, UserService userService) {
        this.dashboard = dashboard;
        this.studentService = studentService;
        this.master = master;
        this.importService = importService;
        this.sequenceService = sequenceService;
        this.userService = userService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardStats> dashboard() {
        return ApiResponse.ok(dashboard.stats());
    }

    @GetMapping("/lookups")
    public ApiResponse<Lookups> lookups() {
        return ApiResponse.ok(master.lookups());
    }

    @GetMapping("/search")
    public ApiResponse<List<SearchHit>> search(@RequestParam("q") String q) {
        return ApiResponse.ok(studentService.quickSearch(q));
    }

    // ---- students ---------------------------------------------------------------------------

    @GetMapping("/students")
    public ApiResponse<Page<StudentRow>> students(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) Long batchId,
                                                  @RequestParam(required = false) Long courseId,
                                                  @RequestParam(required = false, defaultValue = "ACTIVE") String status,
                                                  @RequestParam(required = false) Integer year,
                                                  @RequestParam(required = false) String paymentStatus,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.ok(studentService.search(q, batchId, courseId, "ALL".equalsIgnoreCase(status) ? null : status, year, paymentStatus, page, size));
    }

    @GetMapping("/students/{id}")
    public ApiResponse<StudentRow> student(@PathVariable Long id) {
        return ApiResponse.ok(studentService.get(id));
    }

    @GetMapping("/students/{id}/profile")
    public ApiResponse<StudentProfile> profile(@PathVariable Long id) {
        return ApiResponse.ok(studentService.profile(id));
    }

    @PostMapping("/students")
    public ApiResponse<StudentRow> createStudent(@Valid @RequestBody SaveStudentRequest body, HttpServletRequest req) {
        StudentRow row = studentService.create(body, actor(), RequestUtil.clientIp(req));
        return ApiResponse.ok("Student " + row.studentId() + " created.", row);
    }

    @PutMapping("/students/{id}")
    public ApiResponse<StudentRow> updateStudent(@PathVariable Long id, @Valid @RequestBody SaveStudentRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Student updated.", studentService.update(id, body, actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/students/{id}/archive")
    public ApiResponse<StudentRow> archive(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean archived, HttpServletRequest req) {
        return ApiResponse.ok(archived ? "Student archived." : "Student restored.",
                studentService.setStatus(id, archived, actor(), RequestUtil.clientIp(req)));
    }

    // ---- master data ------------------------------------------------------------------------

    @GetMapping("/batches")
    public ApiResponse<List<BatchDto>> batches() {
        return ApiResponse.ok(master.listBatches());
    }

    @PostMapping("/batches")
    public ApiResponse<BatchDto> createBatch(@Valid @RequestBody SaveBatchRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Batch created.", master.saveBatch(null, body, actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/batches/{id}")
    public ApiResponse<BatchDto> updateBatch(@PathVariable Long id, @Valid @RequestBody SaveBatchRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Batch updated.", master.saveBatch(id, body, actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/batches/{id}/active")
    public ApiResponse<BatchDto> batchActive(@PathVariable Long id, @RequestParam boolean active, HttpServletRequest req) {
        return ApiResponse.ok(active ? "Batch activated." : "Batch deactivated.", master.setBatchActive(id, active, actor(), RequestUtil.clientIp(req)));
    }

    @GetMapping("/courses")
    public ApiResponse<List<CourseDto>> courses() {
        return ApiResponse.ok(master.listCourses());
    }

    @PostMapping("/courses")
    public ApiResponse<CourseDto> createCourse(@Valid @RequestBody SaveCourseRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Course created.", master.saveCourse(null, body, actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/courses/{id}")
    public ApiResponse<CourseDto> updateCourse(@PathVariable Long id, @Valid @RequestBody SaveCourseRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Course updated.", master.saveCourse(id, body, actor(), RequestUtil.clientIp(req)));
    }

    @GetMapping("/master/{category}")
    public ApiResponse<List<MasterItemDto>> masterItems(@PathVariable String category) {
        return ApiResponse.ok(master.items(category.toUpperCase()));
    }

    @PostMapping("/master")
    public ApiResponse<MasterItemDto> createMaster(@Valid @RequestBody SaveMasterItemRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Value added.", master.saveItem(null, body, actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/master/{id}")
    public ApiResponse<MasterItemDto> updateMaster(@PathVariable Long id, @Valid @RequestBody SaveMasterItemRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Value updated.", master.saveItem(id, body, actor(), RequestUtil.clientIp(req)));
    }

    // ---- import -----------------------------------------------------------------------------

    @PostMapping("/import")
    public ApiResponse<ImportResult> importWorkbook(@RequestBody ImportPayload payload, HttpServletRequest req) {
        ImportResult r = importService.run(payload, actor(), RequestUtil.clientIp(req));
        return ApiResponse.ok("Import finished: " + r.studentsCreated() + " students, " + r.paymentsCreated() + " payments, "
                + r.sessionsCreated() + " sessions, " + r.recordsCreated() + " attendance marks added.", r);
    }

    @DeleteMapping("/students/{id}")
    public ApiResponse<StudentRow> deleteAlias(@PathVariable Long id, HttpServletRequest req) {
        // Deleting is never destructive here: it archives (the ledger keeps every payment and receipt).
        return ApiResponse.ok("Student archived.", studentService.setStatus(id, true, actor(), RequestUtil.clientIp(req)));
    }

    // ---- settings: Student ID series ---------------------------------------------------------
    // Admin-only like every /api/automation/** endpoint (SecurityConfig). Only affects future
    // Student ID generation; existing Student IDs are never changed, and the generator's own
    // safety net (AcadSequenceService) still guarantees no ID already in use can be reissued.

    @GetMapping("/settings/student-id-sequence")
    public ApiResponse<StudentIdSeriesDto> studentIdSequence() {
        var info = sequenceService.studentIdSeriesInfo();
        return ApiResponse.ok(new StudentIdSeriesDto(info.year(), info.nextNumber(), info.nextStudentId(),
                info.highestExistingNumber(), info.highestExistingStudentId()));
    }

    @PutMapping("/settings/student-id-sequence")
    public ApiResponse<StudentIdSeriesDto> updateStudentIdSequence(@Valid @RequestBody UpdateStudentIdSeriesRequest body,
                                                                   HttpServletRequest req) {
        var info = sequenceService.setNextStudentNumber(body.nextNumber(), actor(), RequestUtil.clientIp(req));
        return ApiResponse.ok("Student ID series updated. The next student created will be " + info.nextStudentId() + ".",
                new StudentIdSeriesDto(info.year(), info.nextNumber(), info.nextStudentId(),
                        info.highestExistingNumber(), info.highestExistingStudentId()));
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
