-- =============================================================================
-- V21: Course examinations and certificates
--
-- Extends the existing course / enrollment / lesson_progress architecture with the final-exam
-- workflow. Nothing existing is modified; course completion keeps coming from lesson_progress
-- (every active lesson completed -> enrollments.status = COMPLETED, see StudentLearningService).
--
--   * exams                 — one MCQ exam definition per course (a course may have several);
--                             admin-configured total marks, passing marks and maximum attempts.
--   * exam_questions        — exactly four options (A-D) and ONE correct option per question. The
--                             correct option is only ever read by the backend when scoring.
--   * exam_applications     — a student's request to sit the exam for a completed course
--                             (PENDING -> APPROVED -> SCHEDULED -> COMPLETED, or REJECTED).
--   * exam_schedules        — the admin-set exam window (starts_at / ends_at) for one application.
--   * exam_attempts         — every attempt with its backend-computed score and pass/fail;
--                             unique (student, exam, attempt_number) so the attempt limit cannot be
--                             bypassed by parallel requests.
--   * exam_answers          — the option chosen for each question of an attempt (immutable after
--                             submission).
--   * certificate_templates — admin-uploaded background images. NO row = the built-in default
--                             Lord Sai certificate (a classpath template that cannot be deleted).
--   * certificate_sequence + certificates — one certificate per passed attempt, numbered
--                             LSI-CERT-YYYY-NNNNNN by the backend, with the template used at issue
--                             time recorded so an already issued certificate never silently changes.
-- =============================================================================

-- ---- Exams ------------------------------------------------------------------------------
CREATE TABLE exams (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id           BIGINT        NOT NULL,
    title               VARCHAR(200)  NOT NULL,
    description         TEXT,                             -- instructions shown to the student
    total_marks         INT           NOT NULL,
    passing_marks       INT           NOT NULL,
    max_attempts        INT           NOT NULL DEFAULT 1,
    duration_minutes    INT,                              -- optional per-attempt time limit
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',   -- DRAFT | ACTIVE | INACTIVE
    created_by_user_id  BIGINT,
    updated_by_user_id  BIGINT,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    CONSTRAINT fk_exams_course      FOREIGN KEY (course_id)          REFERENCES courses (id),
    CONSTRAINT fk_exams_created_by  FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_exams_updated_by  FOREIGN KEY (updated_by_user_id) REFERENCES users (id)
);
CREATE INDEX idx_exams_course ON exams (course_id, status);

-- ---- Questions ----------------------------------------------------------------------------
CREATE TABLE exam_questions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_id         BIGINT         NOT NULL,
    question_text   TEXT           NOT NULL,
    option_a        VARCHAR(1000)  NOT NULL,
    option_b        VARCHAR(1000)  NOT NULL,
    option_c        VARCHAR(1000)  NOT NULL,
    option_d        VARCHAR(1000)  NOT NULL,
    correct_option  VARCHAR(1)     NOT NULL,              -- A | B | C | D (never sent to students)
    marks           INT            NOT NULL,
    display_order   INT            NOT NULL DEFAULT 0,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP(6)   NOT NULL,
    updated_at      TIMESTAMP(6)   NOT NULL,
    CONSTRAINT fk_exam_questions_exam FOREIGN KEY (exam_id) REFERENCES exams (id)
);
CREATE INDEX idx_exam_questions_exam ON exam_questions (exam_id, display_order);

