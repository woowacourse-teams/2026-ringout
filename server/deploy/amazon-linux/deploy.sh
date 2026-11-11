#!/usr/bin/env bash
set -euo pipefail

# Outbox delivery uses Python's standard library; no application credentials enter logs.
command -v python3 >/dev/null || { echo "python3 is required for deployment history" >&2; exit 1; }
export DEPLOYMENT_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
export DEPLOY_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
export DEPLOY_EVENTS_FILE="$(mktemp)"
export DEPLOY_SUCCESS=false
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
export APP_DIR="${RINGOUT_APP_DIR:-/home/ec2-user/ringout}"
DEPLOY_TOOLS_DIR="$SCRIPT_DIR"

if [[ "${SCRIPT_DIR##*/}" == amazon-linux ]]; then
  DEPLOY_TOOLS_DIR="$SCRIPT_DIR/.."
fi

RECORDER="$DEPLOY_TOOLS_DIR/deployment.py"

log() {
  printf '%s event=%s image=%s %s\n' \
    "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    "$1" "${SERVER_IMAGE:-unknown}" "${2:-}" \
    | tee -a "$DEPLOY_EVENTS_FILE"
}

finish() {
  local status=$?
  if (( status != 0 )); then
    log DEPLOY_FAILED "exit_code=$status"
  fi

  log DEPLOY_FINISHED "exit_code=$status duration_seconds=$SECONDS"

  if [[ -f "$RECORDER" ]]; then
    if python3 "$RECORDER" record; then
      rm -f "$DEPLOY_EVENTS_FILE"
    else
      echo "Deployment history could not be queued; deployment logs remain at $DEPLOY_EVENTS_FILE" >&2
    fi
  fi

  if [[ -n "${NGINX_BACKUP:-}" ]]; then
    rm -f "$NGINX_BACKUP"
  fi
}

# 태그·digest를 뗀 저장소 이름
image_repository() {
  local ref="${1%%@*}"
  if [[ "${ref##*/}" == *:* ]]; then
    ref="${ref%:*}"
  fi
  printf '%s' "$ref"
}

# 로컬 이미지의 Docker Hub digest 참조
repo_digest() {
  docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$1" 2>/dev/null \
    | grep -E '^[^[:space:]@]+@sha256:[0-9a-f]{64}$' \
    | awk -v p="$2@" 'NR==1{first=$0} index($0,p)==1{print; found=1; exit} END{if(!found && first!="") print first}' || true
}

SECONDS=0
trap finish EXIT
log DEPLOY_STARTED

if [[ "$SCRIPT_DIR" == "$APP_DIR" ]]; then
  COMPOSE_SOURCE="$APP_DIR/docker-compose.yaml"
else
  COMPOSE_SOURCE="$DEPLOY_TOOLS_DIR/../docker-compose.yaml"
fi

set -a
source "$APP_DIR/.env"
SERVER_PORT="${SERVER_PORT:-8081}"
set +a

: "${SERVER_IMAGE:?Set SERVER_IMAGE in the EC2 .env}"

# All CI and administrator deployments on this host share this lock.
exec 9>"$APP_DIR/deployment.lock"
flock -w 600 9 || {
  log DEPLOY_FAILED "reason=deployment_lock_timeout"
  exit 1
}

if [[ -n "${DEPLOY_COMMIT_SHA:-}" ]]; then
  [[ "$DEPLOY_COMMIT_SHA" =~ ^[0-9a-f]{40}$ ]] || {
    echo "Invalid commit SHA" >&2
    exit 1
  }

  IMAGE_REPOSITORY="${SERVER_IMAGE%%@*}"
  if [[ "${IMAGE_REPOSITORY##*/}" == *:* ]]; then
    IMAGE_REPOSITORY="${IMAGE_REPOSITORY%:*}"
  fi
  export SERVER_IMAGE="$IMAGE_REPOSITORY:$DEPLOY_COMMIT_SHA"
fi

log DEPLOY_IMAGE

HEALTH_URL="http://127.0.0.1:$SERVER_PORT/actuator/health"

compose=(
  docker compose
  --project-directory "$APP_DIR"
  --env-file "$APP_DIR/.env"
  -f "$APP_DIR/docker-compose.yaml"
)

# 1. Docker Compose 검증 및 파일 복사
docker compose \
  --project-directory "$APP_DIR" \
  --env-file "$APP_DIR/.env" \
  -f "$COMPOSE_SOURCE" config --quiet

if [[ ! "$COMPOSE_SOURCE" -ef "$APP_DIR/docker-compose.yaml" ]]; then
  cp "$COMPOSE_SOURCE" "$APP_DIR/docker-compose.yaml"
