-- Invoices now carry Vaibhav S. Pawar's signature (Authorised Signatory). Invoice PDFs are stored when
-- issued, so the ones issued before this change are marked for a one-time re-render from the same
-- invoice record: InvoiceService.pdfBytes re-renders when pdf_generated_at is NULL. Nothing else about
-- an invoice changes, except that the old default note no longer says "does not require a signature".
UPDATE invoices
SET notes = 'This is a computer-generated invoice for a digital product.'
WHERE notes = 'This is a computer-generated invoice for a digital product and does not require a signature.';

UPDATE invoices SET pdf_generated_at = NULL WHERE pdf_path IS NOT NULL;
