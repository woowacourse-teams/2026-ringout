"""Android internal Play CD routing and publishing regression checks."""

import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import android_internal_cd as cd


class AndroidInternalCDTest(unittest.TestCase):
    def test_같은_저장소의_같은_커밋에서_성공한_빌드만_선택한다(self):
        def run(identifier, **changes):
            item = {
                "id": identifier,
                "head_sha": "a" * 40,
                "head_branch": "develop",
                "event": "push",
                "head_repository": {"full_name": "woowacourse-teams/2026-ringout"},
                "status": "completed",
                "conclusion": "success",
            }
            item.update(changes)
            return item

        sha = "a" * 40
        repository = "woowacourse-teams/2026-ringout"
        runs = [
            run(1, head_sha="b" * 40),
            run(2, head_repository={"full_name": "attacker/fork"}),
            run(3, head_branch="main"),
            run(4),
        ]
        self.assertEqual(cd.choose_run(runs, sha, repository), ("deploy", runs[-1]))
        self.assertEqual(cd.choose_run([run(5, conclusion="failure")], sha, repository)[0], "fail")
        self.assertEqual(cd.choose_run([run(6, status="in_progress")], sha, repository)[0], "wait")
        self.assertEqual(cd.choose_run(runs[:3], sha, repository)[0], "wait")

    def test_메타데이터가_없거나_다르면_업로드_전에_거부한다(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(cd.CDError):
                cd.verify_artifact(directory, "a" * 40, "123", "1")
            artifact = Path(directory) / "artifact"
            artifact.mkdir()
            (artifact / "build-metadata.json").write_text('{"commit":"wrong"}')
            with self.assertRaisesRegex(cd.CDError, "applicationId"):
                cd.verify_artifact(directory, "a" * 40, "123", "1")

    def test_검증된_버전만_internal_트랙에_반영한다(self):
        with tempfile.TemporaryDirectory() as directory:
            aab = Path(directory) / "verified.aab"
            aab.write_bytes(b"example")
            calls = []

            def request(url, token, method="GET", body=None, content_type="application/json", timeout=60):
                calls.append((url, method, body))
                if url.endswith("/edits"):
                    return {"id": "edit123"}
                if "/bundles?" in url:
                    return {"versionCode": 261000007}
                if url.endswith(":commit?changesInReviewBehavior=ERROR_IF_IN_REVIEW"):
                    return {"id": "edit123"}
                return {"track": "internal"}

            env = {
                "GOOGLE_PLAY_ACCESS_TOKEN": "test-token",
                "VERIFIED_AAB_PATH": str(aab),
                "VERIFIED_VERSION_CODE": "261000007",
                "GITHUB_SHA": "a" * 40,
            }
            with patch.dict(os.environ, env), patch.object(cd, "request_json", side_effect=request):
                cd.publish()
            self.assertEqual(len(calls), 4)
            self.assertIn("/tracks/internal", calls[2][0])
            self.assertEqual(calls[2][2], {
                "track": "internal",
                "releases": [{"status": "completed", "versionCodes": ["261000007"]}],
            })


if __name__ == "__main__":
    unittest.main()
