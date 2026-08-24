#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
fail(){ echo "STRICT GATE FAIL: $*" >&2; exit 1; }
pass(){ echo "STRICT GATE PASS: $*"; }

[[ -x ./gradlew ]] || fail "gradlew missing/not executable"
[[ -f app/src/main/AndroidManifest.xml ]] || fail "AndroidManifest.xml missing"
[[ -f app/src/main/res/drawable/shoshi_executive.png ]] || fail "SHOSHI 2D production visual missing"
[[ -f app/src/main/res/drawable/luna_executive.png ]] || fail "LUNA 2D production visual missing"
[[ -f app/src/main/res/drawable/command_center.png ]] || fail "2D command-center master visual missing"
[[ -s app/src/main/assets/models/shoshi.glb ]] || fail "SHOSHI GLB missing"
[[ -s app/src/main/assets/models/luna.glb ]] || fail "LUNA GLB missing"
[[ $(stat -c%s app/src/main/assets/models/shoshi.glb) -ge 102400 ]] || fail "SHOSHI GLB below production threshold"
[[ $(stat -c%s app/src/main/assets/models/luna.glb) -ge 102400 ]] || fail "LUNA GLB below production threshold"

if grep -RInE 'SIMULATED_OUTPUT|Executive Response to|Future: Integrate with a real user approval system|TODO|FIXME|NotImplementedException' app/src/main/java --include='*.kt'; then
  fail "placeholder/fake-success marker found in production code"
fi
pass "no forbidden fake-success markers"
./tools/zero_placeholder_gate.sh

./gradlew --no-daemon clean testDebugUnitTest lintDebug assembleDebug
APK=app/build/outputs/apk/debug/app-debug.apk
[[ -s "$APK" ]] || fail "debug APK not produced"
pass "Gradle tests + lint + assembleDebug"

unzip -l "$APK" | grep -q 'assets/models/shoshi.glb' || fail "SHOSHI GLB not packaged in APK"
unzip -l "$APK" | grep -q 'assets/models/luna.glb' || fail "LUNA GLB not packaged in APK"
pass "2D master visuals and 3D executive assets packaged"

echo "STRICT_ACCEPTANCE_GATE=PASS"
