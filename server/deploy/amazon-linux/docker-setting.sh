#!/usr/bin/env bash
set -Eeuo pipefail

# Amazon Linux 2023용 Docker 설치 스크립트.
source /etc/os-release
if [[ "${ID:-}" != "amzn" || "${VERSION_ID:-}" != "2023" ]]; then
  printf 'Amazon Linux 2023에서 실행하세요.\n' >&2
  exit 1
fi

case "$(uname -m)" in
  aarch64) compose_arch=aarch64 ;;
  x86_64) compose_arch=x86_64 ;;
  *) printf '지원하지 않는 CPU 아키텍처입니다.\n' >&2; exit 1 ;;
esac

# Amazon Linux 패키지 저장소의 Docker Engine을 설치한다.
sudo dnf install -y docker

# Compose v2 플러그인을 모든 사용자가 사용할 수 있도록 설치한다.
# 수동 설치이므로 버전 업그레이드도 별도로 수행해야 한다.
compose_version=v2.39.2
if ! sudo docker compose version >/dev/null 2>&1; then
  compose_tmp_dir=$(mktemp -d)
  trap 'rm -rf "$compose_tmp_dir"' EXIT
  compose_asset="docker-compose-linux-${compose_arch}"
  compose_url="https://github.com/docker/compose/releases/download/${compose_version}"
  curl --fail --show-error --location "${compose_url}/${compose_asset}" \
    --output "${compose_tmp_dir}/${compose_asset}"
  curl --fail --show-error --location "${compose_url}/${compose_asset}.sha256" \
    --output "${compose_tmp_dir}/${compose_asset}.sha256"
  (cd "$compose_tmp_dir" && sha256sum --check "${compose_asset}.sha256")
  sudo install -d -m 0755 /usr/local/lib/docker/cli-plugins
  sudo install -m 0755 "${compose_tmp_dir}/${compose_asset}" \
    /usr/local/lib/docker/cli-plugins/docker-compose
fi
sudo docker compose version

# 서비스 정의를 반영하고 Docker 관련 서비스를 활성화한다.
sudo systemctl daemon-reload
sudo systemctl enable --now containerd.service
sudo systemctl enable --now docker.socket
sudo systemctl enable --now docker.service

# 서비스와 실제 컨테이너 실행을 검증한다.
sudo systemctl is-active containerd.service
sudo systemctl is-active docker.socket
sudo systemctl is-active docker.service
sudo ls -l /run/docker.sock
sudo docker version
sudo docker run --rm hello-world

# ec2-user가 sudo 없이 Docker를 사용할 수 있도록 설정한다.
sudo usermod -aG docker ec2-user
printf '\nDocker 설정 완료. SSH를 종료(exit)한 뒤 ec2-user로 다시 접속하세요.\n'
