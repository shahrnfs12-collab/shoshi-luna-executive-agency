#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
CACHE="/tmp/vercel-android"
mkdir -p "$CACHE" "$ROOT/apk-out"

JAVA_MAJOR="$(java -version 2>&1 | awk -F'[\".]' '/version/ {print $2; exit}' || true)"
if [ "${JAVA_MAJOR:-0}" -lt 21 ]; then
  echo "Installing JDK 21..."
  curl -L --fail --retry 3 -o "$CACHE/jdk.tar.gz" "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
  rm -rf "$CACHE/jdk"
  mkdir -p "$CACHE/jdk"
  tar -xzf "$CACHE/jdk.tar.gz" -C "$CACHE/jdk" --strip-components=1
  export JAVA_HOME="$CACHE/jdk"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
java -version

export ANDROID_HOME="$CACHE/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
mkdir -p "$ANDROID_HOME/cmdline-tools"
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Installing Android command line tools..."
  curl -L --fail --retry 3 -o "$CACHE/cmdline.zip" "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  rm -rf "$CACHE/cmdline-unpack" "$ANDROID_HOME/cmdline-tools/latest"
  mkdir -p "$CACHE/cmdline-unpack"
  unzip -q "$CACHE/cmdline.zip" -d "$CACHE/cmdline-unpack"
  mv "$CACHE/cmdline-unpack/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --licenses >/dev/null || true
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

GRADLE_HOME="$CACHE/gradle-8.9"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  echo "Installing Gradle 8.9..."
  curl -L --fail --retry 3 -o "$CACHE/gradle.zip" "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
  unzip -q -o "$CACHE/gradle.zip" -d "$CACHE"
fi
export PATH="$GRADLE_HOME/bin:$PATH"
gradle --version

echo "Building Android APK..."
gradle --no-daemon --stacktrace assembleDebug

test -s app/build/outputs/apk/debug/app-debug.apk
cp app/build/outputs/apk/debug/app-debug.apk apk-out/ShoshiLuna-latest.apk
sha256sum apk-out/ShoshiLuna-latest.apk | tee apk-out/SHA256.txt
cat > apk-out/index.html <<'HTML'
<!doctype html><html><body><h1>SHOSHI LUNA APK build</h1><p><a href="/ShoshiLuna-latest.apk">Download APK</a></p><p><a href="/SHA256.txt">SHA-256</a></p></body></html>
HTML

rm -rf app/build
