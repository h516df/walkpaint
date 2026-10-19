# Combined v0.65 candidate

Integration parents:
- PR104: 8b0f196a0d6961f5f08eb798ea53b38729f1fa44 (completed author branch)
- PR109/110/111 stack: 8376c743c590eb6f7845030ad840153b467618d5

The isolated integration preserves v0.65/code73, the current first-run consent, journal,
postcards, adaptive dock/replay, following, readable fog, user-selected landmarks and updater.
SQLite remains schema3. Journal and landmark backup sections are separate, versioned metadata;
local edits/explicit journal clears/landmark deletion tombstones win over older backup contents.
Both sections are validated before GPS import. Storage failures are handled independently after
GPS commit, retain original metadata files and report each corresponding partial-restore warning.

## Current evidence

- passed: 481 JVM tests, zero failures/errors/skips, including combined metadata tests.
- passed: 41 Python tests.
- passed: debug app and Android instrumentation APK compilation.
- passed: lintDebug, zero errors (52 warnings, 2 hints).
- passed on API29 software AVD: FogPixelProbe (theme alpha/cache redraw and 50m geometry).
- passed on API29: BackupRestoreProbe (combined metadata, independent partial failures,
  retained originals, live recording during import, reopen, repeat import and future rejection).
- passed on API29: UpdateContinuityProbe (same updater across Activity recreation and newly
  bound observer state; this probe does not claim a download, source-permission or installer test).
- blocked on API29 software AVD: combined journal system save/share/recreation. The final
  attempt reached the display-only fixture, then Android's System UI ANR dialog covered the app:
  `Missing welcome or footprints: [System UI isn't responding, Close app, Wait]`.
  This is an environment/harness failure, not evidence of a production WalkPaint crash.
- not run: the subsequent 320dp/1.3-font navigation, dark/light appearance and landmark-empty UI
  checks, because the software-device run stopped at that blocker. These require final-device acceptance.
- not run: real-phone compass/GPS arrival and official-key map connectivity.

The separate PR104 and PR109/110/111 evidence is historical, not proof that this combined head
passed device acceptance. The new BackupRestoreProbe verifies both metadata sections together,
repeated restore with local clears/deletions, malformed metadata rejection, independent one-sided
and simultaneous failures, preservation of original metadata and retained GPS observations.

Only source, tests and synthetic acceptance descriptions are newly published here. Existing
PR104 synthetic screenshots remain inherited evidence for their original commit, not this one.
Formal signing/release is still a separate acceptance step in the original signing environment.

## Exact native artifacts

Source: 0328c0a2f615087848c940816d2a0dfcc7a21be2.
Developer app SHA256: `9708f25379001286d56771346101a719d9ca76b172d4d26ddba925d9a5404472`.
Initial instrumentation SHA256 (three passed probes):
`0489229c78f858ee1a8b177da7c5764f321161c2dc88ad3754ce4f3c0a49669a`.
Final API29-compatible instrumentation SHA256:
`b3801fd7218a5d8531b95afd321516d29359c06cc79b20626d130c3b84fbd67b`.
Production APK/source was unchanged between those attempts. Final test APK compilation and
lintDebug passed again (0 errors); only the test cache API guard changed.
The dedicated synthetic developer packages were isolated after Android rejected the old
AVD installation's different debug certificate. No formal package or user device was changed.

## Test compatibility and remaining acceptance

The first journal attempt exposed an API34-only test call. `UiAutomation.clearCache()` is now
API34-guarded, per the [Android reference](https://developer.android.com/reference/android/app/UiAutomation#clearCache()).
API29's original system ChooserActivity is also accepted, with the actual component and PNG
share intent checked. Neither adjustment changes production code or relaxes journal assertions.
The final retry passed that compatibility point but stopped on the System UI ANR above.
The dedicated emulator and ADB server were stopped; no KVM permissions were changed.

JournalJourneyProbe and UpdateContinuityProbe deliberately require `cn.walkpaint.dev`; do not
remove that guard to run display fixtures against a formal installation. On a signed formal
candidate, back up first and exercise real UI flows locally. Keep any real personal screenshots,
locations and diagnostic reports out of the public repository. Real-device acceptance still needs
journal editing/favorite/search, paper/night postcard export and system sharing, recreation,
small-screen navigation, actual compass/GPS following, map availability and update-install return.
Existing #104 device evidence does not substitute for testing this integrated candidate.
