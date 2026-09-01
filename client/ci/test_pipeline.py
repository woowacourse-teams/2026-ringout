"""CI routing and artifact regression checks; no Android SDK or real secrets required."""

import base64
import copy
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import pipeline as ci


class RoutingTest(unittest.TestCase):
    def event(self, base="develop", head="feature/test", repository="team/repo"):
        return {
            "repository": {"full_name": "team/repo"},
            "pull_request": {"base": {"ref": base}, "head": {"ref": head, "repo": {"full_name": repository}}},
        }

    def test_main_requires_same_repository_develop(self):
        ci.validate_pr_route(self.event())
        ci.validate_pr_route(self.event(base="main", head="develop"))
        for event in (self.event(base="main"), self.event(base="main", head="develop", repository="fork/repo")):
            with self.subTest(event=event), self.assertRaises(ci.CIError):
                ci.validate_pr_route(event)

    def test_required_gate_does_not_hide_failed_or_skipped_checks(self):
        ci.check_gate("success", "true", "success")
        ci.check_gate("success", "false", "skipped")
        for changes, changed, quality in (
            ("failure", "false", "skipped"), ("cancelled", "true", "success"),
            ("success", "true", "failure"), ("success", "true", "cancelled"),
            ("success", "true", "skipped"), ("success", "", "skipped"),
        ):
            with self.subTest(quality=quality), self.assertRaises(ci.CIError):
                ci.check_gate(changes, changed, quality)

    def test_client_and_ci_changes_are_detected(self):
        for path in (b"client/README.md", b"client/shared/test.kt", b".github/workflows/client-ci.yml",
                     b".github/workflows/build-release-aab.yml", b".github/actions/setup/action.yml"):
            self.assertTrue(ci.is_client_path(path), path)
        self.assertFalse(ci.is_client_path(b"server/App.java"))
        self.assertFalse(ci.is_client_path(b"README.md"))

    def test_moving_client_file_outside_client_is_still_a_client_change(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)

            def git(*args):
                return subprocess.check_output(["git", *args], cwd=root, stderr=subprocess.DEVNULL).decode().strip()

            git("init")
            git("config", "user.name", "CI Test")
            git("config", "user.email", "ci@example.invalid")
            (root / "client").mkdir()
            (root / "client/test.txt").write_text("same content\n")
            git("add", ".")
            git("commit", "-m", "initial")
            base = git("rev-parse", "HEAD")
            git("mv", "client/test.txt", "test.txt")
            git("commit", "-m", "move")
            files = ci.changed_files(root, base, git("rev-parse", "HEAD"))
            self.assertIn(b"client/test.txt", files)


class ConfigurationTest(unittest.TestCase):
    def test_version_is_shared_across_channels_and_reruns(self):
        self.assertEqual(ci.version_code("261010004", "42"), 261010046)
        for base, number in (("", "1"), ("-1", "1"), ("12", "0"), ("2100000000", "1"), ("12", "1.0")):
            with self.subTest(base=base, number=number), self.assertRaises(ci.CIError):
                ci.version_code(base, number)

    def test_google_services_rejects_fixture_and_wrong_app(self):
        fixture = json.loads((ci.CLIENT_ROOT / "ci/google-services.ci.json").read_text())
        with self.assertRaises(ci.CIError):
            ci.validate_google_services(json.dumps(fixture).encode())
        valid = copy.deepcopy(fixture)
        valid["project_info"]["project_id"] = "test-project"
        valid["project_info"]["storage_bucket"] = "test-project.invalid"
        valid["client"][0]["api_key"][0]["current_key"] = "test-config-key"
        ci.validate_google_services(json.dumps(valid).encode())
        valid["client"][0]["client_info"]["android_client_info"]["package_name"] = "other.app"
        with self.assertRaises(ci.CIError):
            ci.validate_google_services(json.dumps(valid).encode())

    def test_missing_secret_error_names_key_without_value(self):
        with patch.dict(os.environ, {"MAPS_API_KEY": ""}):
            with self.assertRaisesRegex(ci.CIError, "MAPS_API_KEY"):
                ci.require_env("MAPS_API_KEY")


class ArtifactTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.r8 = self.root / "androidApp/build/outputs/mapping/release"
        self.r8.mkdir(parents=True)
        (self.root / "settings.gradle.kts").write_text('classpath("com.android.tools:r8:9.1.31")')
        for name in ci.R8_FILES:
            (self.r8 / name).write_text("content\n")
        (self.r8 / "mapping.txt").write_text("# compiler_version: 9.1.31\n")

    def test_r8_accepts_absent_missing_rules_but_rejects_unresolved_rules(self):
        ci.verify_r8(self.root)
        (self.r8 / "missing_rules.txt").write_text("-dontwarn MissingClass\n")
        with self.assertRaises(ci.CIError):
            ci.verify_r8(self.root)

    def test_r8_rejects_wrong_compiler_and_missing_outputs(self):
        (self.r8 / "mapping.txt").write_text("# compiler_version: wrong\n")
        with self.assertRaises(ci.CIError):
            ci.verify_r8(self.root)
        (self.r8 / "mapping.txt").write_text("# compiler_version: 9.1.31\n")
        (self.r8 / "resources.txt").unlink()
        with self.assertRaises(ci.CIError):
            ci.verify_r8(self.root)

    def test_unsigned_bundle_and_ambiguous_outputs(self):
        directory = self.root / "androidApp/build/outputs/bundle/release"
        directory.mkdir(parents=True)
        aab = directory / "app.aab"
        with zipfile.ZipFile(aab, "w") as bundle:
            bundle.writestr("base/manifest/AndroidManifest.xml", "manifest")
        ci.verify_unsigned(ci.find_aab(self.root))
        with zipfile.ZipFile(aab, "a") as bundle:
            bundle.writestr("META-INF/UPLOAD.RSA", "signature")
        with self.assertRaises(ci.CIError):
            ci.verify_unsigned(aab)
        shutil.copy2(aab, directory / "other.aab")
        with self.assertRaises(ci.CIError):
            ci.find_aab(self.root)

    def test_metadata_matches_built_version(self):
        directory = self.root / "androidApp/build/intermediates/merged_manifests/release/processReleaseManifest"
        directory.mkdir(parents=True)
        (directory / "AndroidManifest.xml").write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
            'package="com.joon.ringout" android:versionCode="42" android:versionName="1.1.0"/>'
        )
        env = {"APP_VERSION_CODE": "42", "AAB_CHANNEL": "internal", "GITHUB_SHA": "a" * 40,
               "GITHUB_REF_NAME": "develop", "GITHUB_RUN_ID": "10", "GITHUB_RUN_ATTEMPT": "1"}
        with patch.dict(os.environ, env):
            self.assertEqual(ci.release_metadata(self.root)["versionCode"], 42)
            with patch.dict(os.environ, {"APP_VERSION_CODE": "43"}), self.assertRaises(ci.CIError):
                ci.release_metadata(self.root)

    def test_ci_configuration_rejects_real_or_overridden_firebase_inputs(self):
        directory = self.root / "androidApp/build/generated/res/processReleaseGoogleServices/values"
        directory.mkdir(parents=True)
        path = directory / "values.xml"
        path.write_text('<resources><string name="project_id">other-project</string></resources>')
        with self.assertRaises(ci.CIError):
            ci.verify_ci_configuration(self.root)
        path.write_text('<resources><string name="project_id">ringout-ci-verification</string>'
                        '<string name="google_api_key">CI_VERIFICATION_ONLY</string></resources>')
        ci.verify_ci_configuration(self.root)


