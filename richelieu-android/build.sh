#!/usr/bin/env bash
# Сборка APK без Gradle/Android SDK: aapt + javac + dx + zipalign + apksigner.
# Зависимости (Ubuntu/Debian): apt install openjdk-17-jdk-headless aapt dalvik-exchange zipalign apksigner libandroid-23-java
# Результат: build/richelieu.apk (подписан отладочным ключом build/debug.keystore).
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
OUT=build
rm -rf "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT"/*.apk
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"


# 1. R.java
aapt package -f -m -J "$OUT/gen" -M src/main/AndroidManifest.xml -S src/main/res -I "$ANDROID_JAR"

# 2. Java -> class (без лямбд: dx их не десахарит)
javac -nowarn -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -d "$OUT/classes" \
  $(find src/main/java "$OUT/gen" -name '*.java') 2>&1 | grep -v '^warning: \[options\]' || true

# 3. class -> dex
dalvik-exchange --dex --output="$OUT/dex/classes.dex" "$OUT/classes"

# 4. ресурсы + dex -> неподписанный APK
aapt package -f -M src/main/AndroidManifest.xml -S src/main/res -A src/main/assets \
  -I "$ANDROID_JAR" -F "$OUT/unsigned.apk"
(cd "$OUT/dex" && aapt add -f ../unsigned.apk classes.dex >/dev/null)

# 5. выравнивание (обязательно для targetSdk >= 30) и подпись (v1+v2+v3)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
[ -f "$OUT/debug.keystore" ] || keytool -genkeypair -keystore "$OUT/debug.keystore" -storepass android \
  -keypass android -alias debug -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Richelieu Debug,O=Debug,C=RU" 2>/dev/null
apksigner sign --ks "$OUT/debug.keystore" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/richelieu.apk" "$OUT/aligned.apk"
apksigner verify --verbose "$OUT/richelieu.apk" | head -6
rm -f "$OUT/aligned.apk" "$OUT/unsigned.apk" "$OUT/richelieu.apk.idsig"
ls -l "$OUT/richelieu.apk"
