#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
CACHE="/tmp/vercel-android"
mkdir -p "$CACHE" "$ROOT/apk-out"

# Vercel occasionally resets long HTTP/2 downloads. Force HTTP/1.1 and retry
# transport failures so an infrastructure hiccup cannot masquerade as an Android build failure.
download() {
  local url="$1"
  local output="$2"
  local tmp="${output}.part"
  rm -f "$tmp"
  curl --http1.1 -L --fail --retry 5 --retry-all-errors --retry-delay 2 \
    --connect-timeout 30 --max-time 600 -o "$tmp" "$url"
  test -s "$tmp"
  mv "$tmp" "$output"
}

JAVA_MAJOR="$(java -version 2>&1 | awk -F'[\".]' '/version/ {print $2; exit}' || true)"
if [ "${JAVA_MAJOR:-0}" -lt 21 ]; then
  echo "Installing JDK 21..."
  download "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" "$CACHE/jdk.tar.gz"
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
  download "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" "$CACHE/cmdline.zip"
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
  download "https://services.gradle.org/distributions/gradle-8.9-bin.zip" "$CACHE/gradle.zip"
  unzip -q -o "$CACHE/gradle.zip" -d "$CACHE"
fi
export PATH="$GRADLE_HOME/bin:$PATH"
gradle --version

VERSION_NAME="$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n1)"
VERSION_CODE="$(sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' app/build.gradle.kts | head -n1)"
if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
  echo "Unable to derive Android version identity from app/build.gradle.kts" >&2
  exit 2
fi
SAFE_VERSION="$(printf '%s' "$VERSION_NAME" | tr -cs 'A-Za-z0-9._-' '-')"
ARTIFACT="ShoshiLuna-${SAFE_VERSION}-vc${VERSION_CODE}-debug.apk"

printf 'Building Android APK from checked-out source ref. versionName=%s versionCode=%s\n' "$VERSION_NAME" "$VERSION_CODE"

gradle --no-daemon --stacktrace assembleDebug

test -s app/build/outputs/apk/debug/app-debug.apk
cp app/build/outputs/apk/debug/app-debug.apk "apk-out/$ARTIFACT"
sha256sum "apk-out/$ARTIFACT" | tee apk-out/SHA256.txt
cat > apk-out/index.html <<HTML
<!doctype html><html><body><h1>SHOSHI LUNA ${VERSION_NAME}</h1><p>versionCode: ${VERSION_CODE}</p><p><a href="/${ARTIFACT}">Download APK</a></p><p><a href="/SHA256.txt">SHA-256</a></p></body></html>
HTML

rm -rf app/build
