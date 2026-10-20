#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
APP_DIR="/home/ubuntu/ringout"
set -a
source "$APP_DIR/.env"
SERVER_PORT="${SERVER_PORT:-8080}"
set +a
: "${SERVER_IMAGE:?Set SERVER_IMAGE in the EC2 .env}"
HEALTH_URL="http://127.0.0.1:$SERVER_PORT/actuator/health"
compose=(docker compose --project-directory "$APP_DIR" --env-file "$APP_DIR/.env" -f "$PWD/docker-compose.yaml")

# 1. EC2의 .env로 Compose 설정 검증
"${compose[@]}" config --quiet

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
