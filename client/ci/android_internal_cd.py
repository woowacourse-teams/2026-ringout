"""Gate a develop AAB run and publish its verified artifact to Play internal testing."""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
import zipfile

from pipeline import APPLICATION_ID, CLIENT_ROOT, declared_android_version, expected_certificate


API = "https://androidpublisher.googleapis.com"
GITHUB_API = "https://api.github.com"
POLL_SECONDS = 20
MAX_WAIT_SECONDS = 55 * 60


class CDError(Exception):
    pass


def required(name):
    value = os.environ.get(name, "").strip()
    if not value:
        raise CDError(f"필수 설정이 없습니다: {name}")
    return value


def request_json(url, token, method="GET", body=None, content_type="application/json", timeout=60):
    data = None if body is None else (body if isinstance(body, bytes) else json.dumps(body).encode())
    request = Request(url, data=data, method=method, headers={
        "Authorization": f"Bearer {token}",
        "Accept": "application/json",
        "Content-Type": content_type,
        "User-Agent": "ringout-android-internal-cd",
    })
    try:
        with urlopen(request, timeout=timeout) as response:
            return json.load(response)
    except HTTPError as error:
        # Do not print response bodies: they can contain request or account details.
        raise CDError(f"API 요청 실패: HTTP {error.code} ({method} {url.split('?')[0]})") from error


def choose_run(runs, sha, repository):
    candidates = [run for run in runs if (
        run.get("head_sha") == sha
        and run.get("head_branch") == "develop"
        and run.get("event") == "push"
        and (run.get("head_repository") or {}).get("full_name") == repository
    )]
    if not candidates:
        return "wait", None
    run = max(candidates, key=lambda item: item["id"])
    if run.get("status") != "completed":
        return "wait", run
    return ("deploy" if run.get("conclusion") == "success" else "fail"), run


def gate():
    if os.environ.get("GITHUB_EVENT_NAME") != "push" or os.environ.get("GITHUB_REF") != "refs/heads/develop":
        raise CDError("Android 내부 테스트 CD는 develop push에서만 실행할 수 있습니다.")
    sha = required("GITHUB_SHA")
    repository = required("GITHUB_REPOSITORY")
    if not re.fullmatch(r"[0-9a-f]{40}", sha) or not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise CDError("커밋 또는 저장소 형식이 잘못되었습니다.")
    token = required("GITHUB_TOKEN")
    query = urlencode({"branch": "develop", "event": "push", "head_sha": sha, "per_page": 100})
    url = f"{GITHUB_API}/repos/{repository}/actions/workflows/build-release-aab.yml/runs?{query}"
    deadline = time.monotonic() + MAX_WAIT_SECONDS
    while time.monotonic() < deadline:
        result, run = choose_run(request_json(url, token).get("workflow_runs", []), sha, repository)
        if result == "deploy":
            code, _ = declared_android_version()
            artifact_name = f"ringout-internal-aab-{code}-{sha[:12]}-attempt{run['run_attempt']}"
            with Path(required("GITHUB_OUTPUT")).open("a", encoding="utf-8") as output:
                output.write(f"run_id={run['id']}\nrun_attempt={run['run_attempt']}\nartifact_name={artifact_name}\n")
            print(f"같은 develop 커밋의 서명 AAB 빌드 통과: {run['html_url']}")
            return
        if result == "fail":
            raise CDError(f"서명 AAB 빌드가 {run.get('conclusion')} 상태로 끝났습니다: {run.get('html_url')}")
        time.sleep(POLL_SECONDS)
    raise CDError("같은 커밋의 서명 AAB 빌드를 기다리다 시간 초과되었습니다.")


