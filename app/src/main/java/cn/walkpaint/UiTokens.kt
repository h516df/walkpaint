package cn.walkpaint

import androidx.compose.ui.graphics.Color

internal val Ink = Color(0xFF344743)
internal val Muted = Color(0xFF576F67)
internal val Accent = Color(0xFF426F63)
internal val AccentSoft = Color(0xFFE2ECE6)
internal val Lilac = Color(0xFF789A91)
internal val PageBackground = Color(0xFFF4F7F5)
internal val Panel = Color(0xFFEDF2EE)
internal const val ExploredRoadArgb = RoadPaintColor.DEFAULT
internal const val UnexploredRoadArgb = 0xFFB8C3C0.toInt()
/**
 * Dark casing drawn under the recorded trail. The online basemap paints its own roads near-white,
 * so a white casing disappeared into them and the trail lost its edge on the map.
 */
internal const val TrailOutlineArgb = 0xFF23312D.toInt()
