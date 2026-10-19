# Native landscape UI preview

Base: `bfb27a1edf22caea7a874aebeebe3dd8f4a5a03d` (v0.63/code71).
Status: implementation in progress; no APK/rendering acceptance claimed yet.

## Scope

- A native Compose journey cover with original vector mountain artwork, actual recorded distance and days.
- Original mountain-seal badges and seven original line icons, with no external game/SF Symbols assets or new runtime dependency.
- Quiet grouped settings, a floating semantic tab dock, live measured bottom clearance, and stacked labels at enlarged font scales.
- Selection text contrast remains high in all three existing skins. Existing light/dark settings remain available.
- Brief leaf feedback only accompanies the existing measured new-area event; no invented achievements or progress.
- Existing GPS, map engine, fog calculations, schema3, backup, automatic recording and update verification are unchanged.

## Build identity

The developer debug package is `cn.walkpaint.dev`, version `0.63-ui-preview.1`, code71, launcher label `足迹涂色·预览`.
It coexists with `cn.walkpaint`; it is not a signed official upgrade and does not read the official application's private history.
The public checkout has no AMap application key. AMap and real district-network validation must not be represented as passing.
Do not overwrite a stable release, move an existing tag, or replace an existing asset. The existing official release guard remains intact.
Official version defaults stay v0.63/code71 until a separately coordinated official release.

## Required verification

1. Run JVM and Python regressions, debug build, Android test build and lint.
2. Install only on a dedicated test emulator; never clear a real user's application data.
3. Run the instrumentation argument `landscapeProbe=true` on `cn.walkpaint.dev.test/cn.walkpaint.RoadAcceptanceInstrumentation`.
4. Inspect the generated `landscape-*.png` native screenshots in normal and enlarged fonts, including a narrow viewport.
5. Exercise repeated tabs, appearance/back, backup/back, revoke/cancel, journey explanation/dismiss, and exploration sheet/back.
6. Record precise passed/failed/not-run stages, artifact SHA256, source commit and device limitations before publication.

The new probe drives actual accessibility controls and takes native screenshots. It refuses the official package and an active recorder, does not consent to map services or grant GPS, and restores the original appearance mode.

## Current checks

- Existing Python tests: 18 in `tests`, 23 in `scripts`, passed.
- Static review: XML parses and `git diff --check` passes; no GPS/database/signing changes.
- Android build, native render and interactive acceptance: pending SDK license approval/setup.
- Entity phone, full road basemap, AMap authorization and outdoor/background tracking: not verified by this preview task.
