"""Android CI helpers. Standard library only; never print secret values."""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile


CLIENT_ROOT = Path(__file__).resolve().parents[1]
APPLICATION_ID = "com.joon.ringout"
FIREBASE_PROJECT_BY_REF = {
    "refs/heads/develop": "ringout-8abf2",
    "refs/heads/main": "ringout-prod",
}
MAX_VERSION_CODE = 2_100_000_000
R8_FILES = ("mapping.txt", "configuration.txt", "seeds.txt", "usage.txt", "resources.txt")
SIGNING_SECRETS = (
    "ANDROID_KEYSTORE_BASE64", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD", "MAPS_API_KEY",
    "GOOGLE_SERVICES_JSON_BASE64",
)


class CIError(Exception):
    pass


def require_env(name):
    value = os.environ.get(name, "")
    if not value.strip():
        raise CIError(f"필수 설정이 없습니다: {name}")
    return value


def github_value(destination, key, value):
    value = str(value)
    if "\n" in value or "\r" in value:
        raise CIError(f"여러 줄 값을 기록할 수 없습니다: {key}")
    with Path(require_env(destination)).open("a", encoding="utf-8") as output:
        output.write(f"{key}={value}\n")


def run_tool(arguments, cwd=None):
    result = subprocess.run(arguments, cwd=cwd, capture_output=True, check=False)
    if result.returncode:
        # Tool diagnostics may contain configuration data. Do not dump them into CI logs.
        raise CIError(f"{arguments[0]} 검증 실패 (exit {result.returncode})")
    return result.stdout


def validate_pr_route(event):
    pr = event["pull_request"]
    base = pr["base"]["ref"]
    if base not in ("develop", "main"):
        raise CIError("클라이언트 PR의 대상 브랜치는 develop 또는 main이어야 합니다.")
    if base == "main" and (
        pr["head"]["ref"] != "develop"
        or pr["head"]["repo"]["full_name"] != event["repository"]["full_name"]
    ):
        raise CIError("main에는 같은 저장소의 develop 브랜치에서만 PR을 보낼 수 있습니다.")


def is_client_path(path):
    return path.startswith(b"client/") or path in (
        b".github/workflows/android-ci.yml", b".github/workflows/build-release-aab.yml",
    ) or path.startswith(b".github/actions/")


def changed_files(repository, base, head):
    for revision in (base, head):
        if not re.fullmatch(r"[0-9a-f]{40,64}", revision):
            raise CIError("변경 검사에는 커밋 SHA만 사용할 수 있습니다.")
    # --no-renames includes the old path when a client file moves outside client/.
    return run_tool(
        ["git", "diff", "--name-only", "--no-renames", "-z", f"{base}...{head}"],
        cwd=repository,
    ).split(b"\0")


def detect_changes():
    event = json.loads(Path(require_env("GITHUB_EVENT_PATH")).read_text())
    validate_pr_route(event)
    pr = event["pull_request"]
    changed = any(is_client_path(p) for p in changed_files(
        CLIENT_ROOT.parent, pr["base"]["sha"], pr["head"]["sha"],
    ))
    github_value("GITHUB_OUTPUT", "client_changed", str(changed).lower())
    print(f"클라이언트 검증 필요: {changed}")


def check_gate(changes_result, client_changed, quality_result):
    if changes_result != "success":
        raise CIError("브랜치 또는 변경 경로 검사가 실패했습니다.")
    if client_changed == "true" and quality_result == "success":
        return
    if client_changed == "false" and quality_result == "skipped":
        return
    raise CIError("필요한 클라이언트 검증이 성공하지 않았습니다.")


def select_build():
    branch = require_env("GITHUB_REF")
    expected_firebase_project(branch)
    channel = "internal" if branch == "refs/heads/develop" else "release"
    github_value("GITHUB_ENV", "AAB_CHANNEL", channel)
    print(f"AAB 채널: {channel}")


def decode_secret(name):
    try:
        value = base64.b64decode("".join(require_env(name).split()), validate=True)
    except ValueError:
        raise CIError(f"Base64 형식이 올바르지 않습니다: {name}") from None
    if not value:
        raise CIError(f"비어 있는 파일입니다: {name}")
    return value


