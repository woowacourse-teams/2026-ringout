"""Fail closed if debug developer tools enter release compiler inputs or artifacts."""

import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MARKERS = ("developertools", "DeveloperTools", "개발자 도구")


def reject_markers(data, location):
    for marker in MARKERS:
        for encoding in ("utf-8", "utf-16le"):
            if marker.encode(encoding) in data:
                raise ValueError(f"Developer tools found in {location}: {marker}")


def verify(root, artifact):
    app_build = root / "androidApp/build"
    sources_path = app_build / "reports/developer-tools/release-sources.txt"
    sources = sources_path.read_text().splitlines()
    if not sources or not any("/src/release/" in p for p in sources):
        raise ValueError("Release compiler input report is empty or lacks release sources")
    if not any("/shared/src/commonMain/" in p for p in sources):
        raise ValueError("Shared compiler inputs are missing")
    for source in sources:
        if "/src/debug/" in source.replace("\\", "/"):
            raise ValueError(f"Debug source compiled into release: {source}")
        reject_markers(Path(source).read_bytes(), source)

    classes = app_build / "intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes"
    class_files = list(classes.rglob("*.class"))
    if not class_files:
        raise ValueError("Pre-R8 release classes are missing")
    for path in class_files:
        reject_markers(path.read_bytes(), path)

    manifest = app_build / "intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"
    manifest_bytes = manifest.read_bytes()
    reject_markers(manifest_bytes, manifest)
    if b'android:debuggable="true"' in manifest_bytes:
        raise ValueError("Release manifest is debuggable")
    mapping = app_build / "outputs/mapping/release/mapping.txt"
    if not mapping.is_file() or not mapping.stat().st_size:
        raise ValueError("R8 mapping is missing")
    reject_markers(mapping.read_bytes(), mapping)

    with zipfile.ZipFile(artifact) as archive:
        names = archive.namelist()
        dex_files = [n for n in names if n.endswith(".dex")]
        if not dex_files or not any(n.endswith("AndroidManifest.xml") for n in names):
            raise ValueError("Artifact lacks DEX or manifest")
        # Includes packaged DEX, binary/protobuf manifests, resource tables and assets.
        for name in names:
            reject_markers(name.encode(), name)
            if not name.endswith("/"):
                reject_markers(archive.read(name), name)
    return {
        "artifact": str(artifact.relative_to(root)),
        "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
        "sourceCount": len(sources),
        "preR8ClassCount": len(class_files),
        "dexCount": len(dex_files),
        "checks": ["compiler inputs", "pre-R8 classes", "merged manifest", "R8 mapping", "packaged entries"],
        "result": "PASS",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kind", choices=("apk", "bundle"), required=True)
    args = parser.parse_args()
    extension = "apk" if args.kind == "apk" else "aab"
    folder = ROOT / f"androidApp/build/outputs/{args.kind}/release"
    artifacts = list(folder.glob(f"*.{extension}"))
    if len(artifacts) != 1:
        raise ValueError(f"Expected exactly one {extension} in {folder}")
    destination = ROOT / f"androidApp/build/reports/developer-tools/release-{args.kind}.json"
    destination.unlink(missing_ok=True)
    report = verify(ROOT, artifacts[0])
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