def verify_artifact(directory, sha, run_id, run_attempt):
    root = Path(directory)
    metadata_files = list(root.glob("*/build-metadata.json"))
    if len(metadata_files) != 1:
        raise CDError("AAB 메타데이터가 정확히 하나 있어야 합니다.")
    folder = metadata_files[0].parent
    metadata = json.loads(metadata_files[0].read_text(encoding="utf-8"))
    expected = {
        "applicationId": APPLICATION_ID,
        "channel": "internal",
        "branch": "develop",
        "commit": sha,
        "runId": str(run_id),
        "runAttempt": str(run_attempt),
        "firebaseProjectId": "ringout-8abf2",
        "uploadCertificateSha256": expected_certificate(),
    }
    for key, value in expected.items():
        if metadata.get(key) != value:
            raise CDError(f"AAB 메타데이터가 기대값과 다릅니다: {key}")
    code, name = declared_android_version()
    if metadata.get("versionCode") != int(code) or metadata.get("versionName") != name:
        raise CDError("AAB 버전이 해당 커밋의 Gradle 설정과 다릅니다.")
    artifact_name = f"ringout-internal-aab-{code}-{sha[:12]}-attempt{run_attempt}"
    aab = folder / f"{artifact_name}.aab"
    if folder.name != artifact_name or not aab.is_file() or aab.stat().st_size == 0:
        raise CDError("AAB 파일명 또는 산출물 이름이 빌드 정보와 다릅니다.")
    digest = hashlib.sha256(aab.read_bytes()).hexdigest()
    if metadata.get("sha256") != digest or (folder / "sha256.txt").read_text().strip() != f"{digest}  {aab.name}":
        raise CDError("AAB 체크섬이 빌드 메타데이터와 다릅니다.")
    with zipfile.ZipFile(aab) as archive:
        if "base/manifest/AndroidManifest.xml" not in archive.namelist() or archive.testzip() is not None:
            raise CDError("AAB 압축 구조가 올바르지 않습니다.")
    signature = subprocess.run(["jarsigner", "-verify", str(aab)], capture_output=True, text=True, check=False)
    if signature.returncode or "jar verified" not in signature.stdout.lower():
        raise CDError("AAB 서명 검증에 실패했습니다.")
    certificate = subprocess.run(["keytool", "-printcert", "-jarfile", str(aab), "-rfc"], capture_output=True, check=False)
    match = re.search(rb"-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----", certificate.stdout, re.S)
    if certificate.returncode or not match or hashlib.sha256(base64.b64decode(match[1])).hexdigest().upper() != expected_certificate():
        raise CDError("AAB가 승인된 Play 업로드 인증서로 서명되지 않았습니다.")
    return aab, metadata


def verify():
    aab, metadata = verify_artifact(required("AAB_DOWNLOAD_DIR"), required("GITHUB_SHA"), required("AAB_RUN_ID"), required("AAB_RUN_ATTEMPT"))
    with Path(required("GITHUB_OUTPUT")).open("a", encoding="utf-8") as output:
        output.write(f"aab_path={aab}\nversion_code={metadata['versionCode']}\nversion_name={metadata['versionName']}\n")
    print(f"내부 테스트 AAB 검증 완료: {aab.name}")


def publish():
    token = required("GOOGLE_PLAY_ACCESS_TOKEN")
    aab = Path(required("VERIFIED_AAB_PATH"))
    version_code = required("VERIFIED_VERSION_CODE")
    if not aab.is_file() or not re.fullmatch(r"[1-9][0-9]*", version_code):
        raise CDError("검증된 AAB 또는 버전 코드가 없습니다.")
    base = f"{API}/androidpublisher/v3/applications/{APPLICATION_ID}/edits"
    edit = request_json(base, token, "POST", {})
    edit_id = edit.get("id")
    if not edit_id or not re.fullmatch(r"[A-Za-z0-9_-]+", edit_id):
        raise CDError("Play edit ID를 확인할 수 없습니다.")
    upload_url = f"{API}/upload/androidpublisher/v3/applications/{APPLICATION_ID}/edits/{edit_id}/bundles?uploadType=media"
    bundle = request_json(upload_url, token, "POST", aab.read_bytes(), "application/octet-stream", timeout=600)
    if str(bundle.get("versionCode")) != version_code:
        raise CDError("Play에서 확인한 AAB 버전 코드가 검증값과 다릅니다.")
    track_url = f"{base}/{edit_id}/tracks/internal"
    request_json(track_url, token, "PUT", {"track": "internal", "releases": [{"status": "completed", "versionCodes": [version_code]}]})
    commit_url = f"{base}/{edit_id}:commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW"
    committed = request_json(commit_url, token, "POST", {})
    if committed.get("id") != edit_id:
        raise CDError("Play edit 완료 응답이 예상과 다릅니다.")
    print(f"Google Play 내부 테스트 업로드 완료: {APPLICATION_ID} / versionCode {version_code}")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as summary:
            summary.write(f"### Google Play 내부 테스트 업로드\n\n- 앱: `{APPLICATION_ID}`\n- versionCode: `{version_code}`\n- 커밋: `{required('GITHUB_SHA')}`\n- [Play Console](https://play.google.com/console/)에서 출시 상태 확인\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("gate", "verify", "publish"))
    command = parser.parse_args().command
    {"gate": gate, "verify": verify, "publish": publish}[command]()


if __name__ == "__main__":
    try:
        main()
    except (CDError, KeyError, OSError, ValueError, json.JSONDecodeError, zipfile.BadZipFile) as error:
        print(f"::error::Android 내부 테스트 CD 실패: {error}", file=sys.stderr)
        raise SystemExit(1)
