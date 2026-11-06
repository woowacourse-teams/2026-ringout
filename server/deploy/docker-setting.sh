#!/usr/bin/env bash
set -Eeuo pipefail

# Ubuntu가 관리하는 Docker Engine과 Compose v2를 설치한다.
sudo apt update
sudo apt install -y docker.io docker-compose-v2
docker compose version

# 서비스 정의를 반영하고 Docker 관련 서비스를 활성화한다.
sudo systemctl daemon-reload
sudo systemctl enable --now containerd.service
sudo systemctl enable --now docker.socket
sudo systemctl enable --now docker.service

# 세 서비스 모두 active인지 확인한다. 비활성 상태면 실행을 중단한다.
sudo systemctl is-active containerd.service
sudo systemctl is-active docker.socket
sudo systemctl is-active docker.service

# Docker 소켓, Client/Server 버전, 실제 컨테이너 실행을 검증한다.
sudo ls -l /run/docker.sock
sudo docker version
sudo docker run --rm hello-world

# ubuntu 사용자가 sudo 없이 Docker를 사용할 수 있도록 설정한다.
sudo usermod -aG docker ubuntu

printf '\nDocker 설정 완료. SSH를 종료(exit)한 뒤 다시 접속하세요.\n'
