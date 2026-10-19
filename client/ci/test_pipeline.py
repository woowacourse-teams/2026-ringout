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
    def event(self, base="client/develop", head="feature/test", repository="team/repo"):
        return {
            "repository": {"full_name": "team/repo"},
            "pull_request": {"base": {"ref": base}, "head": {"ref": head, "repo": {"full_name": repository}}},
        }

    def test_main_requires_same_repository_develop(self):
        ci.validate_pr_route(self.event())
        ci.validate_pr_route(self.event(base="main", head="client/release"))
        for event in (self.event(base="main"), self.event(base="main", head="client/release", repository="fork/repo")):
            with self.subTest(event=event), self.assertRaises(ci.CIError):
                ci.validate_pr_route(event)

    def test_승격_PR은_같은_저장소의_직전_브랜치만_허용한다(self):
        ci.validate_pr_route(self.event(base="client/release", head="client/develop"))
        for base, head, repo in (
            ("develop", "feature/test", "team/repo"),
            ("client/release", "feature/test", "team/repo"),
            ("client/release", "client/develop", "fork/repo"),
            ("main", "client/develop", "team/repo"),
            ("main", "client/release", "fork/repo"),
        ):
            with self.subTest(base=base, head=head, repo=repo), self.assertRaises(ci.CIError):
                ci.validate_pr_route(self.event(base, head, repo))

    def test_iOS는_Android_전용_변경을_생략하고_공유_변경을_검증한다(self):
        for path in (b"client/androidApp/App.kt", b"client/shared/src/androidMain/App.kt",
                     b"client/shared/src/androidHostTest/Test.kt", b"client/docs/guide.md"):
            self.assertFalse(ci.is_client_path(path, "ios"), path)
        for path in (b"client/iosApp/App.swift", b"client/shared/src/iosMain/App.kt",
                     b"client/shared/src/commonTest/Test.kt", b".github/workflows/ios-ci.yml"):
            self.assertTrue(ci.is_client_path(path, "ios"), path)

    def test_기준_세_브랜치_push만_검증하고_개발_브랜치에서는_서명을_금지한다(self):
        for branch in ("client/develop", "client/release", "main"):
            with self.subTest(branch=branch):
                with patch.object(ci, "push_changed_files", return_value=[b"client/iosApp/iosApp/App.swift"]):
                    self.assertTrue(ci.verification_required({
                        "ref": f"refs/heads/{branch}", "before": "0" * 40, "after": "b" * 40,
                    }, "push", "ios"))
        for branch in ("develop", "client/develop", "feature/test"):
            with self.subTest(branch=branch), self.assertRaises(ci.CIError):
                ci.expected_firebase_project(f"refs/heads/{branch}")

    def test_배포설정만_변경하면_플랫폼_테스트는_실행하지_않는다(self):
        for platform in ("android", "ios"):
            path = f".github/workflows/{platform}-internal-cd.yml".encode()
            for base, head, expected in (("client/develop", "feature/test", False),
                                         ("client/release", "client/develop", False),
                                         ("main", "client/release", False)):
                event = self.event(base, head)
                event["pull_request"]["base"]["sha"] = "a" * 40
                event["pull_request"]["head"]["sha"] = "b" * 40
                with self.subTest(platform=platform, base=base), patch.object(ci, "changed_files", return_value=[path]):
                    self.assertEqual(ci.verification_required(event, "pull_request", platform), expected)

    def test_승격_PR에서도_Python_변경_판정을_지원한다(self):
        event = self.event("main", "client/release")
        event["pull_request"]["base"]["sha"] = "a" * 40
        event["pull_request"]["head"]["sha"] = "b" * 40
        for path, expected in ((b"client/ci/pipeline.py", True),
                               (b"client/shared/src/commonMain/App.kt", False)):
            with self.subTest(path=path), patch.object(ci, "changed_files", return_value=[path]):
                self.assertEqual(ci.verification_required(event, "pull_request", "python"), expected)

    def test_변경_감지는_플랫폼_테스트_필요_여부만_출력한다(self):
        with tempfile.TemporaryDirectory() as directory:
            event_file = Path(directory) / "event.json"
            event_file.write_text("{}")
            env = {"GITHUB_EVENT_NAME": "pull_request", "GITHUB_EVENT_PATH": str(event_file)}
            for changed in (True, False):
                with self.subTest(changed=changed), patch.dict(os.environ, env), patch.object(ci, "verification_required", return_value=changed), patch.object(ci, "github_value") as output:
                    ci.detect_changes()
                    output.assert_called_once_with("GITHUB_OUTPUT", "client_changed", str(changed).lower())

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

    def test_안드로이드와_공유_입력은_검증하고_iOS와_문서는_제외한다(self):
        for path in (b"client/androidApp/build.gradle.kts", b"client/shared/test.kt", b".github/workflows/android-ci.yml",
                     b".github/actions/setup/action.yml"):
            self.assertTrue(ci.is_client_path(path), path)
        self.assertFalse(ci.is_client_path(b"server/App.java"))
        self.assertFalse(ci.is_client_path(b"README.md"))
        for path in (b"client/README.md", b"client/iosApp/iosApp/View.swift",
                     b"client/shared/src/iosMain/Platform.kt", b"client/shared/src/iosTest/PlatformTest.kt",
                     b"client/developerTools/src/iosMain/Tools.kt",
                     b"client/docs/ci.md", b"client/shared/README.md", b"client/AGENTS.md"):
            with self.subTest(path=path):
                self.assertFalse(ci.is_client_path(path))
        for path in (b"client/gradle/libs.versions.toml", b"client/settings.gradle.kts",
                     b"client/gradle.properties",
                     b"client/developerTools/src/commonMain/Tools.kt", b"client/new-build-input"):
            with self.subTest(path=path):
                self.assertTrue(ci.is_client_path(path))

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


    def test_기준_브랜치_push는_이전과_이후_트리를_비교한다(self):
        event = {"ref": "refs/heads/client/release", "before": "a" * 40, "after": "b" * 40}
        with patch.object(ci, "changed_files", return_value=[b"client/shared/code.kt"]) as diff:
            self.assertTrue(ci.verification_required(event, "push"))
            diff.assert_called_once_with(ci.CLIENT_ROOT.parent, event["before"], event["after"], merge_base=False)
        with patch.object(ci, "changed_files", return_value=[b"server/App.java"]):
            self.assertFalse(ci.verification_required(event, "push"))

    def test_최초_push는_첫_커밋의_부모부터_전체_push를_비교한다(self):
        event = {"ref": "refs/heads/client/release", "before": "0" * 40, "after": "c" * 40,
                 "commits": [{"id": "b" * 40}, {"id": "c" * 40}], "size": 2}
        with patch.object(ci, "run_tool", return_value=f"{'b' * 40} {'a' * 40}\n".encode()), patch.object(ci, "changed_files", return_value=[b"client/androidApp/src/main/App.kt"]) as diff:
            self.assertTrue(ci.verification_required(event, "push"))
            diff.assert_called_once_with(ci.CLIENT_ROOT.parent, "a" * 40, "c" * 40, merge_base=False)
        for changed in ({"ref": "refs/heads/feature/test"}, {"deleted": True}):
            with self.subTest(changed=changed), self.assertRaises(ci.CIError):
                ci.verification_required(dict(event, **changed), "push")
        with self.assertRaises(ci.CIError):
            ci.verification_required(event, "workflow_dispatch")
        with self.assertRaises(ci.CIError):
            ci.push_changed_files(dict(event, size=3))

    def test_새_브랜치의_여러_커밋은_앞선_앱_변경도_검증한다(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "client").mkdir()
            def git(*args):
                return subprocess.check_output(["git", *args], cwd=root, stderr=subprocess.DEVNULL).decode().strip()
            git("init")
            git("config", "user.name", "CI Test")
            git("config", "user.email", "ci@example.invalid")
            (root / "server.txt").write_text("initial")
            git("add", ".")
            git("commit", "-m", "initial")
            source = root / "client/androidApp/src/main/App.kt"
            source.parent.mkdir(parents=True)
            source.write_text("app change")
            git("add", ".")
            git("commit", "-m", "android")
            first = git("rev-parse", "HEAD")
            (root / "client/README.md").write_text("docs change")
            git("add", ".")
            git("commit", "-m", "docs")
            head = git("rev-parse", "HEAD")
            event = {"ref": "refs/heads/client/release", "before": "0" * 40, "after": head,
                     "commits": [{"id": first}, {"id": head}], "size": 2}
            with patch.object(ci, "CLIENT_ROOT", root / "client"):
                self.assertTrue(ci.verification_required(event, "push", "android"))
                self.assertFalse(ci.verification_required(event, "push", "ios"))

    def test_Python_보조코드나_반대_플랫폼_CI는_앱_검증을_시작하지_않는다(self):
        for path in (b"client/ci/test_pipeline.py", b".github/workflows/client-python-ci.yml"):
            for platform in ("android", "ios"):
                self.assertFalse(ci.is_client_path(path, platform), (path, platform))
        self.assertFalse(ci.is_client_path(b".github/workflows/ios-ci.yml", "android"))
        self.assertFalse(ci.is_client_path(b".github/workflows/android-ci.yml", "ios"))

    def test_CI_판정_코드나_빌드_분리_설정_변경에도_플랫폼_테스트를_실행한다(self):
        for platform in ("android", "ios"):
            paths = (b"client/ci/pipeline.py", b"client/ci/change_policy.py",
                     f".github/workflows/{platform}-pr-build.yml".encode())
            for path in paths:
                for base, head in (("client/develop", "fix/ci"),
                                   ("client/release", "client/develop"),
                                   ("main", "client/release")):
                    event = self.event(base, head)
                    event["pull_request"]["base"]["sha"] = "a" * 40
                    event["pull_request"]["head"]["sha"] = "b" * 40
                    with self.subTest(platform=platform, path=path, base=base), patch.object(
                        ci, "changed_files", return_value=[path]
                    ):
                        self.assertTrue(ci.verification_required(event, "pull_request", platform))

    def test_PR은_병합_기준점을_사용하고_main_진입_규칙을_유지한다(self):
        event = self.event()
        event["pull_request"]["base"]["sha"] = "a" * 40
        event["pull_request"]["head"]["sha"] = "b" * 40
        with patch.object(ci, "changed_files", return_value=[b"client/shared/code.kt"]) as diff:
            self.assertTrue(ci.verification_required(event, "pull_request"))
            diff.assert_called_once_with(ci.CLIENT_ROOT.parent, "a" * 40, "b" * 40)
        with self.assertRaises(ci.CIError):
            ci.verification_required(self.event(base="main"), "pull_request")


