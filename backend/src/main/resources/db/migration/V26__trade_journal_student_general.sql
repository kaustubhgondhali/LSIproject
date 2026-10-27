-- V26: Student General > Student Journal
-- Adds the daily trading journal fields (segment, exit, quantity, charges, strategy, result and
-- the two reflections) to the student's own trade_journal rows. All columns are nullable so
-- existing entries stay valid; P&L is derived from these values and is not stored.

ALTER TABLE trade_journal ADD COLUMN segment VARCHAR(30);
ALTER TABLE trade_journal ADD COLUMN exit_price DECIMAL(12,2);
ALTER TABLE trade_journal ADD COLUMN quantity INT;
ALTER TABLE trade_journal ADD COLUMN charges DECIMAL(12,2);
ALTER TABLE trade_journal ADD COLUMN strategy VARCHAR(60);
ALTER TABLE trade_journal ADD COLUMN trade_result VARCHAR(20);
ALTER TABLE trade_journal ADD COLUMN trade_reason TEXT;
ALTER TABLE trade_journal ADD COLUMN outcome_reason TEXT;
