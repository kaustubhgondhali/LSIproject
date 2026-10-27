-- =============================================================================
-- V7: Success Stories (admin-managed) + retirement of the Teacher application role
-- =============================================================================

-- ---- Retire the Teacher role ------------------------------------------------
-- The platform has exactly two application roles: ADMIN and STUDENT. Any legacy
-- TEACHER accounts are disabled and their sessions revoked so they can no longer
-- sign in. Rows are kept for audit history; nothing student- or payment-related
-- is touched.
UPDATE user_sessions
SET active = FALSE, revoke_reason = 'ACCOUNT_DISABLED', revoked_at = CURRENT_TIMESTAMP
WHERE active = TRUE AND user_id IN (SELECT id FROM users WHERE role = 'TEACHER');

UPDATE users SET account_status = 'DISABLED', updated_at = CURRENT_TIMESTAMP
WHERE role = 'TEACHER' AND account_status <> 'DISABLED';

-- Teacher-only tables are no longer mapped by the application. They were introduced in
-- V1/V2 and are dropped here (child table first) so the schema matches the code.
DROP TABLE IF EXISTS teacher_course_assignments;
DROP TABLE IF EXISTS teacher_profiles;

-- ---- Success Stories --------------------------------------------------------
-- "TEACHERS" below is a CONTENT CATEGORY for the public Success Stories page,
-- not an application role.
CREATE TABLE success_stories (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    category            VARCHAR(20)   NOT NULL,              -- STUDENTS | TEACHERS | PARENTS
    title               VARCHAR(200)  NOT NULL,
    person_name         VARCHAR(150),
    location            VARCHAR(150),
    description         TEXT,
    thumbnail_path      VARCHAR(255),                        -- images/<uuid>.jpg (uploaded) or img/... (static)
    video_path          VARCHAR(255),                        -- videos/<uuid>.mp4 uploaded through the admin panel
    video_original_name VARCHAR(255),
    video_url           VARCHAR(500),                        -- optional external embed URL (YouTube) instead of an upload
    published           BOOLEAN       NOT NULL DEFAULT FALSE,
    display_order       INT           NOT NULL DEFAULT 0,
    created_by_user_id  BIGINT,
    updated_by_user_id  BIGINT,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    CONSTRAINT fk_success_stories_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_success_stories_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES users (id)
);

CREATE INDEX idx_success_stories_public ON success_stories (published, category, display_order);

-- Seed the six student video stories that stories.html has always shown, so the public
-- page looks identical after this migration and the admin can edit/replace them.
INSERT INTO success_stories (category, title, person_name, location, description, thumbnail_path, video_url,
                             published, display_order, created_at, updated_at) VALUES
    ('STUDENTS', 'Rahul Sharma — Learning Journey',    'Rahul Sharma',    'From Pune, Maharashtra',       NULL, 'img/students/video_rahul.jpg',   'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ', TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('STUDENTS', 'Pooja Deshmukh — Learning Journey',  'Pooja Deshmukh',  'From Uran, Navi Mumbai',       NULL, 'img/students/video_pooja.jpg',   'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ', TRUE, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('STUDENTS', 'Anand Kulkarni — Learning Journey',  'Anand Kulkarni',  'From Panvel, Maharashtra',     NULL, 'img/students/video_anand.jpg',   'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ', TRUE, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('STUDENTS', 'Swapnil Patil — Learning Journey',   'Swapnil Patil',   'From Alibaug, Raigad',         NULL, 'img/students/video_swapnil.jpg', 'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ', TRUE, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('STUDENTS', 'Sneha Shinde — Learning Journey',    'Sneha Shinde',    'From Navi Mumbai, Maharashtra', NULL, 'img/students/video_sneha.jpg',  'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ', TRUE, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('STUDENTS', 'Amit Kadam — Learning Journey',      'Amit Kadam',      'From Thane, Mumbai',           NULL, 'img/students/video_amit.jpg',    'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ', TRUE, 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
