# Readable fog and manually selected exploration points

This feature follows the request to keep geographic context visible in unexplored areas.
It does not change the recorded-path 50 m reveal geometry, GPS acceptance, SQL schema,
or any existing trajectory. CITY, DUSK and NIGHT choose different translucent veil colours.

## Minimal landmark scope

Tap an existing AMap base-map POI label, then explicitly choose 收藏探索点. The 探索点
button explains this and lists saved places. These are user-selected places, not automatic
recommendations. No POI search, route planning, server, new credential or paid service is added.
Base-map availability and POI label density still depend on the existing AMap configuration.
An unavailable or blank base map is not diagnosed or fixed merely by changing the veil.

POI display coordinates are converted through the existing SDK inverse helper to WGS84;
markers use the existing forward helper. The independent local atomic JSON has version 1
and explicit coordinateSystem=WGS84. Optional backup metadata uses the same representation,
without converting again. Unknown versions or coordinate systems fail before trajectory import.
Merge is additive with local entries winning, including deletion tombstones. Removing a place
clears its saved name and coordinates while retaining its identifier to prevent an older backup
from resurrecting it. This metadata is included in user-requested and existing local backups.
A failed metadata merge reports a partial restore and preserves the original file for retry.

Arrival is evaluated locally only while recording, in the foreground map with precise location
permission. Two non-mock GPS samples must be 10–60 seconds apart, each at most 15 seconds old,
with accuracy <=25 m and the full accuracy circle within 50 m of the saved place. Network,
mock, stale, inaccurate and disabled observations reset the pair. Saved/demo trajectories are
never inputs to arrival detection. Arrival does not unlock an area or affect exploration totals.

## Verification status

- Direct JVM compilation and 12 synthetic unit tests: passed (2026-10-19).
- Full JVM suite: 452 tests, zero failures/errors/skips; 41 Python tests passed.
- Debug application and Android instrumentation APK compilation: passed.
- Native map interactions: pending.
- FogPixelProbe updated for translucent thematic veil, unchanged real-radius/subpixel checks,
  theme-only redraw and reused-buffer opacity. Updated probe has not yet been run on a device.
- Real-phone POI click, map label legibility, compass and outdoor arrival: pending.
- PR104 journal backup integration: pending until its active author finishes; preserve both
  optional metadata sections and both partial-restore warnings in the combined implementation.

Synthetic tests are not evidence of a real visit or a working production map key.
