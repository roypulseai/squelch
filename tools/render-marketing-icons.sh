#!/usr/bin/env bash
#
# Render the marketing icon at all sizes Android / iOS / Play / App Store
# need. Run on macOS or Linux with rsvg-convert installed
# (brew install librsvg / apt install librsvg2-bin).
#
# The source SVG is committed at marketing/icon.svg.
#
# Outputs to marketing/out/ and overwrites without warning.
#
# Usage:
#   tools/render-marketing-icons.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/marketing/icon.svg"
OUT="$ROOT/marketing/out"

mkdir -p "$OUT"

sizes=(48 72 96 144 192 256 512 1024)

for s in "${sizes[@]}"; do
    rsvg-convert -w "$s" -h "$s" "$SRC" -o "$OUT/icon-${s}.png"
    echo "rendered icon-${s}.png"
done

# favicon style for web docs
rsvg-convert -w 32 -h 32 "$SRC" -o "$ROOT/marketing/favicon.png"
echo "rendered favicon.png"