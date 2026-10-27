-- =============================================================================
-- V23: Update default display order of Mutual Fund homepage hero slider images.
-- Matches user specified order:
-- 1. Child Plan (img/mf-hero-child-plan.jpg)
-- 2. Plan for Early Retirement (img/mf-hero-retirement.jpg)
-- 3. SIP Small Steps. Big Dreams (img/mf-hero-sip.jpg)
-- 4. Own Home (img/mf-hero-own-home.jpg)
-- 5. Achieve Your Financial Goals (img/mf-hero-financial-goals.jpg)
-- =============================================================================

UPDATE homepage_slider_images SET display_order = 1 WHERE image_path = 'img/mf-hero-child-plan.jpg';
UPDATE homepage_slider_images SET display_order = 2 WHERE image_path = 'img/mf-hero-retirement.jpg';
UPDATE homepage_slider_images SET display_order = 3 WHERE image_path = 'img/mf-hero-sip.jpg';
UPDATE homepage_slider_images SET display_order = 4 WHERE image_path = 'img/mf-hero-own-home.jpg';
UPDATE homepage_slider_images SET display_order = 5 WHERE image_path = 'img/mf-hero-financial-goals.jpg';

