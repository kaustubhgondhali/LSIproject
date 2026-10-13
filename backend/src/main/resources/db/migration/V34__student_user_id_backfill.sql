-- Student ID and User ID are now separate. The Student ID (LSI-YYYY-NNNNN) is only the record
-- reference on invoices, certificates and the admin panel; students sign in with their User ID
-- (student_profiles.login_id). Students who had not chosen a User ID signed in with their Student
-- ID until now, so that same text becomes their User ID and nobody is locked out.
-- Only academy-issued IDs (LSI-YYYY-NNNNN): a pre-V32 profile whose chosen ID overwrote student_id is
-- still left to StudentIdRepair, which moves that ID into login_id and restores the Student ID.
UPDATE student_profiles SET login_id = student_id WHERE login_id IS NULL AND student_id LIKE 'LSI-____-_____';
