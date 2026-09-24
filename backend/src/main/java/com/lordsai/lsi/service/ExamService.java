package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.exam.ExamDtos.AdminExam;
import com.lordsai.lsi.dto.exam.ExamDtos.AdminQuestion;
import com.lordsai.lsi.dto.exam.ExamDtos.ExamRequest;
import com.lordsai.lsi.dto.exam.ExamDtos.QuestionRequest;
import com.lordsai.lsi.entity.Course;
import com.lordsai.lsi.entity.Exam;
import com.lordsai.lsi.entity.ExamQuestion;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.ExamStatus;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.ExamAnswerRepository;
import com.lordsai.lsi.repository.ExamApplicationRepository;
import com.lordsai.lsi.repository.ExamAttemptRepository;
import com.lordsai.lsi.repository.ExamQuestionRepository;
import com.lordsai.lsi.repository.ExamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Main Admin -> Exams: exam definitions (course, title, total / passing marks, attempt limit) and
 * their MCQ questions. Every rule the student side depends on — passing marks not above total,
 * exactly four distinct non-empty options, one correct option, positive marks — is validated here,
 * on the backend, regardless of what the form sent.
 */
@Service
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository questionRepository;
    private final ExamApplicationRepository applicationRepository;
    private final ExamAttemptRepository attemptRepository;
    private final ExamAnswerRepository answerRepository;
    private final CourseService courseService;
    private final AuditService auditService;

    public ExamService(ExamRepository examRepository,
                       ExamQuestionRepository questionRepository,
                       ExamApplicationRepository applicationRepository,
                       ExamAttemptRepository attemptRepository,
                       ExamAnswerRepository answerRepository,
                       CourseService courseService,
                       AuditService auditService) {
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.applicationRepository = applicationRepository;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.courseService = courseService;
        this.auditService = auditService;
    }

    // ---- exams -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AdminExam> list(Long courseId) {
        List<Exam> exams = courseId == null ? examRepository.findAllByOrderByCreatedAtDesc()
                : examRepository.findByCourseIdOrderByCreatedAtDesc(courseId);
        return exams.stream().map(this::toAdmin).toList();
    }

    @Transactional(readOnly = true)
    public AdminExam get(Long id) {
        return toAdmin(requireExam(id));
    }

    @Transactional(readOnly = true)
    public Exam requireExam(Long id) {
        return examRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Exam", id));
    }

    @Transactional
    public AdminExam create(ExamRequest req, User actor, String ip) {
        Course course = courseService.requireCourse(req.courseId());
        validateMarks(req.totalMarks(), req.passingMarks());
        Exam exam = new Exam();
        exam.setCourse(course);
        apply(exam, req);
        exam.setStatus(ExamStatus.DRAFT);
        exam.setCreatedBy(actor);
        exam.setUpdatedBy(actor);
        exam = examRepository.save(exam);
        auditService.record(actor, "EXAM_CREATED", "Exam", exam.getId(),
                exam.getTitle() + " for " + course.getCourseCode() + " (total " + exam.getTotalMarks()
                        + ", pass " + exam.getPassingMarks() + ", attempts " + exam.getMaxAttempts() + ")", ip);
        return toAdmin(exam);
    }

    @Transactional
    public AdminExam update(Long id, ExamRequest req, User actor, String ip) {
        Exam exam = requireExam(id);
        validateMarks(req.totalMarks(), req.passingMarks());
        if (!exam.getCourse().getId().equals(req.courseId())) {
            if (applicationRepository.countByExamId(id) > 0 || attemptRepository.countByExamId(id) > 0) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "This exam already has applications or attempts; it cannot be moved to another course.");
            }
            exam.setCourse(courseService.requireCourse(req.courseId()));
        }
        boolean marksChanged = exam.getTotalMarks() != req.totalMarks() || exam.getPassingMarks() != req.passingMarks();
        apply(exam, req);
        exam.setUpdatedBy(actor);
        examRepository.save(exam);
        auditService.record(actor, "EXAM_UPDATED", "Exam", id, exam.getTitle()
                + (marksChanged ? " (marks changed: total " + exam.getTotalMarks() + ", pass " + exam.getPassingMarks()
                + " — completed attempts keep their own snapshot)" : ""), ip);
        return toAdmin(exam);
    }

    @Transactional
    public AdminExam setStatus(Long id, ExamStatus status, User actor, String ip) {
        Exam exam = requireExam(id);
        if (status == ExamStatus.ACTIVE) {
            assertReadyForStudents(exam);
        }
        ExamStatus old = exam.getStatus();
        exam.setStatus(status);
        exam.setUpdatedBy(actor);
        examRepository.save(exam);
        auditService.record(actor, "EXAM_STATUS_CHANGED", "Exam", id, old + " -> " + status, ip);
        return toAdmin(exam);
    }

    @Transactional
    public void delete(Long id, User actor, String ip) {
        Exam exam = requireExam(id);
        if (applicationRepository.countByExamId(id) > 0 || attemptRepository.countByExamId(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This exam has applications or attempts and cannot be deleted. Deactivate it instead.");
        }
        questionRepository.deleteAll(questionRepository.findByExamIdOrderByDisplayOrderAscIdAsc(id));
        examRepository.delete(exam);
        auditService.record(actor, "EXAM_DELETED", "Exam", id, exam.getTitle(), ip);
    }

    /**
     * The gate before an exam is scheduled or started: ACTIVE, at least one question, and the
     * active questions must add up to exactly the configured total marks so a score out of
     * "total marks" is meaningful.
     */
    @Transactional(readOnly = true)
    public void assertReadyForStudents(Exam exam) {
        long count = questionRepository.countByExamIdAndActiveTrue(exam.getId());
        if (count == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Add at least one question to \"" + exam.getTitle() + "\" first.");
        }
        long sum = questionRepository.sumActiveMarks(exam.getId());
        if (sum != exam.getTotalMarks()) {
            throw new ApiException(HttpStatus.CONFLICT, "The questions of \"" + exam.getTitle() + "\" add up to " + sum
                    + " marks but the exam total is " + exam.getTotalMarks() + ". Adjust the question marks or the total marks.");
        }
    }

    // ---- questions -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AdminQuestion> questions(Long examId) {
        requireExam(examId);
        return questionRepository.findByExamIdOrderByDisplayOrderAscIdAsc(examId).stream().map(this::toAdminQuestion).toList();
    }

    @Transactional
    public AdminQuestion addQuestion(Long examId, QuestionRequest req, User actor, String ip) {
        Exam exam = requireExam(examId);
        validateQuestion(req);
        ExamQuestion q = new ExamQuestion();
        q.setExam(exam);
        applyQuestion(q, req);
        q.setDisplayOrder(questionRepository.maxDisplayOrder(examId) + 1);
        q.setActive(true);
        q = questionRepository.save(q);
        auditService.record(actor, "EXAM_QUESTION_ADDED", "ExamQuestion", q.getId(),
                "Exam " + exam.getTitle() + " — " + summary(q), ip);
        return toAdminQuestion(q);
    }

    @Transactional
    public AdminQuestion updateQuestion(Long examId, Long questionId, QuestionRequest req, User actor, String ip) {
        ExamQuestion q = requireQuestion(examId, questionId);
        validateQuestion(req);
        applyQuestion(q, req);
        questionRepository.save(q);
        auditService.record(actor, "EXAM_QUESTION_UPDATED", "ExamQuestion", questionId, summary(q), ip);
        return toAdminQuestion(q);
    }

    /**
     * Deletes a question, or — when it has already been answered in a submitted attempt —
     * deactivates it so completed results stay intact.
     */
    @Transactional
    public boolean deleteQuestion(Long examId, Long questionId, User actor, String ip) {
        ExamQuestion q = requireQuestion(examId, questionId);
        if (answerRepository.countByQuestionId(questionId) > 0) {
            q.setActive(false);
            questionRepository.save(q);
            auditService.record(actor, "EXAM_QUESTION_DEACTIVATED", "ExamQuestion", questionId,
                    summary(q) + " (answered in existing attempts, so deactivated instead of deleted)", ip);
            return false;
        }
        questionRepository.delete(q);
        auditService.record(actor, "EXAM_QUESTION_DELETED", "ExamQuestion", questionId, summary(q), ip);
        return true;
    }

    @Transactional(readOnly = true)
    public ExamQuestion requireQuestion(Long examId, Long questionId) {
        return questionRepository.findByIdAndExamId(questionId, examId)
                .orElseThrow(() -> ResourceNotFoundException.of("Question", questionId));
    }

    // ---- validation ------------------------------------------------------------------------

    static void validateMarks(Integer total, Integer passing) {
        if (total == null || total < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Total marks must be at least 1.");
        }
        if (passing == null || passing < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Passing marks cannot be negative.");
        }
        if (passing > total) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Passing marks cannot be greater than total marks.");
        }
    }

    static void validateQuestion(QuestionRequest req) {
        if (blank(req.questionText())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Question text is required.");
        }
        String[] options = {req.optionA(), req.optionB(), req.optionC(), req.optionD()};
        String[] labels = {"A", "B", "C", "D"};
        Set<String> distinct = new LinkedHashSet<>();
        for (int i = 0; i < options.length; i++) {
            if (blank(options[i])) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Option " + labels[i] + " is required.");
            }
            if (!distinct.add(options[i].trim().toLowerCase(Locale.ROOT))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Option " + labels[i] + " duplicates another option. All four options must be different.");
            }
        }
        if (req.correctOption() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select the correct answer (A, B, C or D).");
        }
        if (req.marks() == null || req.marks() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Marks must be a whole number of at least 1.");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static void apply(Exam exam, ExamRequest req) {
        exam.setTitle(req.title().trim());
        exam.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        exam.setTotalMarks(req.totalMarks());
        exam.setPassingMarks(req.passingMarks());
        exam.setMaxAttempts(req.maxAttempts());
        exam.setDurationMinutes(req.durationMinutes());
    }

    private static void applyQuestion(ExamQuestion q, QuestionRequest req) {
        q.setQuestionText(req.questionText().trim());
        q.setOptionA(req.optionA().trim());
        q.setOptionB(req.optionB().trim());
        q.setOptionC(req.optionC().trim());
        q.setOptionD(req.optionD().trim());
        q.setCorrectOption(req.correctOption());
        q.setMarks(req.marks());
    }

    private static String summary(ExamQuestion q) {
        String t = q.getQuestionText();
        return "Q" + q.getDisplayOrder() + ": " + (t.length() > 80 ? t.substring(0, 80) + "…" : t) + " [" + q.getMarks() + " marks]";
    }

    // ---- mapping ---------------------------------------------------------------------------

    public AdminExam toAdmin(Exam e) {
        long count = questionRepository.countByExamIdAndActiveTrue(e.getId());
        long sum = questionRepository.sumActiveMarks(e.getId());
        return new AdminExam(e.getId(), e.getCourse().getId(), e.getCourse().getCourseCode(), e.getCourse().getCourseName(),
                e.getTitle(), e.getDescription(), e.getTotalMarks(), e.getPassingMarks(), e.getMaxAttempts(),
                e.getDurationMinutes(), e.getStatus(), count, sum, count > 0 && sum == e.getTotalMarks(),
                applicationRepository.countByExamId(e.getId()), attemptRepository.countByExamId(e.getId()),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    public AdminQuestion toAdminQuestion(ExamQuestion q) {
        return new AdminQuestion(q.getId(), q.getExam().getId(), q.getQuestionText(), q.getOptionA(), q.getOptionB(),
                q.getOptionC(), q.getOptionD(), q.getCorrectOption(), q.getMarks(), q.getDisplayOrder(), q.isActive(),
                answerRepository.countByQuestionId(q.getId()));
    }
}
