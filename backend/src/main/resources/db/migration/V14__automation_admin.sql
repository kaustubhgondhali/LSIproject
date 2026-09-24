-- =============================================================================
-- V14: Automation Admin — academy office records (students, fees, receipts, attendance)
-- A separate "acad_" family: these are the classroom-academy ledgers that used to live in the
-- LORD SAI ACADEMY workbook. They are independent of the online LMS tables (users, courses,
-- enrollments, payments) which are left untouched.
-- =============================================================================

CREATE TABLE acad_batches (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(50)   NOT NULL,
    schedule       VARCHAR(100)  NULL,
    start_date     DATE          NULL,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    display_order  INT           NOT NULL DEFAULT 0,
    created_at     TIMESTAMP(6)  NOT NULL,
    updated_at     TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_acad_batches_name UNIQUE (name)
);

CREATE TABLE acad_courses (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(150)  NOT NULL,
    default_fee    DECIMAL(10,2) NOT NULL,
    duration       VARCHAR(100)  NULL,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    display_order  INT           NOT NULL DEFAULT 0,
    created_at     TIMESTAMP(6)  NOT NULL,
    updated_at     TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_acad_courses_name UNIQUE (name)
);

-- Reusable dropdown values (payment modes, installment types, session types).
CREATE TABLE acad_master_data (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    category       VARCHAR(40)   NOT NULL,
    code           VARCHAR(40)   NOT NULL,
    label          VARCHAR(100)  NOT NULL,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    display_order  INT           NOT NULL DEFAULT 0,
    created_at     TIMESTAMP(6)  NOT NULL,
    updated_at     TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_acad_master_data UNIQUE (category, code)
);

CREATE TABLE acad_students (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id          VARCHAR(30)   NOT NULL,
    full_name           VARCHAR(150)  NOT NULL,
    name_normalized     VARCHAR(150)  NOT NULL,
    admission_date      DATE          NULL,
    admission_date_raw  VARCHAR(40)   NULL,
    mobile              VARCHAR(15)   NULL,
    email               VARCHAR(190)  NULL,
    address             VARCHAR(500)  NULL,
    batch_id            BIGINT        NULL,
    course_id           BIGINT        NULL,
    course_fee          DECIMAL(10,2) NOT NULL DEFAULT 0,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    review_note         VARCHAR(500)  NULL,
    source              VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    created_by_user_id  BIGINT        NULL,
    updated_by_user_id  BIGINT        NULL,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_acad_students_student_id UNIQUE (student_id),
    CONSTRAINT fk_acad_students_batch FOREIGN KEY (batch_id) REFERENCES acad_batches (id),
    CONSTRAINT fk_acad_students_course FOREIGN KEY (course_id) REFERENCES acad_courses (id),
    CONSTRAINT fk_acad_students_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_acad_students_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES users (id)
);
CREATE INDEX idx_acad_students_name ON acad_students (name_normalized);
CREATE INDEX idx_acad_students_mobile ON acad_students (mobile);
CREATE INDEX idx_acad_students_email ON acad_students (email);
CREATE INDEX idx_acad_students_batch ON acad_students (batch_id);
CREATE INDEX idx_acad_students_status ON acad_students (status);

CREATE TABLE acad_payments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_no          VARCHAR(30)   NOT NULL,
    student_id          BIGINT        NOT NULL,
    installment_no      INT           NOT NULL,
    payment_date        DATE          NULL,
    amount              DECIMAL(10,2) NOT NULL,
    payment_mode        VARCHAR(40)   NOT NULL,
    reference_no        VARCHAR(100)  NULL,
    notes               VARCHAR(500)  NULL,
    review_note         VARCHAR(500)  NULL,
    source              VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    recorded_by_user_id BIGINT        NULL,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_acad_payments_no UNIQUE (payment_no),
    CONSTRAINT uk_acad_payments_installment UNIQUE (student_id, installment_no),
    CONSTRAINT fk_acad_payments_student FOREIGN KEY (student_id) REFERENCES acad_students (id),
    CONSTRAINT fk_acad_payments_recorded_by FOREIGN KEY (recorded_by_user_id) REFERENCES users (id)
);
CREATE INDEX idx_acad_payments_date ON acad_payments (payment_date);

-- One receipt per payment. Snapshot columns keep what was printed even if the student record changes later.
CREATE TABLE acad_receipts (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    receipt_no          VARCHAR(30)   NOT NULL,
    payment_id          BIGINT        NOT NULL,
    student_id          BIGINT        NOT NULL,
    student_code        VARCHAR(30)   NOT NULL,
    student_name        VARCHAR(150)  NOT NULL,
    course_name         VARCHAR(150)  NULL,
    batch_name          VARCHAR(50)   NULL,
    batch_schedule      VARCHAR(100)  NULL,
    total_fee           DECIMAL(10,2) NOT NULL,
    amount_paid         DECIMAL(10,2) NOT NULL,
    total_paid          DECIMAL(10,2) NOT NULL,
    balance             DECIMAL(10,2) NOT NULL,
    payment_date        DATE          NULL,
    payment_mode        VARCHAR(40)   NOT NULL,
    reference_no        VARCHAR(100)  NULL,
    amount_in_words     VARCHAR(200)  NOT NULL,
    issued_at           TIMESTAMP(6)  NOT NULL,
    issued_by_user_id   BIGINT        NULL,
    emailed_at          TIMESTAMP(6)  NULL,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_acad_receipts_no UNIQUE (receipt_no),
    CONSTRAINT uk_acad_receipts_payment UNIQUE (payment_id),
    CONSTRAINT fk_acad_receipts_payment FOREIGN KEY (payment_id) REFERENCES acad_payments (id),
    CONSTRAINT fk_acad_receipts_student FOREIGN KEY (student_id) REFERENCES acad_students (id),
    CONSTRAINT fk_acad_receipts_issued_by FOREIGN KEY (issued_by_user_id) REFERENCES users (id)
);

