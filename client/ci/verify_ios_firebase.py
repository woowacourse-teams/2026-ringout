"""Verify development or production Firebase configuration in a built iOS app."""

import argparse
import plistlib
import sys
from pathlib import Path


PROJECT_ID = "ringout-8abf2"
BUNDLE_ID = "com.joon.ringout.Ringout"
GOOGLE_APP_ID = "1:831074770315:ios:60c9163036c7cb979ba38e"
GCM_SENDER_ID = "831074770315"
REQUIRED_KEYS = ("PROJECT_ID", "GOOGLE_APP_ID", "GCM_SENDER_ID", "BUNDLE_ID")


def read_plist(path: Path) -> dict:
    with path.open("rb") as stream:
        data = plistlib.load(stream)
    if not isinstance(data, dict):
        raise ValueError(f"{path} is not a plist dictionary")
    return data


def verify(source_path: Path, app_path: Path, production: bool = False) -> None:
    source = read_plist(source_path)
    embedded = read_plist(app_path / "GoogleService-Info.plist")
    info = read_plist(app_path / "Info.plist")

    expected = {
        "PROJECT_ID": PROJECT_ID,
        "BUNDLE_ID": BUNDLE_ID,
        "GOOGLE_APP_ID": GOOGLE_APP_ID,
        "GCM_SENDER_ID": GCM_SENDER_ID,
    }
    if production:
        expected = {"PROJECT_ID": "ringout-prod", "BUNDLE_ID": BUNDLE_ID}
    for key, value in expected.items():
        if source.get(key) != value:
            raise ValueError(f"Source Firebase plist has an unexpected {key}")
    if info.get("CFBundleIdentifier") != BUNDLE_ID:
        raise ValueError("Built app bundle ID does not match the Firebase app")

    for key in REQUIRED_KEYS:
        value = source.get(key)
        if not isinstance(value, str) or not value:
            raise ValueError(f"Source Firebase plist is missing {key}")
        if embedded.get(key) != value:
            raise ValueError(f"Embedded Firebase plist has a different {key}")

    print(f"Verified Firebase {expected['PROJECT_ID']} in {app_path.name}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--app", type=Path, required=True)
    parser.add_argument("--production", action="store_true")
    args = parser.parse_args()

    try:
        verify(args.source, args.app, args.production)
    except (OSError, ValueError, plistlib.InvalidFileException) as error:
        print(f"iOS Firebase verification failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
