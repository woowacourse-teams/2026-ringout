#!/usr/bin/env bash
set -euo pipefail

# 내 컴퓨터에서 실행한다. 서버 간 전송도 내 컴퓨터를 경유한다.
# 원격 파일은 SSH 사용자에게 읽기/쓰기 권한이 있어야 한다(sudo 사용 불가).
read -r -p '출발지 (서버는 사용자@IP, 내 컴퓨터는 엔터): ' source_host
read -r -p '옮길 파일의 전체 경로: ' source_path
read -r -p '도착지 (서버는 사용자@IP, 내 컴퓨터는 엔터): ' destination_host
read -r -p '저장할 폴더의 전체 경로 (이미 존재하는 폴더): ' destination_dir

: "${source_path:?옮길 파일 경로를 입력하세요.}"
: "${destination_dir:?도착 폴더 경로를 입력하세요.}"

options=(-3 -o IdentitiesOnly=yes)
if [[ -n "$source_host" ]]; then
  read -r -p '출발 서버 SSH 키 파일의 로컬 전체 경로 (기본 키는 엔터): ' source_key
  if [[ -n "$source_key" ]]; then
    test -f "$source_key"
    options+=(-i "$source_key")
  fi
fi
if [[ -n "$destination_host" ]]; then
  read -r -p '도착 서버 SSH 키 파일의 로컬 전체 경로 (기본 키는 엔터): ' destination_key
  if [[ -n "$destination_key" ]]; then
    test -f "$destination_key"
    options+=(-i "$destination_key")
  fi
fi

source_file="$source_path"
destination="${destination_dir%/}/"
[[ -z "$source_host" ]] || source_file="${source_host}:${source_path}"
[[ -z "$destination_host" ]] || destination="${destination_host}:${destination}"
scp "${options[@]}" -- "$source_file" "$destination"
printf '\n파일 복사 완료. 원본 파일은 그대로 유지됩니다.\n'
