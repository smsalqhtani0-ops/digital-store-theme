#!/usr/bin/env bash
# Regenerate Hala's artwork: icons (VectorDrawables) and wallpapers (WebP).
# Needs python3 with Pillow, node with Playwright and a Chromium it can launch.
set -Eeuo pipefail
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$HERE")"
python3 "$HERE/icons.py"
python3 "$HERE/wallpapers.py"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$ROOT/res/drawable-nodpi"
for svg in "$HERE"/wallpapers/*.svg; do
    name="$(basename "$svg" .svg)"
    node "$HERE/render.js" "$svg" "$TMP/$name.png" 2560 2560
    python3 -c 'import sys; from PIL import Image; Image.open(sys.argv[1]).convert("RGB").save(sys.argv[2], "WEBP", quality=90, method=6)' \
        "$TMP/$name.png" "$ROOT/res/drawable-nodpi/wall_$name.webp"
done
ls -la "$ROOT/res/drawable-nodpi"
