-- =============================================================================
-- V8: Visitor-submitted testimonials / reviews with admin approval
-- Only rows with status = 'APPROVED' are ever shown publicly. Reviews are tagged
-- with the website they were submitted on (ACADEMY / MUTUAL_FUND).
-- =============================================================================

CREATE TABLE reviews (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_id              BIGINT        NOT NULL,
    full_name            VARCHAR(150)  NOT NULL,
    email                VARCHAR(190)  NOT NULL,
    course               VARCHAR(150),
    rating               INT           NOT NULL,              -- 1..5
    review_text          VARCHAR(2000) NOT NULL,
    profile_image_path   VARCHAR(255),                        -- images/<uuid>.jpg uploaded by the visitor
    status               VARCHAR(20)   NOT NULL DEFAULT 'PENDING',   -- PENDING | APPROVED | DECLINED
    submitted_ip         VARCHAR(45),
    approved_at          TIMESTAMP(6)  NULL,
    approved_by_user_id  BIGINT,
    decided_at           TIMESTAMP(6)  NULL,
    decided_by_user_id   BIGINT,
    created_at           TIMESTAMP(6)  NOT NULL,
    updated_at           TIMESTAMP(6)  NOT NULL,
    CONSTRAINT fk_reviews_site        FOREIGN KEY (site_id)             REFERENCES sites (id),
    CONSTRAINT fk_reviews_approved_by FOREIGN KEY (approved_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_decided_by  FOREIGN KEY (decided_by_user_id)  REFERENCES users (id),
    CONSTRAINT ck_reviews_rating      CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_reviews_site_status ON reviews (site_id, status, created_at);
CREATE INDEX idx_reviews_email       ON reviews (email);
