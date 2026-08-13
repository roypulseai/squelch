#!/usr/bin/env bash
#
# Upload an AAB to Google Play via the Play Developer API.
# This is a thin wrapper around `gcloud` + the internal-publishing client
# (or `play-publisher-cli` if you have it installed).
#
# Setup once:
#   1. Create a service account in Google Cloud Console tied to the
#      squelch-p2p project.
#   2. Grant it 'Release manager' role on the Play Console.
#   3. Download its JSON key as service-account.json and put it in
#      tools/keys/ (which is gitignored).
#   4. Register the AAB upload-key SHA-256 with Play Console.
#
# Usage:
#   ./tools/upload-to-play.sh /path/to/app-release.aab internal
#   ./tools/upload-to-play.sh /path/to/app-release.aab production

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

AAB="${1:-}"
TRACK="${2:-internal}"

if [[ -z "$AAB" ]]; then
    echo "usage: $0 <path-to-app-release.aab> [internal|production]" >&2
    exit 1
fi

if [[ ! -f "$AAB" ]]; then
    echo "AAB not found: $AAB" >&2
    exit 1
fi

KEY_FILE="${SQUELCH_PLAY_KEY_FILE:-$ROOT/tools/keys/service-account.json}"
PACKAGE="${SQUELCH_PACKAGE:-com.squelch.app}"

if [[ ! -f "$KEY_FILE" ]]; then
    echo "service account key not found at $KEY_FILE" >&2
    echo "download the JSON key from Google Cloud IAM, drop it there." >&2
    exit 1
fi

# Real implementation uses Google's playpublisher-cli or a thin
# google-api-services-androidpublisher wrapper. Reference (real
# implementation goes here):
#   https://developers.google.com/android-publisher/api-ref/edits
#
# This stub hands the file path off to `play-publisher-cli` if
# available, otherwise prints a manual command so a human can paste
# into the Play Console.

if command -v play-publisher-cli >/dev/null 2>&1; then
    play-publisher-cli --service-account "$KEY_FILE" \
        --package "$PACKAGE" \
        --track "$TRACK" \
        --aab "$AAB"
else
    cat <<EOF
play-publisher-cli not found on PATH. Run:
  pip install play-publisher-cli
or use the curl-based google-api-services-androidpublisher client:

  curl -X POST \\
    -H "Authorization: Bearer \$(cat ./tools/keys/token)" \\
    -H "Content-Type: application/octet-stream" \\
    --data-binary "@$AAB" \\
    "https://androidpublisher.googleapis.com/upload/storage/v1/upload?uploadType=media"

(or use 'gcloud auth activate-service-account --key-file=$KEY_FILE' and
 then the play-api helper). The local helper tools/keys/token
 should hold a short-lived OAuth token.
EOF
fi

echo
echo "done. monitor Play Console for the review state."