#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
APP_DIR="/home/ubuntu/ringout"
set -a
source "$APP_DIR/.env"
SERVER_PORT="${SERVER_PORT:-8080}"
set +a
: "${SERVER_IMAGE:?Set SERVER_IMAGE in the EC2 .env}"
: "${DB_SSH_HOST:?DB_SSH_HOST is required}"
HEALTH_URL="http://127.0.0.1:$SERVER_PORT/actuator/health"
compose=(docker compose --project-directory "$APP_DIR" --env-file "$APP_DIR/.env" -f "$PWD/docker-compose.yaml")

# 1. EC2의 .env로 Compose 설정 검증
"${compose[@]}" config --quiet

# 2. .env의 브랜치 태그를 pull. 다운로드 실패 시 기존 컨테이너는 유지
if ! "${compose[@]}" pull server; then
  echo "Image pull failed. Existing container was not replaced; check Docker login, image tag and network." >&2
  exit 1
fi

# 3. DB EC2의 .env 프로필에 따라 MySQL과 모니터링 실행, 최대 150초 대기
if ! ssh -T -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=10 \
  "${DB_SSH_USER:-ubuntu}@$DB_SSH_HOST" 'bash -se' <<'MYSQL'
cd /home/ubuntu/mysql
docker compose --env-file .env -f docker-compose.yaml pull --policy missing
docker compose --env-file .env -f docker-compose.yaml up -d --no-build --no-recreate --wait --wait-timeout 150
MYSQL
then
  echo "Database preparation failed or timed out; Spring deployment stopped." >&2
  exit 1
fi

# 4. Spring EC2에서 DB 포트에 접근할 수 있는지 확인
if ! timeout 5 bash -c 'exec 3<>/dev/tcp/$1/$2' bash "${DB_HOST:-$DB_SSH_HOST}" "${DB_PORT:-3306}"; then
  echo "Database port is unreachable; Spring deployment stopped." >&2
  exit 1
fi

# 5. Spring 갱신 및 헬스체크. 실패하면 상태와 로그를 출력하고 종료
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
