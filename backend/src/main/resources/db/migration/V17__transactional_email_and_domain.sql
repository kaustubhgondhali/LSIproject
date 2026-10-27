-- =============================================================================
-- V17: Transactional Email, Verified Sending Domain & Deliverability
--
-- Adds:
--   - reply_to: custom reply-to address for outbound transactional emails
--   - sending_domain: verified organization sending domain (e.g. lordsai.com)
--   - dkim_selector: DKIM selector used for DNS diagnostics (e.g. default, s1, resend)
-- =============================================================================

ALTER TABLE email_settings ADD COLUMN reply_to VARCHAR(190) NULL;
ALTER TABLE email_settings ADD COLUMN sending_domain VARCHAR(190) NULL;
ALTER TABLE email_settings ADD COLUMN dkim_selector VARCHAR(100) NULL;
