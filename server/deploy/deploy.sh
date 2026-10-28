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
SERVER_PORT="${SERVER_PORT:-8081}"
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

# 3. 활성 포트를 확인하고 반대 포트에 새 컨테이너 실행
healthy() {
  curl --fail --silent --connect-timeout 2 --max-time 3 \
    "http://127.0.0.1:$1/actuator/health" > /dev/null
}

ACTIVE_PORT=""
if healthy 8081; then ACTIVE_PORT=8081; fi
if healthy 8082; then
  if [[ -n "$ACTIVE_PORT" ]]; then
    echo "Both ports are healthy. Resolve the active deployment before retrying." >&2
    exit 1
  fi
  ACTIVE_PORT=8082
fi

SERVER_PORT=8081
if [[ "$ACTIVE_PORT" == 8081 ]]; then SERVER_PORT=8082; fi
export SERVER_PORT
export SERVER_CONTAINER_NAME="ringout-server-$SERVER_PORT"
compose+=(-p "ringout-$SERVER_PORT")
HEALTH_URL="http://127.0.0.1:$SERVER_PORT/actuator/health"
NGINX_CONFIG="/etc/nginx/sites-available/ringout-api.conf"
NGINX_BACKUP="$(mktemp)"
trap 'rm -f "$NGINX_BACKUP"' EXIT
sudo cat "$NGINX_CONFIG" > "$NGINX_BACKUP"
if ! grep -Eq 'proxy_pass http://127\.0\.0\.1:(8080|8081|8082);' "$NGINX_BACKUP"; then
  echo "Expected nginx proxy_pass was not found in $NGINX_CONFIG." >&2
  exit 1
fi

# 4. Spring 갱신 및 헬스체크. 실패하면 상태와 로그를 출력하고 종료
if "${compose[@]}" up -d --no-build --no-deps server; then
  for attempt in {1..12}; do
    if curl --fail --silent --connect-timeout 2 --max-time 3 "$HEALTH_URL" > /dev/null; then
      # 새 서버가 정상일 때만 nginx 전환. 설정 검사/리로드 실패 시 복구
      sed -E "s@proxy_pass http://127\.0\.0\.1:(8080|8081|8082);@proxy_pass http://127.0.0.1:$SERVER_PORT;@g" \
        "$NGINX_BACKUP" | sudo tee "$NGINX_CONFIG" > /dev/null
      if ! sudo nginx -t || ! sudo systemctl reload nginx; then
        sudo tee "$NGINX_CONFIG" < "$NGINX_BACKUP" > /dev/null
        sudo nginx -t && sudo systemctl reload nginx
        "${compose[@]}" stop server || true
        echo "Nginx switch failed. Previous configuration restored." >&2
        exit 1
      fi

      # 기존 요청을 처리할 시간을 준 뒤 이전 컨테이너 정리
      if [[ -n "$ACTIVE_PORT" ]]; then
        sleep 10
        docker stop "ringout-server-$ACTIVE_PORT" || true
      fi
      echo "Deployment succeeded on port $SERVER_PORT."
      exit 0
    fi
    echo "Waiting for server... ($attempt/12)"
    if [[ "$attempt" -lt 12 ]]; then sleep 5; fi
  done
fi

echo "Deployment failed. Check container status and logs below." >&2
"${compose[@]}" ps server || true
"${compose[@]}" logs --tail=200 server || true
"${compose[@]}" stop server || true
exit 1
