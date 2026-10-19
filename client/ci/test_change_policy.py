"""Behavior matrix for application, infrastructure, and deployment changes."""

import unittest

from change_policy import CD_PATHS, CI_PATHS, ci_required, deployment_required, matches


class ChangePolicyTest(unittest.TestCase):
    def test_변경_종류에_따라_필요한_CI와_배포만_선택한다(self):
        # Expected order: Android CI, iOS CI, Python CI, Android CD, iOS CD.
        cases = {
            "client/androidApp/src/main/Main.kt": (1, 0, 0, 1, 0),
            "client/iosApp/iosApp/App.swift": (0, 1, 0, 0, 1),
            "client/shared/src/commonMain/App.kt": (1, 1, 0, 1, 1),
            "client/shared/src/commonTest/AppTest.kt": (1, 1, 0, 0, 0),
            "client/shared/src/androidMain/App.kt": (1, 0, 0, 1, 0),
            "client/shared/src/androidHostTest/AppTest.kt": (1, 0, 0, 0, 0),
            "client/shared/src/iosMain/App.kt": (0, 1, 0, 0, 1),
            "client/shared/src/iosTest/AppTest.kt": (0, 1, 0, 0, 0),
            "client/androidApp/src/test/Test.kt": (1, 0, 0, 0, 0),
            "client/androidApp/src/androidTest/Test.kt": (1, 0, 0, 0, 0),
            "client/iosApp/iosAppTests/JournalTests.swift": (0, 1, 0, 0, 0),
            "client/developerTools/src/commonMain/Tool.kt": (1, 1, 0, 1, 1),
            "client/developerTools/src/androidMain/Tool.kt": (1, 0, 0, 1, 0),
            "client/developerTools/src/iosMain/Tool.kt": (0, 1, 0, 0, 1),
            "client/shared/build.gradle.kts": (1, 1, 0, 1, 1),
            "client/shared/schemas/database/12.json": (1, 1, 0, 1, 1),
            "client/gradle/libs.versions.toml": (1, 1, 0, 1, 1),
            "client/build.gradle.kts": (1, 1, 0, 1, 1),
            "client/gradlew": (1, 1, 0, 1, 1),
            "client/new-build-setting.properties": (1, 1, 0, 1, 1),
            "client/ci/test_pipeline.py": (0, 0, 1, 0, 0),
            "client/ci/new_helper.py": (0, 0, 1, 0, 0),
            "client/ci/pipeline.py": (1, 1, 1, 1, 1),
            "client/ci/change_policy.py": (1, 1, 1, 1, 1),
            "client/ci/android_internal_cd.py": (0, 0, 1, 1, 0),
            "client/ci/android_production_cd.py": (0, 0, 1, 1, 0),
            "client/ci/ios_internal.py": (0, 0, 1, 0, 1),
            "client/ci/verify_ios_version.py": (0, 0, 1, 0, 1),
            "client/ci/swift/AlarmRecoveryJournalTests.swift": (0, 1, 0, 0, 0),
            "client/ci/google-services.ci.json": (1, 0, 1, 0, 0),
            "client/iosApp/scripts/build-kotlin-framework.sh": (0, 1, 1, 0, 1),
            ".github/workflows/android-ci.yml": (1, 0, 1, 0, 0),
            ".github/workflows/ios-ci.yml": (0, 1, 1, 0, 0),
            ".github/workflows/android-pr-build.yml": (1, 0, 1, 0, 0),
            ".github/workflows/ios-pr-build.yml": (0, 1, 1, 0, 0),
            ".github/workflows/client-python-ci.yml": (0, 0, 1, 0, 0),
            ".github/workflows/client-pr-route.yml": (0, 0, 1, 0, 0),
            ".github/workflows/build-release-aab.yml": (0, 0, 1, 1, 0),
            ".github/workflows/android-internal-cd.yml": (0, 0, 1, 1, 0),
            ".github/workflows/ios-production-cd.yml": (0, 0, 1, 0, 1),
            ".github/actions/setup/action.yml": (1, 1, 1, 1, 1),
            "client/README.md": (0, 0, 0, 0, 0),
            "client/shared/docs/example.kt": (0, 0, 0, 0, 0),
            "client/docs/release/guide.sql": (0, 0, 0, 0, 0),
            "client/androidApp/README.md": (0, 0, 0, 0, 0),
            "server/src/main/App.kt": (0, 0, 0, 0, 0),
            ".github/workflows/server-ci.yml": (0, 0, 0, 0, 0),
        }
        for path, expected in cases.items():
            with self.subTest(path=path):
                actual = tuple(ci_required([path], platform) for platform in CI_PATHS)
                actual += tuple(deployment_required([path], platform) for platform in CD_PATHS)
                self.assertEqual(actual, expected)

    def test_혼합_변경에서_문서_제외가_앱_변경을_취소하지_않는다(self):
        paths = [b"client/shared/src/commonMain/App.kt", b"client/shared/docs/App.kt"]
        for platform in ("android", "ios"):
            self.assertTrue(ci_required(iter(paths), platform))
            self.assertTrue(deployment_required(iter(paths), platform))

    def test_삭제와_이동은_이전_경로와_새_경로를_모두_판정한다(self):
        # git diff --no-renames reports both sides even for moves out of client/.
        paths = [b"client/androidApp/src/main/Old.kt", b"server/New.kt"]
        self.assertTrue(ci_required(paths, "android"))
        self.assertTrue(deployment_required(paths, "android"))
        self.assertFalse(deployment_required(paths, "ios"))

    def test_배포_브랜치와_다른_환경의_워크플로_변경만으로_배포하지_않는다(self):
        for platform in ("android", "ios"):
            internal = f".github/workflows/{platform}-internal-cd.yml"
            production = f".github/workflows/{platform}-production-cd.yml"
            self.assertTrue(deployment_required([internal], platform, "client/release"))
            self.assertFalse(deployment_required([internal], platform, "main"))
            self.assertTrue(deployment_required([production], platform, "refs/heads/main"))
            self.assertFalse(deployment_required([production], platform, "client/release"))
            self.assertFalse(deployment_required([internal], platform, "client/develop"))
            self.assertTrue(deployment_required(
                [internal, f"client/{platform}App/app.config"], platform, "main"))

    def test_별표는_경로_구분자를_넘지_않고_이중_별표는_넘는다(self):
        self.assertFalse(matches(["client/ci/swift/Test.swift"], ["client/*"]))
        self.assertTrue(matches(["client/file.md"], ["client/**/*.md"]))
        self.assertTrue(matches(["client/nested/file.md"], ["client/**/*.md"]))
        self.assertTrue(matches(["client/a.kt"], ["client/**", "!client/**", "client/a.kt"]))
        self.assertFalse(matches([], ["client/**"]))

    def test_동일_파일의_바이트와_문자열_표현은_같이_판정한다(self):
        for path in ("client/shared/src/commonMain/위치.kt", "client/ci/test_pipeline.py"):
            for platform in CI_PATHS:
                self.assertEqual(ci_required([path], platform), ci_required([path.encode()], platform))


if __name__ == "__main__":
    unittest.main()
