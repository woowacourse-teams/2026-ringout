CREATE TABLE user (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    social_provider    VARCHAR(20)  NOT NULL,
    social_provider_id VARCHAR(255) NOT NULL,
    role               VARCHAR(20)  NOT NULL,
    nickname           VARCHAR(10)  NOT NULL,
    email              VARCHAR(320) NULL,
    last_login_at      DATETIME     NOT NULL,
    last_accessed_at   DATETIME     NOT NULL,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,
    CONSTRAINT uk_user_social_identity UNIQUE (social_provider, social_provider_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE terms (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    type       VARCHAR(30) NOT NULL,
    version    DATE        NOT NULL,
    created_at DATETIME    NOT NULL,
    updated_at DATETIME    NOT NULL,
    CONSTRAINT uk_terms_type_version UNIQUE (type, version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE destination (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    alias      VARCHAR(12) NOT NULL,
    latitude   DOUBLE      NOT NULL,
    longitude  DOUBLE      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME    NOT NULL,
    updated_at DATETIME    NOT NULL,
    CONSTRAINT fk_destination_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE stamp (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_date DATE        NOT NULL,
    result      VARCHAR(10) NOT NULL,
    user_id     BIGINT      NULL,
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NOT NULL,
    CONSTRAINT uk_stamp_user_id_record_date UNIQUE (user_id, record_date),
    CONSTRAINT fk_stamp_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_agreement (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NULL,
    terms_id   BIGINT      NULL,
    type       VARCHAR(30) NOT NULL,
    version    DATE        NOT NULL,
    created_at DATETIME    NOT NULL,
    updated_at DATETIME    NOT NULL,
    CONSTRAINT fk_user_agreement_user FOREIGN KEY (user_id) REFERENCES user (id),
    CONSTRAINT fk_user_agreement_terms FOREIGN KEY (terms_id) REFERENCES terms (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
