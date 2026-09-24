-- =============================================================================
-- V20: Central WhatsApp configuration managed from Main Admin -> Settings
-- ONE row. The access token is stored encrypted (SecretCrypto, like Razorpay's key secret)
-- and is never returned by any API. Every WhatsApp send in the system (invoice on WhatsApp,
-- Automation Admin email/WhatsApp automation) resolves this row first and falls back to the
-- WHATSAPP_* environment variables only when no enabled row exists.
-- =============================================================================

CREATE TABLE whatsapp_config (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider               VARCHAR(30)   NOT NULL,
    access_token_enc       VARCHAR(2048) NOT NULL,
    phone_number_id        VARCHAR(50)   NOT NULL,
    business_account_id    VARCHAR(50)   NULL,
    api_url                VARCHAR(200)  NULL,
    api_version            VARCHAR(20)   NULL,
    display_phone_number   VARCHAR(40)   NULL,           -- as reported by the provider when validated
    enabled                BOOLEAN       NOT NULL DEFAULT TRUE,
    validated_at           TIMESTAMP(6)  NULL,
    created_by_user_id     BIGINT,
    updated_by_user_id     BIGINT,
    created_at             TIMESTAMP(6)  NOT NULL,
    updated_at             TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_whatsapp_config_provider UNIQUE (provider),
    CONSTRAINT fk_wac_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_wac_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES users (id)
);
