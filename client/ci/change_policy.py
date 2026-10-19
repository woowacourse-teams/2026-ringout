"""Shared path policy for workflow triggers and same-commit CI/CD decisions.

Paths are repository-relative. Patterns follow the GitHub path-filter subset used
here: *, **, ?, and ordered ! exclusions. Keep these tuples as the workflow
source of truth; an excluded path does not cancel another included changed file.
"""

from functools import lru_cache
import re


CLIENT_WORKFLOWS = (
    "client-pr-route", "client-python-ci", "android-ci", "ios-ci",
    "android-pr-build", "ios-pr-build",
    "build-release-aab", "android-internal-cd", "android-production-cd",
    "ios-internal-cd", "ios-production-cd",
)
_DOC_EXCLUSIONS = ("!client/**/*.md", "!client/**/docs/**")
_BUILD_PATHS = ("client/*", "client/gradle/**", "client/buildSrc/**",
                "client/build-logic/**", "client/config/**", "client/configuration/**")


def _app_paths(platform):
    opposite = "ios" if platform == "android" else "android"
    return (
        f"client/{platform}App/**", "client/shared/**", "client/developerTools/**",
        *(f"!client/{module}/src/{opposite}*/**" for module in ("shared", "developerTools")),
        *_BUILD_PATHS,
    )


CI_PATHS = {
    platform: (
        *_app_paths(platform), ".github/actions/**",
        "client/ci/pipeline.py", "client/ci/change_policy.py",
        f".github/workflows/{platform}-ci.yml",
        f".github/workflows/{platform}-pr-build.yml",
        *(("client/ci/swift/**",) if platform == "ios" else ()),
        *(("client/ci/google-services.ci.json",) if platform == "android" else ()),
        *_DOC_EXCLUSIONS,
    )
    for platform in ("android", "ios")
}
CI_PATHS["python"] = (
    "client/ci/*.py", "client/ci/*.json", "client/ci/*.sha256",
    "client/ci/fixtures/**", "client/iosApp/scripts/build-kotlin-framework.sh",
    *(f".github/workflows/{name}.yml" for name in CLIENT_WORKFLOWS),
    ".github/actions/**",
)

_DEPLOYMENT_HELPERS = (
    "client/ci/pipeline.py", "client/ci/api_config.py",
    "client/ci/production_config.py", "client/ci/ios_cd_gate.py",
    "client/ci/change_policy.py",
)


def _deployment_paths(platform):
    return (
        *_app_paths(platform),
        "!client/**/src/*Test*/**", "!client/**/src/test*/**",
        "!client/**/src/androidTest/**",
        f"!client/{platform}App/**/Tests/**",
        f"!client/{platform}App/**/*Tests/**",
        f"!client/{platform}App/**/*UITests/**",
        *_DEPLOYMENT_HELPERS,
        *(("client/ci/android_internal_cd.py", "client/ci/android_production_cd.py",
           "client/ci/verify_developer_tools.py",
           "client/ci/upload-certificate.sha256", ".github/workflows/build-release-aab.yml")
          if platform == "android" else
          ("client/ci/ios_internal.py", "client/ci/verify_ios_firebase.py",
           "client/ci/verify_ios_version.py")),
        f".github/workflows/{platform}-internal-cd.yml",
        f".github/workflows/{platform}-production-cd.yml",
        ".github/actions/**", *_DOC_EXCLUSIONS,
    )


CD_PATHS = {platform: _deployment_paths(platform) for platform in ("android", "ios")}


# Separate promotion build workflows validate CI and deployment inputs.
PR_CI_PATHS = {
    platform: (
        *(path for path in CD_PATHS[platform]
          if not path.startswith("!") and path not in CI_PATHS[platform]),
        *CI_PATHS[platform],
    )
    for platform in ("android", "ios")
}


@lru_cache(maxsize=None)
def _glob(pattern):
    expression = ""
    index = 0
    while index < len(pattern):
        if pattern.startswith("**/", index):
            expression += "(?:.*/)?"
            index += 3
        elif pattern.startswith("**", index):
            expression += ".*"
            index += 2
        elif pattern[index] == "*":
            expression += "[^/]*"
            index += 1
        elif pattern[index] == "?":
            expression += "[^/]"
            index += 1
        else:
            expression += re.escape(pattern[index])
            index += 1
    return re.compile(expression + r"\Z")


def matches(paths, patterns):
    """Return whether any path remains included after ordered exclusions."""
    patterns = tuple(patterns)
    for path in paths:
        path = path.decode("utf-8", errors="surrogateescape") if isinstance(path, bytes) else path
        included = False
        for pattern in patterns:
            negative = pattern.startswith("!")
            if _glob(pattern[1:] if negative else pattern).fullmatch(path):
                included = not negative
        if included:
            return True
    return False


def ci_required(paths, platform):
    return matches(paths, CI_PATHS[platform])


def deployment_required(paths, platform, branch=None):
    patterns = CD_PATHS[platform]
    if branch is not None:
        branch = branch.removeprefix("refs/heads/")
        if branch not in ("main", "client/release"):
            return False
        opposite = "internal" if branch == "main" else "production"
        patterns = (*patterns, f"!.github/workflows/{platform}-{opposite}-cd.yml")
    return matches(paths, patterns)
