"""Prepare and validate the credentials for an internal TestFlight build."""

import base64
import binascii
import os
import plistlib
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path


BUNDLE_ID = "com.joon.ringout.Ringout"
TEAM_ID = "475GWW72JW"
REQUIRED_XCCONFIG_KEYS = (
    "GOOGLE_IOS_SDK_API_KEY",
    "GOOGLE_IOS_CLIENT_ID",
    "GOOGLE_IOS_REVERSED_CLIENT_ID",
    "GOOGLE_SERVER_CLIENT_ID",
    "KAKAO_NATIVE_APP_KEY",
)


def decode_secret(name: str, path: Path) -> None:
    value = os.environ.get(name, "")
    if not value:
        raise ValueError(f"Missing ios-internal secret: {name}")
    try:
        data = base64.b64decode("".join(value.split()), validate=True)
    except (ValueError, binascii.Error) as error:
        raise ValueError(f"Invalid Base64 in {name}") from error
    if not data:
        raise ValueError(f"Empty file in {name}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    path.chmod(0o600)


def validate_profile(path: Path) -> tuple[str, str]:
    result = subprocess.run(
        ["security", "cms", "-D", "-i", str(path)],
        check=True,
        capture_output=True,
    )
    profile = plistlib.loads(result.stdout)
    entitlements = profile.get("Entitlements", {})
    if TEAM_ID not in profile.get("TeamIdentifier", []):
        raise ValueError("Provisioning profile has the wrong Apple team")
    if entitlements.get("application-identifier") != f"{TEAM_ID}.{BUNDLE_ID}":
        raise ValueError("Provisioning profile has the wrong app ID")
    if entitlements.get("get-task-allow") is not False:
        raise ValueError("Provisioning profile is not for distribution")
    if profile.get("ProvisionedDevices") or profile.get("ProvisionsAllDevices"):
        raise ValueError("Expected an App Store Connect distribution profile")
    if "Default" not in entitlements.get("com.apple.developer.applesignin", []):
        raise ValueError("Provisioning profile is missing Sign in with Apple")
    expiration = profile.get("ExpirationDate")
    if not isinstance(expiration, datetime) or expiration.replace(tzinfo=timezone.utc) <= datetime.now(timezone.utc):
        raise ValueError("Provisioning profile is expired or missing its expiration")
    name, uuid = profile.get("Name"), profile.get("UUID")
    if not isinstance(name, str) or not name or not isinstance(uuid, str) or not uuid:
        raise ValueError("Provisioning profile is missing its name or UUID")
    return name, uuid


def validate_xcconfig(path: Path) -> None:
    settings = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith(("#", "//")):
            key, value = line.split("=", 1)
            settings[key.strip()] = value.strip()
    missing = [key for key in REQUIRED_XCCONFIG_KEYS if not settings.get(key)]
    if missing:
        raise ValueError(f"RingoutSecrets.xcconfig is missing: {', '.join(missing)}")


def export_options(profile_uuid: str, path: Path) -> None:
    options = {
        "method": "app-store-connect",
        "destination": "upload",
        "signingStyle": "manual",
        "signingCertificate": "Apple Distribution",
        "teamID": TEAM_ID,
        "provisioningProfiles": {BUNDLE_ID: profile_uuid},
        "manageAppVersionAndBuildNumber": False,
        "testFlightInternalTestingOnly": True,
    }
    with path.open("wb") as stream:
        plistlib.dump(options, stream)


def main() -> int:
    try:
        runner_temp = Path(os.environ["RUNNER_TEMP"])
        env_path = Path(os.environ["GITHUB_ENV"])
        workspace = Path(os.environ["GITHUB_WORKSPACE"])
        secrets_dir = runner_temp / "ringout-ios-internal"
        cert_path = secrets_dir / "distribution.p12"
        profile_path = secrets_dir / "distribution.mobileprovision"
        api_key_path = secrets_dir / "AuthKey.p8"
        xcconfig_path = workspace / "client/iosApp/Configuration/RingoutSecrets.xcconfig"
        export_path = secrets_dir / "ExportOptions.plist"

        for name in ("APPLE_CERTIFICATE_PASSWORD", "APP_STORE_CONNECT_KEY_ID", "APP_STORE_CONNECT_ISSUER_ID"):
            if not os.environ.get(name):
                raise ValueError(f"Missing ios-internal secret: {name}")
        decode_secret("APPLE_CERTIFICATE_P12_BASE64", cert_path)
        decode_secret("APPLE_PROVISIONING_PROFILE_BASE64", profile_path)
        decode_secret("APP_STORE_CONNECT_API_KEY_BASE64", api_key_path)
        decode_secret("RINGOUT_IOS_SECRETS_XCCONFIG_BASE64", xcconfig_path)
        validate_xcconfig(xcconfig_path)
        _, profile_uuid = validate_profile(profile_path)
        export_options(profile_uuid, export_path)

        values = {
            "IOS_CERTIFICATE_PATH": cert_path,
            "IOS_PROFILE_PATH": profile_path,
            "IOS_PROFILE_UUID": profile_uuid,
            "IOS_API_KEY_PATH": api_key_path,
            "IOS_EXPORT_OPTIONS_PATH": export_path,
        }
        with env_path.open("a", encoding="utf-8") as stream:
            for key, value in values.items():
                print(f"{key}={value}", file=stream)
        print("Validated internal TestFlight signing inputs")
    except (OSError, ValueError, KeyError, plistlib.InvalidFileException, subprocess.CalledProcessError) as error:
        print(f"iOS internal build preparation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
