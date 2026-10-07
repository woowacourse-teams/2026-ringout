"""Upload a verified main AAB as a production draft; never roll out the new release."""

import argparse
import hashlib
import os
from pathlib import Path

from android_internal_cd import API, APPLICATION_ID, CDError, gate, required, request_json, verify
from production_config import require_main


def production_releases(releases, version):
    for release in releases:
        if version in [str(code) for code in release.get('versionCodes', [])]:
            # Never demote a release that an operator already submitted or published.
            return None
    if any(release.get('status') in ('draft', 'inProgress', 'halted') for release in releases):
        raise CDError('운영 초안 또는 단계적 출시가 이미 있습니다. Play Console에서 정리 후 재실행하세요.')
    return releases + [{'status': 'draft', 'versionCodes': [version]}]


def publish():
    require_main()
    token = required('GOOGLE_PLAY_ACCESS_TOKEN')
    aab = Path(required('VERIFIED_AAB_PATH'))
    version = required('VERIFIED_VERSION_CODE')
    if not version.isdigit() or int(version) <= 0 or not aab.is_file():
        raise CDError('검증된 AAB 및 버전이 필요합니다.')
    checksum = hashlib.sha256(aab.read_bytes()).hexdigest()
    base = f'{API}/androidpublisher/v3/applications/{APPLICATION_ID}/edits'
    edit_id = request_json(base, token, 'POST', {}).get('id', '')
    import re
    if not re.fullmatch(r'[A-Za-z0-9_-]+', edit_id):
        raise CDError('잘못된 Play edit ID')
    edit_url = f'{base}/{edit_id}'
    track = request_json(f'{edit_url}/tracks/production', token)
    releases = production_releases(track.get('releases', []), version)
    bundles = request_json(f'{edit_url}/bundles', token).get('bundles', [])
    existing = next((bundle for bundle in bundles if str(bundle.get('versionCode')) == version), None)
    if existing is not None:
        if existing.get('sha256', '').lower() != checksum:
            raise CDError('같은 versionCode에 다른 AAB가 업로드되어 있습니다. 빌드 번호를 올려주세요.')
    elif releases is None:
        raise CDError('기존 릴리스 AAB 체크섬을 확인할 수 없습니다.')
    else:
        url = f'{API}/upload/androidpublisher/v3/applications/{APPLICATION_ID}/edits/{edit_id}/bundles?uploadType=media'
        bundle = request_json(url, token, 'POST', aab.read_bytes(), 'application/octet-stream', timeout=600)
        if str(bundle.get('versionCode')) != version or bundle.get('sha256', '').lower() != checksum:
            raise CDError('업로드 응답의 버전 또는 체크섬이 다릅니다.')
    if releases is not None:
        request_json(f'{edit_url}/tracks/production', token, 'PUT', {'track': 'production', 'releases': releases})
        result = request_json(f'{edit_url}:commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW', token, 'POST')
        if result.get('id') != edit_id:
            raise CDError('Play commit 응답이 다릅니다.')
    summary = f'Google Play production 업로드 확인: versionCode {version}, commit {required("GITHUB_SHA")}. 새 릴리스는 자동 공개하지 않습니다. Play Console에서 심사·게시 상태를 확인하세요.'
    print(summary)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as stream:
            stream.write(summary + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('gate', 'verify', 'publish'))
    command = parser.parse_args().command
    require_main()
    {'gate': lambda: gate('main'), 'verify': lambda: verify('main'), 'publish': publish}[command]()
