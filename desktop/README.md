# SHOSHI LUNA DESKTOP — Windows Target

This directory is the isolated Windows/Desktop target for SHOSHI + LUNA.

## Baseline
- Forked from verified Android Run #232 baseline commit `6507806ab8fb9b3fe59f6df601688e82559f6692`.
- Android source remains unchanged on its existing branch.
- Windows work must not weaken Truth, Evidence, Security, Human Approval, or Financial Firewall invariants.

## Architecture direction
1. Extract platform-neutral executive core contracts from Android-specific adapters.
2. Add a Windows/Desktop shell and desktop-safe persistence.
3. Add capability-gated filesystem, browser, Git/GitHub and build-tool adapters.
4. Require explicit owner approval for irreversible/high-impact external actions and capability acquisition.
5. Keep reversible, zero-cost, authorized mission work autonomous.
6. Add Windows CI, tests, packaging and runtime evidence before declaring the desktop build ready.

## Revenue/autonomy policy to preserve
- Owner gives goals; agents do the work and do not delegate routine execution back to the owner.
- Free-first discovery before paid capability acquisition.
- Cost/benefit + ROI + risk + evidence gate before any proposed spend.
- Spending is restricted to necessary capability development/acquisition and always requires explicit owner approval.
- Revenue is recognized only when payment is verified as received.
- Recovery exhausts reasonable safe alternatives before mission failure.

## Status
BOOTSTRAP_ONLY — Windows executable is not yet built or verified.
