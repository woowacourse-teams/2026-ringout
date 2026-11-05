#!/usr/bin/env bash
set -euo pipefail

# 실행 환경 확인
source /etc/os-release
if [[ "${ID:-}" != amzn || "${VERSION_ID:-}" != 2023 || "$(id -un)" != ec2-user ]]; then
  printf 'Amazon Linux 2023에서 ec2-user로 접속해 실행하세요.\n' >&2
  exit 1
fi

app_dir=/home/ec2-user/ringout
source_dir="$app_dir/.bootstrap-source"
repo_url=https://github.com/woowacourse-teams/2026-ringout.git
umask 077
mkdir -p "$app_dir"
exec 9>"$app_dir/.bootstrap.lock"
flock -n 9 || { printf '다른 초기 설정이 실행 중입니다.\n' >&2; exit 1; }

skip() {
  printf '[건너뜀] %s\n' "$1"
}

# 새 서버에 3 GiB swap을 만들고 재부팅 후에도 활성화한다.
setup_swap() (
  swap_file=/swapfile
  if sudo swapon --show=NAME --noheadings --raw | grep -Fxq "$swap_file"; then
    skip 'swap이 이미 활성화되어 있습니다.'
  else
    if ! sudo test -e "$swap_file"; then
      temp_swap=$(sudo mktemp /swapfile.XXXXXX)
      trap 'sudo rm -f -- "$temp_swap"' EXIT
      sudo dd if=/dev/zero of="$temp_swap" bs=1M count=3072 status=progress
      sudo mkswap "$temp_swap"
      sudo mv -- "$temp_swap" "$swap_file"
    elif [[ "$(sudo blkid -p -s TYPE -o value "$swap_file")" != swap ]]; then
      printf '%s 파일이 swap 형식이 아닙니다. 기존 파일을 확인하세요.\n' "$swap_file" >&2
      exit 1
    fi
    sudo chmod 600 "$swap_file"
    sudo swapon "$swap_file"
  fi

  if ! grep -Eq '^[[:blank:]]*/swapfile[[:blank:]]+[^[:blank:]]+[[:blank:]]+swap([[:blank:]]|$)' /etc/fstab; then
    printf '/swapfile none swap sw 0 0\n' | sudo tee -a /etc/fstab >/dev/null
  fi
  sudo swapon --show
)

# Git 버전과 서버에 준비한 .env·인증서 경로 입력
read -r -p '가져올 Git 브랜치 또는 커밋 SHA [develop]: ' git_ref
git_ref="${git_ref:-develop}"
env_file="$app_dir/.env"
if [[ ! -s "$env_file" || ! -r "$env_file" ]]; then
  read -r -e -p "직접 준비한 .env 경로 [$env_file]: " input
  env_file="${input:-$env_file}"
fi
if [[ ! -s "$env_file" || ! -r "$env_file" ]]; then
  printf '%s에 .env를 직접 작성한 뒤 다시 실행하세요.\n' "$env_file" >&2
  exit 1
fi

cert_archive=
if ! sudo test -d /etc/letsencrypt; then
  default_archive=/home/ec2-user/letsencrypt.tar.gz
  read -r -e -p "인증서 압축 파일 경로 [$default_archive]: " cert_archive
  cert_archive="${cert_archive:-$default_archive}"
  if [[ ! -s "$cert_archive" || ! -r "$cert_archive" ]]; then
    printf '읽을 수 있는 인증서 압축 파일을 먼저 준비하세요.\n' >&2
    exit 1
  fi
fi

# Git에서 선택한 버전의 배포 파일 가져오기
command -v git >/dev/null || sudo dnf install -y git
if [[ ! -d "$source_dir/.git" ]]; then
  git init --quiet "$source_dir"
fi
git -C "$source_dir" fetch --depth=1 -- "$repo_url" "$git_ref"
if [[ "$(git -C "$source_dir" rev-parse --verify HEAD 2>/dev/null || true)" == "$(git -C "$source_dir" rev-parse FETCH_HEAD)" ]]; then
  skip '요청한 Git 버전이 이미 배치되어 있습니다.'
else
  git -C "$source_dir" checkout --detach FETCH_HEAD
fi

server_dir="$source_dir/server"
deploy_dir="$server_dir/deploy"
scripts="$deploy_dir/amazon-linux"
for file in "$server_dir/docker-compose.yaml" "$deploy_dir/deployment.py" \
  "$deploy_dir/nginx.conf" "$deploy_dir/nginx-compose.yaml" "$scripts/deploy.sh" \
  "$scripts/docker-setting.sh" "$scripts/docker-login.sh" "$scripts/nginx-container.sh"; do
  if [[ ! -r "$file" ]]; then
    printf '선택한 Git 버전에 필요한 파일이 없습니다: %s\n' "$file" >&2
    exit 1
  fi
done

# 직접 준비한 .env 배치
if [[ ! "$env_file" -ef "$app_dir/.env" ]]; then
  install -m 0600 "$env_file" "$app_dir/.env"
else
  skip '기존 .env를 사용합니다.'
fi

setup_swap

# Docker·Compose와 runner에 필요한 libicu 설치
docker_as_deployer=(sudo -H -u ec2-user -- docker)
if "${docker_as_deployer[@]}" info >/dev/null 2>&1 &&
   "${docker_as_deployer[@]}" compose version >/dev/null 2>&1; then
  skip 'Docker와 Compose를 사용할 수 있습니다.'
