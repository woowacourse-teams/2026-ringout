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
        "artifact": str(artifact.relative_to(root) if artifact.is_relative_to(root) else artifact),
        "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
        "sourceCount": len(sources),
        "preR8ClassCount": len(class_files),
        "dexCount": len(dex_files),
        "checks": ["compiler inputs", "pre-R8 classes", "merged manifest", "R8 mapping", "packaged entries"],
        "result": "PASS",
    }


def resolve_artifact(kind, path):
    """Use AGP's current output metadata, never an APK left by another build."""
    path = path.resolve()
    if kind == "apk":
        metadata = json.loads((path / "output-metadata.json").read_text())
        if metadata.get("variantName") != "release" or metadata.get("artifactType", {}).get("type") != "APK":
            raise ValueError("Expected release APK output metadata")
        elements = metadata.get("elements", [])
        if len(elements) != 1:
            raise ValueError("Expected exactly one APK in output metadata")
        artifact = (path / elements[0]["outputFile"]).resolve()
        if not artifact.is_relative_to(path) or artifact.suffix != ".apk":
            raise ValueError("Invalid APK output path in metadata")
    else:
        artifact = path
        if artifact.suffix != ".aab":
            raise ValueError("Expected an AAB artifact path")
    if not artifact.is_file():
        raise FileNotFoundError(f"Build artifact is missing: {artifact}")
    return artifact


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kind", choices=("apk", "bundle"), required=True)
    parser.add_argument("--artifact-path", type=Path, help="AGP APK directory or AAB file")
    args = parser.parse_args()
    destination = ROOT / f"androidApp/build/reports/developer-tools/release-{args.kind}.json"
    destination.unlink(missing_ok=True)
    default_path = ROOT / f"androidApp/build/outputs/{args.kind}/release"
    if args.kind == "bundle":
        default_path /= "androidApp-release.aab"
    artifact = resolve_artifact(args.kind, args.artifact_path or default_path)
    report = verify(ROOT, artifact)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
