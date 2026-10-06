CREATE TABLE alarm_movement (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    active_alarm_id BIGINT      NOT NULL,
    movement_started_at DATETIME NULL,
    gave_up_at      DATETIME    NULL,
    arrived_at      DATETIME    NULL,
    created_at      DATETIME    NOT NULL,
    updated_at      DATETIME    NOT NULL,
    deleted_at      DATETIME    NULL,
    CONSTRAINT uk_alarm_movement_active_alarm UNIQUE (active_alarm_id),
    CONSTRAINT fk_alarm_movement_active_alarm FOREIGN KEY (active_alarm_id) REFERENCES active_alarm (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
