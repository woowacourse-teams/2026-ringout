#!/usr/bin/env bash
set -e

# ECR 전용 저장 도구 설정을 제거한다.
python3 - <<'PY'
import json
import os
from pathlib import Path

path = Path(os.environ.get('DOCKER_CONFIG', str(Path.home() / '.docker'))) / 'config.json'
if path.exists():
    config = json.loads(path.read_text())
    if config.get('credsStore') == 'ecr-login':
        del config['credsStore']
        path.write_text(json.dumps(config, indent=2) + '\n')
        path.chmod(0o600)
PY

# sudo 없이 실행하고, 프롬프트에 읽기 전용 토큰을 입력한다.
read -r -p "Docker Hub ID: " dockerhub_id
docker login --username "$dockerhub_id"
