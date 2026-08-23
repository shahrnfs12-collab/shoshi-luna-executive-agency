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

if grep -RInE 'SIMULATED_OUTPUT|Executive Response to|Future: Integrate with a real user approval system|TODO|FIXME|NotImplementedException' app/src/main/java --include='*.kt'; then
  fail "placeholder/fake-success marker found in production code"
fi
pass "no forbidden fake-success markers"
./tools/zero_placeholder_gate.sh

./gradlew --no-daemon clean testDebugUnitTest lintDebug assembleDebug
APK=app/build/outputs/apk/debug/app-debug.apk
[[ -s "$APK" ]] || fail "debug APK not produced"
pass "Gradle tests + lint + assembleDebug"

# Phase 6 production presentation is 2D. 3D/GLB is deliberately deferred and must not block this gate.
unzip -l "$APK" | grep -q 'res/drawable.*/shoshi_executive' || true
pass "2D production UI gate; 3D assets deferred"

echo "STRICT_ACCEPTANCE_GATE=PASS"
