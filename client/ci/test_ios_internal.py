import json
import plistlib
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

import ios_internal
import verify_ios_version


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

    def test_archive_matches_xcode_release_version(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            settings = root / "settings.json"
            settings.write_text(json.dumps([{"target": "iosApp", "buildSettings": {
                "MARKETING_VERSION": "1.0.0", "CURRENT_PROJECT_VERSION": "261000006",
            }}]))
            app = root / "Ringout.app"
            app.mkdir()
            info = app / "Info.plist"
            info.write_bytes(plistlib.dumps({
                "CFBundleShortVersionString": "1.0.0", "CFBundleVersion": "261000006",
            }))
            self.assertEqual(verify_ios_version.verify_archive(settings, app), ("1.0.0", "261000006"))
            info.write_bytes(plistlib.dumps({
                "CFBundleShortVersionString": "1.0.0", "CFBundleVersion": "300000701",
            }))
            with self.assertRaisesRegex(ValueError, "does not match"):
                verify_ios_version.verify_archive(settings, app)
            info.write_bytes(plistlib.dumps({
                "CFBundleShortVersionString": "1.1.0", "CFBundleVersion": "261000006",
            }))
            with self.assertRaisesRegex(ValueError, "does not match"):
                verify_ios_version.verify_archive(settings, app)

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
