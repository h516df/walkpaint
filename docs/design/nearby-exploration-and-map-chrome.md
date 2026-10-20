# Nearby exploration and map chrome

Based on released v0.66 (`05f9fb30f4a4f235351b63b5ace564f29c712a91`). No monthly-history changes, database schema changes, GPS filter changes, or fog geometry changes.

## Real nearby discovery

Opening Nearby Exploration automatically queries the existing AMap Android Search SDK 9.8.1, with the already configured Android key and existing independent map/search consent. The query uses PoiSearchV2 around a single center, radius 2 km, distance order, at most 20 scenic/cultural candidates. It does not add a web-service key, proxy, external provider or persistent permission.

The center is the current acceptable fix, or explicitly labelled “最近足迹” if only recorded history is available. A dialog freezes its first available center to avoid GPS-driven request storms. An initially missing center can become available while the dialog remains open. Refresh is explicit and adopts the latest available center; no background scans.

Only returned nonblank names/IDs and valid nearby coordinates become candidates. GCJ display coordinates are converted back using the existing SDK forward transform and CoordinateInverse; no GPS database values are changed. Discovery is ephemeral. It neither saves a point, marks an arrival, nor clears fog. Saving remains explicit; the unchanged arrival policy still requires two separate fresh accurate non-mock GPS samples in the foreground.

Loading, no center, no consent, zero results, quota, authentication/permission error and retry are different states. No fabricated destinations substitute for an API failure. The production key's actual V2 entitlement and real-phone response remain a device/network acceptance item; source integration and mock UI tests do not establish entitlement. API error codes are shown without keys or coordinates.

Primary references:
- [AMap Android POI guide](https://developer.amap.com/api/android-sdk/guide/map-data/poi)
- [PoiSearchV2 API](https://a.amap.com/lbs/static/unzip/Android_Map_Doc/Search/com/amap/api/services/poisearch/PoiSearchV2.html)
- [SearchBound radius and distance sorting](https://a.amap.com/lbs/static/unzip/Android_Map_Doc/Search/com/amap/api/services/poisearch/PoiSearchV2.SearchBound.html)

## Navigation and attribution

The native map now occupies a bounded weighted viewport above the navigation footer. The footer owns bottom and side navigation insets, so its growth at large font scales cannot cover the native map's logo/legal widgets. OSM road attribution remains visible/clickable in its own wrapping row between the native viewport and app navigation; the native map logo stays within its unobstructed viewport. Neither provider logo nor attribution is removed. Tab transitions keep the same map composition alive.

The old transient celebration capsule has been removed from the map header. The regional percentage still represents recorded history, not a live GPS claim; when no fix exists the header labels its scope “最近足迹”. Current-location waiting does not invalidate prior coverage.

## Theme direction and sources

Three stable preference IDs (city/dusk/night) now represent 简洁浅色 / 自然浅色 / 夜间深色. Semantic surface, text, selected, border, accent, route-default and opaque-fog colors are coordinated. Existing independent saved route colors do not change merely when loading the app. Choosing a theme retains the existing explicit apply behavior. System appearance also updates fog and the SDK's built-in normal/night map mode; no custom AMap style package or replacement tile service is introduced.

Skin previews and the overview hero are native Canvas synthetic map/UI illustrations, labelled 地图示意 / 非真实足迹. They use one identical invented geometry and no user coordinates, real map tiles, scenery photographs or third-party artwork. They illustrate fog, route and controls, not actual AMap cartography.

Research informed adaptation, not asset copying:
- [Organic Maps](https://organicmaps.app/) / [repository](https://github.com/organicmaps/organicmaps): map-first hierarchy and coordinated light/dark appearance. Apache-2.0 code; map-data licensing separate.
- [Protomaps basemaps](https://github.com/protomaps/basemaps) / [flavors](https://docs.protomaps.com/basemaps/flavors): semantic map-theme structure. BSD-3 code / CC0 map design / ODbL data; no data incorporated.
- [StreetComplete](https://github.com/streetcomplete/StreetComplete): concise map/task interaction. GPL-3.0; no code/art copied.
- [Material Web](https://github.com/material-components/material-web): state and semantic-token consistency. Apache-2.0; no added runtime dependency.

The unchanged fog edge remains a 50 m exploration radius with 5 m feathering. Unknown ground remains fully opaque through startup and camera movement. No transparency-based road or POI reveal is introduced.
