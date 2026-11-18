CREATE TABLE admin_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    google_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    email VARCHAR(320) NOT NULL,
    name VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_admin_accounts_google_subject UNIQUE (google_subject),
    CONSTRAINT chk_admin_accounts_status CHECK (status IN ('PENDING', 'APPROVED', 'REVOKED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
