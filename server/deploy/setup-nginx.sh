#!/usr/bin/env bash
set -euo pipefail

# Idempotently installs and configures nginx as a reverse proxy in front of the
# Spring Boot container (published on 127.0.0.1:$APP_PORT), and provisions/renews
# a Let's Encrypt TLS certificate for $DOMAIN via certbot's nginx plugin.
#
# Required environment variables:
#   DOMAIN         - e.g. api.ringout.my
#   CERTBOT_EMAIL  - contact email(s) for Let's Encrypt expiry notices (comma-separated ok)
# Optional:
#   APP_PORT       - upstream port the Spring container publishes (default: 8080)
#
# Safe to run repeatedly: it only writes the initial HTTP server block once
# (certbot rewrites the file afterwards to add the HTTPS block + redirect,
# and this script never overwrites an existing config), and certbot itself
# no-ops when a valid certificate already exists.

: "${DOMAIN:?DOMAIN is required, e.g. api.ringout.my}"
: "${CERTBOT_EMAIL:?CERTBOT_EMAIL is required}"
APP_PORT="${APP_PORT:-8080}"

if ! sudo -n true 2>/dev/null; then
  echo "This script requires passwordless sudo for the current user (needed for apt/nginx/certbot)." >&2
  exit 1
fi

SITE_AVAILABLE="/etc/nginx/sites-available/ringout-api.conf"
SITE_ENABLED="/etc/nginx/sites-enabled/ringout-api.conf"

echo "Installing nginx and certbot..."
sudo apt-get update -y
sudo apt-get install -y nginx certbot python3-certbot-nginx

if [ -f /etc/nginx/sites-enabled/default ]; then
  echo "Disabling default nginx site..."
  sudo rm -f /etc/nginx/sites-enabled/default
fi

if [ ! -f "$SITE_AVAILABLE" ]; then
  echo "Writing initial HTTP server block for $DOMAIN..."
  sudo tee "$SITE_AVAILABLE" > /dev/null <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN;

    location / {
        proxy_pass http://127.0.0.1:$APP_PORT;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF
else
  echo "Existing nginx site config found at $SITE_AVAILABLE, leaving it untouched."
fi

sudo ln -sf "$SITE_AVAILABLE" "$SITE_ENABLED"

echo "Validating nginx configuration..."
sudo nginx -t

echo "Reloading nginx..."
sudo systemctl enable nginx
sudo systemctl reload nginx 2>/dev/null || sudo systemctl restart nginx

echo "Requesting/renewing TLS certificate via certbot..."
sudo certbot --nginx \
  --non-interactive \
  --agree-tos \
  --redirect \
  -m "$CERTBOT_EMAIL" \
  -d "$DOMAIN"

echo "Ensuring certbot auto-renewal timer is enabled..."
sudo systemctl enable --now certbot.timer

echo "nginx + HTTPS setup complete for https://$DOMAIN"
