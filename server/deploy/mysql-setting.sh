#!/usr/bin/env bash
# Run on the Ubuntu database EC2 as ubuntu: ./mysql-setting.sh
###########################################
# $ sudo chmod +x mysql-setting.sh
# $ ./mysql-setting.sh
###########################################
# EC2에서 초기 mysql 환경 설정 파일입니다.
# 실행시키고 exit 한 후 docker ps로 설치를 확인합니다.

set -euo pipefail

source /etc/os-release
if [[ "$ID" != ubuntu || "$(id -un)" != ubuntu ]]; then
  echo "Run this script as ubuntu on the Ubuntu DB EC2, without sudo." >&2
  exit 1
fi
if [[ "$VERSION_ID" != 26.04 || "$(dpkg --print-architecture)" != arm64 ]]; then
  echo "Production uses Ubuntu 26.04 ARM64. Create the DB EC2 with the same OS and architecture." >&2
  exit 1
fi

# 운영 서버와 같은 버전만 설치. 이미 같은 버전이면 생략하고, 다른 버전이면 중단.
packages=()
for package in \
  'docker-ce=5:29.7.2-1~ubuntu.26.04~resolute' \
  'docker-ce-cli=5:29.7.2-1~ubuntu.26.04~resolute' \
  'containerd.io=2.3.3-1~ubuntu.26.04~resolute' \
  'docker-compose-plugin=5.4.0-1~ubuntu.26.04~resolute'; do
  name="${package%%=*}"
  version="${package#*=}"
  installed="$(dpkg-query -W -f='${db:Status-Status} ${Version}' "$name" 2>/dev/null || true)"
  if [[ "$installed" == "installed $version" ]]; then
    echo "$name already matches production; skipping."
  elif [[ "$installed" == installed\ * ]]; then
    echo "$name differs from production ($installed). No automatic replacement was performed." >&2
    exit 1
  else
    packages+=("$package")
  fi
done

# 1. 누락된 패키지만 Docker 공식 저장소에서 지정 버전으로 설치
if [[ "${#packages[@]}" -gt 0 ]]; then
  if [[ ! -f /etc/apt/sources.list.d/docker.sources && ! -f /etc/apt/sources.list.d/docker.list ]]; then
    sudo apt-get update
    sudo apt-get install --no-remove -y ca-certificates curl
    sudo install -m 0755 -d /etc/apt/keyrings
    sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    sudo chmod a+r /etc/apt/keyrings/docker.asc
    sudo tee /etc/apt/sources.list.d/docker.sources > /dev/null <<DOCKER
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: ${UBUNTU_CODENAME:-$VERSION_CODENAME}
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
DOCKER
  fi
  sudo apt-get update
  sudo apt-get install --no-remove -y "${packages[@]}"
fi

# 2. 실행 중인 Docker는 재시작하지 않고, ubuntu의 실행 권한만 추가
if ! systemctl is-enabled --quiet docker; then sudo systemctl enable docker; fi
if ! systemctl is-active --quiet docker; then sudo systemctl start docker; fi
if [[ " $(id -nG ubuntu) " != *" docker "* ]]; then
  sudo usermod -aG docker ubuntu
  echo "Docker group added. Reconnect SSH before running docker without sudo."
fi

# 3. Docker 엔진과 테스트 컨테이너 실행 확인
sudo docker info > /dev/null
engine_version="$(sudo docker version --format '{{.Server.Version}}')"
compose_version="$(sudo docker compose version --short)"
if [[ "$engine_version" != 29.7.2 || "${compose_version#v}" != 5.4.0 ]]; then
  echo "Running Docker/Compose versions differ from production. Check the running daemon." >&2
  exit 1
fi
sudo docker --version
sudo docker compose version
sudo docker run --rm hello-world

echo "Docker initial setup completed."
