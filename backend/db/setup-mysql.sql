-- =============================================================================
-- One-time MySQL setup for the LSI backend. Run as root in MySQL Workbench or:
--   mysql -u root -p < backend/db/setup-mysql.sql
-- Then put the chosen password into backend/.env as DB_PASSWORD.
-- Tables are created automatically by Flyway on first backend start.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS lsi_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'lsi_app'@'localhost' IDENTIFIED BY 'CHANGE_ME_strong_password';
GRANT ALL PRIVILEGES ON lsi_db.* TO 'lsi_app'@'localhost';
FLUSH PRIVILEGES;
