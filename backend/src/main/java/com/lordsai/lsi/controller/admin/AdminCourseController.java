package com.lordsai.lsi.controller.admin;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.course.CourseDtos.ActiveRequest;
import com.lordsai.lsi.dto.course.CourseDtos.AdminCourse;
import com.lordsai.lsi.dto.course.CourseDtos.CoursePriceRequest;
import com.lordsai.lsi.dto.course.CourseDtos.CourseRenameRequest;
import com.lordsai.lsi.dto.course.CourseDtos.CourseRequest;
import com.lordsai.lsi.dto.course.CourseDtos.LessonRequest;
import com.lordsai.lsi.dto.course.CourseDtos.LessonResponse;
import com.lordsai.lsi.dto.course.CourseDtos.ModuleRequest;
import com.lordsai.lsi.dto.course.CourseDtos.ModuleResponse;
import com.lordsai.lsi.dto.course.CourseDtos.ReorderRequest;
import com.lordsai.lsi.dto.course.CourseDtos.StatusRequest;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.CourseService;
import com.lordsai.lsi.service.CurriculumService;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminCourseController {

    private final CourseService courseService;
    private final CurriculumService curriculumService;
    private final UserService userService;

    public AdminCourseController(CourseService courseService,
                                 CurriculumService curriculumService,
                                 UserService userService) {
        this.courseService = courseService;
        this.curriculumService = curriculumService;
        this.userService = userService;
    }

    // ---- Courses --------------------------------------------------------------------------

    @GetMapping("/courses")
    public ApiResponse<List<AdminCourse>> listCourses() {
        return ApiResponse.ok(courseService.listAdmin());
    }

    @GetMapping("/courses/{id}")
    public ApiResponse<AdminCourse> getCourse(@PathVariable Long id) {
        return ApiResponse.ok(courseService.getAdmin(id));
    }

    @PostMapping("/courses")
    public ApiResponse<AdminCourse> createCourse(@Valid @RequestBody CourseRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Course added.", courseService.create(body, actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/courses/{id}")
    public ApiResponse<AdminCourse> updateCourse(@PathVariable Long id, @Valid @RequestBody CourseRequest body,
                                                 HttpServletRequest req) {
        return ApiResponse.ok("Course updated.", courseService.update(id, body, actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/courses/{id}/rename")
    public ApiResponse<AdminCourse> renameCourse(@PathVariable Long id, @Valid @RequestBody CourseRenameRequest body,
                                                 HttpServletRequest req) {
        return ApiResponse.ok("Course renamed.", courseService.rename(id, body.courseName(), actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/courses/{id}/price")
    public ApiResponse<AdminCourse> changePrice(@PathVariable Long id, @Valid @RequestBody CoursePriceRequest body,
                                                HttpServletRequest req) {
        return ApiResponse.ok("Price updated.",
                courseService.changePrice(id, body.price(), body.discountedPrice(), actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/courses/{id}/status")
    public ApiResponse<AdminCourse> setStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest body,
                                              HttpServletRequest req) {
        return ApiResponse.ok("Status updated.", courseService.setStatus(id, body.status(), actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/courses/reorder")
    public ApiResponse<Void> reorderCourses(@Valid @RequestBody ReorderRequest body, HttpServletRequest req) {
        courseService.reorder(body.orderedIds(), actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Order saved.");
    }

    @PostMapping("/courses/{id}/thumbnail")
    public ApiResponse<AdminCourse> uploadThumbnail(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                                    HttpServletRequest req) {
        return ApiResponse.ok("Thumbnail uploaded.", courseService.uploadThumbnail(id, file, actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping("/courses/{id}")
    public ApiResponse<Void> deleteCourse(@PathVariable Long id, HttpServletRequest req) {
        courseService.delete(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Course deleted.");
    }

    // ---- Modules --------------------------------------------------------------------------

    @GetMapping("/courses/{courseId}/modules")
    public ApiResponse<List<ModuleResponse>> curriculum(@PathVariable Long courseId) {
        return ApiResponse.ok(curriculumService.curriculum(courseId));
    }

    @PostMapping("/courses/{courseId}/modules")
    public ApiResponse<ModuleResponse> createModule(@PathVariable Long courseId, @Valid @RequestBody ModuleRequest body,
                                                    HttpServletRequest req) {
        return ApiResponse.ok("Module added.", curriculumService.createModule(courseId, body, actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/courses/{courseId}/modules/reorder")
    public ApiResponse<Void> reorderModules(@PathVariable Long courseId, @Valid @RequestBody ReorderRequest body,
                                            HttpServletRequest req) {
        curriculumService.reorderModules(courseId, body.orderedIds(), actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Order saved.");
    }

    @PutMapping("/modules/{id}")
    public ApiResponse<ModuleResponse> updateModule(@PathVariable Long id, @Valid @RequestBody ModuleRequest body,
                                                    HttpServletRequest req) {
        return ApiResponse.ok("Module updated.", curriculumService.updateModule(id, body, actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/modules/{id}/active")
    public ApiResponse<ModuleResponse> setModuleActive(@PathVariable Long id, @RequestBody ActiveRequest body,
                                                       HttpServletRequest req) {
        return ApiResponse.ok(curriculumService.setModuleActive(id, body.active(), actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping("/modules/{id}")
    public ApiResponse<Void> deleteModule(@PathVariable Long id, HttpServletRequest req) {
        curriculumService.deleteModule(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Module deleted.");
    }

    // ---- Lessons --------------------------------------------------------------------------

    @PostMapping("/modules/{moduleId}/lessons")
    public ApiResponse<LessonResponse> createLesson(@PathVariable Long moduleId, @Valid @RequestBody LessonRequest body,
                                                    HttpServletRequest req) {
        return ApiResponse.ok("Lesson added.", curriculumService.createLesson(moduleId, body, actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/modules/{moduleId}/lessons/reorder")
    public ApiResponse<Void> reorderLessons(@PathVariable Long moduleId, @Valid @RequestBody ReorderRequest body,
                                            HttpServletRequest req) {
        curriculumService.reorderLessons(moduleId, body.orderedIds(), actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Order saved.");
    }

    @PutMapping("/lessons/{id}")
    public ApiResponse<LessonResponse> updateLesson(@PathVariable Long id, @Valid @RequestBody LessonRequest body,
                                                    HttpServletRequest req) {
        return ApiResponse.ok("Lesson updated.", curriculumService.updateLesson(id, body, actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/lessons/{id}/active")
    public ApiResponse<LessonResponse> setLessonActive(@PathVariable Long id, @RequestBody ActiveRequest body,
                                                       HttpServletRequest req) {
        return ApiResponse.ok(curriculumService.setLessonActive(id, body.active(), actor(), RequestUtil.clientIp(req)));
    }

    @PostMapping("/lessons/{id}/video")
    public ApiResponse<LessonResponse> uploadVideo(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                                   HttpServletRequest req) {
        return ApiResponse.ok("Video uploaded.", curriculumService.uploadVideo(id, file, actor(), RequestUtil.clientIp(req)));
    }

    @PostMapping("/lessons/{id}/material")
    public ApiResponse<LessonResponse> uploadMaterial(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                                      HttpServletRequest req) {
        return ApiResponse.ok("Material uploaded.", curriculumService.uploadMaterial(id, file, actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping("/lessons/{id}/video")
    public ApiResponse<LessonResponse> deleteVideo(@PathVariable Long id, HttpServletRequest req) {
        return ApiResponse.ok("Video removed.", curriculumService.deleteVideo(id, actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping("/lessons/{id}/material")
    public ApiResponse<LessonResponse> deleteMaterial(@PathVariable Long id, HttpServletRequest req) {
        return ApiResponse.ok("Material removed.", curriculumService.deleteMaterial(id, actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping("/lessons/{id}")
    public ApiResponse<Void> deleteLesson(@PathVariable Long id, HttpServletRequest req) {
        curriculumService.deleteLesson(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Lesson deleted.");
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
