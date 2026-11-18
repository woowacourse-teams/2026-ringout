#!/usr/bin/env python3
"""Record deployment history and retry outbox delivery."""
import datetime
import json
import os
import re
from pathlib import Path
import sys
import urllib.error
import urllib.request


def deliver(directory, token, ports):
    if not token:
        print('Deployment history queued: DEPLOYMENT_INGEST_TOKEN is not configured.', file=sys.stderr)
        return
    for path in sorted(directory.glob('*.json')):
        payload = path.read_bytes()
        delivered = False
        for port in ports:
            request = urllib.request.Request(
                'http://127.0.0.1:%s/api/v1/internal/deployments' % port,
                data=payload, method='POST',
                headers={'Content-Type': 'application/json', 'X-Deployment-Token': token})
            try:
                with urllib.request.urlopen(request, timeout=4) as response:
                    if response.status == 204:
                        delivered = True
                        break
            except (OSError, urllib.error.URLError):
                pass
        if delivered:
            path.unlink()
        else:
            print('Deployment history delivery failed; retained %s' % path.name, file=sys.stderr)
            break  # Keep retries bounded when the API or its credentials are unavailable.


def collect_schema(token, ports):
    if not token:
        return {}
    for port in ports:
        request = urllib.request.Request(
            'http://127.0.0.1:%s/api/v1/internal/deployments/schema' % port,
            headers={'X-Deployment-Token': token})
        try:
            with urllib.request.urlopen(request, timeout=4) as response:
                data = json.load(response)
            if not isinstance(data, dict):
                continue
            version, fingerprint = data.get('version'), data.get('fingerprint')
            if isinstance(version, str) and len(version) <= 100 and isinstance(fingerprint, str) and re.fullmatch(r'[0-9a-f]{64}', fingerprint):
                return {'flywayVersion': version, 'flywayFingerprint': fingerprint}
        except (OSError, ValueError, urllib.error.URLError):
            pass
    return {}


def api_ports():
    return list(dict.fromkeys([os.environ.get('SERVER_PORT', '8081'), '8081', '8082']))


def main():
    os.umask(0o077)
    directory = Path(os.environ['APP_DIR']) / 'deployment-outbox'
    directory.mkdir(mode=0o700, exist_ok=True)
    deployment_id = os.environ['DEPLOYMENT_ID']
    payload = {
        'deploymentId': deployment_id,
        'environment': os.environ.get('APP_ENV', 'production'),
        'image': os.environ.get('ROLLBACK_TARGET_IMAGE') or os.environ.get('SERVER_IMAGE', 'unknown'),
        'imageId': os.environ.get('DEPLOY_IMAGE_ID') or None,
        'previousImage': os.environ.get('PREVIOUS_IMAGE') or None,
        'previousImageId': os.environ.get('PREVIOUS_IMAGE_ID') or None,
        'imageDigest': os.environ.get('DEPLOY_IMAGE_DIGEST') or None,
        'previousImageDigest': os.environ.get('PREVIOUS_IMAGE_DIGEST') or None,
        'status': 'SUCCEEDED' if os.environ.get('DEPLOY_SUCCESS') == 'true' else 'FAILED',
        'startedAt': os.environ['DEPLOY_STARTED_AT'],
        'finishedAt': datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),
        'events': Path(os.environ['DEPLOY_EVENTS_FILE']).read_text()[-65535:],
        'actionsRunId': int(os.environ['GITHUB_RUN_ID']) if os.environ.get('GITHUB_RUN_ID') else None,
        'rollbackSourceId': int(os.environ['ROLLBACK_SOURCE_ID']) if os.environ.get('ROLLBACK_SOURCE_ID') else None,
    }
    # Capture now, before persisting the outbox. Delayed delivery must not recalculate historical state.
    payload.update(collect_schema(os.environ.get('DEPLOYMENT_INGEST_TOKEN', ''), api_ports()))
    temporary = directory / (deployment_id + '.tmp')
    temporary.write_text(json.dumps(payload))
    temporary.replace(directory / (deployment_id + '.json'))
    # 수집 API는 Spring에 있다. 방금 배포한 포트를 먼저, 실패했으면 기존 포트로 보낸다.
    deliver(directory, os.environ.get('DEPLOYMENT_INGEST_TOKEN', ''), api_ports())


if __name__ == '__main__':
    if sys.argv[1:] == ['record']:
        main()
    elif sys.argv[1:] == ['retry']:
        deliver(Path(os.environ['APP_DIR']) / 'deployment-outbox',
                os.environ.get('DEPLOYMENT_INGEST_TOKEN', ''), api_ports())
    else:
        sys.exit('Usage: deployment.py {record|retry}')
