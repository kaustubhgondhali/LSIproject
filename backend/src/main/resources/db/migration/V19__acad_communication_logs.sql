-- =============================================================================
-- V19: Automation Admin email / WhatsApp automation log
-- Delivery records for messages the Automation Admin sends to ITS OWN students
-- (acad_students). Kept apart from communication_logs, which belongs to the online
-- academy (purchase confirmations, invoice emails). No reference to website / LMS data.
-- =============================================================================

CREATE TABLE acad_communication_logs (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel              VARCHAR(20)    NOT NULL,          -- EMAIL | WHATSAPP
    message_type         VARCHAR(40)    NOT NULL,          -- ADMIN_INDIVIDUAL | ADMIN_BULK
    acad_student_id      BIGINT         NOT NULL,
    student_code         VARCHAR(30),                      -- acad_students.student_id at send time
    student_name         VARCHAR(150),                     -- name at send time (history stays readable)
    recipient            VARCHAR(190)   NOT NULL,          -- email address or mobile number
    subject              VARCHAR(255),
    body                 TEXT,
    status               VARCHAR(20)    NOT NULL,          -- PENDING | SENT | FAILED
    provider             VARCHAR(40),
    provider_message_id  VARCHAR(190),
    error_reason         VARCHAR(1000),
    attachment_path      VARCHAR(255),
    attachment_name      VARCHAR(255),
    batch_ref            VARCHAR(40),                      -- groups the rows of one bulk send
    attempts             INT            NOT NULL DEFAULT 0,
    last_attempt_at      TIMESTAMP(6)   NULL,
    sent_at              TIMESTAMP(6)   NULL,
    sent_by_user_id      BIGINT,
    created_at           TIMESTAMP(6)   NOT NULL,
    updated_at           TIMESTAMP(6)   NOT NULL,
    CONSTRAINT fk_acad_comm_logs_student FOREIGN KEY (acad_student_id) REFERENCES acad_students (id),
    CONSTRAINT fk_acad_comm_logs_sent_by FOREIGN KEY (sent_by_user_id) REFERENCES users (id)
);
CREATE INDEX idx_acad_comm_logs_status  ON acad_communication_logs (status);
CREATE INDEX idx_acad_comm_logs_channel ON acad_communication_logs (channel, status);
CREATE INDEX idx_acad_comm_logs_student ON acad_communication_logs (acad_student_id);
CREATE INDEX idx_acad_comm_logs_created ON acad_communication_logs (created_at);
CREATE INDEX idx_acad_comm_logs_batch   ON acad_communication_logs (batch_ref);
