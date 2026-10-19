# Location permission recovery

Based on PR #113 (`f988e6c804654d5cbd1a1e871f300af309f44815`), separate from frozen v0.65 candidate PR #112.

## Defect and choice

The locate button always requested permissions, even after repeated denial made Android stop showing a dialog. Only the recording entry offered settings recovery. All fine-location requests now record that a request occurred and share a request/settings decision. The settings dialog retains LOCATE versus RECORD purpose; locating does not enable recording. Pending purpose and return state survive saved-state restoration. Returning without permission ends the attempt without another prompt.

This follows Android's [permission denial guidance](https://developer.android.com/training/permissions/requesting#handle-denial). No new permission, SDK or background polling is introduced.

## Verification

- passed: 496 JVM tests, zero failures/errors/skips (7 new permission decision and purpose restoration tests).
- passed: 41 Python delivery/signing/evidence tests.
- passed: developer debug compilation.
- passed: independent read-only wiring review, including existing background-permission return handling.
- passed: four native Robolectric/Roborazzi Compose renders of the production dialog and current Theme at API34, 320dp, font scale1.5, light/dark, LOCATE/RECORD. Both buttons invoked their supplied callbacks exactly once in each test. Images visually reviewed: text and actions remain visible.
- not run: real system permission dialogs/settings return, actual Activity rotation in that flow, physical GPS/compass or AMap connectivity. Pure saved-purpose round trips are not device rotation evidence.

The adjacent render test is an isolated host-render fixture, not a shipping dependency or ordinary project unit test. It was run with Robolectric4.17, Roborazzi1.76.0 and the project's Compose BOM in the existing isolated rendering workspace; copied production dialog, permission policy and Theme sources were byte-identical. Screenshots remain outside the public repository.

No version, database, signing or release changes. No published APK; reported black-map root cause remains unverified.
