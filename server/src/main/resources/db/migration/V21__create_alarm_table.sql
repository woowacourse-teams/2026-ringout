CREATE TABLE alarm (
    id                    BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id               BIGINT       NOT NULL,
    alarm_id              VARCHAR(36)  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    alarm_time            TIME         NOT NULL,
    alarm_days            INT          NOT NULL,
    repeat_minutes        INT          NOT NULL,
    destination_name      VARCHAR(20)  NOT NULL,
    destination_latitude  DOUBLE       NOT NULL,
    destination_longitude DOUBLE       NOT NULL,
    alarm_version         BIGINT       NOT NULL,
    created_at            DATETIME     NOT NULL,
    updated_at            DATETIME     NOT NULL,
    deleted_at            DATETIME     NULL,
    CONSTRAINT uk_alarm_user_alarm_id UNIQUE (user_id, alarm_id),
    CONSTRAINT fk_alarm_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
