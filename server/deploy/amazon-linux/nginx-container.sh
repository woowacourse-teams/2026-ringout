#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
deploy_dir=$(dirname -- "$script_dir")
app_dir=/home/ec2-user/ringout
config_dir="$app_dir/nginx"
config="$config_dir/ringout-api.conf"
webroot=/var/www/ringout-acme
compose=(docker compose --project-directory "$app_dir"
  --env-file "$app_dir/.env" -f "$deploy_dir/nginx-compose.yaml")

if systemctl is-active --quiet nginx; then
  printf '호스트 Nginx가 실행 중입니다. 전환 시 sudo systemctl disable --now nginx를 실행하세요.\n' >&2
  exit 1
fi

compose_config=$("${compose[@]}" config --format json)
read -r domain app_port < <(python3 -c '
import json, sys
env = json.load(sys.stdin)["services"]["nginx"]["environment"]
print(env["NGINX_DOMAIN"], env["NGINX_APP_PORT"])
' <<< "$compose_config")
if [[ ! "$domain" =~ ^[a-zA-Z0-9]([a-zA-Z0-9.-]*[a-zA-Z0-9])?$ ||
      ! "$app_port" =~ ^808[12]$ ]]; then
  printf 'NGINX_DOMAIN과 NGINX_APP_PORT(8081 또는 8082)를 확인하세요.\n' >&2
  exit 1
fi

cert_dir="/etc/letsencrypt/live/$domain"
mkdir -p "$config_dir"
exec 8>"$app_dir/deployment.lock"
flock -w 600 8 || { printf '배포가 끝난 뒤 Nginx 설정을 다시 실행하세요.\n' >&2; exit 1; }
sudo install -d -m 0755 "$webroot"
temp_config=$(mktemp "$config_dir/.ringout-api.XXXXXX")
backup=$(mktemp "$config_dir/.ringout-backup.XXXXXX")
trap 'rm -f -- "$temp_config" "$backup"' EXIT

# 재실행 시 deploy.sh가 전환한 포트를 사용한다.
if [[ -f "$config" ]]; then
  active_port=$(sed -nE 's/^[[:space:]]*proxy_pass http:\/\/127\.0\.0\.1:(808[12]);$/\1/p' "$config")
  if [[ "$active_port" =~ ^808[12]$ ]]; then
    app_port="$active_port"
  fi
fi

apply_config() {
  if [[ -f "$config" ]] && cmp -s "$temp_config" "$config"; then
    printf '[건너뜀] Nginx 설정이 이미 적용되어 있습니다.\n'
    "${compose[@]}" up -d nginx
    return
  fi
  if [[ -f "$config" ]]; then
    cp -- "$config" "$backup"
  else
    : > "$backup"
  fi
  cp -- "$temp_config" "$config"
  if ! { "${compose[@]}" run --rm -T --no-deps --entrypoint nginx nginx -t &&
         "${compose[@]}" up -d nginx &&
         "${compose[@]}" exec -T nginx nginx -s reload; }; then
    if [[ -s "$backup" ]]; then
      cp -- "$backup" "$config"
      "${compose[@]}" exec -T nginx nginx -s reload || true
    else
      rm -- "$config"
    fi
    return 1
  fi
}

render_https() {
  "${compose[@]}" run --rm -T --no-deps -e "NGINX_APP_PORT=$app_port" \
    --entrypoint envsubst nginx '${NGINX_DOMAIN} ${NGINX_APP_PORT}' \
    < "$deploy_dir/nginx.conf" > "$temp_config"
  apply_config
}

ensure_certbot() {
  command -v certbot >/dev/null || sudo dnf install -y certbot
}

# 인증서가 없으면 HTTP 검증 경로만 먼저 활성화한다.
if ! sudo test -s "$cert_dir/fullchain.pem" || ! sudo test -s "$cert_dir/privkey.pem"; then
  read -r -p 'Let’s Encrypt 등록 이메일: ' cert_email
  if [[ -z "$cert_email" ]]; then
    printf '[수동 작업 필요] 등록 이메일을 준비하고 다시 실행하세요.\n' >&2
    exit 1
  fi
  ensure_certbot
  cat > "$temp_config" <<HTTP
server {
    listen 80;
    listen [::]:80;
    server_name $domain;
    location ^~ /.well-known/acme-challenge/ {
        root $webroot;
        try_files \$uri =404;
    }
    location / { return 404; }
}
HTTP
  apply_config
  if ! sudo certbot certonly --webroot -w "$webroot" --cert-name "$domain" -d "$domain" \
    --non-interactive --agree-tos --email "$cert_email" --keep-until-expiring; then
    printf '[수동 작업 필요] %s 인증서를 발급하지 못했습니다.\n' "$domain" >&2
    printf '위 Certbot 오류와 도메인이 새 EC2를 가리키는지, TCP 80이 열려 있는지 확인하고 다시 실행하세요.\n' >&2
    exit 1
  fi
  render_https
else
  # 만료된 인증서도 먼저 HTTPS 설정을 올려 HTTP 검증 경로를 사용할 수 있게 한다.
  if [[ ! -f "$config" ]] || ! grep -Fq "ssl_certificate $cert_dir/fullchain.pem;" "$config" ||
     ! grep -Fq 'location ^~ /.well-known/acme-challenge/' "$config" ||
     ! grep -Fq 'client_max_body_size 10m;' "$config"; then
    render_https
  else
    printf '[건너뜀] HTTPS Nginx 설정이 이미 있습니다.\n'
    "${compose[@]}" up -d nginx
  fi
  if sudo openssl x509 -checkend 0 -noout -in "$cert_dir/fullchain.pem" >/dev/null; then
    printf '[건너뜀] 기존 HTTPS 인증서를 사용합니다.\n'
  else
    ensure_certbot
    sudo certbot renew --cert-name "$domain" --webroot -w "$webroot" --non-interactive
    "${compose[@]}" exec -T nginx nginx -s reload
  fi
fi

# Certbot이 관리하는 이 도메인의 인증서만 자동 갱신한다.
if sudo test -f "/etc/letsencrypt/renewal/$domain.conf"; then
  ensure_certbot
  timer="ringout-certbot@$domain.timer"
  if systemctl is-enabled --quiet "$timer" && systemctl is-active --quiet "$timer"; then
    printf '[건너뜀] 인증서 자동 갱신이 이미 설정되어 있습니다.\n'
  else
    sudo tee /etc/systemd/system/ringout-certbot@.service >/dev/null <<'SERVICE'
[Unit]
Description=Renew RingOut HTTPS certificate for %i
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/bin/certbot renew --non-interactive --cert-name %i --webroot -w /var/www/ringout-acme --deploy-hook "/usr/bin/docker exec ringout-nginx nginx -s reload"
SERVICE
    sudo tee /etc/systemd/system/ringout-certbot@.timer >/dev/null <<'TIMER'
[Unit]
Description=Schedule RingOut HTTPS certificate renewal for %i

[Timer]
OnCalendar=*-*-* 00,12:00:00
RandomizedDelaySec=30m
Persistent=true

[Install]
WantedBy=timers.target
TIMER
    sudo systemctl daemon-reload
    sudo systemctl enable --now "$timer"
  fi
else
  printf '기존 인증서의 Certbot 갱신 설정을 찾지 못했습니다. 자동 갱신은 별도로 설정하세요.\n' >&2
fi

"${compose[@]}" exec -T nginx nginx -t
"${compose[@]}" ps nginx
