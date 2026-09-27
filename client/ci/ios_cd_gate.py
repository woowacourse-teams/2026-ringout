"""Wait for the iOS CI push run of this exact develop commit before TestFlight CD."""

import json
import os
from pathlib import Path
import re
import sys
import time
from urllib.parse import urlencode
from urllib.request import Request, urlopen


POLL_SECONDS = 20
MAX_WAIT_SECONDS = 90 * 60


def decision(runs: list[dict], sha: str, repository: str) -> tuple[str, str]:
    matching = [run for run in runs if (
        run.get("head_sha") == sha
        and run.get("head_branch") == "develop"
        and run.get("event") == "push"
        and (run.get("head_repository") or {}).get("full_name") == repository
    )]
    if not matching:
        return "wait", "iOS CI run has not appeared yet"
    run = max(matching, key=lambda item: item["id"])
    if run.get("status") != "completed":
        return "wait", f"iOS CI is {run.get('status')}"
    if run.get("conclusion") == "success":
        return "deploy", run.get("html_url", "")
    return "skip", f"iOS CI concluded {run.get('conclusion')}: {run.get('html_url', '')}"


def fetch_runs(repository: str, sha: str, token: str) -> list[dict]:
    query = urlencode({"branch": "develop", "event": "push", "head_sha": sha, "per_page": 100})
    url = f"https://api.github.com/repos/{repository}/actions/workflows/ios-ci.yml/runs?{query}"
    request = Request(url, headers={
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "User-Agent": "ringout-ios-cd-gate",
    })
    with urlopen(request, timeout=30) as response:
        return json.load(response)["workflow_runs"]


def main() -> int:
    if os.environ.get("GITHUB_REF") != "refs/heads/develop" or os.environ.get("GITHUB_EVENT_NAME") != "push":
        raise ValueError("TestFlight CD only accepts develop push events")
    sha = os.environ["GITHUB_SHA"]
    repository = os.environ["GITHUB_REPOSITORY"]
    if not re.fullmatch(r"[0-9a-f]{40}", sha) or not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Invalid repository or commit SHA")
    output = Path(os.environ["GITHUB_OUTPUT"])
    token = os.environ["GITHUB_TOKEN"]
    deadline = time.monotonic() + MAX_WAIT_SECONDS
    while time.monotonic() < deadline:
        result, detail = decision(fetch_runs(repository, sha, token), sha, repository)
        if result != "wait":
            with output.open("a", encoding="utf-8") as stream:
                stream.write(f"eligible={'true' if result == 'deploy' else 'false'}\n")
            print(f"TestFlight CD gate: {result} ({detail})")
            return 0
        print(f"TestFlight CD gate: {detail}; checking again in {POLL_SECONDS}s", flush=True)
        time.sleep(POLL_SECONDS)
    raise TimeoutError("Timed out waiting for iOS CI on this develop commit")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, TimeoutError) as error:
        print(f"::error::TestFlight CD gate failed: {error}", file=sys.stderr)
        raise SystemExit(1)
