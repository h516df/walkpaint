# Native landscape UI integration

Base: current main `e6915b2df9b92e7be412f8c0847f6f2e930cbe30` (v0.64/code72).

This is a UI-only transplant from PR103, not a merge of its old v0.63 tree. It brings the original landscape journey cover, mountain-seal badges, grouped profile settings, leaf feedback and measured adaptive tab dock. The map dock now uses the current light/dark palette. Both map controls and v0.64 replay controls use the dock's measured clearance. Narrow replay puts its progress track below the buttons so it cannot collapse at 320dp/150% font. The status/navigation inset areas and their icons follow the theme, fixing white system icons on the light map-placeholder background.

Preserved unchanged: single AMap engine, first-map consent and its explicit decline, historical replay engine, GPS, SQLite schema3, stored data, signing, package identity, version and official update behavior. PR104 was checked but remains independent and unmerged; its journal/postcard/onboarding proposals are not silently imported.

Excluded: PR103 preview workflow, KVM permissions, release scripts, debug preview label/version, update-page override and publication request files. No new dependency.

## Automated and static checks

Final app source: `eec138168263dfdc08c3e4c4248fdaa84a7242d3`.

- 435 JVM tests, zero failures/errors/skips, including the unchanged v0.64 replay tests.
- 41 Python tests (18 repository + 23 release/delivery tooling), passed.
- Developer debug APK and androidTest APK build; lintDebug zero errors, 49 warnings and 2 hints.
- Eight isolated JVM native Compose renders: profile and active replay, light/dark, normal width and 320dp/150% font. The narrow progress-track collapse was reproduced and corrected; final track/buttons and dock clearance visually checked. These are supplemental static renders, not device interactions.
- Required GitHub checks `delivery-tools` and `secret-scan` passed on the app-source commit. Final evidence-document commit checks must also pass before merge.

## Runtime evidence and limits

See `evidence.json` for candidate identity and the exact API29 test cases. Screenshots/logs were retained in the task's `verification/integrated-main-api29` directory. No prior PR103 APK screenshot counts as integration acceptance.

MainActivity, manifest, build/version/signing configuration, replay engine and update logic are byte-identical to base main. This x86_64/no-key developer build cannot initialize the supported AMap service, so first-map disclosure is source-preservation checked but not claimed as runtime exercised; its actual unavailable-map explanation was checked. No GPS or map-service consent was granted and no recording, export or restore operation was performed.

API36, supported-phone AMap basemap, real-road attribution visibility, outdoor/background GPS and physical devices are not accepted by this UI integration. The inherited raw-bottom road attribution placement needs a road-loaded visual check; this task does not claim full map certification. Software-emulator boot/System UI stalls are recorded separately from app crashes.

Formal APK signing/release is separate and not implied by merging UI source. Previous preview-release HTTP403 remains outside this merge task; no new release attempt or permission expansion was made.
