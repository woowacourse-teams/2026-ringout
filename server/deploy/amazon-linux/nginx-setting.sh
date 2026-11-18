#!/usr/bin/env bash
set -euo pipefail

# 최초 환경 설정용. 배포 후 재실행하면 deploy.sh에서 전환한 포트를 덮어쓸 수 있다.
# 사용법: bash nginx-setting.sh
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
set -a
source /home/ec2-user/ringout/.env
set +a
: "${NGINX_DOMAIN:?NGINX_DOMAIN을 .env에 설정하세요.}"
: "${NGINX_APP_PORT:?NGINX_APP_PORT를 .env에 설정하세요.}"
[[ "$NGINX_DOMAIN" =~ ^[a-zA-Z0-9]([a-zA-Z0-9.-]*[a-zA-Z0-9])?$ ]]
[[ "$NGINX_APP_PORT" =~ ^[0-9]{1,5}$ ]]
(( 10#$NGINX_APP_PORT >= 1 && 10#$NGINX_APP_PORT <= 65535 ))

# 기존 인증서와 Certbot의 공통 TLS 설정 파일을 먼저 이전해야 한다.
for file in \
  "/etc/letsencrypt/live/$NGINX_DOMAIN/fullchain.pem" \
  "/etc/letsencrypt/live/$NGINX_DOMAIN/privkey.pem" \
  /etc/letsencrypt/options-ssl-nginx.conf \
  /etc/letsencrypt/ssl-dhparams.pem; do
  sudo test -s "$file" || { printf '필요한 인증서 파일이 없습니다: %s\n' "$file" >&2; exit 1; }
done

sudo dnf install -y nginx gettext

# EC2에서는 스크립트와 같은 폴더에, 저장소에서는 상위 폴더에 있다.
template="$script_dir/nginx.conf"
if [[ ! -f "$template" ]]; then
  template="$script_dir/../nginx.conf"
fi

# Nginx의 $host, $scheme 등은 유지하고 두 설정값만 치환한다.
temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT
envsubst '${NGINX_DOMAIN} ${NGINX_APP_PORT}' \
  < "$template" > "$temp_dir/ringout-api.conf"

config=/etc/nginx/conf.d/ringout-api.conf
if sudo test -e "$config"; then
  sudo cp -p "$config" "$temp_dir/previous.conf"
fi
sudo install -m 0644 "$temp_dir/ringout-api.conf" "$config"
if ! sudo nginx -t; then
  if sudo test -e "$temp_dir/previous.conf"; then
    sudo cp -p "$temp_dir/previous.conf" "$config"
  else
    sudo rm -- "$config"
  fi
  exit 1
fi
sudo systemctl enable --now nginx
sudo systemctl reload nginx
printf 'Nginx 설정 완료: https://%s\n' "$NGINX_DOMAIN"
