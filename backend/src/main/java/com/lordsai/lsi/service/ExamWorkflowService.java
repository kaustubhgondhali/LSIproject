package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.exam.ExamDtos.AnswerRequest;
import com.lordsai.lsi.dto.exam.ExamDtos.ApplicationCounters;
import com.lordsai.lsi.dto.exam.ExamDtos.ApplicationResponse;
import com.lordsai.lsi.dto.exam.ExamDtos.AttemptResult;
import com.lordsai.lsi.dto.exam.ExamDtos.AttemptSummary;
import com.lordsai.lsi.dto.exam.ExamDtos.ExamPaper;
import com.lordsai.lsi.dto.exam.ExamDtos.MyExamCourse;
import com.lordsai.lsi.dto.exam.ExamDtos.PaperQuestion;
import com.lordsai.lsi.dto.exam.ExamDtos.ResultRow;
import com.lordsai.lsi.dto.exam.ExamDtos.ScheduleRequest;
import com.lordsai.lsi.dto.exam.ExamDtos.ScheduleResponse;
import com.lordsai.lsi.entity.Certificate;
import com.lordsai.lsi.entity.Course;
import com.lordsai.lsi.entity.Enrollment;
import com.lordsai.lsi.entity.Exam;
import com.lordsai.lsi.entity.ExamAnswer;
import com.lordsai.lsi.entity.ExamApplication;
import com.lordsai.lsi.entity.ExamAttempt;
import com.lordsai.lsi.entity.ExamQuestion;
import com.lordsai.lsi.entity.ExamSchedule;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.EnrollmentStatus;
import com.lordsai.lsi.entity.enums.ExamApplicationStatus;
import com.lordsai.lsi.entity.enums.ExamAttemptStatus;
import com.lordsai.lsi.entity.enums.ExamResult;
import com.lordsai.lsi.entity.enums.ExamScheduleStatus;
import com.lordsai.lsi.entity.enums.ExamStatus;
import com.lordsai.lsi.entity.enums.McqOption;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.CertificateRepository;
import com.lordsai.lsi.repository.EnrollmentRepository;
import com.lordsai.lsi.repository.ExamAnswerRepository;
import com.lordsai.lsi.repository.ExamApplicationRepository;
import com.lordsai.lsi.repository.ExamAttemptRepository;
import com.lordsai.lsi.repository.ExamQuestionRepository;
import com.lordsai.lsi.repository.ExamRepository;
import com.lordsai.lsi.repository.ExamScheduleRepository;
import com.lordsai.lsi.repository.LessonProgressRepository;
import com.lordsai.lsi.repository.LessonRepository;
import com.lordsai.lsi.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The exam workflow: course completion -> application -> admin review / schedule -> attempt ->
 * backend scoring -> certificate. Every rule lives here and is enforced against the database:
 * <ul>
 *   <li>Eligibility reuses the existing lesson-progress completion rule (every active lesson
 *       completed, or the enrollment already marked COMPLETED) — no second completion system.</li>
 *   <li>The exam window, attempt limit and ownership are checked on start AND on submit.</li>
 *   <li>Correct options never leave the server; the score is computed from exam_questions and the
 *       exam's configured passing marks, then stored immutably on the attempt.</li>
 * </ul>
 */
