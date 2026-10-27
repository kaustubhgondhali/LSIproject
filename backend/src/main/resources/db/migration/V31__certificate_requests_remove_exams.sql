-- =============================================================================
-- V31: Course completion certificates without examinations
--
-- Certificates no longer depend on exams. The new workflow is:
--   course completed (enrollments.status = COMPLETED, set by the existing lesson-progress logic
--   or by the admin)  ->  student applies (certificate_requests, PENDING)  ->  admin approves
--   ->  certificate generated from the ONE master design (certificates)  ->  student views /
--   downloads it. The admin may also reject (REJECTED); a rejected student may apply again.
--
-- Data kept: every certificate already issued (number, student, course, printed name, student ID,
-- issue date and its stored PDF) stays in `certificates` and remains available to its student.
-- Removed: the exam-only tables (exams, questions, applications, schedules, attempts, answers),
-- the exam columns on `certificates`, and the uploaded-background table — the certificate design
-- is now fixed to the master Lord Sai template shipped with the application.
-- =============================================================================

-- ---- Certificate requests --------------------------------------------------------------------
CREATE TABLE certificate_requests (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_user_id      BIGINT        NOT NULL,
    course_id            BIGINT        NOT NULL,
    enrollment_id        BIGINT        NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED | GENERATED
    -- 1 while the request is active (PENDING / APPROVED / GENERATED), NULL once REJECTED: with the
    -- unique key below the database itself refuses a second active request for the same course.
    active_slot          INT           NULL,
    student_name         VARCHAR(150)  NOT NULL,                     -- registered name at request time
    student_code         VARCHAR(30),                                -- the student's Student ID
    course_name          VARCHAR(200)  NOT NULL,
    requested_at         TIMESTAMP(6)  NOT NULL,
    reviewed_by_user_id  BIGINT,
    reviewed_at          TIMESTAMP(6)  NULL,
    rejection_reason     VARCHAR(500),
    certificate_id       BIGINT,
    generated_at         TIMESTAMP(6)  NULL,
    created_at           TIMESTAMP(6)  NOT NULL,
    updated_at           TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_cert_requests_active   UNIQUE (student_user_id, course_id, active_slot),
    CONSTRAINT fk_cert_requests_student  FOREIGN KEY (student_user_id)     REFERENCES users (id),
    CONSTRAINT fk_cert_requests_course   FOREIGN KEY (course_id)           REFERENCES courses (id),
    CONSTRAINT fk_cert_requests_enroll   FOREIGN KEY (enrollment_id)       REFERENCES enrollments (id),
    CONSTRAINT fk_cert_requests_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_cert_requests_cert     FOREIGN KEY (certificate_id)      REFERENCES certificates (id)
);
CREATE INDEX idx_cert_requests_status  ON certificate_requests (status, requested_at);
CREATE INDEX idx_cert_requests_student ON certificate_requests (student_user_id, course_id);

-- ---- Certificates: detach from exams and uploaded designs --------------------------------------
ALTER TABLE certificates DROP FOREIGN KEY fk_certificates_exam;
ALTER TABLE certificates DROP FOREIGN KEY fk_certificates_attempt;
ALTER TABLE certificates DROP FOREIGN KEY fk_certificates_application;
ALTER TABLE certificates DROP FOREIGN KEY fk_certificates_template;
ALTER TABLE certificates DROP INDEX uk_certificates_attempt;
ALTER TABLE certificates DROP COLUMN exam_id;
ALTER TABLE certificates DROP COLUMN attempt_id;
ALTER TABLE certificates DROP COLUMN application_id;
ALTER TABLE certificates DROP COLUMN template_id;
ALTER TABLE certificates DROP COLUMN exam_title;
ALTER TABLE certificates DROP COLUMN score;
ALTER TABLE certificates DROP COLUMN total_marks;
ALTER TABLE certificates DROP COLUMN passing_marks;

-- Printed course period ("January to April 2026"), the course start used for it, and the random
-- code behind the certificate's QR verification link (never the numeric id, so it cannot be guessed).
ALTER TABLE certificates ADD COLUMN course_started_at TIMESTAMP(6) NULL;
ALTER TABLE certificates ADD COLUMN course_period VARCHAR(80) NULL;
ALTER TABLE certificates ADD COLUMN verification_code VARCHAR(40) NULL;
CREATE UNIQUE INDEX uk_certificates_verification ON certificates (verification_code);

-- ---- Remove the examination system (child tables first) ----------------------------------------
DROP TABLE IF EXISTS exam_answers;
DROP TABLE IF EXISTS exam_attempts;
DROP TABLE IF EXISTS exam_schedules;
DROP TABLE IF EXISTS exam_applications;
DROP TABLE IF EXISTS exam_questions;
DROP TABLE IF EXISTS exams;
DROP TABLE IF EXISTS certificate_templates;
