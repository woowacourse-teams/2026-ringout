-- 배포·수동 복구 이력. 관리자 조회와 서버 복구 스크립트에서 사용한다.
CREATE TABLE deployments (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 deployment_id VARCHAR(36) NOT NULL,
 environment VARCHAR(32) NOT NULL,
 image VARCHAR(512) NOT NULL,
 image_id VARCHAR(100),
 previous_image VARCHAR(512),
 previous_image_id VARCHAR(100),
 -- Docker Hub의 불변 참조(repo@sha256:...). 수동 복구 시 이 값으로 pull한다.
 image_digest VARCHAR(512) NULL,
 previous_image_digest VARCHAR(512) NULL,
 action_type VARCHAR(20) NOT NULL,
 rollback_source_id BIGINT NULL,
 -- CI 배포를 실행한 GitHub Actions 실행 ID (기록용)
 actions_run_id BIGINT NULL,
 status VARCHAR(20) NOT NULL,
 started_at DATETIME(6) NOT NULL,
 finished_at DATETIME(6) NOT NULL,
 events MEDIUMTEXT NOT NULL,
 -- 배포 당시 DB의 Flyway 버전·이력 지문. 복구 가능 여부(스키마 호환성) 판단에 사용한다.
 flyway_version VARCHAR(100) NULL,
 flyway_fingerprint VARCHAR(64) NULL,
 CONSTRAINT uk_deployment_id UNIQUE (deployment_id),
 CONSTRAINT fk_deployment_source FOREIGN KEY (rollback_source_id) REFERENCES deployments(id),
 INDEX idx_deployments_environment_finished (environment, finished_at, id),
 INDEX idx_deployments_digest (environment, image_digest, status, finished_at, id)
);
