#!/usr/bin/env bash
#
# Build a release APK + AAB for Squelch.
#   ./tools/release-build.sh           # builds both APK + AAB
#   ./tools/release-build.sh apk      # APK only
#   ./tools/release-build.sh bundle   # AAB only (for Play Store)
#
# Reads signing config from local.properties:
#   squelch.keystore.file / storepass / keypass / alias
# Run tools/setup-release-keystore.sh once before this script.
#
# Output:
#   app/build/outputs/apk/release/app-release.apk
#   app/build/outputs/bundle/release/app-release.aab

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE="${SQUELCH_GRADLE:-${ROOT}/../gradlew}"

if [[ -f "$GRADLE" ]]; then
    echo "using gradle wrapper: $GRADLE"
else
    GRADLE_RAW="$(which gradle || true)"
    if [[ -z "$GRADLE_RAW" ]]; then
        echo "no gradle found - run with \$SQUELCH_GRADLE=/path/to/gradle" >&2
        exit 1
    fi
    GRADLE="$GRADLE_RAW"
    echo "using gradle on PATH: $GRADLE"
fi

MODE="${1:-all}"

cd "$ROOT"

if [[ "$MODE" == "apk" || "$MODE" == "all" ]]; then
    "$GRADLE" :app:assembleRelease
    echo "apk: $ROOT/app/build/outputs/apk/release/app-release.apk"
fi

if [[ "$MODE" == "bundle" || "$MODE" == "all" ]]; then
    "$GRADLE" :app:bundleRelease
    echo "aab: $ROOT/app/build/outputs/bundle/release/app-release.aab"
fi

echo
echo "done. remember to verify the APK's SHA-256 against the value"
echo "Google Play console expects from the upload-key certificate."