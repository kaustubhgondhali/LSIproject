-- =============================================================================
-- V18: Ebooks, ebook entitlements, centralized invoices, communication logs
--
-- Extends the existing purchase architecture instead of duplicating it:
--   * payments      — becomes the single "purchase" record for every digital product. A new
--                     product_type column (COURSE | EBOOK) plus an optional ebook_id let the same
--                     Razorpay order / verify / webhook pipeline sell ebooks. course_id becomes
--                     optional because an ebook payment has no course. Every existing row keeps
--                     product_type = 'COURSE' and its course_id, so nothing changes for them.
--   * enrollments   — unchanged; remains the course entitlement.
--   * ebook_entitlements — the ebook counterpart of enrollments (unique per student + ebook).
--   * invoices      — one invoice per successful payment (unique payment_id), numbered by the
--                     backend from invoice_sequence as LSI-INV-YYYY-NNNNNN. Snapshot columns keep
--                     what was printed even if the student or product changes later.
--   * communication_logs — delivery history for every email / WhatsApp message (purchase
--                     confirmations, invoice resends, admin individual & bulk communication),
--                     with status, provider message id and failure reason so admins can retry.
-- =============================================================================

-- ---- Ebooks ------------------------------------------------------------------------------
CREATE TABLE ebooks (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    ebook_code          VARCHAR(40)    NOT NULL,
    title               VARCHAR(200)   NOT NULL,
    author              VARCHAR(150),
    short_description   VARCHAR(500),
    description         TEXT,
    category            VARCHAR(100),
    language            VARCHAR(60),
    price               DECIMAL(10,2)  NOT NULL,
    discounted_price    DECIMAL(10,2),
    cover_image_path    VARCHAR(255),                 -- images/<uuid>.jpg (served publicly like course thumbnails)
    pdf_path            VARCHAR(255),                 -- ebooks/<uuid>.pdf (NEVER served publicly)
    pdf_original_name   VARCHAR(255),
    pdf_size_bytes      BIGINT,
    status              VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',   -- DRAFT | ACTIVE | INACTIVE
    display_order       INT            NOT NULL DEFAULT 0,
    created_by_user_id  BIGINT,
    updated_by_user_id  BIGINT,
    created_at          TIMESTAMP(6)   NOT NULL,
    updated_at          TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_ebooks_code        UNIQUE (ebook_code),
    CONSTRAINT fk_ebooks_created_by  FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_ebooks_updated_by  FOREIGN KEY (updated_by_user_id) REFERENCES users (id)
);
CREATE INDEX idx_ebooks_status ON ebooks (status, display_order);

-- ---- Payments become product-agnostic purchases --------------------------------------------
ALTER TABLE payments ADD COLUMN product_type VARCHAR(20) NOT NULL DEFAULT 'COURSE';
ALTER TABLE payments ADD COLUMN ebook_id BIGINT NULL;
ALTER TABLE payments MODIFY COLUMN course_id BIGINT NULL;
ALTER TABLE payments ADD CONSTRAINT fk_payments_ebook FOREIGN KEY (ebook_id) REFERENCES ebooks (id);
CREATE INDEX idx_payments_product   ON payments (product_type, course_id, ebook_id);
CREATE INDEX idx_payments_verified  ON payments (verified_at);

-- ---- Ebook entitlements ----------------------------------------------------------------------
CREATE TABLE ebook_entitlements (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_user_id     BIGINT        NOT NULL,
    ebook_id            BIGINT        NOT NULL,
    payment_id          BIGINT,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | REVOKED
    source              VARCHAR(20)   NOT NULL,                    -- PAYMENT | ADMIN_MANUAL
    granted_at          TIMESTAMP(6)  NOT NULL,
    created_by_user_id  BIGINT,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_ebook_entitlement_student_ebook UNIQUE (student_user_id, ebook_id),
    CONSTRAINT uk_ebook_entitlements_payment      UNIQUE (payment_id),
    CONSTRAINT fk_ebook_entitlements_student      FOREIGN KEY (student_user_id)    REFERENCES users (id),
    CONSTRAINT fk_ebook_entitlements_ebook        FOREIGN KEY (ebook_id)           REFERENCES ebooks (id),
    CONSTRAINT fk_ebook_entitlements_payment      FOREIGN KEY (payment_id)         REFERENCES payments (id),
    CONSTRAINT fk_ebook_entitlements_created_by   FOREIGN KEY (created_by_user_id) REFERENCES users (id)
);
CREATE INDEX idx_ebook_entitlements_student ON ebook_entitlements (student_user_id);
CREATE INDEX idx_ebook_entitlements_ebook   ON ebook_entitlements (ebook_id);
CREATE INDEX idx_ebook_entitlements_status  ON ebook_entitlements (status);

-- ---- Invoices --------------------------------------------------------------------------------
-- Row-locked yearly counter, same pattern as student_id_sequence (LSI-INV-2026-000001).
CREATE TABLE invoice_sequence (
    year_value   INT NOT NULL PRIMARY KEY,
    last_number  INT NOT NULL DEFAULT 0
);

