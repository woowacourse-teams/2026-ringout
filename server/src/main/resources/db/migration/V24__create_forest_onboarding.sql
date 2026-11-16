CREATE TABLE forest_onboarding (
    id           BIGINT   AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT   NOT NULL,
    completed_at DATETIME NOT NULL,
    created_at   DATETIME NOT NULL,
    updated_at   DATETIME NOT NULL,
    deleted_at   DATETIME NULL,
    CONSTRAINT uk_forest_onboarding_user UNIQUE (user_id),
    CONSTRAINT fk_forest_onboarding_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
