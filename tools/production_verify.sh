#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
fail(){ echo "PRODUCTION_TRUTH_GATE=FAIL: $*" >&2; exit 1; }
./tools/zero_placeholder_gate.sh
./tools/strict_verify.sh
# Runtime instrumentation remains mandatory for a FULL runtime-ready claim.
if command -v adb >/dev/null 2>&1 && adb get-state >/dev/null 2>&1; then
  ./gradlew --no-daemon connectedDebugAndroidTest
else
  fail "No connected Android emulator/device; runtime UI/voice/AI gate was not executed"
fi
./gradlew --no-daemon assembleRelease
RELEASE_APK=$(find app/build/outputs/apk/release -maxdepth 1 -name '*.apk' -type f | head -1)
[[ -n "$RELEASE_APK" && -s "$RELEASE_APK" ]] || fail "release APK missing"
mkdir -p evidence/current
sha256sum app/build/outputs/apk/debug/app-debug.apk "$RELEASE_APK" > evidence/current/apk_sha256.txt
./gradlew dependencies > evidence/current/dependency_report.txt
cat > evidence/current/presentation_status.txt <<'STATUS'
SHOSHI_LUNA_2D_UI=PRODUCTION_UI
SHOSHI_LUNA_3D=STRUCTURALLY_VALIDATED_AND_SCENEVIEW_RUNTIME_TESTED
STATUS
echo "PRODUCTION_TRUTH_GATE=PASS"
