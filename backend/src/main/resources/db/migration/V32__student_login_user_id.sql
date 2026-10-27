-- V32: Student ID and User ID are two different things.
--   student_id  the academy-issued Student ID (LSI-YYYY-NNNNN): generated once, never changed by the
--               student; printed on certificates / invoices and shown in the admin panel.
--   login_id    the student's own User ID for the Student Admin login (Settings -> Change User ID).
--               NULL = the student has not chosen one, so their Student ID is their User ID.
-- Previously "Change User ID" overwrote student_id itself; StudentIdRepair moves any such chosen ID
-- into login_id at startup and restores the original Student ID (from the audit log).

ALTER TABLE student_profiles ADD COLUMN login_id VARCHAR(40) NULL;
CREATE UNIQUE INDEX uk_student_profiles_login_id ON student_profiles (login_id);
