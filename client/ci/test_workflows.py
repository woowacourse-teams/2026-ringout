"""Cross-workflow release contracts, using only the Python standard library."""

from pathlib import Path
import re
import unittest

from change_policy import CI_PATHS, CD_PATHS, PR_CI_PATHS


WORKFLOWS = Path(__file__).resolve().parents[2] / ".github/workflows"
CI = ("android-ci", "ios-ci")
PR_BUILD = ("android-pr-build", "ios-pr-build")
PYTHON_CI = "client-python-ci"
CD = ("build-release-aab", "android-internal-cd", "android-production-cd",
      "ios-internal-cd", "ios-production-cd")


def trigger(text, event):
    return re.search(rf"^  {event}:\n(.*?)(?=^  \w|^\w|\Z)", text, re.M | re.S).group(1)


class WorkflowContractTest(unittest.TestCase):
    def text(self, name):
        return (WORKFLOWS / f"{name}.yml").read_text()

    def test_YAML과_실행시_변경_판정이_같은_정책을_사용한다(self):
        expected = {
            "android-ci": CI_PATHS["android"], "ios-ci": CI_PATHS["ios"],
            PYTHON_CI: CI_PATHS["python"],
            **{name: PR_CI_PATHS[name.removesuffix("-pr-build")] for name in PR_BUILD},
            **{name: CD_PATHS["ios" if name.startswith("ios-") else "android"] for name in CD},
        }
        for name, patterns in expected.items():
            events = ("pull_request",) if name in PR_BUILD else (("push", "pull_request") if name in (*CI, PYTHON_CI) else ("push",))
            for event in events:
                with self.subTest(workflow=name, event=event):
                    actual = re.findall(r"^      - '([^']+)'$", trigger(self.text(name), event), re.M)
                    self.assertEqual(tuple(actual), patterns)

    def test_Android_AAB_생성과_배포가_같은_변경에_반응한다(self):
        paths = lambda name: re.findall(r"^      - (.+)$", trigger(self.text(name), "push"), re.M)
        self.assertEqual(paths("build-release-aab"), paths("android-internal-cd"))
        self.assertEqual(paths("build-release-aab"), paths("android-production-cd"))

    def test_개발_브랜치는_CI만_실행하고_배포는_릴리스와_main에서_실행한다(self):
        for name in (*CI, PYTHON_CI, "client-pr-route"):
            self.assertIn("branches: [client/develop, client/release, main]", trigger(self.text(name), "pull_request"))
        for name in CD:
            push = trigger(self.text(name), "push")
            branches = re.search(r"branches: \[(.*?)\]", push).group(1).split(", ")
            self.assertNotIn("client/develop", branches)
            self.assertNotIn("branches: [develop", push)
            self.assertIn("client/release" if "internal" in name else "main", push)

    def test_플랫폼_CI는_테스트와_최종_판정을_실행한다(self):
        for name in CI:
            text = self.text(name)
            self.assertIn("pipeline.py changes", text)
            self.assertIn("pipeline.py gate", text)
            self.assertIn("QUALITY_RESULT:", text)
        self.assertIn(":androidApp:lintRelease", self.text("android-ci"))
        self.assertIn(":shared:iosSimulatorArm64Test", self.text("ios-ci"))
        self.assertIn("ci/swift/AlarmRecoveryJournalTests.swift", self.text("ios-ci"))
        self.assertIn("iosApp/iosApp/Platform/AlarmRecoveryCommand.swift", self.text("ios-ci"))
        self.assertIn("ci/swift/AlarmRecoveryCommandTests.swift", self.text("ios-ci"))
        self.assertIn('"$RUNNER_TEMP/ringout-alarm-recovery-command-tests"', self.text("ios-ci"))

    def test_빌드_워크플로는_승격_PR에서만_생성되고_일반_CI에_빌드_job이_없다(self):
        commands = {
            "android": (":androidApp:bundleRelease", "pipeline.py verify-r8", "pipeline.py verify-unsigned"),
            "ios": ("CODE_SIGNING_ALLOWED=NO", "verify_ios_firebase.py"),
        }
        for platform, required in commands.items():
            ci = self.text(f"{platform}-ci")
            build = self.text(f"{platform}-pr-build")
            event = trigger(build, "pull_request")
            self.assertIn("branches: [client/release, main]", event)
            self.assertIn("types: [opened, synchronize, reopened]", event)
            branches = re.search(r"branches: \[(.*?)\]", event).group(1).split(", ")
            self.assertNotIn("client/develop", branches)
            self.assertNotRegex(build, re.compile(r"^  (push|pull_request_target|workflow_run|workflow_dispatch):", re.M))
            self.assertNotIn("promotion-build:", ci)
            self.assertNotIn("PROMOTION_BUILD_RESULT", ci)
            self.assertIn("needs: [changes, quality]", ci)
            self.assertIn("pipeline.py route", build)
            self.assertNotIn("continue-on-error", build)
            self.assertNotRegex(build, re.compile(r"^    if:", re.M))
            for command in required:
                with self.subTest(platform=platform, command=command):
                    self.assertNotIn(command, ci)
                    self.assertIn(command, build)

    def test_승격_PR은_검증_성공한_앱_파일을_별도_artifact로_보관한다(self):
        expected = {
            "android-pr-build": ("Verify promotion AAB", "android-pr-aab-", "client/androidApp/build/outputs/bundle/release/*.aab"),
            "ios-pr-build": ("Verify embedded development Firebase", "ios-pr-simulator-app-", "${{ runner.temp }}/Ringout-simulator.app.zip"),
        }
        for name, (verification, artifact, path) in expected.items():
            build = self.text(name)
            steps = build.split("      - name: ")[1:]
            uploads = [step for step in steps if artifact in step]
            self.assertEqual(len(uploads), 1)
            upload = uploads[0]
            self.assertIn("actions/upload-artifact@", upload)
            self.assertIn(path, upload)
            self.assertIn("if-no-files-found: error", upload)
            self.assertIn("retention-days: 14", upload)
            self.assertNotIn("if: always()", upload)
            self.assertNotIn("continue-on-error", upload)
            self.assertLess(build.index(verification), build.index(artifact))
        ios = self.text("ios-pr-build")
        self.assertIn('ditto -c -k --sequesterRsrc --keepParent', ios)
        self.assertIn('"$RUNNER_TEMP/ringout-ios-derived-data/Build/Products/Debug-iphonesimulator/Ringout.app"', ios)
        self.assertIn('"$RUNNER_TEMP/Ringout-simulator.app.zip"', ios)

    def test_서명_AAB는_동일_커밋의_통합_CI_성공_후_생성한다(self):
        text = self.text("build-release-aab")
        self.assertIn("run: python3 ci/ios_cd_gate.py --platform android --allow-dispatch", text)
        self.assertIn("    needs: gate", text)
        self.assertIn("actions: read", text)

    def test_Python_전체_검증은_공통_job에서만_한번_실행한다(self):
        command = "python3 -B -m unittest discover -s ci -p 'test_*.py' -v"
        owners = [path.stem for path in WORKFLOWS.glob("*.yml") if command in path.read_text()]
        self.assertEqual(owners, [PYTHON_CI])
        common = self.text(PYTHON_CI)
        self.assertIn("runs-on: ubuntu-24.04", common)
        self.assertIn("uses: actions/setup-java@", common)
        self.assertIn("keytool -help", common)
        self.assertIn("jarsigner -help", common)
        self.assertIn("types: [opened, synchronize, reopened]", common)

    def test_CD는_배포_필요_출력과_전체_이력으로_후속_job을_제어한다(self):
        for name in CD:
            with self.subTest(workflow=name):
                text = self.text(name)
                # Every signing/publication job depends on an explicit eligible result.
                self.assertIn("eligible: ${{ steps.check.outputs.eligible }}", text)
                self.assertIn("if: needs.gate.outputs.eligible == 'true'", text)
                self.assertIn("fetch-depth: 0", text)
                self.assertIn("needs: gate", text)