else
  bash "$scripts/docker-setting.sh"
fi
if ! rpm -q libicu >/dev/null; then
  sudo dnf install -y libicu
fi

# Docker 그룹 권한을 새 프로세스에 적용해 Compose 설정을 검사한다.
sudo -H -u ec2-user -- docker compose --project-directory "$app_dir" \
  --env-file "$app_dir/.env" -f "$server_dir/docker-compose.yaml" config --quiet

# HTTPS 인증서 복원 및 Nginx 컨테이너 시작
if [[ -n "$cert_archive" ]]; then
  sudo tar -xzf "$cert_archive" -C /etc
else
  skip '기존 인증서를 사용합니다.'
fi
if [[ -f "$app_dir/nginx/ringout-api.conf" ]] &&
   "${docker_as_deployer[@]}" exec ringout-nginx nginx -t >/dev/null 2>&1; then
  skip 'Nginx 컨테이너가 실행 중입니다.'
else
  sudo -H -u ec2-user -- bash "$scripts/nginx-container.sh"
fi

# Spring 배포 파일 배치
install_if_changed() {
  local mode=$1 source_file=$2 destination=$3
  if cmp -s "$source_file" "$destination"; then
    skip "${destination##*/} 내용이 같습니다."
  else
    install -m "$mode" "$source_file" "$destination"
  fi
}
install_if_changed 0644 "$server_dir/docker-compose.yaml" "$app_dir/docker-compose.yaml"
install_if_changed 0755 "$scripts/deploy.sh" "$app_dir/deploy.sh"
install_if_changed 0755 "$deploy_dir/deployment.py" "$app_dir/deployment.py"

# Docker Hub 로그인 설정이 이미 있으면 토큰 입력을 생략한다.
if python3 - <<'PY_AUTH'
import json
from pathlib import Path

try:
    config = json.loads((Path.home() / '.docker/config.json').read_text())
except (OSError, ValueError):
    raise SystemExit(1)
auth = config.get('auths') or {}
entries = (auth.get(host) or {} for host in ('https://index.docker.io/v1/', 'docker.io'))
raise SystemExit(0 if any(entry.get('auth') or entry.get('identitytoken') for entry in entries) else 1)
PY_AUTH
then
  skip 'Docker Hub 로그인 정보가 이미 있습니다.'
else
  sudo -H -u ec2-user -- bash "$scripts/docker-login.sh"
fi

# GitHub Actions runner 설치·등록 및 서비스 시작
setup_runner() (
  runner_dir=/home/ec2-user/actions-runner
  mkdir -p "$runner_dir"
  cd "$runner_dir"

  if [[ ! -f .runner ]]; then
    case "$(uname -m)" in
      aarch64) runner_arch=arm64 ;;
      x86_64) runner_arch=x64 ;;
      *) printf '지원하지 않는 runner 아키텍처입니다.\n' >&2; exit 1 ;;
    esac

    read -r runner_url runner_checksum < <(
      curl --fail --silent --show-error --location \
        https://api.github.com/repos/actions/runner/releases/latest |
        python3 -c '
import json, re, sys
arch = sys.argv[1]
release = json.load(sys.stdin)
for asset in release["assets"]:
    if re.fullmatch(rf"actions-runner-linux-{arch}-[0-9.]+\.tar\.gz", asset["name"]):
        digest = asset.get("digest") or ""
        if re.fullmatch(r"sha256:[a-f0-9]{64}", digest):
            print(asset["browser_download_url"], digest.removeprefix("sha256:"))
            sys.exit(0)
sys.exit("GitHub runner 릴리스에서 설치 파일과 체크섬을 찾지 못했습니다.")
' "$runner_arch"
    )

    archive=$(mktemp)
    trap 'rm -f -- "$archive"' EXIT
    curl --fail --silent --show-error --location "$runner_url" -o "$archive"
    printf '%s  %s\n' "$runner_checksum" "$archive" | sha256sum --check
    tar -xzf "$archive"

    read -r -p "Runner 이름 [$(hostname)]: " runner_name
    runner_name="${runner_name:-$(hostname)}"
    printf 'GitHub 저장소의 Settings → Actions → Runners → New self-hosted runner에서 등록 토큰을 확인하세요.\n'
    read -r -s -p 'Runner 등록 토큰: ' runner_token
    printf '\n'
    : "${runner_token:?Runner 등록 토큰을 입력하세요.}"
    ACTIONS_RUNNER_INPUT_TOKEN="$runner_token" ./config.sh --unattended \
      --url "${repo_url%.git}" --name "$runner_name"
    unset runner_token
  fi

  if [[ ! -f .service ]]; then
    sudo ./svc.sh install ec2-user
  fi
  sudo ./svc.sh start
  sudo ./svc.sh status
)

runner_service=/home/ec2-user/actions-runner/.service
if [[ -s "$runner_service" ]] && systemctl is-active --quiet "$(cat "$runner_service")"; then
  skip 'GitHub Actions runner 서비스가 실행 중입니다.'
else
  read -r -p 'GitHub Actions runner를 설치하고 시작할까요? [y/N]: ' setup_runner_answer
  if [[ "$setup_runner_answer" == [yY] ]]; then
    setup_runner
    printf 'GitHub에서 새 runner가 Idle인지 확인하세요. 환경 라벨은 전환 시 추가하세요.\n'
  fi
fi

printf '\n초기 설정 완료. SSH에 다시 접속한 뒤 첫 배포를 진행하세요.\n'
