package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.course.CourseDtos.AdminCourse;
import com.lordsai.lsi.dto.course.CourseDtos.CourseRequest;
import com.lordsai.lsi.dto.course.CourseDtos.LessonResponse;
import com.lordsai.lsi.dto.course.CourseDtos.ModuleResponse;
import com.lordsai.lsi.dto.course.CourseDtos.PublicCourse;
import com.lordsai.lsi.dto.course.CourseDtos.PublicModule;
import com.lordsai.lsi.entity.Course;
import com.lordsai.lsi.entity.CourseModule;
import com.lordsai.lsi.entity.Lesson;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.CourseStatus;
import com.lordsai.lsi.entity.enums.EnrollmentStatus;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.CourseModuleRepository;
import com.lordsai.lsi.repository.CourseRepository;
import com.lordsai.lsi.repository.EnrollmentRepository;
import com.lordsai.lsi.repository.LessonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AuditService auditService;
    private final FileStorageService storage;

    public CourseService(CourseRepository courseRepository,
                         CourseModuleRepository moduleRepository,
                         LessonRepository lessonRepository,
                         EnrollmentRepository enrollmentRepository,
                         AuditService auditService,
                         FileStorageService storage) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.auditService = auditService;
        this.storage = storage;
    }

    // ---- Public ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PublicCourse> listPublic() {
        return courseRepository.findByStatusOrderByDisplayOrderAscCourseNameAsc(CourseStatus.ACTIVE)
                .stream().map(this::toPublic).toList();
    }

    @Transactional(readOnly = true)
    public PublicCourse getPublic(String courseCode) {
        Course course = courseRepository.findByCourseCodeIgnoreCase(courseCode)
                .filter(c -> c.getStatus() == CourseStatus.ACTIVE)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", courseCode));
        return toPublic(course);
    }

    @Transactional(readOnly = true)
    public Course requireCourse(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Course", id));
    }

    @Transactional(readOnly = true)
    public Course requirePurchasableCourse(Long id) {
        Course course = requireCourse(id);
        if (course.getStatus() != CourseStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This course is not open for enrollment right now.");
        }
        return course;
    }

    // ---- Admin ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AdminCourse> listAdmin() {
        return courseRepository.findAllByOrderByDisplayOrderAscCourseNameAsc().stream().map(this::toAdmin).toList();
    }

    @Transactional(readOnly = true)
    public AdminCourse getAdmin(Long id) {
        return toAdmin(requireCourse(id));
    }

    @Transactional
    public AdminCourse create(CourseRequest req, User actor, String ip) {
        if (courseRepository.existsByCourseCodeIgnoreCase(req.courseCode())) {
            throw new ApiException(HttpStatus.CONFLICT, "A course with this code already exists.");
        }
        validatePricing(req.price(), req.discountedPrice());

        Course course = new Course();
        apply(course, req);
        course.setStatus(CourseStatus.DRAFT);
        course = courseRepository.save(course);

        auditService.record(actor, "COURSE_CREATED", "Course", course.getId(),
                "Created course '" + course.getCourseName() + "' (" + course.getCourseCode() + ")", ip);
        return toAdmin(course);
    }

    @Transactional
    public AdminCourse update(Long id, CourseRequest req, User actor, String ip) {
        Course course = requireCourse(id);
        if (!course.getCourseCode().equalsIgnoreCase(req.courseCode())
                && courseRepository.existsByCourseCodeIgnoreCase(req.courseCode())) {
            throw new ApiException(HttpStatus.CONFLICT, "A course with this code already exists.");
        }
        validatePricing(req.price(), req.discountedPrice());

        BigDecimal oldPrice = course.effectivePrice();
        String oldName = course.getCourseName();
        apply(course, req);
        course = courseRepository.save(course);

        StringBuilder desc = new StringBuilder("Updated course '").append(course.getCourseName()).append("'");
        if (!oldName.equals(course.getCourseName())) {
            desc.append("; renamed from '").append(oldName).append("'");
        }
        if (oldPrice.compareTo(course.effectivePrice()) != 0) {
            desc.append("; price ").append(oldPrice).append(" -> ").append(course.effectivePrice());
        }
        auditService.record(actor, "COURSE_UPDATED", "Course", course.getId(), desc.toString(), ip);
        return toAdmin(course);
    }

    @Transactional
    public AdminCourse rename(Long id, String newName, User actor, String ip) {
        Course course = requireCourse(id);
        String old = course.getCourseName();
        course.setCourseName(newName.trim());
        courseRepository.save(course);
        auditService.record(actor, "COURSE_RENAMED", "Course", id, "'" + old + "' -> '" + newName.trim() + "'", ip);
        return toAdmin(course);
    }

    @Transactional
    public AdminCourse changePrice(Long id, BigDecimal price, BigDecimal discountedPrice, User actor, String ip) {
        validatePricing(price, discountedPrice);
        Course course = requireCourse(id);
        BigDecimal old = course.effectivePrice();
        course.setPrice(price);
        course.setDiscountedPrice(discountedPrice);
        courseRepository.save(course);
        auditService.record(actor, "COURSE_PRICE_CHANGED", "Course", id,
                "Effective price " + old + " -> " + course.effectivePrice(), ip);
        return toAdmin(course);
    }

    @Transactional
    public AdminCourse setStatus(Long id, CourseStatus status, User actor, String ip) {
        Course course = requireCourse(id);
        CourseStatus old = course.getStatus();
        course.setStatus(status);
        courseRepository.save(course);
        auditService.record(actor, "COURSE_STATUS_CHANGED", "Course", id, old + " -> " + status, ip);
        return toAdmin(course);
    }

    @Transactional
    public void reorder(List<Long> orderedIds, User actor, String ip) {
        List<Course> courses = courseRepository.findAllById(orderedIds);
        for (Course course : courses) {
            course.setDisplayOrder(orderedIds.indexOf(course.getId()));
        }
        courseRepository.saveAll(courses);
        auditService.record(actor, "COURSE_REORDERED", "Courses reordered", ip);
    }

    /** Stores an uploaded thumbnail (served publicly by /api/public/images/...) and replaces any earlier upload. */
    @Transactional
    public AdminCourse uploadThumbnail(Long id, MultipartFile file, User actor, String ip) {
        Course course = requireCourse(id);
        String previous = course.getThumbnailPath();
        course.setThumbnailPath(storage.store(file, FileStorageService.Kind.IMAGE));
        course = courseRepository.save(course);
        if (previous != null && previous.startsWith("images/")) {
            storage.deleteQuietly(previous);
        }
        auditService.record(actor, "COURSE_THUMBNAIL_UPLOADED", "Course", id,
                "Thumbnail uploaded for '" + course.getCourseName() + "'", ip);
        return toAdmin(course);
    }

    /** Deletion is only allowed when nothing references the course; otherwise deactivate instead. */
    @Transactional
    public void delete(Long id, User actor, String ip) {
        Course course = requireCourse(id);
        long enrollments = enrollmentRepository.findByCourseId(id, org.springframework.data.domain.Pageable.ofSize(1))
                .getTotalElements();
        if (enrollments > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This course has enrolled students and cannot be deleted. Deactivate it instead.");
        }
        List<CourseModule> modules = moduleRepository.findByCourseIdOrderByDisplayOrderAsc(id);
        for (CourseModule module : modules) {
            lessonRepository.deleteAll(lessonRepository.findByModuleIdOrderByDisplayOrderAsc(module.getId()));
        }
        moduleRepository.deleteAll(modules);
        courseRepository.delete(course);
        auditService.record(actor, "COURSE_DELETED", "Course", id,
                "Deleted course '" + course.getCourseName() + "' (" + course.getCourseCode() + ")", ip);
    }

    // ---- Mapping --------------------------------------------------------------------------

    private PublicCourse toPublic(Course c) {
        List<CourseModule> modules = moduleRepository.findByCourseIdAndActiveTrueOrderByDisplayOrderAsc(c.getId());
        List<PublicModule> publicModules = modules.stream().map(m -> new PublicModule(
                m.getId(), m.getModuleName(), m.getDescription(), m.getDisplayOrder(),
                lessonRepository.findByModuleIdAndActiveTrueOrderByDisplayOrderAsc(m.getId())
                        .stream().map(Lesson::getLessonTitle).toList())).toList();
        return new PublicCourse(c.getId(), c.getCourseCode(), c.getCourseName(), c.getShortDescription(),
                c.getDescription(), c.getPrice(), c.getDiscountedPrice(), c.effectivePrice(), c.getDuration(),
                c.getThumbnailPath(), modules.size(), lessonRepository.countActiveByCourseId(c.getId()), publicModules);
    }

    private AdminCourse toAdmin(Course c) {
        return new AdminCourse(c.getId(), c.getCourseCode(), c.getCourseName(), c.getShortDescription(),
                c.getDescription(), c.getPrice(), c.getDiscountedPrice(), c.effectivePrice(), c.getDuration(),
                c.getThumbnailPath(), c.getStatus(), c.getDisplayOrder(),
                moduleRepository.countByCourseId(c.getId()),
                lessonRepository.countActiveByCourseId(c.getId()),
                enrollmentRepository.countByCourseIdAndStatus(c.getId(), EnrollmentStatus.ACTIVE),
                c.getCreatedAt(), c.getUpdatedAt());
    }

    public static ModuleResponse toModuleResponse(CourseModule m, List<Lesson> lessons) {
        return new ModuleResponse(m.getId(), m.getCourse().getId(), m.getModuleName(), m.getDescription(),
                m.getDisplayOrder(), m.isActive(), lessons.stream().map(CourseService::toLessonResponse).toList());
    }

    public static LessonResponse toLessonResponse(Lesson l) {
        return new LessonResponse(l.getId(), l.getModule().getId(), l.getLessonTitle(), l.getDescription(),
                l.getDisplayOrder(), l.isActive(), l.getVideoPath() != null, l.getVideoDurationSeconds(),
                l.getMaterialPath() != null, l.getMaterialOriginalName());
    }

    private static void apply(Course course, CourseRequest req) {
        course.setCourseCode(req.courseCode().trim().toUpperCase());
        course.setCourseName(req.courseName().trim());
        course.setShortDescription(req.shortDescription());
        course.setDescription(req.description());
        course.setPrice(req.price());
        course.setDiscountedPrice(req.discountedPrice());
        course.setDuration(req.duration());
        String thumb = req.thumbnailPath() == null || req.thumbnailPath().isBlank() ? null : req.thumbnailPath().trim();
        // An uploaded thumbnail (images/...) is only replaced through uploadThumbnail; a blank
        // field in the edit form must not silently drop it.
        if (thumb != null || course.getThumbnailPath() == null || !course.getThumbnailPath().startsWith("images/")) {
            course.setThumbnailPath(thumb);
        }
        if (req.displayOrder() != null) {
            course.setDisplayOrder(req.displayOrder());
        }
    }

    private static void validatePricing(BigDecimal price, BigDecimal discounted) {
        if (discounted != null && discounted.compareTo(price) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Discounted price cannot be higher than the list price.");
        }
    }
}
