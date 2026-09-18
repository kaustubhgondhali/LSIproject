-- =============================================================================
-- V16: Gmail via Google OAuth 2.0 (Main Admin > Email Settings)
--
-- Adds an authentication mode to the single email_settings row. 'SMTP' keeps the
-- existing behaviour exactly as it was; 'GMAIL_OAUTH' sends through the Gmail API
-- using a refresh token the admin grants through Google's consent screen.
--
-- The existing SMTP columns are deliberately left in place and unchanged: switching
-- to OAuth must not destroy a working SMTP configuration, and SMTP stays available
-- as a fallback provider.
--
-- google_refresh_token_enc holds the refresh token AES-GCM encrypted with the same
-- SecretCrypto used for smtp_password_enc and the payment gateway secrets. Access
-- tokens are short-lived and are never persisted at all.
-- =============================================================================

ALTER TABLE email_settings ADD COLUMN auth_mode VARCHAR(20) NOT NULL DEFAULT 'SMTP';
ALTER TABLE email_settings ADD COLUMN google_email VARCHAR(190) NULL;
ALTER TABLE email_settings ADD COLUMN google_refresh_token_enc VARCHAR(1024) NULL;
ALTER TABLE email_settings ADD COLUMN google_scope VARCHAR(255) NULL;
ALTER TABLE email_settings ADD COLUMN google_connected_at TIMESTAMP(6) NULL;
ALTER TABLE email_settings ADD COLUMN google_connected_by_user_id BIGINT NULL;

ALTER TABLE email_settings
    ADD CONSTRAINT fk_email_settings_google_connected_by
    FOREIGN KEY (google_connected_by_user_id) REFERENCES users (id);
