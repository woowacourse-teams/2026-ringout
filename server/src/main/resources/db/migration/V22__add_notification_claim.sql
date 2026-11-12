ALTER TABLE notification
    ADD COLUMN claim_token VARCHAR(36) NULL,
    ADD COLUMN claimed_at  DATETIME(6) NULL,
    ADD INDEX idx_notification_status_created_at (status, created_at),
    ADD INDEX idx_notification_status_claimed_at (status, claimed_at);
