-- =============================================================================
-- V15: Email Settings — detected/selected provider and the last successful SMTP connection test
-- =============================================================================

ALTER TABLE email_settings ADD COLUMN provider VARCHAR(40) NULL;
ALTER TABLE email_settings ADD COLUMN connection_verified_at TIMESTAMP(6) NULL;