def expected_firebase_project(branch=None):
    branch = branch or require_env("GITHUB_REF")
    try:
        return FIREBASE_PROJECT_BY_REF[branch]
    except KeyError:
        raise CIError("서명 AAB는 develop 또는 main에서만 생성할 수 있습니다.") from None


def validate_google_services(data, expected_project_id=None):
    try:
        document = json.loads(data)
        project = document["project_info"]
        clients = document["client"]
        matching = [c for c in clients if c["client_info"]["android_client_info"]["package_name"] == APPLICATION_ID]
        if not project["project_id"] or not project["project_number"] or not matching:
            raise ValueError()
        client = matching[0]
        if not client["client_info"]["mobilesdk_app_id"] or not client["api_key"][0]["current_key"]:
            raise ValueError()
        if b"CI_VERIFICATION_ONLY" in data or b"ringout-ci-verification" in data:
            raise ValueError()
    except (ValueError, KeyError, TypeError, IndexError):
        raise CIError("Firebase 설정이 유효하지 않거나 검증용 파일입니다. com.joon.ringout 설정을 확인하세요.") from None
    if expected_project_id is not None and project["project_id"] != expected_project_id:
        raise CIError(f"Firebase 프로젝트가 브랜치와 일치하지 않습니다. 예상: {expected_project_id}")
    return {
        "project_id": project["project_id"],
        "google_app_id": client["client_info"]["mobilesdk_app_id"],
        "project_number": str(project["project_number"]),
    }


def expected_certificate(root=CLIENT_ROOT):
    value = (root / "ci/upload-certificate.sha256").read_text().strip().replace(":", "").upper()
    if not re.fullmatch(r"[0-9A-F]{64}", value):
        raise CIError("승인 업로드 인증서 SHA-256 설정이 잘못되었습니다.")
    return value


def signing_directory():
    runner_temp = Path(require_env("RUNNER_TEMP"))
    if not runner_temp.is_absolute():
        raise CIError("RUNNER_TEMP는 절대 경로여야 합니다.")
    return runner_temp / "ringout-signing"


def prepare_signing():
    for name in SIGNING_SECRETS:
        value = require_env(name)
        if value in ("CI_VERIFICATION_ONLY", "00000000000000000000000000000000"):
            raise CIError(f"배포용 빌드에는 실제 설정이 필요합니다: {name}")
    keystore = decode_secret("ANDROID_KEYSTORE_BASE64")
    google_services = decode_secret("GOOGLE_SERVICES_JSON_BASE64")
    validate_google_services(google_services, expected_firebase_project())
    directory = signing_directory()
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    key_path = directory / "upload.jks"
    config_path = directory / "google-services.json"
    for path, data in ((key_path, keystore), (config_path, google_services)):
        with path.open("wb") as output:
            path.chmod(0o600)
            output.write(data)
    cert = run_tool([
        "keytool", "-exportcert", "-keystore", str(key_path),
        "-storepass:env", "ANDROID_KEYSTORE_PASSWORD", "-alias", require_env("ANDROID_KEY_ALIAS"),
    ])
    if hashlib.sha256(cert).hexdigest().upper() != expected_certificate():
        raise CIError("승인되지 않은 Android 업로드 인증서입니다.")
    # Access the private key too: exporting a certificate alone does not check keyPassword.
    run_tool([
        "keytool", "-certreq", "-keystore", str(key_path),
        "-storepass:env", "ANDROID_KEYSTORE_PASSWORD", "-keypass:env", "ANDROID_KEY_PASSWORD",
        "-alias", require_env("ANDROID_KEY_ALIAS"), "-file", str(directory / "key-check.csr"),
    ])
    github_value("GITHUB_ENV", "ANDROID_KEYSTORE_PATH", key_path)
    github_value("GITHUB_ENV", "GOOGLE_SERVICES_JSON_PATH", config_path)
    print("Firebase 설정과 업로드 인증서·개인 키를 확인했습니다.")


