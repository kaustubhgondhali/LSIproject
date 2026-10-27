-- =============================================================================
-- V22: Permanent fee receipt number per academy student
--
-- Until now every installment payment got its OWN receipt number (acad_receipts.receipt_no,
-- sequence kind RECEIPT, format LSR/YYYY/####), issued fresh each time acad_payments.record()
-- ran. The office wants ONE permanent receipt/reference number per student's fee plan that is
-- reused on every installment receipt, while each payment keeps its own unique payment_no
-- (acad_payments.payment_no, unchanged) so individual payments stay traceable.
--
--   * acad_students.fee_receipt_no — the ONE permanent number for that student's fee record,
--     assigned the first time a payment/receipt is issued for them (same LSR/YYYY/#### format,
--     drawn from the existing RECEIPT sequence — nothing about the number format changes).
--   * acad_receipts.receipt_no      — no longer unique per row: every receipt issued for the
--     same student now carries that student's fee_receipt_no. The historical uk_acad_receipts_no
--     constraint is dropped FIRST (before the backfill below can legitimately write the same
--     number onto more than one row) and replaced with a plain index (receipt lookups by number
--     still need to find every installment's receipt for that number).
--
-- Backfill (safe, non-destructive — no row is deleted, no amount/date/status changes):
-- every student who already has one or more receipts is assigned their EARLIEST receipt's
-- number as the permanent fee_receipt_no, and every one of that student's existing receipts is
-- rewritten to carry that same number, so historical multi-installment students immediately
-- show one consistent receipt number across all their printed receipts.
-- =============================================================================

ALTER TABLE acad_students ADD COLUMN fee_receipt_no VARCHAR(30) NULL;

-- Drop the old one-number-per-receipt constraint BEFORE the backfill below deliberately writes
-- the same receipt_no onto every installment of the same student.
ALTER TABLE acad_receipts DROP INDEX uk_acad_receipts_no;
CREATE INDEX idx_acad_receipts_receipt_no ON acad_receipts (receipt_no);

-- One student, one permanent number: the earliest receipt issued for them (lowest id = first
-- ever issued) becomes canonical. Students with no receipts yet are left NULL and get a number
-- assigned by AcadPaymentService the first time a payment is recorded for them.
UPDATE acad_students s
   SET s.fee_receipt_no = (
       SELECT r.receipt_no FROM acad_receipts r
        WHERE r.student_id = s.id
        ORDER BY r.id ASC
        LIMIT 1
   )
 WHERE EXISTS (SELECT 1 FROM acad_receipts r2 WHERE r2.student_id = s.id);

-- Rewrite every existing receipt of that student to the same canonical number so every printed
-- receipt — first installment or fifth — now shows the one permanent reference.
UPDATE acad_receipts r
   SET r.receipt_no = (SELECT s.fee_receipt_no FROM acad_students s WHERE s.id = r.student_id)
 WHERE EXISTS (
       SELECT 1 FROM acad_students s
        WHERE s.id = r.student_id
          AND s.fee_receipt_no IS NOT NULL
          AND s.fee_receipt_no <> r.receipt_no
   );

ALTER TABLE acad_students ADD CONSTRAINT uk_acad_students_fee_receipt_no UNIQUE (fee_receipt_no);
