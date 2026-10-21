#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="/home/ubuntu/ringout"
if [[ "$SCRIPT_DIR" == "$APP_DIR" ]]; then
  COMPOSE_SOURCE="$APP_DIR/docker-compose.yaml"
else
  COMPOSE_SOURCE="$SCRIPT_DIR/../docker-compose.yaml"
fi
set -a
source "$APP_DIR/.env"
SERVER_PORT="${SERVER_PORT:-8080}"
set +a
: "${SERVER_IMAGE:?Set SERVER_IMAGE in the EC2 .env}"
HEALTH_URL="http://127.0.0.1:$SERVER_PORT/actuator/health"
compose=(docker compose --project-directory "$APP_DIR" --env-file "$APP_DIR/.env" -f "$APP_DIR/docker-compose.yaml")

# 1. Spring Compose를 검증하고 스크립트와 함께 앱 폴더에 복사 (.env는 유지)
docker compose --project-directory "$APP_DIR" --env-file "$APP_DIR/.env" -f "$COMPOSE_SOURCE" config --quiet
if [[ ! "$COMPOSE_SOURCE" -ef "$APP_DIR/docker-compose.yaml" ]]; then
  cp "$COMPOSE_SOURCE" "$APP_DIR/docker-compose.yaml"
fi
if [[ ! "$SCRIPT_DIR/deploy.sh" -ef "$APP_DIR/deploy.sh" ]]; then
  install -m 755 "$SCRIPT_DIR/deploy.sh" "$APP_DIR/deploy.sh"
fi

# 2. .env의 브랜치 태그를 pull. 다운로드 실패 시 기존 컨테이너는 유지
if ! "${compose[@]}" pull server; then
  echo "Image pull failed. Existing container was not replaced; check Docker login, image tag and network." >&2
  exit 1
fi

# 3. Spring 갱신 및 헬스체크. 실패하면 상태와 로그를 출력하고 종료
if "${compose[@]}" up -d --no-build --no-deps server; then
  for attempt in {1..12}; do
    if curl --fail --silent --connect-timeout 2 --max-time 3 "$HEALTH_URL" > /dev/null; then
      echo "Deployment succeeded."
      exit 0
    fi
    echo "Waiting for server... ($attempt/12)"
    if [[ "$attempt" -lt 12 ]]; then sleep 5; fi
  done
fi

echo "Deployment failed. Check container status and logs below." >&2
"${compose[@]}" ps server || true
"${compose[@]}" logs --tail=200 server || true
exit 1
