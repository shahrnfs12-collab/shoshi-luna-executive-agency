#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
fail(){ echo "ZERO_PLACEHOLDER_GATE=FAIL: $*" >&2; exit 1; }
patterns='SIMULATED_OUTPUT|Executive Response to|NotImplementedException|TODO|FIXME|return[[:space:]]+"Success"|delay\([0-9]+\).*simulate|placeholder'
# Documentation comments may describe gates; production Kotlin may not contain them.
if grep -RInE "$patterns" app/src/main/java --include='*.kt'; then fail "placeholder/fake-success marker found in production Kotlin"; fi
if grep -RIn 'http://' app/src/main/java app/src/main/assets --include='*.kt' --include='*.txt' --include='*.md'; then fail "cleartext HTTP reference found in production code/assets"; fi
echo "ZERO_PLACEHOLDER_GATE=PASS"