fi

if [[ ! "$SCRIPT_DIR/deploy.sh" -ef "$APP_DIR/deploy.sh" ]]; then
  install -m 755 "$SCRIPT_DIR/deploy.sh" "$APP_DIR/deploy.sh"
  install -m 755 "$RECORDER" "$APP_DIR/deployment.py"
fi

# 2. 이미지 Pull
log IMAGE_PULL_STARTED

if ! "${compose[@]}" pull server; then
  echo "Image pull failed. Existing container was not replaced." >&2
  exit 1
fi

export DEPLOY_IMAGE_ID="$(
  docker image inspect --format '{{.Id}}' "$SERVER_IMAGE"
)"

export DEPLOY_IMAGE_DIGEST="$(
  repo_digest "$SERVER_IMAGE" "$(image_repository "$SERVER_IMAGE")"
)"

log IMAGE_PULL_FINISHED

docker image inspect \
  --format 'image_id={{.Id}} repo_digests={{json .RepoDigests}}' \
  "$SERVER_IMAGE"


# ==========================================================
# [무중단 배포 비활성화] Blue-Green 활성 포트 감지
# ==========================================================

# healthy() {
#   curl --fail --silent --connect-timeout 2 --max-time 3 \
#     "http://127.0.0.1:$1/actuator/health" > /dev/null
# }
#
# ACTIVE_PORT=""
# if healthy 8081; then ACTIVE_PORT=8081; fi
# if healthy 8082; then
#   if [[ -n "$ACTIVE_PORT" ]]; then
#     echo "Both ports are healthy. Resolve the active deployment before retrying." >&2
#     exit 1
#   fi
#   ACTIVE_PORT=8082
# fi


# ==========================================================
# [무중단 배포 비활성화] 반대 포트 선택
# ==========================================================

# SERVER_PORT=8081
# if [[ "$ACTIVE_PORT" == 8081 ]]; then SERVER_PORT=8082; fi


# 3. 현재 Nginx 설정 확인
container_nginx_config="$APP_DIR/nginx/ringout-api.conf"

if [[ -f "$container_nginx_config" ]]; then
  NGINX_CONFIG="$container_nginx_config"
  docker exec ringout-nginx nginx -t
else
  NGINX_CONFIG="${RINGOUT_NGINX_CONFIG:-/etc/nginx/conf.d/ringout-api.conf}"
fi

NGINX_BACKUP="$(mktemp)"
sudo cat "$NGINX_CONFIG" > "$NGINX_BACKUP"

if ! grep -Eq 'proxy_pass http://127\.0\.0\.1:(8081|8082);' "$NGINX_BACKUP"; then
  echo "Expected nginx proxy_pass was not found in $NGINX_CONFIG." >&2
  exit 1
fi

# 일반 배포: Nginx가 현재 사용하는 포트에 그대로 배포
mapfile -t nginx_ports < <(
  grep -Eo 'proxy_pass http://127\.0\.0\.1:(8081|8082);' "$NGINX_BACKUP" \
    | sed -E 's/.*:([0-9]+);/\1/' \
    | sort -u
)

if (( ${#nginx_ports[@]} != 1 )); then
  echo "Expected exactly one Nginx upstream port." >&2
  exit 1
fi

SERVER_PORT="${nginx_ports[0]}"
export SERVER_PORT
export SERVER_CONTAINER_NAME="ringout-server-$SERVER_PORT"

compose+=(-p "ringout-$SERVER_PORT")
HEALTH_URL="http://127.0.0.1:$SERVER_PORT/actuator/health"

# 기존 컨테이너 이미지 정보 기록
if docker container inspect "$SERVER_CONTAINER_NAME" >/dev/null 2>&1; then
  export PREVIOUS_IMAGE="$(
    docker inspect --format '{{.Config.Image}}' "$SERVER_CONTAINER_NAME"
  )"

  export PREVIOUS_IMAGE_ID="$(
    docker inspect --format '{{.Image}}' "$SERVER_CONTAINER_NAME"
  )"

  export PREVIOUS_IMAGE_DIGEST="$(
    repo_digest "$PREVIOUS_IMAGE_ID" "$(image_repository "$PREVIOUS_IMAGE")"
  )"

  log PREVIOUS_SERVER "port=$SERVER_PORT"

  docker inspect \
    --format 'previous_image={{.Config.Image}} previous_image_id={{.Image}}' \
    "$SERVER_CONTAINER_NAME"
fi


# ==========================================================
# [무중단 배포 비활성화] Nginx 트래픽 전환 함수
# ==========================================================

