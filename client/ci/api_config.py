"""Validate the API origin embedded by the shared Gradle source generator."""

import argparse
import os
from pathlib import Path
import re

PRODUCTION_URL = 'https://api.ringout.my'
DEVELOPMENT_URL = 'https://dev-api.ringout.my'
GENERATED_PATH = 'shared/build/generated/apiConfig/commonMain/kotlin/com/joon/ringout/data/network/ApiBuildConfig.kt'


def configured_url():
    return os.environ.get('RINGOUT_API_BASE_URL', DEVELOPMENT_URL).strip().rstrip('/')


def require_production_url():
    if configured_url() != PRODUCTION_URL:
        raise ValueError('Production requires RINGOUT_API_BASE_URL=https://api.ringout.my')


def verify_generated(root, production=False):
    if production:
        require_production_url()
    text = (Path(root)/GENERATED_PATH).read_text()
    matches = re.findall(r'^\s*const val BASE_URL = "([^"\n]+)"\s*$', text, re.M)
    if matches != [configured_url()]:
        raise ValueError('Generated API URL does not match the build environment')
    return matches[0]


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--production', action='store_true')
    args = parser.parse_args()
    print('Verified build API: ' + verify_generated(Path(__file__).resolve().parents[1], args.production))
