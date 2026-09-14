-- =============================================================================
-- V4: Trade journal, doubt desk, admin-managed site content
-- =============================================================================

CREATE TABLE trade_journal (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_user_id      BIGINT         NOT NULL,
    trade_date           DATE           NOT NULL,
    symbol               VARCHAR(40)    NOT NULL,
    trade_type           VARCHAR(20)    NOT NULL,
    entry_price          DECIMAL(12,2)  NOT NULL,
    stop_loss            DECIMAL(12,2),
    target               DECIMAL(12,2),
    risk_reward          VARCHAR(20),
    notes                TEXT,
    review_status        VARCHAR(20)    NOT NULL,
    mentor_comment       TEXT,
    reviewed_by_user_id  BIGINT,
    reviewed_at          TIMESTAMP(6)   NULL,
    created_at           TIMESTAMP(6)   NOT NULL,
    updated_at           TIMESTAMP(6)   NOT NULL,
    CONSTRAINT fk_trade_journal_student  FOREIGN KEY (student_user_id)     REFERENCES users (id),
    CONSTRAINT fk_trade_journal_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id)
);
CREATE INDEX idx_trade_journal_student ON trade_journal (student_user_id, trade_date);

CREATE TABLE doubts (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_user_id  BIGINT        NOT NULL,
    course_id        BIGINT,
    lesson_id        BIGINT,
    title            VARCHAR(200)  NOT NULL,
    description      TEXT          NOT NULL,
    attachment_path  VARCHAR(255),
    status           VARCHAR(20)   NOT NULL,
    resolved_at      TIMESTAMP(6)  NULL,
    created_at       TIMESTAMP(6)  NOT NULL,
    updated_at       TIMESTAMP(6)  NOT NULL,
    CONSTRAINT fk_doubts_student FOREIGN KEY (student_user_id) REFERENCES users (id),
    CONSTRAINT fk_doubts_course  FOREIGN KEY (course_id)       REFERENCES courses (id),
    CONSTRAINT fk_doubts_lesson  FOREIGN KEY (lesson_id)       REFERENCES lessons (id)
);
CREATE INDEX idx_doubts_student ON doubts (student_user_id, status);
CREATE INDEX idx_doubts_status  ON doubts (status);

CREATE TABLE doubt_replies (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    doubt_id        BIGINT        NOT NULL,
    author_user_id  BIGINT        NOT NULL,
    message         TEXT          NOT NULL,
    created_at      TIMESTAMP(6)  NOT NULL,
    updated_at      TIMESTAMP(6)  NOT NULL,
    CONSTRAINT fk_doubt_replies_doubt  FOREIGN KEY (doubt_id)       REFERENCES doubts (id),
    CONSTRAINT fk_doubt_replies_author FOREIGN KEY (author_user_id) REFERENCES users (id)
);
CREATE INDEX idx_doubt_replies_doubt ON doubt_replies (doubt_id, created_at);

CREATE TABLE site_content (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_id             BIGINT        NOT NULL,
    content_key         VARCHAR(150)  NOT NULL,
    label               VARCHAR(200)  NOT NULL,
    content_value       TEXT,
    content_type        VARCHAR(20)   NOT NULL DEFAULT 'TEXT',
    updated_by_user_id  BIGINT,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_site_content_key UNIQUE (site_id, content_key),
    CONSTRAINT fk_site_content_site       FOREIGN KEY (site_id)            REFERENCES sites (id),
    CONSTRAINT fk_site_content_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES users (id)
);
