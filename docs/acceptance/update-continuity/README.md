# Update continuity and safe local recovery

The existing public-release updater is retained. Checking is still explicit, downloads are
still explicit, and no new permission, account, network destination or signing configuration
is introduced. The update screen displays package size and current-network/Wi-Fi guidance.

The Activity ViewModel owns the updater. Leaving the update page, changing tabs or rotating
the Activity no longer cancels a running transfer; the new UI binds to the same state. Closing
the Activity for good still releases its worker. Existing within-attempt range retries remain;
this is not a new background service or a promise to resume partial transfers after process death.

Only a fully downloaded and validated APK is saved as cacheDir/updates/update.apk plus bounded
ready.json metadata. Metadata contains version/code/size/hash/name, no URL, account or token.
New sessions remove only fixed-name incomplete/orphan cache files.
Recovery rejects other filenames/symlink destinations and revalidates size/SHA256, package,
version/code, installed signing certificate, minSdk and ABI before offering installation. Cached
metadata alone is never trusted. Recovery is local and does not check the network automatically.

An explicit Install click still goes through full validation. If Android needs the app-source
permission, returning can continue that same recent action (10 minute monotonic-time limit),
with another full package check. The system installer still asks for confirmation. Cancellation
or refusal leaves the installed app and its data intact. Installation readiness is consumed once
on the currently resumed Activity; an old Activity is not retained in a worker callback.

## Validation

- Twelve focused JVM tests passed: bounded/invalid metadata, version downgrade, destination-free
  recovery, fixed filenames, APK symlink rejection, required hash verification, permission-return
  expiry/paused-restore ordering and download-size/network wording.
- Full JVM suite: passed, 464 tests, zero failures/errors/skips. Python: passed, 41 tests.
- Android debug app and instrumentation APK compilation: passed (2026-10-19).
- UpdateContinuityProbe added for developer-package Activity recreation/state rebinding only;
  not run yet. It does not claim download, permission or real installation acceptance.
- Native page exit/rotation, interruption retry, permission refusal/return, valid signed update and
  existing-data preservation still require dedicated device acceptance. Formal release signing
  remains with the original signing environment.

## Adaptation rationale

- Android's [ViewModel guidance](https://developer.android.com/topic/libraries/architecture/views/viewmodel)
  provides configuration-change retention. The retained updater uses application context and
  replaces/unbinds its Activity listener; installation is a consumed state event, not a captured
  Activity callback. This fits the existing small serial downloader without a replacement framework.
- [Activity Result guidance](https://developer.android.com/training/basics/intents/result)
  requires unconditional registration through recreation. The source-permission launcher is a
  stable Activity property, with a separately bounded saved continuation token.
- [Saving UI states](https://developer.android.com/topic/libraries/architecture/saving-states)
  distinguishes retained memory from durable storage. Only complete verified packages receive an
  atomic local recovery record; the implementation does not advertise durable partial-download
  recovery or add a long-running background-transfer service.
- Cross-category reference: Google's [architecture-samples](https://github.com/android/architecture-samples)
  uses Compose presentation with a feature ViewModel in a task-management example. The useful
  transfer here is lifecycle ownership, not its whole repository/DI stack.
- Package-management reference: [Droid-ify releases](https://github.com/Droid-ify/client/releases)
  document signature-verification and installer/download-validation fixes. This reinforces keeping
  WalkPaint's existing strict verification stages. Its multi-repository package-manager scope is
  larger than a single-app updater, so no third-party updater or installer privilege is imported.
