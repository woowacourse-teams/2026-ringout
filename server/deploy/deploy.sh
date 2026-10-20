#!/usr/bin/env bash

set -Eeuo pipefail

set -a
source /home/ubuntu/ringout/.env
set +a

APP_DIR="/home/ubuntu/ringout"
HEALTH_URL="http://127.0.0.1:8080/actuator/health"

cd "$APP_DIR"

# docker-compose.yml과 .env 설정 검증
docker compose config --quiet

# MySQL 이미지가 없을 때만 다운로드
docker compose pull --policy missing mysql

# 서버 이미지는 항상 최신 버전 다운로드
docker compose pull server

# 이미지 빌드 없이 컨테이너 실행
docker compose up -d --no-build

# 최대 60초 동안 서버 상태 확인
for attempt in {1..12}; do
  if curl --fail --silent "$HEALTH_URL" > /dev/null; then
    echo "Deployment succeeded."
    docker image prune --force
    exit 0
  fi
  echo "Waiting for server... ($attempt/12)"
  sleep 5
done

echo "Deployment failed: health check did not pass."
docker compose ps
docker compose logs --tail=200 server
exit 1
