ALTER TABLE destination
    ADD COLUMN destination_id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL AFTER id;

UPDATE destination
SET destination_id = CAST(id AS CHAR);

ALTER TABLE destination
    MODIFY COLUMN destination_id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    ADD CONSTRAINT uk_destination_user_destination_id UNIQUE (user_id, destination_id);
