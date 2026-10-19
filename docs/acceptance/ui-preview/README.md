# Native landscape UI preview

Base: `bfb27a1edf22caea7a874aebeebe3dd8f4a5a03d` (v0.63/code71).
Status: native implementation, CI build/regressions and supplemental rendering completed; exact-candidate API29 core interaction evidence is recorded alongside this document. Android16 acceptance is not passed.

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

## Verification and limitations

- CI source: `21a92b54496fe260868b953e62906a766b456156`, [run39216650812](https://github.com/h516df/walkpaint/actions/runs/39216650812).
- APK SHA256: `e37c66fcc4cd198c4bb6415a293bcc5cf0b214645895f8ce2941f5ad011d0b62`.
- Both candidate APKs have verified V2 signatures. The installed developer base.apk was hashed on the dedicated emulator and matched the candidate exactly.
- Python: 23 + 23 regressions passed. Android: developer APK and test APK built, 431 JVM tests passed, lint zero errors (54 warnings and one informational finding). The CI build/package steps passed.
- API29: dedicated AOSP x86_64 software emulator, synthetic/empty data, ordinary launcher and touch navigation. Exact-candidate results are recorded in `api29-acceptance.json`. No GPS, map consent, automatic recording, export or restore operation was authorized by the probe.
- Local initial accessibility ACTION_CLICK probe did not navigate reliably. A coordinate touch on the semantic target successfully opened appearance settings; the diagnostic driver now uses touch injection and bounded state waits. This is a test-driver correction, not a business-navigation rewrite.
- Supplemental JVM native Compose renders passed for light, dark and 320dp / 150% font. They are empty-state previews, not device interactions; enlarged-font/device and full dark-mode device flows are not claimed by the core API29 loop.
- Android16/API36: emulator boot and APK installation succeeded, but instrumentation returned `Process crashed`. No crash stack was retained in that run; this does not establish whether the production app or instrumentation caused it. API36 acceptance is **not passed**. Its failed device step remains visible in CI and is explicitly disclosed in the prerelease.
- The one separately approved ephemeral KVM ACL was restored on shell exit. The ACL-changing workflow code has since been removed; future tests do not inherit that permission.
- Software emulation was slow (normal first frame about24 seconds) and MapLibre emitted `std::bad_alloc` renderer logs without an observed fatal process crash in the manual API29 core loop. Performance and full basemap operation are not accepted by this preview.
- Physical phones, real AMap/district-network access, outdoor GPS and background recording were not verified. No formal signing key or AMap key was accessed. Official data/schema3 and stable releases remain unchanged.

## Upstream compatibility

Read-only recheck before preview publication: main is `e6915b2df9b92e7be412f8c0847f6f2e930cbe30`; stable v0.64 was published while this UI task was in progress. It now has single-AMap rendering, explicit first-map consent and historical replay. PR104 proposes v0.65 with consolidated onboarding, per-day annotations/favorites/search and route postcards. This independently installed preview remains based on v0.63 and does not contain or replace those newer features. It is not the latest official app. PR103 integration/rebase and conflict resolution against those changes are outstanding and must be separately reviewed; no other PR was merged or overwritten.

## Publication

A separate workflow verifies the source build/package steps, exact artifact digest and recorded API29 evidence before attempting a non-latest prerelease. It intentionally does not mislabel the failed API36 instrumentation as green. No stable update manifest is written, no existing tag/asset is overwritten, and a successful upload requires anonymous SHA256 read-back verification. Release availability is only claimed after that run succeeds.

### Observed delivery result

[Publication run39220792080](https://github.com/h516df/walkpaint/actions/runs/39220792080) verified the evidence and downloaded APK checksum, then GitHub rejected creation of `refs/tags/ui-preview-21a92b54496f` with HTTP403 `Resource not accessible by integration`. No tag or prerelease was created. Stable `v0.64` release417021837 remains latest. No privilege expansion or alternate write route was attempted after that denial.

The exact accepted APK is downloadable now in the [GitHub Actions artifact](https://github.com/h516df/walkpaint/actions/runs/39216650812/artifacts/12170471518); sign in to GitHub, download the ZIP, and use `walkpaint-ui-preview.apk` (not the `-test.apk`). Artifact retention currently ends2026-11-02. This is temporary GitHub delivery, not a permanent Release asset.
