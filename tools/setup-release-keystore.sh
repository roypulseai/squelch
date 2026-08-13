#!/usr/bin/env bash
#
# Generate the Squelch release keystore. Run once per machine that
# builds the release APK. The keystore is committed to a gitignored
# path; only the *example* in keystore/squelch.jks.example is in the
# repo.
#
# IMPORTANT: register the SHA-1 of the resulting keystore against the
# Google Cloud OAuth client (see docs/repo-settings.md for the gh cli
# command). The Print SHA-1 line at the end tells you which to paste.
#
# Usage:
#   ./tools/setup-release-keystore.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KS_DIR="$ROOT/keystore"
KS_FILE="$KS_DIR/squelch.jks"
KS_EXAMPLE="$KS_DIR/squelch.jks.example"
ALIAS="${SQUELCH_KEY_ALIAS:-squelch}"
STORE_PASS="${SQUELCH_STORE_PASS:-squelch-dev-passphrase}"
KEY_PASS="${SQUELCH_KEY_PASS:-$STORE_PASS}"
DNAME="${SQUELCH_DNAME:-CN=Squelch P2P, OU=Roypulse, O=Squelch, L=City, S=State, C=US}"

mkdir -p "$KS_DIR"

if [[ -f "$KS_FILE" ]]; then
    echo "keystore already exists at $KS_FILE"
    echo "rm $KS_FILE and re-run this script if you really want to rotate."
else
    echo "generating $KS_FILE (alias=$ALIAS, storepass=$STORE_PASS)"
    keytool -genkeypair \
        -keystore "$KS_FILE" \
        -storepass "$STORE_PASS" \
        -keypass  "$KEY_PASS" \
        -alias "$ALIAS" \
        -keyalg RSA -keysize 2048 \
        -validity 9125 \
        -dname "$DNAME"
    echo "wrote $KS_FILE"
fi

# Anonymised example, committed so contributors know what to put in
# local.properties without ever checking in their private key.
if [[ ! -f "$KS_EXAMPLE" ]]; then
    cat > "$KS_EXAMPLE" <<'EOF'
# Squelch release keystore - EXAMPLE ONLY.
#
# Generate a real one with:
#   ./tools/setup-release-keystore.sh
# This generates keystore/squelch.jks locally, prints the SHA-1, and
# configures local.properties. The actual .jks is gitignored.
#
# In local.properties (gitignored), the following lines point Gradle at
# the keystore. Passwords are environment-only and never committed.
#
#   squelch.keystore.file=keystore/squelch.jks
#   squelch.keystore.storepass=<store password>
#   squelch.keystore.keypass=<key password>
#   squelch.keystore.alias=squelch
EOF
    echo "wrote $KS_EXAMPLE"
fi

echo
echo "-----  SHA-1 fingerprint (paste into Google Cloud OAuth client)  -----"
keytool -list -v -keystore "$KS_FILE" -storepass "$STORE_PASS" \
    | grep -E "SHA1:|Alias name: " | head -2