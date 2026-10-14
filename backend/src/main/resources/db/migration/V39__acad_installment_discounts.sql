-- V39: Automation Admin — a discount can be given at any installment (1st, 2nd, 3rd ...), not only
-- before the first one. Each row is the discount given at one installment of one student; a receipt
-- shows the discounts given at its own installment and the ones before it, never a later one.
--
-- acad_students.discount_* stay as the student's totals (sum of the rows, latest reason / date / by)
-- so every list, report and dashboard figure keeps working unchanged.
-- Existing discounts were printed on every receipt, so they become a discount at the 1st installment.

CREATE TABLE acad_discounts (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id        BIGINT        NOT NULL,
    installment_no    INT           NOT NULL,
    amount            DECIMAL(10,2) NOT NULL,
    reason            VARCHAR(200)  NOT NULL,
    given_by_user_id  BIGINT        NULL,
    created_at        TIMESTAMP(6)  NOT NULL,
    updated_at        TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_acad_discounts_installment UNIQUE (student_id, installment_no),
    CONSTRAINT fk_acad_discounts_student FOREIGN KEY (student_id) REFERENCES acad_students (id),
    CONSTRAINT fk_acad_discounts_given_by FOREIGN KEY (given_by_user_id) REFERENCES users (id)
);

INSERT INTO acad_discounts (student_id, installment_no, amount, reason, given_by_user_id, created_at, updated_at)
SELECT id, 1, discount_amount, COALESCE(discount_reason, 'Discount'), discount_by_user_id,
       COALESCE(discount_at, CURRENT_TIMESTAMP(6)), COALESCE(discount_at, CURRENT_TIMESTAMP(6))
FROM acad_students
WHERE discount_amount > 0;
