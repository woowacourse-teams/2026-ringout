CREATE TABLE forest_tree (
    id              BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    growth_cycle    INT         NOT NULL,
    kind            VARCHAR(20) NOT NULL,
    slot_index      INT         NOT NULL,
    planted_at      DATETIME    NOT NULL,
    acknowledged_at DATETIME    NULL,
    created_at      DATETIME    NOT NULL,
    updated_at      DATETIME    NOT NULL,
    deleted_at      DATETIME    NULL,
    CONSTRAINT uk_forest_tree_user_growth_cycle UNIQUE (user_id, growth_cycle),
    CONSTRAINT uk_forest_tree_user_slot_index UNIQUE (user_id, slot_index),
    CONSTRAINT fk_forest_tree_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