CREATE TABLE acad_attendance_sessions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_date        DATE          NOT NULL,
    batch_id            BIGINT        NOT NULL,
    course_id           BIGINT        NULL,
    session_type        VARCHAR(40)   NOT NULL DEFAULT 'CLASS',
    instructor          VARCHAR(100)  NULL,
    notes               VARCHAR(500)  NULL,
    created_by_user_id  BIGINT        NULL,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_acad_sessions UNIQUE (batch_id, session_date, session_type),
    CONSTRAINT fk_acad_sessions_batch FOREIGN KEY (batch_id) REFERENCES acad_batches (id),
    CONSTRAINT fk_acad_sessions_course FOREIGN KEY (course_id) REFERENCES acad_courses (id),
    CONSTRAINT fk_acad_sessions_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id)
);
CREATE INDEX idx_acad_sessions_date ON acad_attendance_sessions (session_date);

CREATE TABLE acad_attendance_records (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT        NOT NULL,
    student_id          BIGINT        NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    marked_by_user_id   BIGINT        NULL,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_acad_attendance UNIQUE (session_id, student_id),
    CONSTRAINT fk_acad_attendance_session FOREIGN KEY (session_id) REFERENCES acad_attendance_sessions (id),
    CONSTRAINT fk_acad_attendance_student FOREIGN KEY (student_id) REFERENCES acad_students (id),
    CONSTRAINT fk_acad_attendance_marked_by FOREIGN KEY (marked_by_user_id) REFERENCES users (id)
);
CREATE INDEX idx_acad_attendance_student ON acad_attendance_records (student_id);

-- Row-locked counters for LSA/YYYY/#### student IDs, LSR/YYYY/#### receipts and PAY/YYYY/##### payments.
CREATE TABLE acad_sequences (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    kind         VARCHAR(20) NOT NULL,
    seq_year     INT         NOT NULL,
    last_number  INT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_acad_sequences UNIQUE (kind, seq_year)
);

-- ---- Master data from the academy workbook ---------------------------------------------------
INSERT INTO acad_batches (name, schedule, start_date, active, display_order, created_at, updated_at) VALUES
    ('1ST', 'Sunday',         '2026-01-04', TRUE, 1, NOW(6), NOW(6)),
    ('2ND', 'Monday-Tuesday', '2026-03-01', TRUE, 2, NOW(6), NOW(6)),
    ('3RD', 'Sunday',         '2026-07-12', TRUE, 3, NOW(6), NOW(6));

INSERT INTO acad_courses (name, default_fee, duration, active, display_order, created_at, updated_at) VALUES
    ('Share Market Basic to Advance', 10000.00, NULL, TRUE, 1, NOW(6), NOW(6));

INSERT INTO acad_master_data (category, code, label, active, display_order, created_at, updated_at) VALUES
    ('PAYMENT_MODE', 'CASH',          'Cash',          TRUE, 1, NOW(6), NOW(6)),
    ('PAYMENT_MODE', 'UPI',           'UPI',           TRUE, 2, NOW(6), NOW(6)),
    ('PAYMENT_MODE', 'BANK_TRANSFER', 'Bank Transfer', TRUE, 3, NOW(6), NOW(6)),
    ('PAYMENT_MODE', 'CARD',          'Card',          TRUE, 4, NOW(6), NOW(6)),
    ('PAYMENT_MODE', 'CHEQUE',        'Cheque',        TRUE, 5, NOW(6), NOW(6)),
    ('PAYMENT_MODE', 'DISCOUNT',      'Discount',      TRUE, 6, NOW(6), NOW(6)),
    ('PAYMENT_MODE', 'OTHER',         'Other',         TRUE, 7, NOW(6), NOW(6)),
    ('INSTALLMENT',  '1',  '1st Installment', TRUE, 1, NOW(6), NOW(6)),
    ('INSTALLMENT',  '2',  '2nd Installment', TRUE, 2, NOW(6), NOW(6)),
    ('INSTALLMENT',  '3',  '3rd Installment', TRUE, 3, NOW(6), NOW(6)),
    ('INSTALLMENT',  '4',  '4th Installment', TRUE, 4, NOW(6), NOW(6)),
    ('INSTALLMENT',  '5',  '5th Installment', TRUE, 5, NOW(6), NOW(6)),
    ('SESSION_TYPE', 'CLASS',       'Classroom Session',      TRUE, 1, NOW(6), NOW(6)),
    ('SESSION_TYPE', 'LIVE_MARKET', 'Live Market Observation', TRUE, 2, NOW(6), NOW(6)),
    ('SESSION_TYPE', 'REVISION',    'Revision',               TRUE, 3, NOW(6), NOW(6)),
    ('SESSION_TYPE', 'DOUBT',       'Doubt Session',          TRUE, 4, NOW(6), NOW(6));
