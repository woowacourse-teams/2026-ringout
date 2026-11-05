#!/usr/bin/env bash
set -euo pipefail

# 새 DB EC2에서 ec2-user로 실행한다.
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
mysql_dir=/home/ec2-user/mysql
compose_file="$script_dir/../docker-compose.yaml"

if [[ "$(id -un)" != ec2-user ]]; then
  printf 'ec2-user로 접속한 뒤 sudo 없이 실행하세요.\n' >&2
  exit 1
fi

if [[ ! -r "$mysql_dir/.env" || ! -r "$compose_file" ]]; then
  printf '먼저 %s/.env와 %s를 준비하세요.\n' "$mysql_dir" "$compose_file" >&2
  exit 1
fi

bash "$script_dir/docker-setting.sh"

# 새 프로세스에서 docker 그룹 권한을 적용한다. 로그인 입력은 터미널에서 받는다.
sudo -H -u ec2-user -- bash "$script_dir/docker-login.sh"
compose=(sudo -H -u ec2-user -- docker compose
  --project-directory "$mysql_dir" --env-file "$mysql_dir/.env" -f "$compose_file")
"${compose[@]}" config --quiet
"${compose[@]}" up -d
"${compose[@]}" ps