-- ---- Applications -------------------------------------------------------------------------
CREATE TABLE exam_applications (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_user_id       BIGINT        NOT NULL,
    course_id             BIGINT        NOT NULL,
    enrollment_id         BIGINT        NOT NULL,
    exam_id               BIGINT,                         -- chosen by the admin when scheduling
    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | SCHEDULED | COMPLETED | REJECTED
    result                VARCHAR(20),                    -- PASSED | FAILED once COMPLETED
    applied_at            TIMESTAMP(6)  NOT NULL,
    course_completed_at   TIMESTAMP(6)  NULL,
    reviewed_by_user_id   BIGINT,
    reviewed_at           TIMESTAMP(6)  NULL,
    admin_remarks         VARCHAR(500),
    completed_at          TIMESTAMP(6)  NULL,
    created_at            TIMESTAMP(6)  NOT NULL,
    updated_at            TIMESTAMP(6)  NOT NULL,
    CONSTRAINT fk_exam_apps_student     FOREIGN KEY (student_user_id)     REFERENCES users (id),
    CONSTRAINT fk_exam_apps_course      FOREIGN KEY (course_id)           REFERENCES courses (id),
    CONSTRAINT fk_exam_apps_enrollment  FOREIGN KEY (enrollment_id)       REFERENCES enrollments (id),
    CONSTRAINT fk_exam_apps_exam        FOREIGN KEY (exam_id)             REFERENCES exams (id),
    CONSTRAINT fk_exam_apps_reviewed_by FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id)
);
CREATE INDEX idx_exam_apps_student ON exam_applications (student_user_id, course_id);
CREATE INDEX idx_exam_apps_status  ON exam_applications (status);
CREATE INDEX idx_exam_apps_applied ON exam_applications (applied_at);

-- ---- Schedules ----------------------------------------------------------------------------
CREATE TABLE exam_schedules (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id         BIGINT        NOT NULL,
    exam_id                BIGINT        NOT NULL,
    student_user_id        BIGINT        NOT NULL,
    starts_at              TIMESTAMP(6)  NOT NULL,
    ends_at                TIMESTAMP(6)  NOT NULL,
    instructions           TEXT,
    status                 VARCHAR(20)   NOT NULL DEFAULT 'SCHEDULED',  -- SCHEDULED | CANCELLED | COMPLETED
    scheduled_by_user_id   BIGINT,
    reschedule_count       INT           NOT NULL DEFAULT 0,
    created_at             TIMESTAMP(6)  NOT NULL,
    updated_at             TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_exam_schedules_application UNIQUE (application_id),
    CONSTRAINT fk_exam_schedules_application  FOREIGN KEY (application_id)       REFERENCES exam_applications (id),
    CONSTRAINT fk_exam_schedules_exam         FOREIGN KEY (exam_id)              REFERENCES exams (id),
    CONSTRAINT fk_exam_schedules_student      FOREIGN KEY (student_user_id)      REFERENCES users (id),
    CONSTRAINT fk_exam_schedules_scheduled_by FOREIGN KEY (scheduled_by_user_id) REFERENCES users (id)
);
CREATE INDEX idx_exam_schedules_student ON exam_schedules (student_user_id, status);
CREATE INDEX idx_exam_schedules_window  ON exam_schedules (starts_at, ends_at);

-- ---- Attempts -----------------------------------------------------------------------------
CREATE TABLE exam_attempts (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id       BIGINT        NOT NULL,
    application_id    BIGINT        NOT NULL,
    exam_id           BIGINT        NOT NULL,
    student_user_id   BIGINT        NOT NULL,
    attempt_number    INT           NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS | SUBMITTED | EXPIRED
    started_at        TIMESTAMP(6)  NOT NULL,
    deadline_at       TIMESTAMP(6)  NOT NULL,             -- min(window end, start + duration)
    submitted_at      TIMESTAMP(6)  NULL,
    question_count    INT           NOT NULL DEFAULT 0,
    correct_count     INT           NOT NULL DEFAULT 0,
    score             INT           NOT NULL DEFAULT 0,   -- computed by the backend only
    total_marks       INT           NOT NULL,             -- snapshot of the exam configuration
    passing_marks     INT           NOT NULL,
    passed            BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP(6)  NOT NULL,
    updated_at        TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_exam_attempts_number UNIQUE (student_user_id, exam_id, attempt_number),
    CONSTRAINT fk_exam_attempts_schedule    FOREIGN KEY (schedule_id)     REFERENCES exam_schedules (id),
    CONSTRAINT fk_exam_attempts_application FOREIGN KEY (application_id)  REFERENCES exam_applications (id),
    CONSTRAINT fk_exam_attempts_exam        FOREIGN KEY (exam_id)         REFERENCES exams (id),
    CONSTRAINT fk_exam_attempts_student     FOREIGN KEY (student_user_id) REFERENCES users (id)
);
CREATE INDEX idx_exam_attempts_student ON exam_attempts (student_user_id, exam_id);
CREATE INDEX idx_exam_attempts_exam    ON exam_attempts (exam_id, status);
CREATE INDEX idx_exam_attempts_submit  ON exam_attempts (submitted_at);

