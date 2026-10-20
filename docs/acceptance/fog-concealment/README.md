# Unexplored map concealment regression

## Cause and scope

The v0.65 themed palette introduced alpha `0x70` for CITY/DUSK and `0x80` for NIGHT in `FogAppearance`. Both the actual fog bitmap and `AmapMapController`'s uncovered-viewport/startup guard use that palette. This let roughly 56% / 50% of underlying road and place-label contrast through **everywhere**, independently of recorded exploration. The issue is presentation, not evidence of changes to stored tracks or exploration accounting.

The corrected contract supersedes the translucent presentation described in the historical `fog-landmarks` and `follow-camera` acceptance notes: unvisited detailed map content is concealed, not just tinted. All three palettes retain their low-saturation, non-black RGB colours, but use alpha 255. The existing 50 m recorded-path radius, 5 m feather, 84/255 corridor tint, disconnected segments, subpixel-zoom protection and camera guard are unchanged. A closed route does not reveal its interior. There is no loading/error fallback that uncovers the entire map, no database migration and no change to GPS acceptance.

## Reference and design choice

- [Android Color alpha](https://developer.android.com/reference/android/graphics/Color#alpha(int)): 255 is opaque; changing RGB rather than reducing opacity preserves a soft colour without exposing detail.
- [Android PorterDuff SRC](https://developer.android.com/reference/android/graphics/PorterDuff.Mode#SRC): the existing renderer replaces pixels rather than accumulating opacity on reused buffers. Retain that architecture and its narrow edge ramp; adding another global overlay would also hide genuine explored corridors.

No third-party mapping engine or new dependency is required for the production change.

## Regression evidence

`FogPixelProbe` renders the production `TrailPainter` with Android Canvas, not a screenshot approximation. It checks all theme alphas; opposite-background compositing hides unknown detail but reveals the recorded corridor; empty selections, startup and wide-view fallbacks; closed-route interiors; disconnected gaps; repeated traversal; and feather monotonicity. It writes synthetic-grid before/after PNGs to the test application's cache. No private screenshot, place or recorded track is included.

Validation on code commit `08826df17869165d5029b38b32a6599631df814f`:

- **passed:** all 496 repository JVM tests; production Kotlin/Java compiled against SDK36.
- **passed:** actual Android Canvas `FogPixelProbe` under Robolectric 4.17 native graphics / Android SDK35. Combined suite: 497 tests, 81 suites, zero failures/errors/skips. See [machine-readable summary](test-summary.json).
- **passed:** 18 delivery-tool Python tests + 23 script/device-guard Python tests.
- **passed:** independent static review and `git diff --check`.
- **not run:** live AMap surface lifecycle, physical-device acceptance, signed APK build or release. Native raster results must not be described as a full app/AMap screenshot or live gesture acceptance.

Actual production-painter outputs over a synthetic labelled street grid:

- [Before: translucent CITY](before-city.png), with labels readable everywhere.
- [After: CITY](city.png), [DUSK](dusk.png), [NIGHT](night.png), with unvisited detail concealed and only the recorded corridor revealed. The enclosed but unwalked centre stays concealed.

The repository's device route remains `RoadAcceptanceInstrumentation` with `-e fogPixelProbe true` on an explicitly owned test device. The cloud-only run used a temporary test source invoking the same `FogPixelProbe.run` with a Robolectric instrumentation context and native graphics; no production or permanent dependency was added.

## Release handoff

This patch does not sign, publish, bump a release version or alter the independent monthly-review change. Before release, integrate onto the intended release head, rerun targeted/full suites, and check first launch, actual AMap map loading, theme changes, zoom/pan including uncovered viewport corners, history selections and offline/error states on an explicitly owned test device. Prepare a same-signature upgrade with a higher versionCode using the existing release process. Do not uninstall or clear the user's data.
