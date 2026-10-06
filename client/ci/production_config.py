"""Prepare deterministic production inputs in the disposable CI checkout only."""

import argparse
import os
from pathlib import Path
import plistlib
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ANDROID_PATH = 'androidApp/build.gradle.kts'
IOS_PATH = 'iosApp/iosApp.xcodeproj/project.pbxproj'
PRODUCTION_API = 'https://api.ringout.my'


def require_main():
    if os.environ.get('GITHUB_REF') != 'refs/heads/main' or os.environ.get('GITHUB_EVENT_NAME') != 'push':
        raise ValueError('Production CD accepts main push runs only; recover using Re-run jobs')


def validate_build_number(number):
    value = int(number)
    if not 0 < value <= 2_100_000_000:
        raise ValueError('Production build number must fit the Play versionCode range')
    return str(value)


def source(path):
    # Read committed inputs, so reruns use the same declared version.
    return subprocess.run(['git', 'show', f'HEAD:client/{path}'], cwd=ROOT,
                          check=True, capture_output=True, text=True).stdout


def prepare(platform):
    require_main()
    from api_config import require_production_url
    require_production_url()
    if platform == 'android':
        text = source(ANDROID_PATH)
        pattern = r'^(\s*versionCode\s*=\s*)([0-9]+)(\s*)$'
    else:
        text = source(IOS_PATH)
        pattern = r'(CURRENT_PROJECT_VERSION = )([0-9]+)(;)'
    values = re.findall(pattern, text, re.M)
    if not values or len({value[1] for value in values}) != 1:
        raise ValueError('Expected one consistent committed build number')
    number = validate_build_number(values[0][1])
    text = re.sub(pattern, lambda m: m[1] + number + m[3], text, flags=re.M)
    (ROOT / (ANDROID_PATH if platform == 'android' else IOS_PATH)).write_text(text)
    print(f'Production configuration prepared: {platform}, build {number}, API {PRODUCTION_API}')


def prepare_firebase(path):
    from ios_internal import decode_secret
    decode_secret('GOOGLE_SERVICE_INFO_PLIST_BASE64', path)
    with path.open('rb') as stream:
        data = plistlib.load(stream)
    if data.get('PROJECT_ID') != 'ringout-prod' or data.get('BUNDLE_ID') != 'com.joon.ringout.Ringout':
        raise ValueError('Expected ringout-prod Firebase configuration for the iOS app')
    for key in ('GOOGLE_APP_ID', 'GCM_SENDER_ID', 'API_KEY'):
        if not isinstance(data.get(key), str) or not data[key]:
            raise ValueError(f'Missing production Firebase field: {key}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('platform', choices=('android', 'ios'))
    args = parser.parse_args()
    prepare(args.platform)
    if args.platform == 'ios':
        prepare_firebase(ROOT / 'iosApp/iosApp/GoogleService-Info.plist')
