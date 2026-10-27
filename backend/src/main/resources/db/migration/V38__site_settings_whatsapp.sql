-- V38: Website settings the Main Admin can change without a release. First setting: the website
-- WhatsApp number — every "Chat on WhatsApp" link and the enquiry forms on the public website open
-- a chat with this number (Main Admin > WhatsApp Settings). Additive only.

CREATE TABLE site_settings (
    setting_key        VARCHAR(80)  NOT NULL,
    setting_value      VARCHAR(500) NOT NULL,
    updated_at         TIMESTAMP(6) NULL,
    updated_by_user_id BIGINT       NULL,
    CONSTRAINT pk_site_settings PRIMARY KEY (setting_key),
    CONSTRAINT fk_site_settings_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES users (id)
);

INSERT INTO site_settings (setting_key, setting_value, updated_at)
VALUES ('WEBSITE_WHATSAPP_NUMBER', '919987049070', CURRENT_TIMESTAMP);