# reload_nginx() {
#   if [[ "$NGINX_CONFIG" == "$container_nginx_config" ]]; then
#     docker exec ringout-nginx nginx -t &&
#     docker exec ringout-nginx nginx -s reload
#   else
#     sudo nginx -t && sudo systemctl reload nginx
#   fi
# }


# ==========================================================
# [무중단 배포 비활성화] 이중 컨테이너 메모리 검사
# ==========================================================

# if [[ -n "$ACTIVE_PORT" ]]; then
#   limit_bytes=$("${compose[@]}" config --format json | python3 -c '
# import json, sys
# print(int(json.load(sys.stdin)["services"]["server"]["mem_limit"]))
# ')
#   available_kib=$(awk '$1 == "MemAvailable:" {print $2}' /proc/meminfo)
#   : "${available_kib:?Could not read available host memory}"
#   required_kib=$(( (limit_bytes + 1023) / 1024 + 128 * 1024 ))
#
#   if (( available_kib < required_kib )); then
#     log DEPLOY_FAILED "reason=insufficient_host_memory available_kib=$available_kib required_kib=$required_kib"
#     printf 'Not enough available memory to start a second Spring container.\n' >&2
#     exit 1
#   fi
# fi


# 4. 기존 컨테이너 교체 및 헬스체크
if "${compose[@]}" up -d --pull never --no-build --no-deps server; then

  for attempt in {1..30}; do

    if curl --fail --silent --connect-timeout 2 --max-time 3 "$HEALTH_URL" > /dev/null; then

      log NEW_SERVER_READY "port=$SERVER_PORT attempt=$attempt"


      # ====================================================
      # [무중단 배포 비활성화] Nginx 트래픽 전환 및 롤백
      # ====================================================

      # sed -E "s@proxy_pass http://127\.0\.0\.1:(8081|8082);@proxy_pass http://127.0.0.1:$SERVER_PORT;@g" \
      #   "$NGINX_BACKUP" | sudo tee "$NGINX_CONFIG" > /dev/null
      #
      # if ! reload_nginx; then
      #   log DEPLOY_FAILED "reason=nginx_switch_failed"
      #   log ROLLBACK_STARTED "scope=nginx_configuration previous_port=${ACTIVE_PORT:-none}"
      #
      #   if ! {
      #     sudo tee "$NGINX_CONFIG" < "$NGINX_BACKUP" > /dev/null &&
      #     reload_nginx
      #   }; then
      #     log ROLLBACK_FAILED "reason=nginx_restore_failed"
      #     "${compose[@]}" stop server || true
      #     exit 1
      #   fi
      #
      #   if [[ -n "$ACTIVE_PORT" ]] && healthy "$ACTIVE_PORT"; then
      #     log ROLLBACK_SUCCEEDED "scope=nginx_configuration previous_port=$ACTIVE_PORT"
      #   else
      #     log ROLLBACK_FAILED "reason=previous_server_not_healthy"
      #   fi
      #
      #   "${compose[@]}" stop server || true
      #   echo "Nginx switch failed. Previous configuration restored." >&2
      #   exit 1
      # fi
      #
      # log TRAFFIC_SWITCHED "port=$SERVER_PORT"


      # ====================================================
      # [무중단 배포 비활성화] 기존 컨테이너 종료 대기
      # ====================================================

      # if [[ -n "$ACTIVE_PORT" ]]; then
      #   sleep 10
      #   docker stop "ringout-server-$ACTIVE_PORT" || true
      # fi


      # 일반 배포 성공
      export DEPLOY_SUCCESS=true
      log DEPLOY_SUCCEEDED "port=$SERVER_PORT"
      exit 0
    fi

    if ! read -r container_status oom_killed container_exit < <(
      docker inspect \
        --format '{{.State.Status}} {{.State.OOMKilled}} {{.State.ExitCode}}' \
        "$SERVER_CONTAINER_NAME"
    ); then

      log DEPLOY_FAILED "reason=new_container_missing port=$SERVER_PORT"
      break
    fi

    if [[ "$oom_killed" == true ||
          "$container_status" == exited ||
          "$container_status" == dead ||
          "$container_status" == restarting ]]; then

      log DEPLOY_FAILED \
        "reason=new_container_stopped status=$container_status oom=$oom_killed exit_code=$container_exit"
      break
    fi

    echo "Waiting for server... ($attempt/30)"

    if [[ "$attempt" -lt 30 ]]; then
      sleep 5
    fi
  done
fi

echo "Deployment failed. Check container status and logs below." >&2

"${compose[@]}" ps server || true
"${compose[@]}" logs --tail=200 server || true
"${compose[@]}" stop server || true
exit 1
