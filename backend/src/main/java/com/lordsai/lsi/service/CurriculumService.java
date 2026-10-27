package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.course.CourseDtos.LessonRequest;
import com.lordsai.lsi.dto.course.CourseDtos.LessonResponse;
import com.lordsai.lsi.dto.course.CourseDtos.ModuleRequest;
import com.lordsai.lsi.dto.course.CourseDtos.ModuleResponse;
import com.lordsai.lsi.entity.Course;
import com.lordsai.lsi.entity.CourseModule;
import com.lordsai.lsi.entity.Lesson;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.CourseModuleRepository;
import com.lordsai.lsi.repository.LessonProgressRepository;
import com.lordsai.lsi.repository.LessonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Modules and lessons inside a course, including video/material uploads. */
@Service
public class CurriculumService {

    private final CourseService courseService;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final FileStorageService storage;
    private final AuditService auditService;

    public CurriculumService(CourseService courseService,
                             CourseModuleRepository moduleRepository,
                             LessonRepository lessonRepository,
                             LessonProgressRepository progressRepository,
                             FileStorageService storage,
                             AuditService auditService) {
        this.courseService = courseService;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
        this.storage = storage;
        this.auditService = auditService;
    }

    // ---- Read -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ModuleResponse> curriculum(Long courseId) {
        courseService.requireCourse(courseId);
        return moduleRepository.findByCourseIdOrderByDisplayOrderAsc(courseId).stream()
                .map(m -> CourseService.toModuleResponse(m, lessonRepository.findByModuleIdOrderByDisplayOrderAsc(m.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseModule requireModule(Long id) {
        return moduleRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Module", id));
    }

    @Transactional(readOnly = true)
    public Lesson requireLesson(Long id) {
        return lessonRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Lesson", id));
    }

    // ---- Modules --------------------------------------------------------------------------

    @Transactional
    public ModuleResponse createModule(Long courseId, ModuleRequest req, User actor, String ip) {
        Course course = courseService.requireCourse(courseId);
        CourseModule module = new CourseModule();
        module.setCourse(course);
        module.setModuleName(req.moduleName().trim());
        module.setDescription(req.description());
        module.setDisplayOrder(moduleRepository.countByCourseId(courseId) + 1);
        module = moduleRepository.save(module);
        auditService.record(actor, "MODULE_CREATED", "CourseModule", module.getId(),
                "Added module '" + module.getModuleName() + "' to course " + course.getCourseCode(), ip);
        return CourseService.toModuleResponse(module, List.of());
    }

    @Transactional
    public ModuleResponse updateModule(Long moduleId, ModuleRequest req, User actor, String ip) {
        CourseModule module = requireModule(moduleId);
        String old = module.getModuleName();
        module.setModuleName(req.moduleName().trim());
        module.setDescription(req.description());
        moduleRepository.save(module);
        String desc = old.equals(module.getModuleName())
                ? "Updated module '" + old + "'"
                : "Renamed module '" + old + "' -> '" + module.getModuleName() + "'";
        auditService.record(actor, "MODULE_UPDATED", "CourseModule", moduleId, desc, ip);
        return CourseService.toModuleResponse(module, lessonRepository.findByModuleIdOrderByDisplayOrderAsc(moduleId));
    }

    @Transactional
    public ModuleResponse setModuleActive(Long moduleId, boolean active, User actor, String ip) {
        CourseModule module = requireModule(moduleId);
        module.setActive(active);
        moduleRepository.save(module);
        auditService.record(actor, active ? "MODULE_ACTIVATED" : "MODULE_DEACTIVATED", "CourseModule", moduleId,
                module.getModuleName(), ip);
        return CourseService.toModuleResponse(module, lessonRepository.findByModuleIdOrderByDisplayOrderAsc(moduleId));
    }

    @Transactional
    public void reorderModules(Long courseId, List<Long> orderedIds, User actor, String ip) {
        List<CourseModule> modules = moduleRepository.findByCourseIdOrderByDisplayOrderAsc(courseId);
        for (CourseModule m : modules) {
            int idx = orderedIds.indexOf(m.getId());
            if (idx >= 0) {
                m.setDisplayOrder(idx + 1);
            }
        }
        moduleRepository.saveAll(modules);
        auditService.record(actor, "MODULES_REORDERED", "Course", courseId, "Modules reordered", ip);
    }

    @Transactional
    public void deleteModule(Long moduleId, User actor, String ip) {
        CourseModule module = requireModule(moduleId);
        List<Lesson> lessons = lessonRepository.findByModuleIdOrderByDisplayOrderAsc(moduleId);
        for (Lesson lesson : lessons) {
            deleteLessonInternal(lesson);
        }
        moduleRepository.delete(module);
        auditService.record(actor, "MODULE_DELETED", "CourseModule", moduleId,
                "Deleted module '" + module.getModuleName() + "' and " + lessons.size() + " lesson(s)", ip);
    }

    // ---- Lessons --------------------------------------------------------------------------

    @Transactional
    public LessonResponse createLesson(Long moduleId, LessonRequest req, User actor, String ip) {
        CourseModule module = requireModule(moduleId);
        Lesson lesson = new Lesson();
        lesson.setModule(module);
        lesson.setLessonTitle(req.lessonTitle().trim());
        lesson.setDescription(req.description());
        lesson.setVideoDurationSeconds(req.videoDurationSeconds());
        lesson.setDisplayOrder(lessonRepository.findByModuleIdOrderByDisplayOrderAsc(moduleId).size() + 1);
        lesson = lessonRepository.save(lesson);
        auditService.record(actor, "LESSON_CREATED", "Lesson", lesson.getId(),
                "Added lesson '" + lesson.getLessonTitle() + "' to module '" + module.getModuleName() + "'", ip);
        return CourseService.toLessonResponse(lesson);
    }

    @Transactional
    public LessonResponse updateLesson(Long lessonId, LessonRequest req, User actor, String ip) {
        Lesson lesson = requireLesson(lessonId);
        String old = lesson.getLessonTitle();
        lesson.setLessonTitle(req.lessonTitle().trim());
        lesson.setDescription(req.description());
        if (req.videoDurationSeconds() != null) {
            lesson.setVideoDurationSeconds(req.videoDurationSeconds());
        }
        lessonRepository.save(lesson);
        String desc = old.equals(lesson.getLessonTitle())
                ? "Updated lesson '" + old + "'"
                : "Renamed lesson '" + old + "' -> '" + lesson.getLessonTitle() + "'";
        auditService.record(actor, "LESSON_UPDATED", "Lesson", lessonId, desc, ip);
        return CourseService.toLessonResponse(lesson);
    }

    @Transactional
    public LessonResponse setLessonActive(Long lessonId, boolean active, User actor, String ip) {
        Lesson lesson = requireLesson(lessonId);
        lesson.setActive(active);
        lessonRepository.save(lesson);
        auditService.record(actor, active ? "LESSON_ACTIVATED" : "LESSON_DEACTIVATED", "Lesson", lessonId,
                lesson.getLessonTitle(), ip);
        return CourseService.toLessonResponse(lesson);
    }

    @Transactional
    public void reorderLessons(Long moduleId, List<Long> orderedIds, User actor, String ip) {
        List<Lesson> lessons = lessonRepository.findByModuleIdOrderByDisplayOrderAsc(moduleId);
        for (Lesson l : lessons) {
            int idx = orderedIds.indexOf(l.getId());
            if (idx >= 0) {
                l.setDisplayOrder(idx + 1);
            }
        }
        lessonRepository.saveAll(lessons);
        auditService.record(actor, "LESSONS_REORDERED", "CourseModule", moduleId, "Lessons reordered", ip);
    }

    @Transactional
    public LessonResponse uploadVideo(Long lessonId, MultipartFile file, User actor, String ip) {
        Lesson lesson = requireLesson(lessonId);
        String previous = lesson.getVideoPath();
        lesson.setVideoPath(storage.store(file, FileStorageService.Kind.VIDEO));
        lessonRepository.save(lesson);
        storage.deleteQuietly(previous);
        auditService.record(actor, "LESSON_VIDEO_UPLOADED", "Lesson", lessonId,
                "Video uploaded for '" + lesson.getLessonTitle() + "' (" + file.getSize() / (1024 * 1024) + " MB)", ip);
        return CourseService.toLessonResponse(lesson);
    }

    @Transactional
    public LessonResponse uploadMaterial(Long lessonId, MultipartFile file, User actor, String ip) {
        Lesson lesson = requireLesson(lessonId);
        String previous = lesson.getMaterialPath();
        lesson.setMaterialPath(storage.store(file, FileStorageService.Kind.DOCUMENT));
        lesson.setMaterialOriginalName(safeName(file.getOriginalFilename()));
        lessonRepository.save(lesson);
        storage.deleteQuietly(previous);
        auditService.record(actor, "LESSON_MATERIAL_UPLOADED", "Lesson", lessonId,
                "Material uploaded for '" + lesson.getLessonTitle() + "': " + lesson.getMaterialOriginalName(), ip);
        return CourseService.toLessonResponse(lesson);
    }

    @Transactional
    public LessonResponse deleteVideo(Long lessonId, User actor, String ip) {
        Lesson lesson = requireLesson(lessonId);
        storage.deleteQuietly(lesson.getVideoPath());
        lesson.setVideoPath(null);
        lessonRepository.save(lesson);
        auditService.record(actor, "LESSON_VIDEO_DELETED", "Lesson", lessonId, "Video removed from '" + lesson.getLessonTitle() + "'", ip);
        return CourseService.toLessonResponse(lesson);
    }

    @Transactional
    public LessonResponse deleteMaterial(Long lessonId, User actor, String ip) {
        Lesson lesson = requireLesson(lessonId);
        storage.deleteQuietly(lesson.getMaterialPath());
        lesson.setMaterialPath(null);
        lesson.setMaterialOriginalName(null);
        lessonRepository.save(lesson);
        auditService.record(actor, "LESSON_MATERIAL_DELETED", "Lesson", lessonId, "Material removed from '" + lesson.getLessonTitle() + "'", ip);
        return CourseService.toLessonResponse(lesson);
    }

    @Transactional
    public void deleteLesson(Long lessonId, User actor, String ip) {
        Lesson lesson = requireLesson(lessonId);
        deleteLessonInternal(lesson);
        auditService.record(actor, "LESSON_DELETED", "Lesson", lessonId, "Deleted lesson '" + lesson.getLessonTitle() + "'", ip);
    }

    private void deleteLessonInternal(Lesson lesson) {
        progressRepository.deleteByLessonId(lesson.getId());
        storage.deleteQuietly(lesson.getVideoPath());
        storage.deleteQuietly(lesson.getMaterialPath());
        lessonRepository.delete(lesson);
    }

    private static String safeName(String name) {
        if (name == null) {
            return "material.pdf";
        }
        String base = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }

}
