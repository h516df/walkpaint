# Native landscape UI preview

Base: `bfb27a1edf22caea7a874aebeebe3dd8f4a5a03d` (v0.63/code71).
Status: native implementation and CI build/regressions passed; emulator acceptance is still pending. No device-rendering acceptance or release is claimed yet.

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

- Python regressions: 23 in `tests`, 23 in `scripts`, all passed locally and in GitHub Actions.
- Static review: XML parses and `git diff --check` passes; no GPS/database/signing changes.
- Android SDK terms approved by the requester; official SDK installed in the isolated cloud build environment.
- JDK21 / Gradle9.1 / AGP9.0.1 developer debug APK, instrumentation APK, 431 JVM tests and lint passed locally and in [CI run39214455737](https://github.com/h516df/walkpaint/actions/runs/39214455737). Lint: zero errors; 54 warnings and one informational finding.
- Accepted build candidate source: `f86b0e17468192f2ec771680052a7dfae5571427`.
- Candidate APK SHA256: `51daba939f1407a37cff6b17c3e0dd8d4efb9692030924f354e23eeec0e9c8b3`. Package/version verified from APK metadata and signature verified; not yet accepted for publication.
- Cloud API36 software emulator did not complete boot (system-server watchdog); no successful application interaction occurred. The GitHub API36 attempt stopped at inaccessible `/dev/kvm`; no KVM permissions were changed.
- The API29 software-only CI attempt exited before Android launch because SDK tools and emulator resolved different AVD paths; no UI validation occurred. The next run explicitly shares AVD directories. A single ephemeral runner KVM ACL change was separately approved for API36 validation, limited to workflow run5 attempt1, with original ACL restoration on exit.
- Native screenshots, visual review and interaction acceptance: pending.
- Physical phone, full road basemap, AMap authorization and outdoor/background tracking: not verified by this preview task.
