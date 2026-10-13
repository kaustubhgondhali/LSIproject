-- V28: Automation Admin > Email & WhatsApp — one-click payment receipt sharing.
-- A receipt sent by email / WhatsApp is recorded in the existing acad_communication_logs (one row
-- per channel, message_type FEE_RECEIPT). This nullable link says WHICH receipt was sent, so the
-- history shows it and Retry re-sends that same receipt. Existing rows keep NULL.
-- ON DELETE SET NULL: deleting a payment still voids its receipt exactly as before; the history
-- row stays and simply no longer points at a receipt.

ALTER TABLE acad_communication_logs ADD COLUMN acad_receipt_id BIGINT NULL;
ALTER TABLE acad_communication_logs ADD CONSTRAINT fk_acad_comm_logs_receipt
    FOREIGN KEY (acad_receipt_id) REFERENCES acad_receipts (id) ON DELETE SET NULL;
CREATE INDEX idx_acad_comm_logs_receipt ON acad_communication_logs (acad_receipt_id);
