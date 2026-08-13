#!/usr/bin/env bash
#
# Print all SHA-1 fingerprints a developer should register with the
# Squelch OAuth client in Google Cloud Console. Run on each machine
# that builds the app and copy each output line into the OAuth client's
# "SHA-1 certificate fingerprint" field.
#
# For Play Store uploads you'll also want the SHA-256 of the release
# keystore (printed last).

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "== SHA-1 fingerprints =="
echo "(paste each into Google Cloud Console -> API & Services ->"
echo " Credentials -> OAuth 2.0 client IDs -> Squelch-Android ->"
echo " 'Signing-cert fingerprint')"
echo

if [[ -f "$USERPROFILE/.android/debug.keystore" ]]; then
    DEBUG_KS="$(cygpath "$USERPROFILE")/.android/debug.keystore"
    echo "[debug keystore]"
    keytool -list -v -keystore "$DEBUG_KS" -storepass android 2>&1 | grep "SHA1:"
else
    echo "[no ~/.android/debug.keystore yet]"
fi

if [[ -f "$ROOT/keystore/squelch.jks" ]]; then
    echo
    echo "[release keystore (keystore/squelch.jks)]"
    keytool -list -v -keystore "$ROOT/keystore/squelch.jks" \
        -storepass "squelch-dev-passphrase" 2>&1 | grep "SHA1:"
else
    echo
    echo "[no release keystore yet - run tools/setup-release-keystore.sh]"
fi

echo
echo "== SHA-256 (for Play Console 'upload-key certificate') =="
if [[ -f "$ROOT/keystore/squelch.jks" ]]; then
    keytool -list -v -keystore "$ROOT/keystore/squelch.jks" \
        -storepass "squelch-dev-passphrase" 2>&1 | grep "SHA256:"
fi