-- ---- Answers ------------------------------------------------------------------------------
CREATE TABLE exam_answers (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    attempt_id       BIGINT        NOT NULL,
    question_id      BIGINT        NOT NULL,
    selected_option  VARCHAR(1),                          -- NULL = unanswered
    correct          BOOLEAN       NOT NULL DEFAULT FALSE,
    marks_awarded    INT           NOT NULL DEFAULT 0,
    created_at       TIMESTAMP(6)  NOT NULL,
    updated_at       TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_exam_answers_question UNIQUE (attempt_id, question_id),
    CONSTRAINT fk_exam_answers_attempt  FOREIGN KEY (attempt_id)  REFERENCES exam_attempts (id),
    CONSTRAINT fk_exam_answers_question FOREIGN KEY (question_id) REFERENCES exam_questions (id)
);

-- ---- Certificate templates ----------------------------------------------------------------
-- Uploaded background designs. When no row is active the built-in default Lord Sai certificate
-- is used; that default lives on the classpath and can never be removed from here.
CREATE TABLE certificate_templates (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                 VARCHAR(150)  NOT NULL,
    image_path           VARCHAR(255)  NOT NULL,          -- certificate-templates/<uuid>.png (never public)
    original_name        VARCHAR(255),
    content_type         VARCHAR(60),
    size_bytes           BIGINT,
    active               BOOLEAN       NOT NULL DEFAULT FALSE,
    uploaded_by_user_id  BIGINT,
    created_at           TIMESTAMP(6)  NOT NULL,
    updated_at           TIMESTAMP(6)  NOT NULL,
    CONSTRAINT fk_cert_templates_uploaded_by FOREIGN KEY (uploaded_by_user_id) REFERENCES users (id)
);
CREATE INDEX idx_cert_templates_active ON certificate_templates (active);

-- ---- Certificates -------------------------------------------------------------------------
CREATE TABLE certificate_sequence (
    year_value   INT NOT NULL PRIMARY KEY,
    last_number  INT NOT NULL DEFAULT 0
);

CREATE TABLE certificates (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    certificate_number  VARCHAR(30)   NOT NULL,
    student_user_id     BIGINT        NOT NULL,
    course_id           BIGINT        NOT NULL,
    exam_id             BIGINT        NOT NULL,
    attempt_id          BIGINT        NOT NULL,
    application_id      BIGINT        NOT NULL,
    template_id         BIGINT,                           -- NULL = default Lord Sai certificate
    -- snapshot of what was printed
    student_name        VARCHAR(150)  NOT NULL,
    student_code        VARCHAR(20),
    course_name         VARCHAR(200)  NOT NULL,
    exam_title          VARCHAR(200)  NOT NULL,
    score               INT           NOT NULL,
    total_marks         INT           NOT NULL,
    passing_marks       INT           NOT NULL,
    course_completed_at TIMESTAMP(6)  NULL,
    issued_at           TIMESTAMP(6)  NOT NULL,
    pdf_path            VARCHAR(255),                     -- certificates/<uuid>.pdf (never public)
    pdf_generated_at    TIMESTAMP(6)  NULL,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_certificates_number  UNIQUE (certificate_number),
    CONSTRAINT uk_certificates_attempt UNIQUE (attempt_id),
    CONSTRAINT fk_certificates_student     FOREIGN KEY (student_user_id) REFERENCES users (id),
    CONSTRAINT fk_certificates_course      FOREIGN KEY (course_id)       REFERENCES courses (id),
    CONSTRAINT fk_certificates_exam        FOREIGN KEY (exam_id)         REFERENCES exams (id),
    CONSTRAINT fk_certificates_attempt     FOREIGN KEY (attempt_id)      REFERENCES exam_attempts (id),
    CONSTRAINT fk_certificates_application FOREIGN KEY (application_id)  REFERENCES exam_applications (id),
    CONSTRAINT fk_certificates_template    FOREIGN KEY (template_id)     REFERENCES certificate_templates (id)
);
CREATE INDEX idx_certificates_student ON certificates (student_user_id);
CREATE INDEX idx_certificates_course  ON certificates (course_id);
CREATE INDEX idx_certificates_issued  ON certificates (issued_at);