@unittest.skipUnless(shutil.which("keytool") and shutil.which("jarsigner"), "JDK tools required")
class SignatureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.tmp.cleanup)
        cls.root = Path(cls.tmp.name)
        cls.store = cls.root / "test.jks"
        cls.env_patch = patch.dict(os.environ, {"ANDROID_KEYSTORE_PASSWORD": "ci-test-password"})
        cls.env_patch.start()
        cls.addClassCleanup(cls.env_patch.stop)
        ci.run_tool([
            "keytool", "-genkeypair", "-keystore", str(cls.store), "-storetype", "JKS",
            "-alias", "upload", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
            "-dname", "CN=CI Test", "-storepass:env", "ANDROID_KEYSTORE_PASSWORD",
            "-keypass:env", "ANDROID_KEYSTORE_PASSWORD",
        ])
        cert = ci.run_tool(["keytool", "-exportcert", "-keystore", str(cls.store),
                            "-storepass:env", "ANDROID_KEYSTORE_PASSWORD", "-alias", "upload"])
        cls.fingerprint = hashlib.sha256(cert).hexdigest().upper()
        cls.signed = cls.root / "signed.aab"
        with zipfile.ZipFile(cls.signed, "w") as bundle:
            bundle.writestr("base/manifest/AndroidManifest.xml", "test manifest")
        ci.run_tool(["jarsigner", "-keystore", str(cls.store), "-storepass:env", "ANDROID_KEYSTORE_PASSWORD",
                     "-keypass:env", "ANDROID_KEYSTORE_PASSWORD", str(cls.signed), "upload"])

    def test_approved_self_signed_upload_key_passes(self):
        ci.verify_signature(self.signed, self.store, "upload", self.fingerprint)

    def test_wrong_certificate_fails(self):
        with self.assertRaises(ci.CIError):
            ci.verify_signature(self.signed, self.store, "upload", "0" * 64)

    def test_prepare_checks_private_key_password_and_exports_only_paths(self):
        config = {
            "project_info": {"project_id": "test-project", "project_number": "123"},
            "client": [{"client_info": {"mobilesdk_app_id": "1:123:android:abcd",
                         "android_client_info": {"package_name": ci.APPLICATION_ID}},
                        "api_key": [{"current_key": "test-config-key"}]}],
        }
        github_env = self.root / "github-env"
        github_env.touch()
        values = {
            "RUNNER_TEMP": str(self.root), "GITHUB_ENV": str(github_env),
            "ANDROID_KEYSTORE_BASE64": base64.b64encode(self.store.read_bytes()).decode(),
            "ANDROID_KEY_ALIAS": "upload", "ANDROID_KEY_PASSWORD": "wrong-password",
            "MAPS_API_KEY": "test-maps-key",
            "GOOGLE_SERVICES_JSON_BASE64": base64.b64encode(json.dumps(config).encode()).decode(),
        }
        with patch.dict(os.environ, values), patch.object(ci, "expected_certificate", return_value=self.fingerprint):
            with self.assertRaises(ci.CIError):
                ci.prepare_signing()
            self.assertEqual(github_env.read_text(), "")
            with patch.dict(os.environ, {"ANDROID_KEY_PASSWORD": "ci-test-password"}):
                ci.prepare_signing()
            exported = dict(line.split("=", 1) for line in github_env.read_text().splitlines())
            self.assertEqual(set(exported), {"ANDROID_KEYSTORE_PATH", "GOOGLE_SERVICES_JSON_PATH"})
            self.assertEqual(Path(exported["ANDROID_KEYSTORE_PATH"]).stat().st_mode & 0o777, 0o600)

    def test_unsigned_added_entry_fails(self):
        modified = self.root / "extra-entry.aab"
        shutil.copy2(self.signed, modified)
        with zipfile.ZipFile(modified, "a") as bundle:
            bundle.writestr("base/unsigned.txt", "must be rejected")
        with self.assertRaises(ci.CIError):
            ci.verify_signature(modified, self.store, "upload", self.fingerprint)

    def test_tampered_signed_entry_fails(self):
        modified = self.root / "tampered.aab"
        with zipfile.ZipFile(self.signed) as source, zipfile.ZipFile(modified, "w") as target:
            for item in source.infolist():
                target.writestr(item, b"tampered" if item.filename.endswith("AndroidManifest.xml") else source.read(item))
        with self.assertRaises(ci.CIError):
            ci.verify_signature(modified, self.store, "upload", self.fingerprint)


if __name__ == "__main__":
    unittest.main()
