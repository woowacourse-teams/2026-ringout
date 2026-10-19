"""Framework routing and profiling must preserve the selected build graph."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "iosApp/scripts/build-kotlin-framework.sh"


class KotlinFrameworkTest(unittest.TestCase):
    def invoke(self, configuration, profile="false"):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "iosApp").mkdir()
            wrapper = root / "gradlew"
            wrapper.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\n')
            wrapper.chmod(0o755)
            return subprocess.run(
                ["sh", str(SCRIPT)],
                env=dict(os.environ, SRCROOT=str(root / "iosApp"), CONFIGURATION=configuration,
                         RINGOUT_GRADLE_PROFILE=profile),
                capture_output=True, text=True, check=False,
            )

    def test_Debug는_개발자_도구를_빌드하고_요청한_경우에만_프로파일을_남긴다(self):
        normal = self.invoke("Debug")
        measured = self.invoke("Debug", "true")
        self.assertEqual(normal.returncode, 0)
        self.assertEqual(measured.returncode, 0)
        self.assertEqual(normal.stdout.splitlines(), [":developerTools:embedAndSignAppleFrameworkForXcode"])
        self.assertEqual(measured.stdout.splitlines(), [":developerTools:embedAndSignAppleFrameworkForXcode", "--profile"])

    def test_Release는_공유_모듈만_빌드한다(self):
        result = self.invoke("Release")
        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stdout.splitlines(), [":shared:embedAndSignAppleFrameworkForXcode"])

    def test_알수없는_구성은_Gradle을_실행하지_않는다(self):
        result = self.invoke("Unknown")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
