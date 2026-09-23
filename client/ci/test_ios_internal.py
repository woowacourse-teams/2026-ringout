import os
import plistlib
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

import ios_internal


class IosInternalTest(unittest.TestCase):
    def test_export_is_internal_only_and_uses_manual_signing(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "ExportOptions.plist"
            ios_internal.export_options("profile-uuid", path)
            options = plistlib.loads(path.read_bytes())

        self.assertEqual(options["destination"], "upload")
        self.assertEqual(options["method"], "app-store-connect")
        self.assertEqual(options["signingStyle"], "manual")
        self.assertTrue(options["testFlightInternalTestingOnly"])
        self.assertFalse(options["manageAppVersionAndBuildNumber"])
        self.assertEqual(options["provisioningProfiles"], {ios_internal.BUNDLE_ID: "profile-uuid"})

    def test_build_number_changes_for_each_attempt(self):
        with patch.dict(os.environ, {"GITHUB_RUN_NUMBER": "42", "GITHUB_RUN_ATTEMPT": "1"}):
            first = ios_internal.build_number()
        with patch.dict(os.environ, {"GITHUB_RUN_NUMBER": "42", "GITHUB_RUN_ATTEMPT": "2"}):
            retry = ios_internal.build_number()
        self.assertEqual(int(retry), int(first) + 1)
        self.assertGreater(int(first), 261000004)

    def test_profile_rejects_a_different_app(self):
        profile = {
            "TeamIdentifier": [ios_internal.TEAM_ID],
            "Entitlements": {
                "application-identifier": "475GWW72JW.com.example.other",
                "get-task-allow": False,
                "com.apple.developer.applesignin": ["Default"],
            },
            "Name": "Ringout iOS App Store CI",
            "UUID": "profile-uuid",
            "ExpirationDate": datetime.now(timezone.utc) + timedelta(days=30),
        }
        encoded = plistlib.dumps(profile)
        with patch.object(ios_internal.subprocess, "run") as run:
            run.return_value.stdout = encoded
            with self.assertRaisesRegex(ValueError, "wrong app ID"):
                ios_internal.validate_profile(Path("unused.mobileprovision"))


if __name__ == "__main__":
    unittest.main()
