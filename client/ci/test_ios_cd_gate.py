import unittest

import ios_cd_gate


class IosCdGateTest(unittest.TestCase):
    def run_record(self, **overrides):
        record = {
            "id": 10,
            "head_sha": "a" * 40,
            "head_branch": "develop",
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


if __name__ == "__main__":
    unittest.main()
