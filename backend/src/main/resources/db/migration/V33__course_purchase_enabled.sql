-- V33: Main Admin can open / close online purchase of a course (the "Share Market Course Purchase"
-- switch). Stored on the server so every visitor sees the same state and the payment API refuses a
-- closed course; before this the switch lived only in the admin's own browser. Existing courses
-- stay purchasable. Manual enrollment by the admin is not affected.

ALTER TABLE courses ADD COLUMN purchase_enabled BOOLEAN NOT NULL DEFAULT TRUE;
