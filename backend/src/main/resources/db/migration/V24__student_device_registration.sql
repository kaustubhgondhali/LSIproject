-- V24: Student Account-to-Device Binding
-- Enforces ONE STUDENT ACCOUNT = ONE REGISTERED COMPUTER/DEVICE.
-- Stores cryptographically generated device identity, public key, and secret token hash.

CREATE TABLE student_devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_id VARCHAR(64) NOT NULL UNIQUE,
    device_name VARCHAR(100) NOT NULL,
    device_platform VARCHAR(100),
    device_public_key TEXT,
    device_secret_hash VARCHAR(100) NOT NULL,
    device_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    reset_required BOOLEAN NOT NULL DEFAULT FALSE,
    registered_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    last_verified_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_devices_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_student_devices_user UNIQUE (user_id)
);

CREATE INDEX idx_student_devices_user_status ON student_devices (user_id, device_status);
CREATE INDEX idx_student_devices_device_id ON student_devices (device_id);

CREATE TABLE device_verification_otps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    otp_hash VARCHAR(100) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    target_device_id VARCHAR(64),
    device_name VARCHAR(100),
    device_platform VARCHAR(100),
    attempts INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_otps_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_device_otps_user_purpose ON device_verification_otps (user_id, purpose, expires_at);

ALTER TABLE user_sessions ADD COLUMN device_id VARCHAR(64);
CREATE INDEX idx_user_sessions_device_id ON user_sessions (device_id);