class ConfigurationTest(unittest.TestCase):
    def test_channel_selection_does_not_allocate_a_version(self):
        with tempfile.TemporaryDirectory() as directory:
            env_file = Path(directory) / "github-env"
            env_file.touch()
            with patch.dict(os.environ, {"GITHUB_REF": "refs/heads/client/release", "GITHUB_ENV": str(env_file)}):
                ci.select_build()
            self.assertEqual(env_file.read_text(), "AAB_CHANNEL=internal\n")
            with patch.dict(os.environ, {"GITHUB_REF": "refs/heads/main", "GITHUB_ENV": str(env_file)}):
                ci.select_build()
            self.assertEqual(env_file.read_text(), "AAB_CHANNEL=internal\nAAB_CHANNEL=release\n")

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

    def test_signed_build_rejects_firebase_project_from_other_branch(self):
        fixture = json.loads((ci.CLIENT_ROOT / "ci/google-services.ci.json").read_text())
        fixture["project_info"]["project_id"] = "ringout-8abf2"
        fixture["project_info"]["storage_bucket"] = "ringout-8abf2.invalid"
        fixture["client"][0]["api_key"][0]["current_key"] = "test-config-key"
        data = json.dumps(fixture).encode()
        ci.validate_google_services(data, ci.expected_firebase_project("refs/heads/client/release"))
        with self.assertRaises(ci.CIError):
            ci.validate_google_services(data, ci.expected_firebase_project("refs/heads/main"))
        with self.assertRaises(ci.CIError):
            ci.expected_firebase_project("refs/heads/feature")

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
        source = self.root / "androidApp/build.gradle.kts"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text('versionCode = 42\nversionName = "1.1.0"\n')
        directory = self.root / "androidApp/build/intermediates/merged_manifests/release/processReleaseManifest"
        directory.mkdir(parents=True)
        (directory / "AndroidManifest.xml").write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
            'package="com.joon.ringout" android:versionCode="42" android:versionName="1.1.0"/>'
        )
        env = {"AAB_CHANNEL": "internal", "GITHUB_SHA": "a" * 40,
               "GITHUB_REF_NAME": "client/release", "GITHUB_RUN_ID": "10", "GITHUB_RUN_ATTEMPT": "1"}
        with patch.dict(os.environ, env):
            self.assertEqual(ci.release_metadata(self.root)["versionCode"], 42)
            source.write_text('versionCode = 43\nversionName = "1.1.0"\n')
            with self.assertRaises(ci.CIError):
                ci.release_metadata(self.root)
            source.write_text('versionCode = 42\nversionName = "1.2.0"\n')
            with self.assertRaises(ci.CIError):
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

    def test_release_configuration_rejects_generated_resources_from_other_project(self):
        fixture = json.loads((ci.CLIENT_ROOT / "ci/google-services.ci.json").read_text())
        fixture["project_info"]["project_id"] = "ringout-prod"
        fixture["project_info"]["storage_bucket"] = "ringout-prod.invalid"
        fixture["client"][0]["api_key"][0]["current_key"] = "test-config-key"
        config = self.root / "google-services.json"
        config.write_text(json.dumps(fixture))
        directory = self.root / "androidApp/build/generated/res/processReleaseGoogleServices/values"
        directory.mkdir(parents=True)
        generated = directory / "values.xml"
        generated.write_text('<resources><string name="project_id">ringout-8abf2</string>'
                             '<string name="google_app_id">1:123456789012:android:0000000000000000000000</string>'
                             '<string name="gcm_defaultSenderId">123456789012</string></resources>')
        with patch.dict(os.environ, {"GITHUB_REF": "refs/heads/main"}):
            with self.assertRaises(ci.CIError):
                ci.verify_release_firebase_configuration(self.root, config)
            generated.write_text(generated.read_text().replace("ringout-8abf2", "ringout-prod"))
            self.assertEqual(ci.verify_release_firebase_configuration(self.root, config), "ringout-prod")
            generated.write_text(generated.read_text().replace("123456789012", "other", 1))
            with self.assertRaises(ci.CIError):
                ci.verify_release_firebase_configuration(self.root, config)


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
            "project_info": {"project_id": "ringout-8abf2", "project_number": "123"},
            "client": [{"client_info": {"mobilesdk_app_id": "1:123:android:abcd",
                         "android_client_info": {"package_name": ci.APPLICATION_ID}},
                        "api_key": [{"current_key": "test-config-key"}]}],
        }
        github_env = self.root / "github-env"
        github_env.touch()
        values = {
            "RUNNER_TEMP": str(self.root), "GITHUB_ENV": str(github_env),
            "GITHUB_REF": "refs/heads/client/release",
            "ANDROID_KEYSTORE_BASE64": base64.b64encode(self.store.read_bytes()).decode(),
            "ANDROID_KEY_ALIAS": "upload", "ANDROID_KEY_PASSWORD": "wrong-password",
            "MAPS_API_KEY": "test-maps-key",
            "GOOGLE_SERVICES_JSON_BASE64": base64.b64encode(json.dumps(config).encode()).decode(),
        }
        with patch.dict(os.environ, values), patch.object(ci, "expected_certificate", return_value=self.fingerprint):
            with patch.dict(os.environ, {"GITHUB_REF": "refs/heads/main"}), self.assertRaises(ci.CIError):
                ci.prepare_signing()
            self.assertFalse((self.root / "ringout-signing").exists())
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
