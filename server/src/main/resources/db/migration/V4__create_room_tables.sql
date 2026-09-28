CREATE TABLE room (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    host_user_id   BIGINT       NOT NULL,
    image_id       BIGINT       NULL,
    name           VARCHAR(20)  NOT NULL,
    description    VARCHAR(300) NULL,
    activity_days  INT          NOT NULL,
    activity_time  TIME         NOT NULL,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    CONSTRAINT fk_room_host_user FOREIGN KEY (host_user_id) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE room_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT   NOT NULL,
    room_id     BIGINT   NOT NULL,
    created_at  DATETIME NOT NULL,
    updated_at  DATETIME NOT NULL,
    CONSTRAINT uk_room_user_user_room UNIQUE (user_id, room_id),
    CONSTRAINT fk_room_user_user FOREIGN KEY (user_id) REFERENCES user (id),
    CONSTRAINT fk_room_user_room FOREIGN KEY (room_id) REFERENCES room (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE room_blacklist (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id     BIGINT   NOT NULL,
    user_id     BIGINT   NOT NULL,
    created_at  DATETIME NOT NULL,
    updated_at  DATETIME NOT NULL,
    CONSTRAINT uk_room_blacklist_room_user UNIQUE (room_id, user_id),
    CONSTRAINT fk_room_blacklist_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT fk_room_blacklist_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
