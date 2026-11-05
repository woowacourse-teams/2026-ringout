#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
deploy_dir=$(dirname -- "$script_dir")
app_dir=/home/ec2-user/ringout
config_dir="$app_dir/nginx"
config="$config_dir/ringout-api.conf"
compose=(docker compose --project-directory "$app_dir"
  --env-file "$app_dir/.env" -f "$deploy_dir/nginx-compose.yaml")

if systemctl is-active --quiet nginx; then
  printf '호스트 Nginx가 실행 중입니다. 전환 시 sudo systemctl disable --now nginx를 실행하세요.\n' >&2
  exit 1
fi

mkdir -p "$config_dir"
# 재실행할 때 deploy.sh가 전환한 포트를 유지한다.
if [[ ! -f "$config" ]]; then
  temp_config=$(mktemp "$config_dir/.ringout-api.XXXXXX")
  trap 'rm -f -- "$temp_config"' EXIT
  "${compose[@]}" run --rm -T --no-deps --entrypoint envsubst nginx \
    '${NGINX_DOMAIN} ${NGINX_APP_PORT}' < "$deploy_dir/nginx.conf" > "$temp_config"
  if ! grep -Eq 'proxy_pass http://127\.0\.0\.1:(8081|8082);' "$temp_config"; then
    printf 'NGINX_APP_PORT는 8081 또는 8082로 설정하세요.\n' >&2
    exit 1
  fi
  mv -- "$temp_config" "$config"
  if ! "${compose[@]}" run --rm -T --no-deps --entrypoint nginx nginx -t; then
    rm -- "$config"
    exit 1
  fi
fi

"${compose[@]}" up -d nginx
"${compose[@]}" exec -T nginx nginx -t
"${compose[@]}" ps nginx