CREATE TABLE invoices (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_number      VARCHAR(30)    NOT NULL,
    payment_id          BIGINT         NOT NULL,
    student_user_id     BIGINT         NOT NULL,
    product_type        VARCHAR(20)    NOT NULL,          -- COURSE | EBOOK
    course_id           BIGINT,
    ebook_id            BIGINT,
    -- snapshot of what was printed
    student_name        VARCHAR(150)   NOT NULL,
    student_code        VARCHAR(20),                      -- LSI-YYYY-NNNNN
    student_email       VARCHAR(190)   NOT NULL,
    student_phone       VARCHAR(20),
    product_name        VARCHAR(200)   NOT NULL,
    quantity            INT            NOT NULL DEFAULT 1,
    unit_price          DECIMAL(10,2)  NOT NULL,
    subtotal            DECIMAL(10,2)  NOT NULL,
    discount            DECIMAL(10,2)  NOT NULL DEFAULT 0,
    tax                 DECIMAL(10,2)  NOT NULL DEFAULT 0,
    total               DECIMAL(10,2)  NOT NULL,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'INR',
    payment_method      VARCHAR(40),
    transaction_id      VARCHAR(64),
    order_ref           VARCHAR(40),
    payment_status      VARCHAR(20)    NOT NULL,
    invoice_date        DATE           NOT NULL,
    purchase_date       TIMESTAMP(6)   NOT NULL,
    notes               VARCHAR(500),
    pdf_path            VARCHAR(255),                     -- invoices/<uuid>.pdf (never public)
    pdf_generated_at    TIMESTAMP(6)   NULL,
    emailed_at          TIMESTAMP(6)   NULL,
    email_count         INT            NOT NULL DEFAULT 0,
    created_at          TIMESTAMP(6)   NOT NULL,
    updated_at          TIMESTAMP(6)   NOT NULL,
    CONSTRAINT uk_invoices_number   UNIQUE (invoice_number),
    CONSTRAINT uk_invoices_payment  UNIQUE (payment_id),
    CONSTRAINT fk_invoices_payment  FOREIGN KEY (payment_id)      REFERENCES payments (id),
    CONSTRAINT fk_invoices_student  FOREIGN KEY (student_user_id) REFERENCES users (id),
    CONSTRAINT fk_invoices_course   FOREIGN KEY (course_id)       REFERENCES courses (id),
    CONSTRAINT fk_invoices_ebook    FOREIGN KEY (ebook_id)        REFERENCES ebooks (id)
);
CREATE INDEX idx_invoices_student      ON invoices (student_user_id);
CREATE INDEX idx_invoices_email        ON invoices (student_email);
CREATE INDEX idx_invoices_product      ON invoices (product_type, course_id, ebook_id);
CREATE INDEX idx_invoices_invoice_date ON invoices (invoice_date);
CREATE INDEX idx_invoices_purchase     ON invoices (purchase_date);
CREATE INDEX idx_invoices_transaction  ON invoices (transaction_id);

-- ---- Communication logs ----------------------------------------------------------------------
CREATE TABLE communication_logs (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel              VARCHAR(20)    NOT NULL,          -- EMAIL | WHATSAPP
    message_type         VARCHAR(40)    NOT NULL,          -- COURSE_PURCHASE | EBOOK_PURCHASE | EXISTING_STUDENT_PURCHASE | INVOICE | ADMIN_INDIVIDUAL | ADMIN_BULK
    student_user_id      BIGINT,
    student_code         VARCHAR(20),
    recipient            VARCHAR(190)   NOT NULL,          -- email address or mobile number
    subject              VARCHAR(255),
    body                 TEXT,
    status               VARCHAR(20)    NOT NULL,          -- PENDING | SENT | FAILED
    provider             VARCHAR(40),
    provider_message_id  VARCHAR(190),
    error_reason         VARCHAR(1000),
    attachment_path      VARCHAR(255),
    attachment_name      VARCHAR(255),
    invoice_id           BIGINT,
    product_type         VARCHAR(20),
    product_id           BIGINT,
    batch_ref            VARCHAR(40),                      -- groups the rows of one bulk send
    attempts             INT            NOT NULL DEFAULT 0,
    last_attempt_at      TIMESTAMP(6)   NULL,
    sent_at              TIMESTAMP(6)   NULL,
    sent_by_user_id      BIGINT,
    created_at           TIMESTAMP(6)   NOT NULL,
    updated_at           TIMESTAMP(6)   NOT NULL,
    CONSTRAINT fk_comm_logs_student  FOREIGN KEY (student_user_id) REFERENCES users (id),
    CONSTRAINT fk_comm_logs_invoice  FOREIGN KEY (invoice_id)      REFERENCES invoices (id),
    CONSTRAINT fk_comm_logs_sent_by  FOREIGN KEY (sent_by_user_id) REFERENCES users (id)
);
CREATE INDEX idx_comm_logs_status   ON communication_logs (status);
CREATE INDEX idx_comm_logs_channel  ON communication_logs (channel, status);
CREATE INDEX idx_comm_logs_student  ON communication_logs (student_user_id);
CREATE INDEX idx_comm_logs_created  ON communication_logs (created_at);
CREATE INDEX idx_comm_logs_batch    ON communication_logs (batch_ref);
CREATE INDEX idx_comm_logs_product  ON communication_logs (product_type, product_id);
