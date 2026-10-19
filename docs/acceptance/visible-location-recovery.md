# Foreground location subscription recovery

Base: frozen v0.65 candidate `46ad2476b1066b816ec3d5a1eb82be9cb534a2b8`.

## Defect and scope

The Activity previously subscribed only when each provider was enabled. Enabling location from Quick Settings without a new Activity resume could therefore leave the foreground map without a subscription. A GPS registration exception also skipped the network registration in the shared exception block.

Register available providers, including disabled ones, independently. Track successful registrations to avoid duplicate work; retry failed registrations on the next existing refresh; remove listeners on pause or denied permission. Late callbacks require a resumed Activity and current fine-location permission. RecordingService and its recording filters are unchanged.

Android's [LocationManager contract](https://developer.android.com/reference/android/location/LocationManager) supports subscribing to a disabled provider and receiving updates when it is enabled. `getAllProviders()` includes disabled providers. This uses the existing platform API without a polling timer or additional service.

## Verification

- passed: 489 JVM tests, zero failures/errors/skips, including 8 new subscription-state tests.
- passed: developer debug APK compilation (`testDebugUnitTest assembleDebug`, single worker).
- not run: device Quick Settings/provider toggling, real GPS, compass, live AMap tiles/authentication.
- No visual UI changes. This addresses a proven foreground subscription gap; it does not establish the cause or resolution of a reported black map.
- The frozen candidate, version, signing, database and release configuration remain unchanged. No release APK published.
