"""Regression checks for fail-closed release artifact verification."""

from pathlib import Path
import tempfile
import unittest
import zipfile

from verify_developer_tools import verify


class DeveloperToolsReleaseTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.build = self.root / "androidApp/build"
        source = self.write("androidApp/src/release/kotlin/Entry.kt", b"val extraContent = null")
        shared = self.write("shared/src/commonMain/kotlin/App.kt", b"fun App() {}")
        self.source_report = self.write(
            "androidApp/build/reports/developer-tools/release-sources.txt",
            f"{source}\n{shared}\n".encode(),
        )
        self.classes = self.write(
            "androidApp/build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes/Main.class",
            b"release class fixture",
        )
        self.write(
            "androidApp/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml",
            b"<manifest><application /></manifest>",
        )
        self.mapping = self.write("androidApp/build/outputs/mapping/release/mapping.txt", b"Main -> a:\n")
        self.artifact = self.root / "app.aab"
        self.archive()

    def write(self, path, content):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content)
        return target

    def archive(self, dex=b"dex fixture", manifest=b"manifest fixture"):
        with zipfile.ZipFile(self.artifact, "w") as output:
            output.writestr("base/dex/classes.dex", dex)
            output.writestr("base/manifest/AndroidManifest.xml", manifest)

    def test_개발자_도구가_없는_결과물은_통과한다(self):
        self.assertEqual(verify(self.root, self.artifact)["result"], "PASS")

    def test_난독화로_이름이_바뀌어도_매핑에_도구가_있으면_거부한다(self):
        self.mapping.write_text("com.joon.ringout.presentation.developertools.DeveloperToolsScreenKt -> a:\n")
        with self.assertRaisesRegex(ValueError, "Developer tools"):
            verify(self.root, self.artifact)

    def test_R8이_도구를_제거해도_컴파일_입력에_debug가_있으면_거부한다(self):
        with self.source_report.open("a") as output:
            output.write(str(self.root / "androidApp/src/debug/kotlin/Entry.kt") + "\n")
        with self.assertRaisesRegex(ValueError, "Debug source"):
            verify(self.root, self.artifact)

    def test_컴파일된_클래스의_도구_참조를_거부한다(self):
        self.classes.write_bytes(b"com/joon/ringout/presentation/developertools/Screen")
        with self.assertRaisesRegex(ValueError, "Developer tools"):
            verify(self.root, self.artifact)

    def test_바이너리_manifest의_도구_등록을_거부한다(self):
        self.archive(manifest="DeveloperToolsActivity".encode("utf-16le"))
        with self.assertRaisesRegex(ValueError, "Developer tools"):
            verify(self.root, self.artifact)

    def test_추가_DEX에_도구_문구가_남으면_거부한다(self):
        with zipfile.ZipFile(self.artifact, "a") as output:
            output.writestr("base/dex/classes2.dex", "개발자 도구".encode())
        with self.assertRaisesRegex(ValueError, "Developer tools"):
            verify(self.root, self.artifact)

    def test_컴파일_입력_보고서가_없으면_통과시키지_않는다(self):
        self.source_report.unlink()
        with self.assertRaises(FileNotFoundError):
            verify(self.root, self.artifact)

    def test_공통_모듈_컴파일_입력이_누락되면_거부한다(self):
        self.source_report.write_text(self.source_report.read_text().splitlines()[0] + "\n")
        with self.assertRaisesRegex(ValueError, "Shared compiler"):
            verify(self.root, self.artifact)
