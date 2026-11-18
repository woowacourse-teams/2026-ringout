CREATE TABLE scheduler_checkpoint (
    name              VARCHAR(64) NOT NULL PRIMARY KEY,
    last_processed_at DATETIME(6) NULL,
    version           BIGINT      NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
