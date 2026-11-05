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

# Git 버전과 서버에 준비한 .env·인증서 경로 입력
read -r -p '가져올 Git 브랜치 또는 커밋 SHA [develop]: ' git_ref
git_ref="${git_ref:-develop}"
read -r -e -p "직접 준비한 .env 경로 [$app_dir/.env]: " env_file
env_file="${env_file:-$app_dir/.env}"
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
git init --quiet "$source_dir"
git -C "$source_dir" fetch --depth=1 -- "$repo_url" "$git_ref"
git -C "$source_dir" checkout --detach FETCH_HEAD

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
fi

# Docker·Compose와 runner에 필요한 libicu 설치
bash "$scripts/docker-setting.sh"
sudo dnf install -y libicu

# Docker 그룹 권한을 새 프로세스에 적용해 Compose 설정을 검사한다.
sudo -H -u ec2-user -- docker compose --project-directory "$app_dir" \
  --env-file "$app_dir/.env" -f "$server_dir/docker-compose.yaml" config --quiet

# HTTPS 인증서 복원 및 Nginx 컨테이너 시작
if [[ -n "$cert_archive" ]]; then
  sudo tar -xzf "$cert_archive" -C /etc
fi
sudo -H -u ec2-user -- bash "$scripts/nginx-container.sh"

# Spring 배포 파일 배치
install -m 0644 "$server_dir/docker-compose.yaml" "$app_dir/docker-compose.yaml"
install -m 0755 "$scripts/deploy.sh" "$app_dir/deploy.sh"
install -m 0755 "$deploy_dir/deployment.py" "$app_dir/deployment.py"
# 배포 계정으로 Docker Hub 로그인
sudo -H -u ec2-user -- bash "$scripts/docker-login.sh"

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

read -r -p 'GitHub Actions runner를 설치하고 시작할까요? [y/N]: ' setup_runner_answer
if [[ "$setup_runner_answer" == [yY] ]]; then
  setup_runner
  printf 'GitHub에서 새 runner가 Idle인지 확인하세요. 환경 라벨은 전환 시 추가하세요.\n'
fi

printf '\n초기 설정 완료. SSH에 다시 접속한 뒤 첫 배포를 진행하세요.\n'