def verify_r8(root=CLIENT_ROOT):
    directory = root / "androidApp/build/outputs/mapping/release"
    for name in R8_FILES:
        path = directory / name
        if not path.is_file() or path.stat().st_size == 0:
            raise CIError(f"R8 산출물이 없거나 비어 있습니다: {name}")
    missing = directory / "missing_rules.txt"
    if missing.exists() and missing.stat().st_size:
        raise CIError("해결되지 않은 R8 missing_rules.txt가 있습니다.")
    versions = re.findall(r'classpath\("com\.android\.tools:r8:([^"\s]+)"\)', (root / "settings.gradle.kts").read_text())
    if len(versions) != 1:
        raise CIError("settings.gradle.kts에서 승인 R8 버전을 확인할 수 없습니다.")
    mapping = (directory / "mapping.txt").read_text()
    if f"# compiler_version: {versions[0]}" not in mapping.splitlines():
        raise CIError("설정한 R8 버전과 mapping.txt의 컴파일러 버전이 다릅니다.")
    return directory


def find_aab(root=CLIENT_ROOT):
    files = list((root / "androidApp/build/outputs/bundle/release").glob("*.aab"))
    if len(files) != 1 or files[0].stat().st_size == 0:
        raise CIError("release AAB는 정확히 하나 존재해야 합니다.")
    return files[0]


def verify_unsigned(aab):
    with zipfile.ZipFile(aab) as bundle:
        names = bundle.namelist()
        if "base/manifest/AndroidManifest.xml" not in names:
            raise CIError("AAB의 base manifest가 없습니다.")
        if any(re.fullmatch(r"META-INF/[^/]+\.(RSA|DSA|EC|SF)", n.upper()) for n in names):
            raise CIError("PR 검증 AAB에 서명이 포함되어 있습니다.")


def verify_ci_configuration(root=CLIENT_ROOT):
    path = root / "androidApp/build/generated/res/processReleaseGoogleServices/values/values.xml"
    values = {entry.get("name"): entry.text for entry in ET.parse(path).getroot()}
    if values.get("project_id") != "ringout-ci-verification" or values.get("google_api_key") != "CI_VERIFICATION_ONLY":
        raise CIError("PR 빌드에 Firebase 검증용 설정이 적용되지 않았습니다.")


def verify_release_firebase_configuration(root=CLIENT_ROOT, config_path=None):
    config_path = Path(config_path) if config_path else Path(require_env("GOOGLE_SERVICES_JSON_PATH"))
    expected = validate_google_services(config_path.read_bytes(), expected_firebase_project())
    path = root / "androidApp/build/generated/res/processReleaseGoogleServices/values/values.xml"
    values = {entry.get("name"): entry.text for entry in ET.parse(path).getroot()}
    for resource, expected_value in (
        ("project_id", expected["project_id"]),
        ("google_app_id", expected["google_app_id"]),
        ("gcm_defaultSenderId", expected["project_number"]),
    ):
        if values.get(resource) != expected_value:
            raise CIError(f"빌드된 Firebase 설정이 브랜치와 일치하지 않습니다: {resource}")
    return expected["project_id"]


def verify_signature(aab, keystore, alias, expected):
    # The upload keystore is the explicit trust anchor, including self-signed upload keys.
    # -strict and the alias reject unsigned entries and entries signed by other keys.
    run_tool([
        "jarsigner", "-verify", "-strict", "-keystore", str(keystore),
        "-storepass:env", "ANDROID_KEYSTORE_PASSWORD", str(aab), alias,
    ])
    pem = run_tool(["keytool", "-printcert", "-jarfile", str(aab), "-rfc"])
    match = re.search(rb"-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----", pem, re.S)
    if not match:
        raise CIError("AAB 서명 인증서를 읽을 수 없습니다.")
    cert = base64.b64decode(match[1])
    if hashlib.sha256(cert).hexdigest().upper() != expected:
        raise CIError("AAB 서명 인증서가 승인된 업로드 인증서와 다릅니다.")


