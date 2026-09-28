CREATE TABLE image_file (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    url        VARCHAR(2048) NOT NULL,
    created_at DATETIME      NOT NULL,
    updated_at DATETIME      NOT NULL,
    deleted_at DATETIME      NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE mp3_file (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    url        VARCHAR(2048) NOT NULL,
    created_at DATETIME      NOT NULL,
    updated_at DATETIME      NOT NULL,
    deleted_at DATETIME      NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE `user`
    ADD COLUMN image_id BIGINT NULL AFTER email,
    ADD COLUMN deleted_at DATETIME NULL AFTER updated_at,
    ADD CONSTRAINT uk_user_image_file UNIQUE (image_id),
    ADD CONSTRAINT fk_user_image_file FOREIGN KEY (image_id) REFERENCES image_file (id);

ALTER TABLE terms
    ADD COLUMN deleted_at DATETIME NULL AFTER updated_at;

ALTER TABLE user_agreement
    MODIFY COLUMN user_id BIGINT NOT NULL,
    MODIFY COLUMN terms_id BIGINT NOT NULL,
    ADD COLUMN deleted_at DATETIME NULL AFTER updated_at,
    ADD CONSTRAINT uk_user_agreement_user_terms UNIQUE (user_id, terms_id);

ALTER TABLE destination
    ADD COLUMN deleted_at DATETIME NULL AFTER updated_at;

ALTER TABLE stamp
    MODIFY COLUMN user_id BIGINT NOT NULL,
    MODIFY COLUMN result VARCHAR(20) NOT NULL,
    ADD COLUMN deleted_at DATETIME NULL AFTER updated_at;

ALTER TABLE room
    ADD COLUMN deleted_at DATETIME NULL AFTER updated_at,
    ADD CONSTRAINT uk_room_image_file UNIQUE (image_id),
    ADD CONSTRAINT fk_room_image_file FOREIGN KEY (image_id) REFERENCES image_file (id);

ALTER TABLE room_user
    ADD COLUMN deleted_at DATETIME NULL AFTER updated_at;

ALTER TABLE room_blacklist
    ADD COLUMN deleted_at DATETIME NULL AFTER updated_at;

CREATE TABLE device (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    user_token VARCHAR(255) NOT NULL,
    os         VARCHAR(20)  NOT NULL,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    deleted_at DATETIME     NULL,
    CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE push_alarm_content (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    content    VARCHAR(1000) NOT NULL,
    created_at DATETIME      NOT NULL,
    updated_at DATETIME      NOT NULL,
    deleted_at DATETIME      NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE alarm (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT  NOT NULL,
    destination_id   BIGINT  NOT NULL,
    song_id          BIGINT  NOT NULL,
    alarm_time       TIME    NOT NULL,
    interval_minutes BIGINT  NOT NULL,
    day_of_week      INT     NULL,
    is_active        BOOLEAN NOT NULL,
    created_at       DATETIME NOT NULL,
    updated_at       DATETIME NOT NULL,
    deleted_at       DATETIME NULL,
    CONSTRAINT fk_alarm_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_alarm_destination FOREIGN KEY (destination_id) REFERENCES destination (id),
    CONSTRAINT fk_alarm_song FOREIGN KEY (song_id) REFERENCES mp3_file (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE active_alarm (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    alarm_id        BIGINT   NOT NULL,
    active_datetime DATETIME NOT NULL,
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL,
    deleted_at      DATETIME NULL,
    CONSTRAINT fk_active_alarm_alarm FOREIGN KEY (alarm_id) REFERENCES alarm (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE push_alarm_record (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id               BIGINT      NOT NULL,
    device_id             BIGINT      NOT NULL,
    push_alarm_content_id BIGINT      NOT NULL,
    push_alarm_status     VARCHAR(20) NOT NULL,
    is_read               BOOLEAN     NOT NULL,
    created_at            DATETIME    NOT NULL,
    updated_at            DATETIME    NOT NULL,
    deleted_at            DATETIME    NULL,
    CONSTRAINT fk_push_alarm_record_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_push_alarm_record_device FOREIGN KEY (device_id) REFERENCES device (id),
    CONSTRAINT fk_push_alarm_record_content FOREIGN KEY (push_alarm_content_id) REFERENCES push_alarm_content (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `record` (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT      NOT NULL,
    alarm_id         BIGINT      NOT NULL,
    active_alarm_id  BIGINT      NULL,
    start_datetime   DATETIME    NOT NULL,
    end_datetime     DATETIME    NULL,
    user_action      VARCHAR(20) NULL,
    created_at       DATETIME    NOT NULL,
    updated_at       DATETIME    NOT NULL,
    deleted_at       DATETIME    NULL,
    CONSTRAINT fk_record_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_record_alarm FOREIGN KEY (alarm_id) REFERENCES alarm (id),
    CONSTRAINT fk_record_active_alarm FOREIGN KEY (active_alarm_id) REFERENCES active_alarm (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
