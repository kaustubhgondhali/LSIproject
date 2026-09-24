-- =============================================================================
-- V6: Admin-managed payment gateway configuration + payment mode on transactions
-- =============================================================================

CREATE TABLE payment_gateway_config (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider               VARCHAR(30)   NOT NULL,
    key_id                 VARCHAR(100)  NOT NULL,
    key_secret_enc         VARCHAR(512)  NOT NULL,
    webhook_secret_enc     VARCHAR(512),
    currency               VARCHAR(3)    NOT NULL DEFAULT 'INR',
    enabled                BOOLEAN       NOT NULL DEFAULT TRUE,
    validated_at           TIMESTAMP(6)  NULL,
    created_by_user_id     BIGINT,
    updated_by_user_id     BIGINT,
    created_at             TIMESTAMP(6)  NOT NULL,
    updated_at             TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_payment_gateway_provider UNIQUE (provider),
    CONSTRAINT fk_pgc_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_pgc_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES users (id)
);

-- Existing rows were all real Razorpay transactions.
ALTER TABLE payments ADD COLUMN payment_mode VARCHAR(20) NOT NULL DEFAULT 'RAZORPAY';