def declared_android_version(root=CLIENT_ROOT):
    build_script = (root / "androidApp/build.gradle.kts").read_text(encoding="utf-8")
    code = re.findall(r'^\s*versionCode\s*=\s*([0-9]+)\s*$', build_script, re.M)
    name = re.findall(r'^\s*versionName\s*=\s*"([^"\n]+)"\s*$', build_script, re.M)
    if len(code) != 1 or len(name) != 1 or not 1 <= int(code[0]) <= MAX_VERSION_CODE:
        raise CIError("androidApp/build.gradle.kts의 versionCode·versionName을 확인할 수 없습니다.")
    return code[0], name[0]


def release_metadata(root=CLIENT_ROOT):
    manifests = list((root / "androidApp/build/intermediates/merged_manifests/release").glob("*/AndroidManifest.xml"))
    if len(manifests) != 1:
        raise CIError("빌드된 release manifest를 하나로 식별할 수 없습니다.")
    manifest = ET.parse(manifests[0]).getroot()
    android = "{http://schemas.android.com/apk/res/android}"
    if manifest.get("package") != APPLICATION_ID:
        raise CIError("빌드한 앱의 applicationId가 다릅니다.")
    code, name = declared_android_version(root)
    if manifest.get(android + "versionCode") != code or manifest.get(android + "versionName") != name:
        raise CIError("빌드한 앱의 버전이 androidApp/build.gradle.kts 설정과 다릅니다.")
    return {
        "applicationId": APPLICATION_ID,
        "versionCode": int(manifest.get(android + "versionCode")),
        "versionName": manifest.get(android + "versionName"),
        "channel": require_env("AAB_CHANNEL"),
        "commit": require_env("GITHUB_SHA"),
        "branch": require_env("GITHUB_REF_NAME"),
        "runId": require_env("GITHUB_RUN_ID"),
        "runAttempt": require_env("GITHUB_RUN_ATTEMPT"),
    }


def package_aab():
    aab = find_aab()
    r8 = verify_r8()
    firebase_project_id = verify_release_firebase_configuration()
    cert = expected_certificate()
    verify_signature(aab, require_env("ANDROID_KEYSTORE_PATH"), require_env("ANDROID_KEY_ALIAS"), cert)
    metadata = release_metadata()
    metadata["firebaseProjectId"] = firebase_project_id
    artifact = (f"ringout-{metadata['channel']}-aab-{metadata['versionCode']}-"
                f"{metadata['commit'][:12]}-attempt{metadata['runAttempt']}")
    destination = CLIENT_ROOT / "build/ci/artifacts"
    destination.mkdir(parents=True, exist_ok=False)
    name = f"{artifact}.aab"
    shutil.copy2(aab, destination / name)
    checksum = hashlib.sha256(aab.read_bytes()).hexdigest()
    (destination / "sha256.txt").write_text(f"{checksum}  {name}\n")
    metadata.update({"sha256": checksum, "uploadCertificateSha256": cert})
    (destination / "build-metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
    reports = destination / "r8"
    reports.mkdir()
    for name in R8_FILES:
        shutil.copy2(r8 / name, reports / name)
    github_value("GITHUB_OUTPUT", "artifact_name", artifact)
    print(f"AAB 서명·인증서·버전 검증 완료: {artifact}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("changes", "gate", "channel", "prepare-signing", "verify-r8", "verify-unsigned", "package", "cleanup"))
    command = parser.parse_args().command
    if command == "changes":
        detect_changes()
    elif command == "gate":
        check_gate(require_env("CHANGES_RESULT"), require_env("CLIENT_CHANGED"), require_env("QUALITY_RESULT"))
        print("Android CI 검증을 통과했습니다.")
    elif command == "channel":
        select_build()
    elif command == "prepare-signing":
        prepare_signing()
    elif command == "verify-r8":
        verify_r8()
        print("R8 산출물을 확인했습니다.")
    elif command == "verify-unsigned":
        verify_ci_configuration()
        verify_unsigned(find_aab())
        print("PR 검증 AAB에 서명이 없음을 확인했습니다.")
    elif command == "package":
        package_aab()
    elif command == "cleanup":
        directory = signing_directory()
        if directory.exists():
            shutil.rmtree(directory)


if __name__ == "__main__":
    try:
        main()
    except (CIError, OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"::error::{error}", file=sys.stderr)
        sys.exit(1)
