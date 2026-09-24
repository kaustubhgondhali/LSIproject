package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.student.StudentDtos.CourseContent;
import com.lordsai.lsi.dto.student.StudentDtos.LessonDetail;
import com.lordsai.lsi.dto.student.StudentDtos.LessonSummary;
import com.lordsai.lsi.dto.student.StudentDtos.ModuleContent;
import com.lordsai.lsi.dto.student.StudentDtos.MyCourse;
import com.lordsai.lsi.dto.student.StudentDtos.Overview;
import com.lordsai.lsi.dto.student.StudentDtos.ProgressResponse;
import com.lordsai.lsi.entity.Course;
import com.lordsai.lsi.entity.Enrollment;
import com.lordsai.lsi.entity.Lesson;
import com.lordsai.lsi.entity.LessonProgress;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.DoubtStatus;
import com.lordsai.lsi.entity.enums.EnrollmentStatus;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.CourseModuleRepository;
import com.lordsai.lsi.repository.DoubtRepository;
import com.lordsai.lsi.repository.EnrollmentRepository;
import com.lordsai.lsi.repository.LessonProgressRepository;
import com.lordsai.lsi.repository.LessonRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.repository.TradeJournalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Everything a logged-in student can see. Every method that touches course content first
 * calls {@link EnrollmentService#requireAccess}, so content is only ever served for a
 * valid enrollment — this is the authorization the frontend cannot bypass.
 */
@Service
public class StudentLearningService {

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentService enrollmentService;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TradeJournalRepository tradeJournalRepository;
    private final DoubtRepository doubtRepository;
    private final UserService userService;

    public StudentLearningService(EnrollmentRepository enrollmentRepository,
                                  EnrollmentService enrollmentService,
                                  CourseModuleRepository moduleRepository,
                                  LessonRepository lessonRepository,
                                  LessonProgressRepository progressRepository,
                                  StudentProfileRepository studentProfileRepository,
                                  TradeJournalRepository tradeJournalRepository,
                                  DoubtRepository doubtRepository,
                                  UserService userService) {
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentService = enrollmentService;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.tradeJournalRepository = tradeJournalRepository;
        this.doubtRepository = doubtRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public Overview overview(Long studentUserId) {
        User user = userService.requireUser(studentUserId);
        StudentProfile profile = studentProfileRepository.findByUserId(studentUserId).orElse(null);
        List<Enrollment> enrollments = enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(studentUserId)
                .stream().filter(Enrollment::grantsAccess).toList();

        long total = 0;
        long done = 0;
        for (Enrollment e : enrollments) {
            total += lessonRepository.countActiveByCourseId(e.getCourse().getId());
            done += progressRepository.countCompletedByStudentAndCourse(studentUserId, e.getCourse().getId());
        }
        long openDoubts = doubtRepository.findByStudentIdOrderByCreatedAtDesc(studentUserId,
                org.springframework.data.domain.Pageable.unpaged()).stream()
                .filter(d -> d.getStatus() != DoubtStatus.RESOLVED).count();

        return new Overview(user.getId(),
                profile == null ? null : profile.getStudentId(),
                user.getFullName(), user.getEmail(), user.getMobile(),
                profile == null ? null : profile.getBatch(),
                profile == null ? null : profile.getLocation(),
                profile == null ? null : profile.getRegistrationDate(),
                user.getLastLoginAt(), enrollments.size(), total, done, percent(done, total),
                tradeJournalRepository.countByStudentId(studentUserId), openDoubts);
    }

    @Transactional(readOnly = true)
    public List<MyCourse> myCourses(Long studentUserId) {
        return enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(studentUserId).stream()
                .map(e -> toMyCourse(studentUserId, e)).toList();
    }

    @Transactional(readOnly = true)
    public CourseContent courseContent(Long studentUserId, Long courseId) {
        Enrollment enrollment = enrollmentService.requireAccess(studentUserId, courseId);
        Course course = enrollment.getCourse();
        Map<Long, LessonProgress> progress = progressMap(studentUserId, courseId);

        List<ModuleContent> modules = moduleRepository.findByCourseIdAndActiveTrueOrderByDisplayOrderAsc(courseId)
                .stream().map(m -> {
                    List<Lesson> lessons = lessonRepository.findByModuleIdAndActiveTrueOrderByDisplayOrderAsc(m.getId());
                    List<LessonSummary> summaries = lessons.stream().map(l -> toSummary(l, progress.get(l.getId()))).toList();
                    long completed = summaries.stream().filter(LessonSummary::completed).count();
                    return new ModuleContent(m.getId(), m.getModuleName(), m.getDescription(), m.getDisplayOrder(),
                            lessons.size(), completed, summaries);
                }).toList();

        long total = lessonRepository.countActiveByCourseId(courseId);
        long done = progressRepository.countCompletedByStudentAndCourse(studentUserId, courseId);
        return new CourseContent(course.getId(), course.getCourseCode(), course.getCourseName(), course.getDescription(),
                percent(done, total), total, done, modules);
    }

    @Transactional(readOnly = true)
    public LessonDetail lessonDetail(Long studentUserId, Long lessonId) {
        Lesson lesson = requireAccessibleLesson(studentUserId, lessonId);
        Long courseId = lesson.getModule().getCourse().getId();
        List<Lesson> ordered = lessonRepository.findActiveByCourseId(courseId);
        int idx = indexOf(ordered, lessonId);
        Lesson prev = idx > 0 ? ordered.get(idx - 1) : null;
        Lesson next = idx >= 0 && idx < ordered.size() - 1 ? ordered.get(idx + 1) : null;
        Optional<LessonProgress> p = progressRepository.findByStudentIdAndLessonId(studentUserId, lessonId);

        return new LessonDetail(lesson.getId(), courseId, lesson.getModule().getId(), lesson.getModule().getModuleName(),
                lesson.getLessonTitle(), lesson.getDescription(), lesson.getVideoPath() != null,
                lesson.getVideoDurationSeconds(), lesson.getMaterialPath() != null, lesson.getMaterialOriginalName(),
                p.map(LessonProgress::isCompleted).orElse(false), p.map(LessonProgress::getWatchedPercentage).orElse(0),
                p.map(LessonProgress::getLastWatchedAt).orElse(null),
                prev == null ? null : prev.getId(), prev == null ? null : prev.getLessonTitle(),
                next == null ? null : next.getId(), next == null ? null : next.getLessonTitle());
    }

    /** The gate in front of every video/material byte served. */
    @Transactional(readOnly = true)
    public Lesson requireAccessibleLesson(Long studentUserId, Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .filter(Lesson::isActive)
                .filter(l -> l.getModule().isActive())
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));
        enrollmentService.requireAccess(studentUserId, lesson.getModule().getCourse().getId());
        return lesson;
    }

    @Transactional
    public ProgressResponse updateProgress(Long studentUserId, Long lessonId, Integer watchedPercentage, Boolean completed) {
        Lesson lesson = requireAccessibleLesson(studentUserId, lessonId);
        User student = userService.requireUser(studentUserId);
        Long courseId = lesson.getModule().getCourse().getId();

        LessonProgress p = progressRepository.findByStudentIdAndLessonId(studentUserId, lessonId).orElseGet(() -> {
            LessonProgress np = new LessonProgress();
            np.setStudent(student);
            np.setLesson(lesson);
            return np;
        });
        Instant now = Instant.now();
        if (watchedPercentage != null) {
            p.setWatchedPercentage(Math.max(p.getWatchedPercentage(), Math.min(100, Math.max(0, watchedPercentage))));
        }
        if (Boolean.TRUE.equals(completed) || p.getWatchedPercentage() >= 90) {
            if (!p.isCompleted()) {
                p.setCompleted(true);
                p.setCompletedAt(now);
            }
        } else if (Boolean.FALSE.equals(completed)) {
            p.setCompleted(false);
            p.setCompletedAt(null);
        }
        p.setLastWatchedAt(now);
        progressRepository.save(p);

        long total = lessonRepository.countActiveByCourseId(courseId);
        long done = progressRepository.countCompletedByStudentAndCourse(studentUserId, courseId);
        if (total > 0 && done >= total) {
            enrollmentRepository.findByStudentIdAndCourseId(studentUserId, courseId).ifPresent(e -> {
                if (e.getStatus() == EnrollmentStatus.ACTIVE) {
                    e.setStatus(EnrollmentStatus.COMPLETED);
                    e.setCompletedAt(now);
                    enrollmentRepository.save(e);
                }
            });
        }
        return new ProgressResponse(lessonId, p.isCompleted(), p.getWatchedPercentage(), percent(done, total), done, total);
    }

    // ---- helpers --------------------------------------------------------------------------

    private MyCourse toMyCourse(Long studentUserId, Enrollment e) {
        Course c = e.getCourse();
        long total = lessonRepository.countActiveByCourseId(c.getId());
        long done = progressRepository.countCompletedByStudentAndCourse(studentUserId, c.getId());
        Map<Long, LessonProgress> progress = progressMap(studentUserId, c.getId());
        Lesson next = lessonRepository.findActiveByCourseId(c.getId()).stream()
                .filter(l -> progress.get(l.getId()) == null || !progress.get(l.getId()).isCompleted())
                .findFirst().orElse(null);
        return new MyCourse(e.getId(), c.getId(), c.getCourseCode(), c.getCourseName(), c.getShortDescription(),
                c.getThumbnailPath(), c.getDuration(), e.getStatus().name(), e.getEnrolledAt(), e.getExpiryDate(),
                moduleRepository.findByCourseIdAndActiveTrueOrderByDisplayOrderAsc(c.getId()).size(),
                total, done, percent(done, total),
                next == null ? null : next.getId(), next == null ? null : next.getLessonTitle());
    }

    private Map<Long, LessonProgress> progressMap(Long studentUserId, Long courseId) {
        return progressRepository.findByStudentAndCourse(studentUserId, courseId).stream()
                .collect(Collectors.toMap(p -> p.getLesson().getId(), Function.identity(), (a, b) -> a));
    }

    private static LessonSummary toSummary(Lesson l, LessonProgress p) {
        return new LessonSummary(l.getId(), l.getLessonTitle(), l.getDisplayOrder(), l.getVideoPath() != null,
                l.getVideoDurationSeconds(), l.getMaterialPath() != null, l.getMaterialOriginalName(),
                p != null && p.isCompleted(), p == null ? 0 : p.getWatchedPercentage());
    }

    private static int indexOf(List<Lesson> lessons, Long id) {
        for (int i = 0; i < lessons.size(); i++) {
            if (lessons.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    static int percent(long done, long total) {
        return total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
    }
}
