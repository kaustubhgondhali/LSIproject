-- V37: Automation Admin students' free ebook login. After the first fee installment an academy
-- (office) student gets a Student Panel login that opens only the ebook. Additive only.
--
-- * acad_students.portal_user_id: the website login (users.id) of that student; one each.
-- * portal_password_enc: the first (or office-reset) password, encrypted, so the office can see and
--   share it until the student changes it. Never the student's own password.
-- * ebook_blocked: the office blocked this student's ebook access (Automation Admin > student).
-- * student_profiles.academy_ebook_only: a login made by the academy for the ebook only — its
--   Student ID is the academy one (LSA/...), never a website LSI- ID — so the column grows to 30
--   characters like acad_students.student_id.

ALTER TABLE acad_students ADD COLUMN portal_user_id BIGINT NULL;
ALTER TABLE acad_students ADD CONSTRAINT uk_acad_students_portal_user UNIQUE (portal_user_id);
ALTER TABLE acad_students ADD CONSTRAINT fk_acad_students_portal_user FOREIGN KEY (portal_user_id) REFERENCES users (id);
ALTER TABLE acad_students ADD COLUMN portal_password_enc VARCHAR(255) NULL;
ALTER TABLE acad_students ADD COLUMN portal_created_at TIMESTAMP(6) NULL;
ALTER TABLE acad_students ADD COLUMN portal_emailed_at TIMESTAMP(6) NULL;
ALTER TABLE acad_students ADD COLUMN ebook_blocked BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE student_profiles ADD COLUMN academy_ebook_only BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE student_profiles MODIFY COLUMN student_id VARCHAR(30) NOT NULL;