@Service
public class ExamWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(ExamWorkflowService.class);
    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
    /** A submission that arrives shortly after the deadline (network delay) is still accepted. */
    static final Duration SUBMIT_GRACE = Duration.ofSeconds(90);

    private final ExamRepository examRepository;
    private final ExamQuestionRepository questionRepository;
    private final ExamApplicationRepository applicationRepository;
    private final ExamScheduleRepository scheduleRepository;
    private final ExamAttemptRepository attemptRepository;
    private final ExamAnswerRepository answerRepository;
    private final CertificateRepository certificateRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final ExamService examService;
    private final CertificateService certificateService;
    private final ExamNotificationService notifications;
    private final UserService userService;
    private final AuditService auditService;

    public ExamWorkflowService(ExamRepository examRepository,
                               ExamQuestionRepository questionRepository,
                               ExamApplicationRepository applicationRepository,
                               ExamScheduleRepository scheduleRepository,
                               ExamAttemptRepository attemptRepository,
                               ExamAnswerRepository answerRepository,
                               CertificateRepository certificateRepository,
                               EnrollmentRepository enrollmentRepository,
                               LessonRepository lessonRepository,
                               LessonProgressRepository progressRepository,
                               StudentProfileRepository studentProfileRepository,
                               ExamService examService,
                               CertificateService certificateService,
                               ExamNotificationService notifications,
                               UserService userService,
                               AuditService auditService) {
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.applicationRepository = applicationRepository;
        this.scheduleRepository = scheduleRepository;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.certificateRepository = certificateRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.examService = examService;
        this.certificateService = certificateService;
        this.notifications = notifications;
        this.userService = userService;
        this.auditService = auditService;
    }

    // =========================================================================================
    // Course completion (the existing rule, reused)
    // =========================================================================================

    public record Completion(long total, long done, int percent, boolean completed, Instant completedAt) {
    }

    /**
     * The project's course-completion rule: every active lesson completed (lesson_progress), which
     * StudentLearningService also uses to mark the enrollment COMPLETED. An enrollment an admin has
     * marked COMPLETED counts as completed too.
     */
    @Transactional(readOnly = true)
    public Completion completion(Long studentUserId, Enrollment enrollment) {
        Long courseId = enrollment.getCourse().getId();
        long total = lessonRepository.countActiveByCourseId(courseId);
        long done = progressRepository.countCompletedByStudentAndCourse(studentUserId, courseId);
        boolean allLessons = total > 0 && done >= total;
        boolean completed = allLessons || enrollment.getStatus() == EnrollmentStatus.COMPLETED;
        Instant at = enrollment.getCompletedAt();
        if (at == null && completed) {
            at = progressRepository.findByStudentAndCourse(studentUserId, courseId).stream()
                    .map(p -> p.getCompletedAt()).filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null);
        }
        return new Completion(total, done, total == 0 ? 0 : (int) Math.round(done * 100.0 / total), completed, at);
    }

    // =========================================================================================
    // Student: My Exams
    // =========================================================================================

    @Transactional
    public List<MyExamCourse> myExams(Long studentUserId) {
        List<MyExamCourse> out = new ArrayList<>();
        for (Enrollment e : enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(studentUserId)) {
            if (!e.grantsAccess()) {
                continue;
            }
            Course course = e.getCourse();
            Completion completion = completion(studentUserId, e);
            ExamApplication latest = latestApplication(studentUserId, course.getId());
            if (latest != null) {
                expireDueAttempts(latest);
            }
            Optional<Certificate> certificate = certificateRepository
                    .findFirstByStudentIdAndCourseIdOrderByIssuedAtDesc(studentUserId, course.getId());
            boolean examAvailable = examRepository.countByCourseIdAndStatus(course.getId(), ExamStatus.ACTIVE) > 0;
            String why = eligibilityMessage(completion, latest, certificate.isPresent());
            out.add(new MyExamCourse(course.getId(), course.getCourseCode(), course.getCourseName(), e.getStatus().name(),
                    completion.total(), completion.done(), completion.percent(), completion.completed(),
                    why == null, why == null ? "You are eligible to apply for the final exam." : why, examAvailable,
                    latest == null ? null : toResponse(latest, true),
                    certificate.map(certificateService::toMine).orElse(null)));
        }
        return out;
    }

    /** Null when the student may apply now; otherwise the reason shown on the card. */
    private String eligibilityMessage(Completion completion, ExamApplication latest, boolean hasCertificate) {
        if (hasCertificate) {
            return "You have passed the exam for this course. Your certificate is available under Certificates.";
        }
        if (!completion.completed()) {
            return "Complete the course before applying for the exam.";
        }
        if (latest != null && latest.getStatus().isOpen()) {
            return switch (latest.getStatus()) {
                case PENDING -> "Your exam application is under review.";
                case APPROVED -> "Your exam application has been approved. The academy will schedule your exam.";
                default -> "Your exam has been scheduled.";
            };
        }
        if (latest != null && latest.getStatus() == ExamApplicationStatus.COMPLETED && latest.getResult() == ExamResult.PASSED) {
            return "You have passed the exam for this course.";
        }
        return null;
    }

    private ExamApplication latestApplication(Long studentUserId, Long courseId) {
        return applicationRepository.findByStudentIdAndCourseIdOrderByAppliedAtDesc(studentUserId, courseId).stream()
                .findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> myApplications(Long studentUserId) {
        return applicationRepository.findByStudentIdOrderByAppliedAtDesc(studentUserId).stream()
                .map(a -> toResponse(a, true)).toList();
    }

    @Transactional
    public ApplicationResponse myApplication(Long studentUserId, Long applicationId) {
        ExamApplication a = requireOwnedApplication(studentUserId, applicationId);
        expireDueAttempts(a);
        return toResponse(a, true);
    }

    /** CASE 1 / CASE 2: refuses students who have not completed the course or already hold an open application. */
    @Transactional
    public ApplicationResponse apply(Long studentUserId, Long courseId) {
        User student = userService.requireUser(studentUserId);
        Enrollment enrollment = enrollmentRepository.findByStudentIdAndCourseId(studentUserId, courseId)
                .filter(Enrollment::grantsAccess)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "You are not enrolled in this course."));
        Completion completion = completion(studentUserId, enrollment);
        if (!completion.completed()) {
            throw new ApiException(HttpStatus.CONFLICT, "Complete the course before applying for the exam. "
                    + completion.done() + " of " + completion.total() + " lessons completed.");
        }
        if (certificateRepository.findFirstByStudentIdAndCourseIdOrderByIssuedAtDesc(studentUserId, courseId).isPresent()
                || attemptRepository.existsByStudentIdAndApplicationCourseIdAndPassedTrue(studentUserId, courseId)) {
            throw new ApiException(HttpStatus.CONFLICT, "You have already passed the exam for this course.");
        }
        applicationRepository.findFirstByStudentIdAndCourseIdAndStatusInOrderByAppliedAtDesc(studentUserId, courseId,
                List.of(ExamApplicationStatus.PENDING, ExamApplicationStatus.APPROVED, ExamApplicationStatus.SCHEDULED))
                .ifPresent(a -> {
                    throw new ApiException(HttpStatus.CONFLICT, "You already have an exam application for this course ("
                            + a.getStatus().name().toLowerCase(Locale.ROOT) + ").");
                });

        ExamApplication a = new ExamApplication();
        a.setStudent(student);
        a.setCourse(enrollment.getCourse());
        a.setEnrollment(enrollment);
        a.setStatus(ExamApplicationStatus.PENDING);
        a.setAppliedAt(Instant.now());
        a.setCourseCompletedAt(completion.completedAt() == null ? Instant.now() : completion.completedAt());
        a = applicationRepository.save(a);

        String code = studentCode(studentUserId);
        auditService.record(student, "EXAM_APPLICATION_SUBMITTED", "ExamApplication", a.getId(),
                student.getEmail() + " applied for " + enrollment.getCourse().getCourseCode(), null);
        notifications.applicationReceived(student, code, enrollment.getCourse().getCourseName());
        return toResponse(a, true);
    }

    // =========================================================================================
    // Student: attempts
    // =========================================================================================

    /**
     * CASE 3 / 4 / 5: starts (or resumes) an attempt only when the application is scheduled, the
     * server clock is inside the window, the exam is active and attempts remain. The unique
     * (student, exam, attempt_number) constraint makes the limit hold under concurrent requests.
     */
    @Transactional
    public ExamPaper start(Long studentUserId, Long applicationId) {
        ExamApplication a = requireOwnedApplication(studentUserId, applicationId);
        expireDueAttempts(a);
        if (a.getStatus() == ExamApplicationStatus.REJECTED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This exam application was rejected.");
        }
        if (a.getStatus() != ExamApplicationStatus.SCHEDULED) {
            throw new ApiException(HttpStatus.CONFLICT, a.getStatus() == ExamApplicationStatus.COMPLETED
                    ? "This exam has already been completed." : "Your exam has not been scheduled yet.");
        }
        ExamSchedule schedule = scheduleRepository.findByApplicationId(a.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Your exam has not been scheduled yet."));
        Exam exam = schedule.getExam();
        Instant now = Instant.now();

        Optional<ExamAttempt> open = attemptRepository.findFirstByStudentIdAndExamIdAndStatus(studentUserId, exam.getId(),
                ExamAttemptStatus.IN_PROGRESS);
        if (open.isPresent()) {
            return paper(open.get(), now);
        }
        if (schedule.getStatus() != ExamScheduleStatus.SCHEDULED) {
            throw new ApiException(HttpStatus.CONFLICT, "This exam schedule is no longer active.");
        }
        if (now.isBefore(schedule.getStartsAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "The exam window has not opened yet. It opens on "
                    + DATE.format(schedule.getStartsAt().atZone(INDIA)) + " at " + TIME.format(schedule.getStartsAt().atZone(INDIA)) + " IST.");
        }
        if (!now.isBefore(schedule.getEndsAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "The exam window has closed.");
        }
        if (exam.getStatus() != ExamStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "This exam is not active. Please contact the academy.");
        }
        examService.assertReadyForStudents(exam);
        long used = attemptRepository.countByStudentIdAndExamId(studentUserId, exam.getId());
        if (used >= exam.getMaxAttempts()) {
            throw new ApiException(HttpStatus.CONFLICT, "Maximum exam attempts reached.");
        }
        if (attemptRepository.existsByStudentIdAndExamIdAndPassedTrue(studentUserId, exam.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "You have already passed this exam.");
        }

        List<ExamQuestion> questions = questionRepository.findByExamIdAndActiveTrueOrderByDisplayOrderAscIdAsc(exam.getId());
        ExamAttempt attempt = new ExamAttempt();
        attempt.setSchedule(schedule);
        attempt.setApplication(a);
        attempt.setExam(exam);
        attempt.setStudent(a.getStudent());
        attempt.setAttemptNumber((int) used + 1);
        attempt.setStatus(ExamAttemptStatus.IN_PROGRESS);
        attempt.setStartedAt(now);
        Instant deadline = schedule.getEndsAt();
        if (exam.getDurationMinutes() != null) {
            Instant byDuration = now.plus(Duration.ofMinutes(exam.getDurationMinutes()));
            if (byDuration.isBefore(deadline)) {
                deadline = byDuration;
            }
        }
        attempt.setDeadlineAt(deadline);
        attempt.setQuestionCount(questions.size());
        attempt.setTotalMarks(exam.getTotalMarks());
        attempt.setPassingMarks(exam.getPassingMarks());
        try {
            attempt = attemptRepository.saveAndFlush(attempt);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "An attempt is already in progress. Please refresh and continue it.");
        }
        auditService.record(a.getStudent(), "EXAM_ATTEMPT_STARTED", "ExamAttempt", attempt.getId(),
                exam.getTitle() + " attempt " + attempt.getAttemptNumber() + "/" + exam.getMaxAttempts(), null);
        return paper(attempt, now);
    }

    /** The question paper of the caller's own in-progress attempt (used to resume after a refresh). */
    @Transactional
    public ExamPaper paper(Long studentUserId, Long attemptId) {
        ExamAttempt attempt = requireOwnedAttempt(studentUserId, attemptId);
        Instant now = Instant.now();
        expireIfDue(attempt, now);
        if (attempt.isFinished()) {
            throw new ApiException(HttpStatus.CONFLICT, attempt.getStatus() == ExamAttemptStatus.EXPIRED
                    ? "The time for this attempt is over." : "This attempt has already been submitted.");
        }
        return paper(attempt, now);
    }

    /** Builds the student's paper: questions and options only — the correct option is never included. */
    private ExamPaper paper(ExamAttempt attempt, Instant now) {
        Exam exam = attempt.getExam();
        List<ExamQuestion> questions = questionRepository.findByExamIdAndActiveTrueOrderByDisplayOrderAscIdAsc(exam.getId());
        List<PaperQuestion> paper = new ArrayList<>();
        int n = 1;
        for (ExamQuestion q : questions) {
            paper.add(new PaperQuestion(q.getId(), n++, q.getQuestionText(), q.getOptionA(), q.getOptionB(), q.getOptionC(),
                    q.getOptionD(), q.getMarks()));
        }
        ExamSchedule schedule = attempt.getSchedule();
        long remaining = Math.max(0, Duration.between(now, attempt.getDeadlineAt()).getSeconds());
        return new ExamPaper(attempt.getId(), exam.getId(), exam.getTitle(), exam.getCourse().getCourseName(),
                exam.getDescription(), schedule.getInstructions(), attempt.getAttemptNumber(), exam.getMaxAttempts(),
                attempt.getTotalMarks(), attempt.getPassingMarks(), attempt.getStartedAt(), attempt.getDeadlineAt(),
                remaining, paper);
    }

    /**
     * Scores the attempt on the backend: each submitted option is compared with the stored correct
     * option, marks are summed, and PASS/FAIL comes from the passing marks snapshot taken at start.
     * The attempt is then immutable (CASE 12: nothing in the request can set a score or a result).
     */
    @Transactional
    public AttemptResult submit(Long studentUserId, Long attemptId, List<AnswerRequest> answers) {
        ExamAttempt attempt = requireOwnedAttempt(studentUserId, attemptId);
        if (attempt.isFinished()) {
            throw new ApiException(HttpStatus.CONFLICT, "This attempt has already been submitted.");
        }
        Instant now = Instant.now();
        if (now.isAfter(attempt.getDeadlineAt().plus(SUBMIT_GRACE))) {
            expire(attempt, now);
            throw new ApiException(HttpStatus.CONFLICT, "The exam window has closed. This attempt was not accepted.");
        }
        Exam exam = attempt.getExam();
        List<ExamQuestion> questions = questionRepository.findByExamIdAndActiveTrueOrderByDisplayOrderAscIdAsc(exam.getId());
        Map<Long, ExamQuestion> byId = new HashMap<>();
        questions.forEach(q -> byId.put(q.getId(), q));

        Map<Long, McqOption> selected = new HashMap<>();
        for (AnswerRequest ans : answers == null ? List.<AnswerRequest>of() : answers) {
            if (ans == null || ans.questionId() == null) {
                continue;
            }
            if (!byId.containsKey(ans.questionId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Answer refers to a question that is not part of this exam.");
            }
            selected.put(ans.questionId(), ans.selectedOption());
        }

        int score = 0;
        int correct = 0;
        for (ExamQuestion q : questions) {
            McqOption chosen = selected.get(q.getId());
            boolean isCorrect = chosen != null && chosen == q.getCorrectOption();
            ExamAnswer row = new ExamAnswer();
            row.setAttempt(attempt);
            row.setQuestion(q);
            row.setSelectedOption(chosen);
            row.setCorrect(isCorrect);
            row.setMarksAwarded(isCorrect ? q.getMarks() : 0);
            answerRepository.save(row);
            if (isCorrect) {
                correct++;
                score += q.getMarks();
            }
        }
        attempt.setStatus(ExamAttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(now);
        attempt.setQuestionCount(questions.size());
        attempt.setCorrectCount(correct);
        attempt.setScore(score);
        attempt.setPassed(score >= attempt.getPassingMarks());
        attemptRepository.save(attempt);
        auditService.record(attempt.getStudent(), attempt.isPassed() ? "EXAM_ATTEMPT_PASSED" : "EXAM_ATTEMPT_FAILED",
                "ExamAttempt", attempt.getId(), exam.getTitle() + " attempt " + attempt.getAttemptNumber() + ": "
                        + score + "/" + attempt.getTotalMarks() + " (pass " + attempt.getPassingMarks() + ")", null);

        Certificate certificate = finishAttempt(attempt);
        log.info("[EXAM] {} attempt {} of '{}' scored {}/{} -> {}", attempt.getStudent().getEmail(), attempt.getAttemptNumber(),
                exam.getTitle(), score, attempt.getTotalMarks(), attempt.isPassed() ? "PASS" : "FAIL");
        return toResult(attempt, certificate, null);
    }

    /** The result of one of the caller's own finished attempts. */
    @Transactional
    public AttemptResult result(Long studentUserId, Long attemptId) {
        ExamAttempt attempt = requireOwnedAttempt(studentUserId, attemptId);
        expireIfDue(attempt, Instant.now());
        if (!attempt.isFinished()) {
            throw new ApiException(HttpStatus.CONFLICT, "This attempt has not been submitted yet.");
        }
        return toResult(attempt, certificateRepository.findByAttemptId(attempt.getId()).orElse(null), null);
    }

    /**
     * After an attempt finishes: a pass completes the application and issues the certificate; a
     * fail with no attempts left completes it as FAILED; otherwise the student may try again while
     * the window is open. Sends the result email either way.
     */
    private Certificate finishAttempt(ExamAttempt attempt) {
        ExamApplication a = attempt.getApplication();
        Exam exam = attempt.getExam();
        long used = attemptRepository.countByStudentIdAndExamId(attempt.getStudent().getId(), exam.getId());
        int remaining = (int) Math.max(0, exam.getMaxAttempts() - used);
        Certificate certificate = null;
        if (attempt.isPassed()) {
            certificate = certificateService.issueForPassedAttempt(attempt);
            complete(a, ExamResult.PASSED);
        } else if (remaining == 0) {
            complete(a, ExamResult.FAILED);
        }
        String code = studentCode(attempt.getStudent().getId());
        notifications.examResult(attempt.getStudent(), code, a.getCourse().getCourseName(), exam.getTitle(),
                attempt.getAttemptNumber(), attempt.getScore(), attempt.getTotalMarks(), attempt.getPassingMarks(),
                attempt.isPassed(), remaining, certificate == null ? null : certificate.getCertificateNumber(),
                a.getCourse().getId());
        return certificate;
    }

    private void complete(ExamApplication a, ExamResult result) {
        a.setStatus(ExamApplicationStatus.COMPLETED);
        a.setResult(result);
        a.setCompletedAt(Instant.now());
        applicationRepository.save(a);
        scheduleRepository.findByApplicationId(a.getId()).ifPresent(s -> {
            s.setStatus(ExamScheduleStatus.COMPLETED);
            scheduleRepository.save(s);
        });
    }

    /** An in-progress attempt whose deadline passed is closed as EXPIRED (score 0) and counts as used. */
    private void expireDueAttempts(ExamApplication a) {
        if (a.getExam() == null) {
            return;
        }
        Instant now = Instant.now();
        attemptRepository.findFirstByStudentIdAndExamIdAndStatus(a.getStudent().getId(), a.getExam().getId(),
                ExamAttemptStatus.IN_PROGRESS).ifPresent(att -> expireIfDue(att, now));
    }

    private void expireIfDue(ExamAttempt attempt, Instant now) {
        if (attempt.getStatus() == ExamAttemptStatus.IN_PROGRESS && now.isAfter(attempt.getDeadlineAt().plus(SUBMIT_GRACE))) {
            expire(attempt, now);
        }
    }

    private void expire(ExamAttempt attempt, Instant now) {
        attempt.setStatus(ExamAttemptStatus.EXPIRED);
        attempt.setSubmittedAt(now);
        attempt.setScore(0);
        attempt.setCorrectCount(0);
        attempt.setPassed(false);
        attemptRepository.save(attempt);
        auditService.record(attempt.getStudent(), "EXAM_ATTEMPT_EXPIRED", "ExamAttempt", attempt.getId(),
                attempt.getExam().getTitle() + " attempt " + attempt.getAttemptNumber() + " expired without submission", null);
        finishAttempt(attempt);
    }

    // =========================================================================================
    // Admin: applications, review, scheduling
    // =========================================================================================

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> applications(ExamApplicationStatus status, Long courseId, String q, Pageable pageable) {
        String term = q == null || q.isBlank() ? null : q.trim();
        return applicationRepository.search(status, courseId, term, pageable).map(a -> toResponse(a, false));
    }

    @Transactional(readOnly = true)
    public ApplicationCounters counters() {
        return new ApplicationCounters(applicationRepository.countByStatus(ExamApplicationStatus.PENDING),
                applicationRepository.countByStatus(ExamApplicationStatus.APPROVED),
                applicationRepository.countByStatus(ExamApplicationStatus.SCHEDULED),
                applicationRepository.countByStatus(ExamApplicationStatus.COMPLETED),
                applicationRepository.countByStatus(ExamApplicationStatus.REJECTED));
    }

    @Transactional
    public ApplicationResponse application(Long id) {
        ExamApplication a = requireApplication(id);
        expireDueAttempts(a);
        return toResponse(a, false);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> applicationsForStudent(Long studentUserId) {
        return applicationRepository.findByStudentIdOrderByAppliedAtDesc(studentUserId).stream().map(a -> toResponse(a, false)).toList();
    }

    @Transactional
    public ApplicationResponse approve(Long id, String remarks, User actor, String ip) {
        ExamApplication a = requireApplication(id);
        if (a.getStatus() != ExamApplicationStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only pending applications can be approved (this one is " + a.getStatus() + ").");
        }
        a.setStatus(ExamApplicationStatus.APPROVED);
        review(a, remarks, actor);
        applicationRepository.save(a);
        auditService.record(actor, "EXAM_APPLICATION_APPROVED", "ExamApplication", id, a.getStudent().getEmail() + " — " + a.getCourse().getCourseCode(), ip);
        notifications.applicationApproved(a.getStudent(), studentCode(a.getStudent().getId()), a.getCourse().getCourseName());
        return toResponse(a, false);
    }

    @Transactional
    public ApplicationResponse reject(Long id, String remarks, User actor, String ip) {
        ExamApplication a = requireApplication(id);
        if (!a.getStatus().isOpen()) {
            throw new ApiException(HttpStatus.CONFLICT, "This application is already " + a.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }
        if (!attemptRepository.findByApplicationIdOrderByAttemptNumberAsc(id).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "This student has already attempted the exam; the application cannot be rejected.");
        }
        scheduleRepository.findByApplicationId(id).ifPresent(s -> {
            s.setStatus(ExamScheduleStatus.CANCELLED);
            scheduleRepository.save(s);
        });
        a.setStatus(ExamApplicationStatus.REJECTED);
        review(a, remarks, actor);
        applicationRepository.save(a);
        auditService.record(actor, "EXAM_APPLICATION_REJECTED", "ExamApplication", id,
                a.getStudent().getEmail() + " — " + a.getCourse().getCourseCode() + (remarks == null ? "" : ": " + remarks), ip);
        notifications.applicationRejected(a.getStudent(), studentCode(a.getStudent().getId()), a.getCourse().getCourseName(), remarks);
        return toResponse(a, false);
    }

    private void review(ExamApplication a, String remarks, User actor) {
        a.setReviewedBy(actor);
        a.setReviewedAt(Instant.now());
        if (remarks != null && !remarks.isBlank()) {
            a.setAdminRemarks(remarks.trim());
        }
    }

    /**
     * Creates the exam window for an application (approving it implicitly) or edits an existing one.
     * Refused once the application is completed / rejected or while an attempt is in progress, so a
     * schedule change can never corrupt a sitting or a finished result.
     */
    @Transactional
    public ApplicationResponse schedule(Long applicationId, ScheduleRequest req, User actor, String ip) {
        ExamApplication a = requireApplication(applicationId);
        if (!a.getStatus().isOpen()) {
            throw new ApiException(HttpStatus.CONFLICT, "This application is " + a.getStatus().name().toLowerCase(Locale.ROOT) + " and cannot be scheduled.");
        }
        Exam exam = examService.requireExam(req.examId());
        if (!exam.getCourse().getId().equals(a.getCourse().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The selected exam belongs to a different course than this application.");
        }
        if (exam.getStatus() != ExamStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "Activate the exam \"" + exam.getTitle() + "\" before scheduling it.");
        }
        examService.assertReadyForStudents(exam);
        if (attemptRepository.existsByStudentIdAndExamIdAndPassedTrue(a.getStudent().getId(), exam.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "This student has already passed the selected exam.");
        }
        if (attemptRepository.countByStudentIdAndExamId(a.getStudent().getId(), exam.getId()) >= exam.getMaxAttempts()) {
            throw new ApiException(HttpStatus.CONFLICT, "This student has used all " + exam.getMaxAttempts()
                    + " attempts of the selected exam. Increase the exam's maximum attempts to schedule it again.");
        }

        Instant startsAt = ZonedDateTime.of(req.examDate(), req.startTime(), INDIA).toInstant();
        Instant endsAt = ZonedDateTime.of(req.examDate(), req.endTime(), INDIA).toInstant();
        if (!endsAt.isAfter(startsAt)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "End time must be after start time.");
        }
        if (!endsAt.isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The exam window is already in the past. Choose a future date and time.");
        }

        Optional<ExamSchedule> existing = scheduleRepository.findByApplicationId(applicationId);
        boolean reschedule = existing.isPresent();
        ExamSchedule s = existing.orElseGet(ExamSchedule::new);
        if (reschedule && attemptRepository.findFirstByStudentIdAndExamIdAndStatus(a.getStudent().getId(),
                s.getExam().getId(), ExamAttemptStatus.IN_PROGRESS).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "The student is attempting the exam right now; the schedule cannot be changed.");
        }
        if (reschedule && !s.getExam().getId().equals(exam.getId()) && attemptRepository.countByScheduleId(s.getId()) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Attempts already exist for this schedule's exam; the exam cannot be changed. Reschedule the same exam or cancel and create a new schedule.");
        }
        s.setApplication(a);
        s.setExam(exam);
        s.setStudent(a.getStudent());
        s.setStartsAt(startsAt);
        s.setEndsAt(endsAt);
        s.setInstructions(req.instructions() == null || req.instructions().isBlank() ? null : req.instructions().trim());
        s.setStatus(ExamScheduleStatus.SCHEDULED);
        s.setScheduledBy(actor);
        if (reschedule) {
            s.setRescheduleCount(s.getRescheduleCount() + 1);
        }
        scheduleRepository.save(s);

        a.setExam(exam);
        a.setStatus(ExamApplicationStatus.SCHEDULED);
        if (a.getReviewedAt() == null) {
            review(a, null, actor);
        }
        applicationRepository.save(a);
        auditService.record(actor, reschedule ? "EXAM_RESCHEDULED" : "EXAM_SCHEDULED", "ExamSchedule", s.getId(),
                a.getStudent().getEmail() + " — " + exam.getTitle() + " " + startsAt + " → " + endsAt, ip);
        notifications.examScheduled(a.getStudent(), studentCode(a.getStudent().getId()), a.getCourse().getCourseName(),
                exam.getTitle(), startsAt, endsAt, reschedule, a.getCourse().getId());
        return toResponse(a, false);
    }

    /** Cancels the window; the application returns to APPROVED so it can be rescheduled. Finished attempts are kept. */
    @Transactional
    public ApplicationResponse cancelSchedule(Long applicationId, User actor, String ip) {
        ExamApplication a = requireApplication(applicationId);
        ExamSchedule s = scheduleRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "This application has no schedule."));
        if (a.getStatus() != ExamApplicationStatus.SCHEDULED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a scheduled application can have its schedule cancelled.");
        }
        if (attemptRepository.findFirstByStudentIdAndExamIdAndStatus(a.getStudent().getId(), s.getExam().getId(),
                ExamAttemptStatus.IN_PROGRESS).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "The student is attempting the exam right now; the schedule cannot be cancelled.");
        }
        s.setStatus(ExamScheduleStatus.CANCELLED);
        scheduleRepository.save(s);
        a.setStatus(ExamApplicationStatus.APPROVED);
        applicationRepository.save(a);
        auditService.record(actor, "EXAM_SCHEDULE_CANCELLED", "ExamSchedule", s.getId(), a.getStudent().getEmail() + " — " + s.getExam().getTitle(), ip);
        notifications.examCancelled(a.getStudent(), studentCode(a.getStudent().getId()), a.getCourse().getCourseName(),
                s.getExam().getTitle(), a.getCourse().getId());
        return toResponse(a, false);
    }

    @Transactional(readOnly = true)
    public Page<ScheduleResponse> schedules(ExamScheduleStatus status, Long examId, Long courseId, Pageable pageable) {
        return scheduleRepository.search(status, examId, courseId, pageable).map(this::toSchedule);
    }

    // =========================================================================================
    // Admin: results
    // =========================================================================================

    @Transactional(readOnly = true)
    public Page<ResultRow> results(Long examId, Long courseId, Boolean passed, String q, Pageable pageable) {
        String term = q == null || q.isBlank() ? null : q.trim();
        return attemptRepository.results(examId, courseId, passed, term, pageable).map(this::toResultRow);
    }

    @Transactional(readOnly = true)
    public List<AttemptSummary> attemptsForApplication(Long applicationId) {
        requireApplication(applicationId);
        return attemptRepository.findByApplicationIdOrderByAttemptNumberAsc(applicationId).stream().map(this::toSummary).toList();
    }

    // =========================================================================================
    // Lookups
    // =========================================================================================

    @Transactional(readOnly = true)
    public ExamApplication requireApplication(Long id) {
        return applicationRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Exam application", id));
    }

    /** Student A can never resolve Student B's application: the lookup itself is scoped to the owner. */
    @Transactional(readOnly = true)
    public ExamApplication requireOwnedApplication(Long studentUserId, Long id) {
        return applicationRepository.findByIdAndStudentId(id, studentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this exam application."));
    }

    @Transactional(readOnly = true)
    public ExamAttempt requireOwnedAttempt(Long studentUserId, Long id) {
        return attemptRepository.findByIdAndStudentId(id, studentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this exam attempt."));
    }

    private String studentCode(Long userId) {
        return studentProfileRepository.findByUserId(userId).map(StudentProfile::getStudentId).orElse(null);
    }

    // =========================================================================================
    // Mapping
    // =========================================================================================

    public ApplicationResponse toResponse(ExamApplication a, boolean forStudent) {
        User s = a.getStudent();
        Exam exam = a.getExam();
        ExamSchedule schedule = scheduleRepository.findByApplicationId(a.getId()).orElse(null);
        List<ExamAttempt> attempts = attemptRepository.findByApplicationIdOrderByAttemptNumberAsc(a.getId());
        long used = exam == null ? attempts.size() : attemptRepository.countByStudentIdAndExamId(s.getId(), exam.getId());
        Integer remaining = exam == null ? null : (int) Math.max(0, exam.getMaxAttempts() - used);
        Certificate certificate = certificateRepository.findFirstByStudentIdAndCourseIdOrderByIssuedAtDesc(s.getId(), a.getCourse().getId())
                .filter(c -> c.getApplication().getId().equals(a.getId())).orElse(null);
        String[] actionAndMessage = actionFor(a, schedule, attempts, remaining);
        return new ApplicationResponse(a.getId(), s.getId(), studentCode(s.getId()), s.getFullName(), s.getEmail(),
                a.getCourse().getId(), a.getCourse().getCourseName(), a.getEnrollment().getId(),
                exam == null ? null : exam.getId(), exam == null ? null : exam.getTitle(),
                exam == null ? null : exam.getTotalMarks(), exam == null ? null : exam.getPassingMarks(),
                exam == null ? null : exam.getMaxAttempts(),
                a.getStatus(), a.getResult(), a.getAppliedAt(), a.getCourseCompletedAt(), a.getReviewedAt(),
                a.getReviewedBy() == null ? null : a.getReviewedBy().getFullName(),
                forStudent && a.getStatus() != ExamApplicationStatus.REJECTED ? null : a.getAdminRemarks(),
                a.getCompletedAt(), schedule == null ? null : toSchedule(schedule), used, remaining,
                attempts.stream().map(this::toSummary).toList(),
                certificate == null ? null : certificate.getId(), certificate == null ? null : certificate.getCertificateNumber(),
                actionAndMessage[0], actionAndMessage[1]);
    }

    /** The one thing the student can do next, decided by the server clock and the stored state. */
    private String[] actionFor(ExamApplication a, ExamSchedule schedule, List<ExamAttempt> attempts, Integer remaining) {
        Instant now = Instant.now();
        switch (a.getStatus()) {
            case PENDING:
                return new String[]{"PENDING", "Your exam application is under review."};
            case APPROVED:
                return new String[]{"APPROVED", "Your exam application has been approved. The academy will schedule your exam shortly."};
            case REJECTED:
                return new String[]{"REJECTED", "Your exam application was not approved."
                        + (a.getAdminRemarks() == null ? "" : " Remarks: " + a.getAdminRemarks())};
            case COMPLETED:
                return a.getResult() == ExamResult.PASSED
                        ? new String[]{"PASSED", "Exam cleared. Your certificate is available."}
                        : new String[]{"FAILED", "Maximum exam attempts reached."};
            case SCHEDULED:
            default:
                if (schedule == null || schedule.getStatus() != ExamScheduleStatus.SCHEDULED) {
                    return new String[]{"APPROVED", "The academy will schedule your exam shortly."};
                }
                boolean inProgress = attempts.stream().anyMatch(t -> t.getStatus() == ExamAttemptStatus.IN_PROGRESS);
                if (inProgress && now.isBefore(schedule.getEndsAt().plus(SUBMIT_GRACE))) {
                    return new String[]{"RESUME", "Your exam is in progress. Continue and submit it before the time ends."};
                }
                if (now.isBefore(schedule.getStartsAt())) {
                    return new String[]{"WAIT", "Exam scheduled. The Start Exam button becomes available when the window opens."};
                }
                if (!now.isBefore(schedule.getEndsAt())) {
                    return new String[]{"CLOSED", "Exam window closed."};
                }
                if (remaining != null && remaining <= 0) {
                    return new String[]{"FAILED", "Maximum exam attempts reached."};
                }
                ExamAttempt last = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);
                if (last != null && last.isFinished() && !last.isPassed()) {
                    return new String[]{"START", "Exam not cleared. You have " + remaining + " attempt(s) remaining."};
                }
                return new String[]{"START", "The exam window is open. You can start the exam now."};
        }
    }

    public ScheduleResponse toSchedule(ExamSchedule s) {
        Instant now = Instant.now();
        String window = s.getStatus() != ExamScheduleStatus.SCHEDULED ? s.getStatus().name()
                : now.isBefore(s.getStartsAt()) ? "UPCOMING" : now.isBefore(s.getEndsAt()) ? "OPEN" : "CLOSED";
        ZonedDateTime start = s.getStartsAt().atZone(INDIA);
        ZonedDateTime end = s.getEndsAt().atZone(INDIA);
        return new ScheduleResponse(s.getId(), s.getApplication().getId(), s.getExam().getId(), s.getExam().getTitle(),
                s.getStartsAt(), s.getEndsAt(), DATE.format(start), TIME.format(start), TIME.format(end),
                s.getInstructions(), s.getStatus(), s.getRescheduleCount(), attemptRepository.countByScheduleId(s.getId()), window);
    }

    public AttemptSummary toSummary(ExamAttempt t) {
        return new AttemptSummary(t.getId(), t.getAttemptNumber(), t.getStatus(), t.getStartedAt(), t.getDeadlineAt(),
                t.getSubmittedAt(), t.getScore(), t.getTotalMarks(), t.getPassingMarks(), t.isPassed(), t.getQuestionCount(),
                t.getCorrectCount());
    }

    private AttemptResult toResult(ExamAttempt t, Certificate certificate, String message) {
        User s = t.getStudent();
        Exam exam = t.getExam();
        long used = attemptRepository.countByStudentIdAndExamId(s.getId(), exam.getId());
        int remaining = (int) Math.max(0, exam.getMaxAttempts() - used);
        String msg = message != null ? message
                : t.getStatus() == ExamAttemptStatus.EXPIRED ? "The time for this attempt ran out before it was submitted."
                : t.isPassed() ? "Congratulations! You have cleared the exam and your certificate has been issued."
                : remaining > 0 ? "Exam not cleared. You have " + remaining + " attempt(s) remaining."
                : "Exam not cleared. Maximum exam attempts reached.";
        return new AttemptResult(t.getId(), t.getApplication().getId(), s.getFullName(), studentCode(s.getId()), exam.getTitle(),
                exam.getCourse().getCourseName(), t.getAttemptNumber(), exam.getMaxAttempts(), remaining, t.getQuestionCount(),
                t.getCorrectCount(), t.getScore(), t.getTotalMarks(), t.getPassingMarks(), t.isPassed(),
                t.isPassed() ? "PASS" : "FAIL", t.getStatus(), t.getSubmittedAt(), t.isPassed(),
                certificate == null ? null : certificate.getId(), certificate == null ? null : certificate.getCertificateNumber(), msg);
    }

    private ResultRow toResultRow(ExamAttempt t) {
        User s = t.getStudent();
        Exam exam = t.getExam();
        Certificate c = certificateRepository.findByAttemptId(t.getId()).orElse(null);
        return new ResultRow(t.getId(), s.getId(), studentCode(s.getId()), s.getFullName(), s.getEmail(),
                exam.getCourse().getId(), exam.getCourse().getCourseName(), exam.getId(), exam.getTitle(),
                t.getAttemptNumber(), exam.getMaxAttempts(), t.getScore(), t.getTotalMarks(), t.getPassingMarks(),
                t.isPassed(), t.getStatus(), t.getStartedAt(), t.getSubmittedAt(), t.isPassed(),
                c == null ? null : c.getId(), c == null ? null : c.getCertificateNumber());
    }
}
