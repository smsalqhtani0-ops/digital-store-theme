#!/usr/bin/env bash
# Build Hala Home's APK with only the Android SDK's command-line tools (no Gradle).
# Needs: ANDROID_JAR (or ANDROID_HOME with platforms/android-35), aapt2, javac, d8 or dx,
# zipalign and apksigner on PATH.
set -Eeuo pipefail
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"
VERSION_CODE=2
VERSION_NAME=1.1
ANDROID_JAR="${ANDROID_JAR:-${ANDROID_HOME:?set ANDROID_HOME or ANDROID_JAR}/platforms/android-35/android.jar}"
OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes"

aapt2 compile --dir res -o "$OUT/res.zip"
aapt2 link -o "$OUT/base.apk" -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
    --min-sdk-version 28 --target-sdk-version 35 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
    --java "$OUT/gen" "$OUT/res.zip"

javac -encoding UTF-8 --release 8 -nowarn -Xlint:-options -cp "$ANDROID_JAR" -d "$OUT/classes" \
    $(find src "$OUT/gen" -name '*.java')

if command -v d8 >/dev/null && d8 --version >/dev/null 2>&1; then
    d8 --min-api 28 --lib "$ANDROID_JAR" --output "$OUT" $(find "$OUT/classes" -name '*.class')
else
    dx --dex --min-sdk-version=28 --output="$OUT/classes.dex" "$OUT/classes"
fi

cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT" && zip -q -j unsigned.apk classes.dex)
zipalign -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

# A development key kept in the repository, so every build can update the last one.
KS="$HERE/hala-dev.keystore"
if [[ ! -f "$KS" ]]; then
    keytool -genkeypair -keystore "$KS" -storepass hala-dev -keypass hala-dev -alias hala \
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Hala Home" -noprompt
fi
apksigner sign --ks "$KS" --ks-pass pass:hala-dev --key-pass pass:hala-dev --ks-key-alias hala \
    --out "HalaHome-$VERSION_NAME.apk" "$OUT/aligned.apk"
apksigner verify "HalaHome-$VERSION_NAME.apk"
echo "Built $HERE/HalaHome-$VERSION_NAME.apk"
