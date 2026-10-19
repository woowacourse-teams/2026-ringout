"""Require only applicable CI push runs for the exact release commit before signing or CD."""

import argparse
import json
import os
from pathlib import Path
import re
import sys
import time
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from change_policy import ci_required, deployment_required
from pipeline import CIError, CLIENT_ROOT, changed_files, push_changed_files, run_tool


POLL_SECONDS = 20
MAX_WAIT_SECONDS = 90 * 60


def decision(runs: list[dict], sha: str, repository: str, branch: str = "client/release") -> tuple[str, str]:
    matching = [run for run in runs if (
        run.get("head_sha") == sha
        and run.get("head_branch") == branch
        and run.get("event") == "push"
        and (run.get("head_repository") or {}).get("full_name") == repository
    )]
    if not matching:
        return "wait", "CI run has not appeared yet"
    run = max(matching, key=lambda item: item["id"])
    if run.get("status") != "completed":
        return "wait", f"CI is {run.get('status')}"
    if run.get("conclusion") == "success":
        return "deploy", run.get("html_url", "")
    return "skip", f"CI concluded {run.get('conclusion')}: {run.get('html_url', '')}"


def fetch_runs(repository: str, sha: str, token: str, branch: str = "client/release", workflow: str = "ios-ci.yml") -> list[dict]:
    query = urlencode({"branch": branch, "event": "push", "head_sha": sha, "per_page": 100})
    url = f"https://api.github.com/repos/{repository}/actions/workflows/{workflow}/runs?{query}"
    request = Request(url, headers={
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "User-Agent": "ringout-ios-cd-gate",
    })
    with urlopen(request, timeout=30) as response:
        return json.load(response)["workflow_runs"]


def release_changes() -> list[bytes]:
    """Read the actual push range; recovery uses the target commit's first parent."""
    sha = os.environ["GITHUB_SHA"]
    ref = os.environ["GITHUB_REF"]
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Invalid release commit SHA")
    event_name = os.environ["GITHUB_EVENT_NAME"]
    repository = CLIENT_ROOT.parent
    if event_name == "push":
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
        if (event.get("ref") != ref or event.get("after") != sha or event.get("deleted")
                or not re.fullmatch(r"[0-9a-f]{40}", event.get("before", ""))):
            raise ValueError("Push payload does not match the release ref and commit")
        return push_changed_files(event)
    elif event_name == "workflow_dispatch":
        # Recovery has no push payload. Inspect exactly the selected commit,
        # never a moving branch tip or an unrelated previous workflow run.
        parents = run_tool(["git", "rev-list", "--parents", "-n", "1", sha], cwd=repository).split()
        if not parents or parents[0].decode() != sha:
            raise ValueError("Cannot determine recovery commit parent")
        base = parents[1].decode() if len(parents) > 1 else "0" * 40
    else:
        raise ValueError("Release changes require push or explicitly enabled recovery")
    if base == "0" * 40:
        return run_tool(["git", "ls-tree", "-r", "--name-only", "-z", sha], cwd=repository).split(b"\0")
    return changed_files(repository, base, sha, merge_base=False)


def wait_for_ci(repository: str, sha: str, token: str, branch: str, paths=None) -> None:
    """Poll applicable workflows under one deadline; missing or failed checks block release."""
    paths = release_changes() if paths is None else paths
    workflows = []
    for platform, workflow in (("android", "android-ci.yml"), ("ios", "ios-ci.yml"), ("python", "client-python-ci.yml")):
        if ci_required(paths, platform):
            workflows.append(workflow)
        else:
            print(f"Skipping {workflow}: no applicable changes")
    if not workflows:
        print(f"No CI checks required for {branch} at {sha}")
        return
    deadline = time.monotonic() + MAX_WAIT_SECONDS
    while time.monotonic() < deadline:
        pending = []
        for workflow in workflows:
            result, detail = decision(fetch_runs(repository, sha, token, branch, workflow), sha, repository, branch)
            if result == "skip":
                raise ValueError(f"{workflow} did not succeed: {detail}")
            if result == "wait":
                pending.append(workflow)
        if not pending:
            print(f"Required push CI succeeded for {branch} at {sha}: {', '.join(workflows)}")
            return
        print(f"Waiting for {', '.join(pending)}; checking again in {POLL_SECONDS}s", flush=True)
        time.sleep(POLL_SECONDS)
    raise TimeoutError(f"Timed out waiting for required CI on this commit: {', '.join(pending)}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--allow-dispatch", action="store_true", help="Allow signed AAB recovery after successful common and platform push CI")
    parser.add_argument("--platform", choices=("android", "ios"), default="ios")
    arguments = parser.parse_args()
    branch = os.environ.get("GITHUB_REF", "").removeprefix("refs/heads/")
    allowed_events = ("push", "workflow_dispatch") if arguments.allow_dispatch else ("push",)
    if branch not in ("client/release", "main") or os.environ.get("GITHUB_EVENT_NAME") not in allowed_events:
        raise ValueError("Release gate only accepts client/release/main push events (or explicitly enabled recovery)")
    sha = os.environ["GITHUB_SHA"]
    repository = os.environ["GITHUB_REPOSITORY"]
    if not re.fullmatch(r"[0-9a-f]{40}", sha) or not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Invalid repository or commit SHA")
    paths = release_changes()
    eligible = os.environ["GITHUB_EVENT_NAME"] == "workflow_dispatch" or deployment_required(paths, arguments.platform, branch)
    if eligible:
        wait_for_ci(repository, sha, os.environ["GITHUB_TOKEN"], branch, paths=paths)
    else:
        print(f"Skipping {arguments.platform} deployment: no application, build, or deployment changes")
    with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as stream:
        stream.write(f"eligible={str(eligible).lower()}\n")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, TimeoutError, CIError) as error:
        print(f"::error::Release CI gate failed: {error}", file=sys.stderr)
        raise SystemExit(1)
