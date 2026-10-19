#!/bin/sh
set -eu

# Both configurations produce Shared.framework, but from disjoint dependency graphs.
# Always run the selected build, including IDE builds, to avoid reusing the other framework.
case "${CONFIGURATION:?Xcode CONFIGURATION is required}" in
    Debug) module=developerTools ;;
    Release) module=shared ;;
    *) echo "Unsupported Xcode configuration: $CONFIGURATION" >&2; exit 1 ;;
esac
cd "$SRCROOT/.."
set -- ":$module:embedAndSignAppleFrameworkForXcode"
if [ "${RINGOUT_GRADLE_PROFILE:-false}" = "true" ]; then
    set -- "$@" --profile
fi
./gradlew "$@"
