-- V27: Automation Admin > Student Admission — the complete admission form on acad_students.
-- Name, admission date, mobile, email, address and course already live on this table and are
-- reused as-is. Every new column is nullable so students admitted or imported before this change
-- stay valid; no existing row is modified.

ALTER TABLE acad_students ADD COLUMN date_of_birth DATE NULL;
ALTER TABLE acad_students ADD COLUMN gender VARCHAR(10) NULL;
ALTER TABLE acad_students ADD COLUMN highest_qualification VARCHAR(100) NULL;
ALTER TABLE acad_students ADD COLUMN institute_name VARCHAR(150) NULL;
ALTER TABLE acad_students ADD COLUMN year_of_completion INT NULL;
ALTER TABLE acad_students ADD COLUMN employment_status VARCHAR(20) NULL;
ALTER TABLE acad_students ADD COLUMN current_employer VARCHAR(150) NULL;
ALTER TABLE acad_students ADD COLUMN job_title VARCHAR(100) NULL;
ALTER TABLE acad_students ADD COLUMN annual_income DECIMAL(14,2) NULL;
ALTER TABLE acad_students ADD COLUMN stock_market_experience BOOLEAN NULL;
ALTER TABLE acad_students ADD COLUMN stock_market_years DECIMAL(4,1) NULL;
ALTER TABLE acad_students ADD COLUMN trading_experience BOOLEAN NULL;
ALTER TABLE acad_students ADD COLUMN trading_years DECIMAL(4,1) NULL;
ALTER TABLE acad_students ADD COLUMN trading_platforms VARCHAR(255) NULL;
ALTER TABLE acad_students ADD COLUMN trading_strategies VARCHAR(500) NULL;
ALTER TABLE acad_students ADD COLUMN preferred_start_date DATE NULL;
ALTER TABLE acad_students ADD COLUMN payment_method VARCHAR(20) NULL;
