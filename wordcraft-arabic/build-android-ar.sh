#!/usr/bin/env bash
# Build the Arabic WordCraft APK with MostafaXS1/artcraft-android.
# Usage: bash build-android-ar.sh /path/to/artcraft-android
set -Eeuo pipefail
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
AC="${1:?path to an artcraft-android checkout}"
SRC="$AC/source/wordcraft"
if [[ ! -d "$SRC/.git" ]]; then
    git clone https://github.com/storytold/wordcraft "$SRC"
    git -C "$SRC" checkout -q e6c5a701ddff5be16e266028168c70658aae39d9
fi
if git -C "$SRC" apply --check "$HERE/wordcraft-arabic.patch" 2>/dev/null; then
    git -C "$SRC" apply "$HERE/wordcraft-arabic.patch"
else
    echo "Patch already applied or the source moved on; continuing." >&2
fi
# Embed craft-fonts so the interface carries its own Arabic face.
FONTS="$AC/craft-fonts"
[[ -d "$FONTS" ]] || git clone --depth 1 https://github.com/storytold/craft-fonts "$FONTS"
export CRAFT_FONTS_DIR="$FONTS"
bash "$AC/build.sh" -wordcraft
