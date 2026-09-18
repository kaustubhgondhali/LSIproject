-- =============================================================================
-- V1: Identity, sessions, tokens, sites, audit log
-- =============================================================================

CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name       VARCHAR(150)  NOT NULL,
    email           VARCHAR(190)  NOT NULL,
    mobile          VARCHAR(20),
    password_hash   VARCHAR(100),
    role            VARCHAR(20)   NOT NULL,
    account_status  VARCHAR(20)   NOT NULL,
    last_login_at   TIMESTAMP(6)  NULL,
    token_version   INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(6)  NOT NULL,
    updated_at      TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);
CREATE INDEX idx_users_role ON users (role);

CREATE TABLE student_profiles (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT        NOT NULL,
    student_id          VARCHAR(20)   NOT NULL,
    batch               VARCHAR(100),
    location            VARCHAR(150),
    profile_photo_path  VARCHAR(255),
    registration_date   DATE          NOT NULL,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_student_profiles_user UNIQUE (user_id),
    CONSTRAINT uk_student_profiles_student_id UNIQUE (student_id),
    CONSTRAINT fk_student_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE teacher_profiles (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT        NOT NULL,
    teacher_code  VARCHAR(20)   NOT NULL,
    role_title    VARCHAR(200),
    bio           TEXT,
    created_at    TIMESTAMP(6)  NOT NULL,
    updated_at    TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_teacher_profiles_user UNIQUE (user_id),
    CONSTRAINT uk_teacher_profiles_code UNIQUE (teacher_code),
    CONSTRAINT fk_teacher_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE user_sessions (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT        NOT NULL,
    token_id          VARCHAR(64)   NOT NULL,
    device_info       VARCHAR(300),
    ip_address        VARCHAR(45),
    last_activity_at  TIMESTAMP(6)  NOT NULL,
    expires_at        TIMESTAMP(6)  NOT NULL,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    revoke_reason     VARCHAR(30),
    revoked_at        TIMESTAMP(6)  NULL,
    created_at        TIMESTAMP(6)  NOT NULL,
    updated_at        TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_user_sessions_token UNIQUE (token_id),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_user_sessions_user_active ON user_sessions (user_id, active);

CREATE TABLE account_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    token_hash  VARCHAR(64)   NOT NULL,
    purpose     VARCHAR(20)   NOT NULL,
    expires_at  TIMESTAMP(6)  NOT NULL,
    used_at     TIMESTAMP(6)  NULL,
    created_at  TIMESTAMP(6)  NOT NULL,
    updated_at  TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_account_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_account_tokens_user ON account_tokens (user_id);

CREATE TABLE sites (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_code   VARCHAR(30)   NOT NULL,
    site_name   VARCHAR(150)  NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP(6)  NOT NULL,
    updated_at  TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_sites_code UNIQUE (site_code)
);

CREATE TABLE audit_logs (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id  BIGINT,
    action         VARCHAR(60)    NOT NULL,
    entity_type    VARCHAR(60),
    entity_id      BIGINT,
    description    VARCHAR(1000)  NOT NULL,
    ip_address     VARCHAR(45),
    created_at     TIMESTAMP(6)   NOT NULL,
    updated_at     TIMESTAMP(6)   NOT NULL,
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
);
CREATE INDEX idx_audit_logs_actor   ON audit_logs (actor_user_id);
CREATE INDEX idx_audit_logs_entity  ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_created ON audit_logs (created_at);

CREATE TABLE student_id_sequence (
    year_value   INT NOT NULL PRIMARY KEY,
    last_number  INT NOT NULL DEFAULT 0
);
