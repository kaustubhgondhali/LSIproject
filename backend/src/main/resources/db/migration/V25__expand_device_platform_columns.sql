-- V25: Expand device_platform columns in student_devices and device_verification_otps
-- Allows full browser User-Agent strings to be safely recorded without truncation or SQL exceptions.

ALTER TABLE student_devices MODIFY COLUMN device_platform VARCHAR(500);
ALTER TABLE device_verification_otps MODIFY COLUMN device_platform VARCHAR(500);

