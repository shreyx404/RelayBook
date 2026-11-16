-- RelayBook schema (MySQL 8+)
-- Version 2: routes live in the normalized contact_methods table so every
-- route carries its own verification date. The old flat columns
-- (phone, email, alternate_phone) are no longer part of the schema; if the
-- application opens a database created with version 1, it copies those
-- columns into contact_methods inside a transaction and then removes them.

CREATE DATABASE IF NOT EXISTS relaybook CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE relaybook;

CREATE TABLE IF NOT EXISTS contacts (
    id                  VARCHAR(36)  NOT NULL PRIMARY KEY,
    contact_type        VARCHAR(24)  NOT NULL,
    full_name           VARCHAR(160) NOT NULL,
    organization        VARCHAR(160) NULL,
    relationship_label  VARCHAR(100) NULL,
    preferred_channel   VARCHAR(30)  NULL,
    best_time           VARCHAR(160) NULL,
    time_zone           VARCHAR(80)  NULL,
    notes               TEXT         NULL,
    last_verified       DATE         NULL,  -- derived: latest of the route dates, kept for fast sorting
    share_consent       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_contacts_name (full_name),
    INDEX idx_contacts_type (contact_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS contact_methods (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contact_id    VARCHAR(36)  NOT NULL,
    channel       VARCHAR(30)  NOT NULL,  -- PHONE | EMAIL | ALTERNATE_PHONE | WHATSAPP | TELEGRAM
    `value`       VARCHAR(254) NOT NULL,  -- VALUE is reserved in MySQL 8.0.17+, hence backticks
    last_verified DATE         NULL,      -- verification date of this individual route
    label         VARCHAR(100) NULL,      -- optional, e.g. "Mobile" or "Office landline"
    UNIQUE KEY uq_contact_channel (contact_id, channel),
    INDEX idx_methods_contact (contact_id),
    CONSTRAINT fk_methods_contact FOREIGN KEY (contact_id)
        REFERENCES contacts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
