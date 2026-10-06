"""Verify that an iOS archive uses the version committed in Xcode build settings."""

import argparse
import json
import plistlib
from pathlib import Path
import re
import sys


def expected_version(settings_path: Path) -> tuple[str, str]:
    targets = json.loads(settings_path.read_text(encoding="utf-8"))
    matches = [entry["buildSettings"] for entry in targets if entry.get("target") == "iosApp"]
    if len(matches) != 1:
        raise ValueError("Expected one iosApp Release build settings entry")
    settings = matches[0]
    name = settings.get("MARKETING_VERSION", "")
    number = settings.get("CURRENT_PROJECT_VERSION", "")
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+)*", name) or not re.fullmatch(r"[0-9]+", number):
        raise ValueError("Set a numeric MARKETING_VERSION and CURRENT_PROJECT_VERSION in the Xcode project")
    return name, number


def verify_archive(settings_path: Path, app_path: Path) -> tuple[str, str]:
    name, number = expected_version(settings_path)
    with (app_path / "Info.plist").open("rb") as stream:
        info = plistlib.load(stream)
    if info.get("CFBundleShortVersionString") != name or str(info.get("CFBundleVersion")) != number:
        raise ValueError("Archived iOS version does not match committed Xcode Release settings")
    return name, number


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--settings", required=True, type=Path)
    parser.add_argument("--app", required=True, type=Path)
    args = parser.parse_args()
    try:
        name, number = verify_archive(args.settings, args.app)
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError, plistlib.InvalidFileException) as error:
        print(f"iOS archive version check failed: {error}", file=sys.stderr)
        return 1
    print(f"Verified iOS archive version: {name} ({number})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
