import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import ios_cd_gate


ALL_CHECKS = [b"client/shared/src/commonMain/Example.kt", b"client/ci/pipeline.py"]


class IosCdGateTest(unittest.TestCase):
    def run_record(self, **overrides):
        record = {
            "id": 10,
            "head_sha": "a" * 40,
            "head_branch": "client/release",
            "event": "push",
            "head_repository": {"full_name": "team/ringout"},
            "status": "completed",
            "conclusion": "success",
            "html_url": "https://github.com/team/ringout/actions/runs/10",
        }
        record.update(overrides)
        return record

    def test_only_successful_push_ci_for_same_commit_can_deploy(self):
        valid = self.run_record()
        self.assertEqual(ios_cd_gate.decision([valid], "a" * 40, "team/ringout")[0], "deploy")
        for changed in (
            {"head_sha": "b" * 40},
            {"head_branch": "main"},
            {"event": "pull_request"},
            {"head_repository": {"full_name": "other/ringout"}},
        ):
            with self.subTest(changed=changed):
                self.assertEqual(ios_cd_gate.decision([self.run_record(**changed)], "a" * 40, "team/ringout")[0], "wait")

    def test_failure_or_cancellation_skips_deployment(self):
        for conclusion in ("failure", "cancelled", "timed_out"):
            with self.subTest(conclusion=conclusion):
                run = self.run_record(conclusion=conclusion)
                self.assertEqual(ios_cd_gate.decision([run], "a" * 40, "team/ringout")[0], "skip")
        self.assertEqual(ios_cd_gate.decision([self.run_record(status="in_progress", conclusion=None)], "a" * 40, "team/ringout")[0], "wait")

    def test_both_platforms_must_succeed_before_signing_or_deployment(self):
        valid = self.run_record()
        with patch.object(ios_cd_gate, "fetch_runs", side_effect=[[valid], [], [valid], [valid], [valid], [valid]]) as fetch, patch.object(ios_cd_gate.time, "sleep") as sleep:
            ios_cd_gate.wait_for_ci("team/ringout", "a" * 40, "token", "client/release", paths=ALL_CHECKS)
        self.assertEqual([call.args[-1] for call in fetch.call_args_list], ["android-ci.yml", "ios-ci.yml", "client-python-ci.yml"] * 2)
        sleep.assert_called_once()

    def test_either_platform_failure_blocks_signing_or_deployment(self):
        valid = self.run_record()
        failed = self.run_record(conclusion="failure")
        for records in (([failed],), ([valid], [failed])):
            with self.subTest(records=records), patch.object(ios_cd_gate, "fetch_runs", side_effect=records), self.assertRaisesRegex(ValueError, "did not succeed"):
                ios_cd_gate.wait_for_ci("team/ringout", "a" * 40, "token", "client/release", paths=ALL_CHECKS)

    def test_newest_failed_run_blocks_an_older_success(self):
        records = [self.run_record(), self.run_record(id=11, conclusion="cancelled")]
        self.assertEqual(ios_cd_gate.decision(records, "a" * 40, "team/ringout")[0], "skip")

    def test_missing_platform_run_times_out(self):
        with patch.object(ios_cd_gate.time, "monotonic", side_effect=[0, 0, ios_cd_gate.MAX_WAIT_SECONDS]), patch.object(ios_cd_gate, "fetch_runs", return_value=[]), patch.object(ios_cd_gate.time, "sleep"), self.assertRaises(TimeoutError):
            ios_cd_gate.wait_for_ci("team/ringout", "a" * 40, "token", "client/release", paths=ALL_CHECKS)

    def test_release_gate_rejects_development_branches_and_normal_dispatch(self):
        for branch, event in (("develop", "push"), ("client/develop", "push"), ("client/release", "workflow_dispatch")):
            env = {"GITHUB_REF": f"refs/heads/{branch}", "GITHUB_EVENT_NAME": event}
            with self.subTest(branch=branch, event=event), patch.dict(os.environ, env), patch("sys.argv", ["ios_cd_gate.py"]), self.assertRaises(ValueError):
                ios_cd_gate.main()

    def test_recovery_still_waits_for_both_push_ci_runs(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            env = {
                "GITHUB_REF": "refs/heads/client/release", "GITHUB_EVENT_NAME": "workflow_dispatch",
                "GITHUB_SHA": "a" * 40, "GITHUB_REPOSITORY": "team/ringout", "GITHUB_TOKEN": "token", "GITHUB_OUTPUT": str(output),
            }
            with patch.dict(os.environ, env), patch("sys.argv", ["ios_cd_gate.py", "--allow-dispatch"]), patch.object(ios_cd_gate, "release_changes", return_value=ALL_CHECKS), patch.object(ios_cd_gate, "wait_for_ci") as wait:
                self.assertEqual(ios_cd_gate.main(), 0)
            wait.assert_called_once_with("team/ringout", "a" * 40, "token", "client/release", paths=ALL_CHECKS)
            self.assertEqual(output.read_text(), "eligible=true\n")


class CommonPythonGateTest(unittest.TestCase):
    def test_Python_실패나_취소는_플랫폼_CI_성공에도_배포를_막는다(self):
        fixture = IosCdGateTest()
        valid = fixture.run_record()
        for conclusion in ("failure", "cancelled", "timed_out"):
            def fetch(repository, sha, token, branch, workflow):
                return [fixture.run_record(conclusion=conclusion) if workflow == "client-python-ci.yml" else valid]
            with self.subTest(conclusion=conclusion), patch.object(ios_cd_gate, "fetch_runs", side_effect=fetch), self.assertRaisesRegex(ValueError, "client-python-ci.yml"):
                ios_cd_gate.wait_for_ci("team/ringout", "a" * 40, "token", "client/release", paths=ALL_CHECKS)

    def test_Python_결과가_없으면_플랫폼_CI_성공에도_대기하다_실패한다(self):
        valid = IosCdGateTest().run_record()
        def fetch(repository, sha, token, branch, workflow):
            return [] if workflow == "client-python-ci.yml" else [valid]
        with patch.object(ios_cd_gate, "fetch_runs", side_effect=fetch), patch.object(ios_cd_gate.time, "monotonic", side_effect=[0, 0, ios_cd_gate.MAX_WAIT_SECONDS]), patch.object(ios_cd_gate.time, "sleep"), self.assertRaises(TimeoutError):
            ios_cd_gate.wait_for_ci("team/ringout", "a" * 40, "token", "client/release", paths=ALL_CHECKS)


class SelectiveGateTest(unittest.TestCase):
    def test_android_only_waits_for_android_and_skips_absent_other_checks(self):
        valid = IosCdGateTest().run_record()
        with patch.object(ios_cd_gate, "fetch_runs", return_value=[valid]) as fetch:
            ios_cd_gate.wait_for_ci("team/ringout", "a" * 40, "token", "client/release", paths=[b"client/androidApp/src/main/Example.kt"])
        self.assertEqual([call.args[-1] for call in fetch.call_args_list], ["android-ci.yml"])

    def test_shared_application_requires_both_platforms_but_not_python(self):
        valid = IosCdGateTest().run_record()
        with patch.object(ios_cd_gate, "fetch_runs", return_value=[valid]) as fetch:
            ios_cd_gate.wait_for_ci("team/ringout", "a" * 40, "token", "client/release", paths=[b"client/shared/src/commonMain/Example.kt"])
        self.assertEqual([call.args[-1] for call in fetch.call_args_list], ["android-ci.yml", "ios-ci.yml"])

    def test_docs_need_no_ci(self):
        with patch.object(ios_cd_gate, "fetch_runs") as fetch:
            ios_cd_gate.wait_for_ci("team/ringout", "a" * 40, "token", "client/release", paths=[b"client/docs/guide.md"])
        fetch.assert_not_called()

    def test_required_android_failure_or_cancel_blocks_without_querying_ios(self):
        fixture = IosCdGateTest()
        for conclusion in ("failure", "cancelled", "timed_out"):
            with self.subTest(conclusion=conclusion), patch.object(ios_cd_gate, "fetch_runs", return_value=[fixture.run_record(conclusion=conclusion)]) as fetch, self.assertRaisesRegex(ValueError, "android-ci.yml"):
                ios_cd_gate.wait_for_ci("team/ringout", "a" * 40, "token", "client/release", paths=[b"client/androidApp/src/main/Example.kt"])
            self.assertEqual([call.args[-1] for call in fetch.call_args_list], ["android-ci.yml"])

    def test_required_android_missing_times_out_without_querying_ios(self):
        with patch.object(ios_cd_gate.time, "monotonic", side_effect=[0, 0, ios_cd_gate.MAX_WAIT_SECONDS]), patch.object(ios_cd_gate.time, "sleep"), patch.object(ios_cd_gate, "fetch_runs", return_value=[]) as fetch, self.assertRaisesRegex(TimeoutError, "android-ci.yml"):
            ios_cd_gate.wait_for_ci("team/ringout", "a" * 40, "token", "client/release", paths=[b"client/androidApp/src/main/Example.kt"])
        self.assertEqual([call.args[-1] for call in fetch.call_args_list], ["android-ci.yml"])

    def test_ci_only_changes_do_not_deploy(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            env = {
                "GITHUB_REF": "refs/heads/client/release", "GITHUB_EVENT_NAME": "push",
                "GITHUB_SHA": "a" * 40, "GITHUB_REPOSITORY": "team/ringout", "GITHUB_TOKEN": "token", "GITHUB_OUTPUT": str(output),
            }
            with patch.dict(os.environ, env), patch("sys.argv", ["ios_cd_gate.py"]), patch.object(ios_cd_gate, "release_changes", return_value=[b".github/workflows/ios-ci.yml"]), patch.object(ios_cd_gate, "wait_for_ci") as wait:
                self.assertEqual(ios_cd_gate.main(), 0)
            wait.assert_not_called()
            self.assertEqual(output.read_text(), "eligible=false\n")

    def test_push_delegates_validated_exact_event_to_common_change_detection(self):
        with tempfile.TemporaryDirectory() as directory:
            event_path = Path(directory) / "event.json"
            event = {"ref": "refs/heads/client/release", "before": "b" * 40, "after": "a" * 40, "deleted": False}
            event_path.write_text(json.dumps(event))
            env = {"GITHUB_REF": event["ref"], "GITHUB_SHA": event["after"], "GITHUB_EVENT_NAME": "push", "GITHUB_EVENT_PATH": str(event_path)}
            with patch.dict(os.environ, env), patch.object(ios_cd_gate, "push_changed_files", return_value=[b"client/androidApp/main.kt"]) as changed:
                self.assertEqual(ios_cd_gate.release_changes(), [b"client/androidApp/main.kt"])
            changed.assert_called_once_with(event)

    def test_push_payload_mismatch_or_deleted_is_rejected(self):
        for overrides in ({"ref": "refs/heads/main"}, {"after": "c" * 40}, {"deleted": True}, {"before": "HEAD"}):
            with self.subTest(overrides=overrides), tempfile.TemporaryDirectory() as directory:
                event = {"ref": "refs/heads/client/release", "before": "b" * 40, "after": "a" * 40, **overrides}
                event_path = Path(directory) / "event.json"
                event_path.write_text(json.dumps(event))
                env = {"GITHUB_REF": "refs/heads/client/release", "GITHUB_SHA": "a" * 40, "GITHUB_EVENT_NAME": "push", "GITHUB_EVENT_PATH": str(event_path)}
                with patch.dict(os.environ, env), self.assertRaises(ValueError):
                    ios_cd_gate.release_changes()

    def test_first_push_uses_same_change_detection_as_ci(self):
        with tempfile.TemporaryDirectory() as directory:
            event_path = Path(directory) / "event.json"
            event = {"ref": "refs/heads/main", "before": "0" * 40, "after": "a" * 40, "commits": [{"id": "b" * 40}]}
            event_path.write_text(json.dumps(event))
            env = {"GITHUB_REF": "refs/heads/main", "GITHUB_SHA": "a" * 40, "GITHUB_EVENT_NAME": "push", "GITHUB_EVENT_PATH": str(event_path)}
            with patch.dict(os.environ, env), patch.object(ios_cd_gate, "push_changed_files", return_value=[b"client/androidApp/app.kt"]) as changed:
                self.assertEqual(ios_cd_gate.release_changes(), [b"client/androidApp/app.kt"])
            changed.assert_called_once_with(event)

    def test_dispatch_compares_selected_commit_first_parent(self):
        env = {"GITHUB_REF": "refs/heads/main", "GITHUB_SHA": "a" * 40, "GITHUB_EVENT_NAME": "workflow_dispatch"}
        with patch.dict(os.environ, env), patch.object(ios_cd_gate, "run_tool", return_value=(("a" * 40) + " " + ("b" * 40) + " " + ("c" * 40)).encode()), patch.object(ios_cd_gate, "changed_files", return_value=ALL_CHECKS) as changed:
            self.assertEqual(ios_cd_gate.release_changes(), ALL_CHECKS)
        changed.assert_called_once_with(ios_cd_gate.CLIENT_ROOT.parent, "b" * 40, "a" * 40, merge_base=False)


if __name__ == "__main__":
    unittest.main()
