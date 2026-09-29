CREATE TABLE alarm_occurrence (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    occurrence_uuid  CHAR(36)    NOT NULL,
    user_id          BIGINT      NOT NULL,
    client_alarm_id  VARCHAR(64) NOT NULL,
    scheduled_at     DATETIME    NOT NULL,
    alarm_time       TIME        NOT NULL,
    started_at       DATETIME    NOT NULL,
    end_type         VARCHAR(20) NULL,
    ended_at         DATETIME    NULL,
    created_at       DATETIME    NOT NULL,
    updated_at       DATETIME    NOT NULL,
    deleted_at       DATETIME    NULL,
    CONSTRAINT uk_alarm_occurrence_uuid UNIQUE (occurrence_uuid),
    CONSTRAINT uk_alarm_occurrence_user_alarm_scheduled UNIQUE (user_id, client_alarm_id, scheduled_at),
    INDEX idx_alarm_occurrence_user_started (user_id, started_at),
    CONSTRAINT fk_alarm_occurrence_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE alarm_ringing (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    alarm_occurrence_id BIGINT      NOT NULL,
    ringing_type        VARCHAR(20) NOT NULL,
    event_id            VARCHAR(64) NULL,
    ringing_at          DATETIME    NOT NULL,
    dismissed_at        DATETIME    NULL,
    created_at          DATETIME    NOT NULL,
    updated_at          DATETIME    NOT NULL,
    deleted_at          DATETIME    NULL,
    CONSTRAINT uk_alarm_ringing_occurrence_event UNIQUE (alarm_occurrence_id, event_id),
    INDEX idx_alarm_ringing_occurrence (alarm_occurrence_id, ringing_at),
    CONSTRAINT fk_alarm_ringing_alarm_occurrence FOREIGN KEY (alarm_occurrence_id) REFERENCES alarm_occurrence (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
