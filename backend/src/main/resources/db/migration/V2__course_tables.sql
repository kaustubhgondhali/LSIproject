-- =============================================================================
-- V2: Courses, modules, lessons, teacher assignments
-- =============================================================================

CREATE TABLE courses (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_code        VARCHAR(40)    NOT NULL,
    course_name        VARCHAR(200)   NOT NULL,
    short_description  VARCHAR(500),
    description        TEXT,
    price              DECIMAL(10,2)  NOT NULL,
    discounted_price   DECIMAL(10,2),
    duration           VARCHAR(100),
    thumbnail_path     VARCHAR(255),
    status             VARCHAR(20)    NOT NULL,
    display_order      INT            NOT NULL DEFAULT 0,
    created_at         TIMESTAMP(6)   NOT NULL,
    updated_at         TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_courses_code UNIQUE (course_code)
);
CREATE INDEX idx_courses_status ON courses (status);

CREATE TABLE course_modules (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id      BIGINT        NOT NULL,
    module_name    VARCHAR(200)  NOT NULL,
    description    TEXT,
    display_order  INT           NOT NULL DEFAULT 0,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP(6)  NOT NULL,
    updated_at     TIMESTAMP(6)  NOT NULL,
    CONSTRAINT fk_course_modules_course FOREIGN KEY (course_id) REFERENCES courses (id)
);
CREATE INDEX idx_course_modules_course ON course_modules (course_id, display_order);

CREATE TABLE lessons (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    module_id                BIGINT        NOT NULL,
    lesson_title             VARCHAR(200)  NOT NULL,
    description              TEXT,
    video_path               VARCHAR(255),
    video_duration_seconds   INT,
    material_path            VARCHAR(255),
    material_original_name   VARCHAR(255),
    display_order            INT           NOT NULL DEFAULT 0,
    active                   BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMP(6)  NOT NULL,
    updated_at               TIMESTAMP(6)  NOT NULL,
    CONSTRAINT fk_lessons_module FOREIGN KEY (module_id) REFERENCES course_modules (id)
);
CREATE INDEX idx_lessons_module ON lessons (module_id, display_order);

CREATE TABLE teacher_course_assignments (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_user_id      BIGINT        NOT NULL,
    course_id            BIGINT        NOT NULL,
    assigned_by_user_id  BIGINT,
    created_at           TIMESTAMP(6)  NOT NULL,
    updated_at           TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_teacher_course UNIQUE (teacher_user_id, course_id),
    CONSTRAINT fk_tca_teacher     FOREIGN KEY (teacher_user_id)     REFERENCES users (id),
    CONSTRAINT fk_tca_course      FOREIGN KEY (course_id)           REFERENCES courses (id),
    CONSTRAINT fk_tca_assigned_by FOREIGN KEY (assigned_by_user_id) REFERENCES users (id)
);
