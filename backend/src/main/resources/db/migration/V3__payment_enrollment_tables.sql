-- =============================================================================
-- V3: Payments, enrollments, lesson progress
-- =============================================================================

CREATE TABLE payments (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_ref            VARCHAR(40)    NOT NULL,
    razorpay_order_id    VARCHAR(64)    NOT NULL,
    razorpay_payment_id  VARCHAR(64),
    razorpay_signature   VARCHAR(255),
    user_id              BIGINT,
    customer_name        VARCHAR(150)   NOT NULL,
    customer_email       VARCHAR(190)   NOT NULL,
    customer_mobile      VARCHAR(20)    NOT NULL,
    course_id            BIGINT         NOT NULL,
    amount               DECIMAL(10,2)  NOT NULL,
    currency             VARCHAR(3)     NOT NULL DEFAULT 'INR',
    status               VARCHAR(20)    NOT NULL,
    payment_method       VARCHAR(40),
    failure_reason       VARCHAR(500),
    verified_at          TIMESTAMP(6)   NULL,
    processed_at         TIMESTAMP(6)   NULL,
    created_at           TIMESTAMP(6)   NOT NULL,
    updated_at           TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_payments_order_ref UNIQUE (order_ref),
    CONSTRAINT uk_payments_rzp_order UNIQUE (razorpay_order_id),
    CONSTRAINT fk_payments_user   FOREIGN KEY (user_id)   REFERENCES users (id),
    CONSTRAINT fk_payments_course FOREIGN KEY (course_id) REFERENCES courses (id)
);
CREATE INDEX idx_payments_rzp_payment ON payments (razorpay_payment_id);
CREATE INDEX idx_payments_status      ON payments (status);
CREATE INDEX idx_payments_email       ON payments (customer_email);

CREATE TABLE enrollments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_user_id     BIGINT        NOT NULL,
    course_id           BIGINT        NOT NULL,
    payment_id          BIGINT,
    status              VARCHAR(20)   NOT NULL,
    source              VARCHAR(20)   NOT NULL,
    enrolled_at         TIMESTAMP(6)  NOT NULL,
    expiry_date         DATE,
    created_by_user_id  BIGINT,
    completed_at        TIMESTAMP(6)  NULL,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_enrollment_student_course UNIQUE (student_user_id, course_id),
    CONSTRAINT uk_enrollments_payment UNIQUE (payment_id),
    CONSTRAINT fk_enrollments_student    FOREIGN KEY (student_user_id)    REFERENCES users (id),
    CONSTRAINT fk_enrollments_course     FOREIGN KEY (course_id)          REFERENCES courses (id),
    CONSTRAINT fk_enrollments_payment    FOREIGN KEY (payment_id)         REFERENCES payments (id),
    CONSTRAINT fk_enrollments_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id)
);
CREATE INDEX idx_enrollments_student ON enrollments (student_user_id);
CREATE INDEX idx_enrollments_course  ON enrollments (course_id);
CREATE INDEX idx_enrollments_status  ON enrollments (status);

CREATE TABLE lesson_progress (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_user_id     BIGINT        NOT NULL,
    lesson_id           BIGINT        NOT NULL,
    completed           BOOLEAN       NOT NULL DEFAULT FALSE,
    watched_percentage  INT           NOT NULL DEFAULT 0,
    last_watched_at     TIMESTAMP(6)  NULL,
    completed_at        TIMESTAMP(6)  NULL,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_progress_student_lesson UNIQUE (student_user_id, lesson_id),
    CONSTRAINT fk_lesson_progress_student FOREIGN KEY (student_user_id) REFERENCES users (id),
    CONSTRAINT fk_lesson_progress_lesson  FOREIGN KEY (lesson_id)       REFERENCES lessons (id)
);
CREATE INDEX idx_lesson_progress_student ON lesson_progress (student_user_id);
