-- =============================================================================
-- V10: Admin-managed homepage slider images (Mutual Fund website only for now).
-- The existing hero slider on home.html (mf-hero-slider-wrapper / #mfCarouselTrack)
-- keeps its exact CSS/animation/markup; only its image data source becomes dynamic.
-- The 5 images already hardcoded in home.html are migrated here as the initial rows,
-- kept active and in their current order, so the homepage looks identical after this.
-- =============================================================================

CREATE TABLE homepage_slider_images (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_id             BIGINT        NOT NULL,
    image_path          VARCHAR(255)  NOT NULL,   -- static "img/..." path or an admin upload "images/<uuid>.ext"
    title               VARCHAR(200),
    subtitle            VARCHAR(300),
    button_text         VARCHAR(100),
    button_link         VARCHAR(500),
    alt_text            VARCHAR(255),
    display_order       INT           NOT NULL DEFAULT 0,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by_user_id  BIGINT,
    updated_by_user_id  BIGINT,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    CONSTRAINT fk_slider_images_site       FOREIGN KEY (site_id)             REFERENCES sites (id),
    CONSTRAINT fk_slider_images_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_slider_images_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES users (id)
);

CREATE INDEX idx_slider_images_site_active ON homepage_slider_images (site_id, active, display_order);

-- Preserve the 5 slides already hardcoded in home.html's Mutual Fund hero carousel,
-- in their current order, all active, with their existing alt text carried over.
INSERT INTO homepage_slider_images (site_id, image_path, alt_text, display_order, active, created_at, updated_at)
SELECT s.id, x.image_path, x.alt_text, x.display_order, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s
JOIN (
    SELECT 'img/mf-hero-retirement.jpg' AS image_path, 'Plan for Early Retirement — Build Your Wealth Today for a Peaceful Tomorrow' AS alt_text, 1 AS display_order
    UNION ALL SELECT 'img/mf-hero-child-plan.jpg', 'Child Plan — Secure Their Tomorrow with Today''s Planning', 2
    UNION ALL SELECT 'img/mf-hero-financial-goals.jpg', 'Achieve Your Financial Goals — Plan, Invest, Grow', 3
    UNION ALL SELECT 'img/mf-hero-own-home.jpg', 'Own Home — Build a Place for Your Dreams', 4
    UNION ALL SELECT 'img/mf-hero-sip.jpg', 'SIP — Small Steps, Big Dreams. Build Wealth with Discipline', 5
) x ON 1 = 1
WHERE s.site_code = 'MUTUAL_FUND';
