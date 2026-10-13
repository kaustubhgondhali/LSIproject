-- V36: Automation Admin (academy office) — Aadhaar number, per-student discount, and receipt
-- numbers that follow the Student ID. Every change is additive; no existing value is altered here.
--
-- * aadhaar_no: the student's 12-digit Aadhaar number (digits only). Required by the Add / Edit
--   Student form from now on; NULL for students saved before it, until they are next edited.
--   Unique, so one Aadhaar number can never be on two academy students (NULLs never clash).
-- * discount_*: a discount given to ONE student. Fee after discount = course_fee - discount_amount;
--   balances and receipts use that. 0 = no discount (every existing student).
-- * acad_receipts.discount: the discount printed on the receipt (Course Fee, Discount, Fee after
--   Discount, Amount Paid, Total Paid to Date, Balance).
-- * Receipt numbers now follow the Student ID (LSA/2026/0025 -> LSR/2026/0025). A Student ID
--   with an office-chosen prefix gives a longer receipt number ("LSR/" + the ID), so both
--   receipt-number columns grow from 30 to 40 characters. The renumbering of existing receipts
--   runs at startup (AcadReceiptNumberAlignment), where every old number is written to the
--   audit log.

ALTER TABLE acad_students ADD COLUMN aadhaar_no VARCHAR(12) NULL;
ALTER TABLE acad_students ADD CONSTRAINT uk_acad_students_aadhaar UNIQUE (aadhaar_no);

ALTER TABLE acad_students ADD COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0;
ALTER TABLE acad_students ADD COLUMN discount_reason VARCHAR(200) NULL;
ALTER TABLE acad_students ADD COLUMN discount_at TIMESTAMP(6) NULL;
ALTER TABLE acad_students ADD COLUMN discount_by_user_id BIGINT NULL;
ALTER TABLE acad_students ADD CONSTRAINT fk_acad_students_discount_by FOREIGN KEY (discount_by_user_id) REFERENCES users (id);

ALTER TABLE acad_receipts ADD COLUMN discount DECIMAL(10,2) NOT NULL DEFAULT 0;

ALTER TABLE acad_students MODIFY COLUMN fee_receipt_no VARCHAR(40) NULL;
ALTER TABLE acad_receipts MODIFY COLUMN receipt_no VARCHAR(40) NOT NULL;
