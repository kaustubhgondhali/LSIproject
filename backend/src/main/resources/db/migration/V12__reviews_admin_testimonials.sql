-- =============================================================================
-- V12: Admin-authored testimonials in the existing reviews table.
-- Admins can now add/edit testimonials from the Admin Panel (same table, same public page).
-- An admin-entered testimonial (e.g. "Job Professional — Uran") has no visitor email, so the
-- column becomes optional. Visitor submissions still always carry an email. No data changes.
-- =============================================================================

ALTER TABLE reviews MODIFY COLUMN email VARCHAR(190) NULL;
