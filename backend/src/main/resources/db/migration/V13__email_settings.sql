-- =============================================================================
-- V13: Admin-managed SMTP configuration (Main Admin > Email Settings)
-- One row only. The SMTP password is stored AES-GCM encrypted (same SecretCrypto
-- as payment_gateway_config) and is never returned by any API.
-- =============================================================================

CREATE TABLE email_settings (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_name            VARCHAR(100)  NOT NULL,
    sender_email           VARCHAR(190)  NOT NULL,
    smtp_host              VARCHAR(255)  NOT NULL,
    smtp_port              INT           NOT NULL,
    smtp_username          VARCHAR(190),
    smtp_password_enc      VARCHAR(512),
    security_mode          VARCHAR(20)   NOT NULL DEFAULT 'STARTTLS',
    enabled                BOOLEAN       NOT NULL DEFAULT FALSE,
    test_recipient         VARCHAR(190),
    last_tested_at         TIMESTAMP(6)  NULL,
    last_test_ok           BOOLEAN       NULL,
    created_by_user_id     BIGINT,
    updated_by_user_id     BIGINT,
    created_at             TIMESTAMP(6)  NOT NULL,
    updated_at             TIMESTAMP(6)  NOT NULL,
    CONSTRAINT fk_email_settings_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_email_settings_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES users (id)
);
