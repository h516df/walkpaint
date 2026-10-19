# Follow camera and first-exploration feedback

Base: main a88a11f. Isolated from the active PR104 feature branch.

## Changes

- An unready AMap camera reports refused dispatch; MainActivity no longer records that position/bearing as successfully applied. SDK-ready callback retries only when the map is visible and following.
- Explicit locate requests retain street zoom until accepted, and manual browsing cancels the request. Locate now leaves history and restores live position/phone-heading following. If only a historical fallback position is available, live following stays armed for the next reliable fix.
- Compass noise holds the last reliable direction rather than repeatedly rotating north. A device without a reliable sensor starts north-up. Existing screen-rotation mapping, circular smoothing, maximum turn rate and 5 Hz output remain; GPS course is not substituted for phone orientation.
- First-time users with no saved points see a short explanation and locate/start controls, distinguishing SDK initialization, paused recording, unreliable location and waiting for saved exploration. This does not diagnose tile loading or remove the exploration mask.
- GPS saving/filtering/schema, map consent, credentials and recording service are unchanged. No route navigation service added.

## Validation status

- Read-only review identified and corrected lost deferred street zoom and stale locate/probe selectors.
- Full JVM suite: 439 tests, zero failures/errors/skips, including four new empty-state/heading/deferred-zoom/follow-mode tests.
- Gradle testDebugUnitTest, assembleDebug and assembleDebugAndroidTest passed (1m5s). No emulator started; lint not rerun in this bounded build slot.
- Android probe now covers refused dispatch/no cache acknowledgment, manual browsing and background suppression, and accepted readiness retry. This is new test code, not an executed acceptance claim.
- Real sensor heading, magnetic interference, supported-phone AMap, actual position following, lifecycle interruptions and dual-phone walking/lock-screen checks remain required. Cloud synthetic bearings do not verify a real compass.

## Next visual work (separate)

The user now wants readable geographical contours under unexplored fog and original landmark markers. That supersedes the previous all-opaque visual preference, but does not change the real 50 m exploration accounting. Theme tint and foreground guard must be coordinated with TrailPaint; no arbitrary regional unlock. Live POI search requires checking existing AMap service entitlement/quota and user-controlled foreground query scope. This change does not add POI requests.
