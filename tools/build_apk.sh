#!/usr/bin/env bash
# 手工构建 APK（不依赖 Gradle / 网络）
# 使用 Android SDK build-tools: aapt2 -> javac -> d8 -> zip -> zipalign -> apksigner
set -euo pipefail

SDK="C:/Users/Administrator/AppData/Local/Android/sdk"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
JDK="D:/javaJDK/jdk-17.0.12"
PROJ="D:/Projects/ChineseXiangqi"
SRC="$PROJ/app/src/main"
OUT="$PROJ/build/manual"
DIST="$PROJ/dist"
APK_NAME="ChineseXiangqi-1.2.0.apk"
# DEBUGGABLE=1 时产出可 run-as 的调试包（仅用于自动化验证，不用于分发）
if [ "${DEBUGGABLE:-0}" = "1" ]; then
    APK_NAME="ChineseXiangqi-debug.apk"
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$DIST"

echo "[1/7] aapt2 compile"
"$BT/aapt2.exe" compile --dir "$SRC/res" -o "$OUT/res.zip"

echo "[2/7] aapt2 link"
# AGP8 风格 manifest 无 package 属性，这里生成临时副本补上
MANIFEST_SED='s|<manifest |<manifest package="com.workbuddy.xiangqi" |'
if [ "${DEBUGGABLE:-0}" = "1" ]; then
    MANIFEST_SED="$MANIFEST_SED; s|<application|<application android:debuggable=\"true\"|"
fi
sed "$MANIFEST_SED" "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"

"$BT/aapt2.exe" link \
    -o "$OUT/unaligned.apk" \
    -I "$PLATFORM" \
    --manifest "$OUT/AndroidManifest.xml" \
    -R "$OUT/res.zip" \
    --java "$OUT/gen" \
    --min-sdk-version 24 \
    --target-sdk-version 34 \
    --version-code 3 \
    --version-name "1.2.0" \
    --auto-add-overlay

echo "[3/7] javac"
find "$SRC/java" "$OUT/gen" -name "*.java" > "$OUT/sources.txt"
# android.jar 之外还需要 core-lambda-stubs.jar 才支持 lambda 表达式
if ! "$JDK/bin/javac.exe" -encoding UTF-8 -nowarn \
    -J-Duser.language=en -J-Duser.country=US \
    -source 8 -target 8 \
    -bootclasspath "$PLATFORM;$BT/core-lambda-stubs.jar" \
    -d "$OUT/classes" \
    @"$OUT/sources.txt" > "$OUT/javac.log" 2>&1; then
    echo "javac 失败:"
    cat "$OUT/javac.log"
    exit 1
fi
echo "  编译 $(grep -c '\.java' "$OUT/sources.txt") 个源文件"

echo "[4/7] jar + d8"
"$JDK/bin/jar.exe" cf "$OUT/classes.jar" -C "$OUT/classes" .
"$BT/d8.bat" --lib "$PLATFORM" --min-api 24 --release \
    --output "$OUT/dex" "$OUT/classes.jar"

echo "[5/7] 打包 dex"
python - "$OUT/unaligned.apk" "$OUT/dex/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
print("  dex 已写入")
PY

echo "[6/7] zipalign"
"$BT/zipalign.exe" -f -p 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

echo "[7/7] apksigner"
export JAVA_HOME="$JDK"
"$BT/apksigner.bat" sign \
    --ks "C:/Users/Administrator/.android/debug.keystore" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$DIST/$APK_NAME" \
    "$OUT/aligned.apk"

echo
echo "构建完成: $DIST/$APK_NAME"
ls -lh "$DIST/$APK_NAME"
