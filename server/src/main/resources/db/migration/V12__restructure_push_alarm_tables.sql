-- device: FCM 토큰/플랫폼 의미가 드러나도록 컬럼명을 변경하고, 토큰 단위로 조회하므로 유니크 제약을 건다.
ALTER TABLE device
    CHANGE COLUMN user_token fcm_token VARCHAR(255) NOT NULL,
    CHANGE COLUMN os platform VARCHAR(20) NOT NULL,
    ADD CONSTRAINT uk_device_fcm_token UNIQUE (fcm_token);

-- push_alarm_content 는 campaign 으로 대체되어 push_alarm_record 와의 연관을 제거한다.
ALTER TABLE push_alarm_record
    DROP FOREIGN KEY fk_push_alarm_record_content,
    DROP COLUMN push_alarm_content_id;

DROP TABLE IF EXISTS push_alarm_content;

CREATE TABLE campaign (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    title                VARCHAR(255) NOT NULL,
    body                 VARCHAR(255) NOT NULL,
    deeplink             VARCHAR(255) NULL,
    campaign_target_type VARCHAR(20)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE notification (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    device_id   BIGINT      NOT NULL,
    campaign_id BIGINT      NOT NULL,
    target_type VARCHAR(20) NULL,
    status      VARCHAR(20) NOT NULL,
    is_read     BOOLEAN     NOT NULL,
    read_at     DATETIME    NULL,
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NOT NULL,
    deleted_at  DATETIME    NULL,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_notification_device FOREIGN KEY (device_id) REFERENCES device (id),
    CONSTRAINT fk_notification_campaign FOREIGN KEY (campaign_id) REFERENCES campaign (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
