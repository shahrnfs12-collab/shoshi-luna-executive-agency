# Android Cloud execution command — zero shortcuts

Treat `STRICT_ACCEPTANCE_GATE_v1.5.md` as the acceptance contract. First run `bash tools/strict_verify.sh`. Fix every compile, unit-test, lint, packaging and runtime defect until the script prints exactly `STRICT_ACCEPTANCE_GATE=PASS`.

Do not remove tests, downgrade checks, comment out code, return canned success strings, create empty GLB files, substitute generic avatars, or mark unavailable capabilities as READY. Do not silently switch from real connector execution to simulation. Keep REAL actions fail-closed and approval-gated.

After the static/build gate passes, install the APK on an API 36 emulator/device and execute the runtime checklist. Exercise every bottom navigation screen and every actionable button. Capture Logcat for crashes/ANRs. Fix and rerun the entire gate after every material change.

If an external prerequisite cannot be completed (network, API credential, faithful 3D generation, unavailable external service), record it as BLOCKED with the exact error and leave the runtime status NOT READY. Never fabricate completion.
