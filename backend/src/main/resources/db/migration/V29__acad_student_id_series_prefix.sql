-- V29: Automation Admin > Add Student — the office decides the Student ID series.
-- The STUDENT counter row for a year can now carry its own prefix (e.g. "LSI-B3-") and digit
-- width, set by typing the next Student ID the office wants. NULL = the original format
-- LSA/<year>/<4 digits>, so every existing counter and Student ID stays exactly as it is.

ALTER TABLE acad_sequences ADD COLUMN id_prefix VARCHAR(24) NULL;
ALTER TABLE acad_sequences ADD COLUMN pad_width INT NULL;
