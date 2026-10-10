package cn.walkpaint

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------------------------
// Daylight paper palette.
//
// The app before this was five shades of the same desaturated green: canvas, panel, ink and
// accent all sat in one hue family, so nothing on screen had a focal point and the whole thing
// read as flat. The fix is not decoration, it is contrast and depth:
//
//   * a neutral paper canvas, with no colour cast;
//   * neutral near-black ink, so the green is an accent instead of the entire world;
//   * exactly ONE saturated pigment, used only for live data (the distance, the heatmap, the
//     trail). It is the same value as the default trail colour, because this app is about
//     leaving colour behind you, so the paint is the identity;
//   * depth from a white card with a hairline border and a soft edge, not from a blur layer.
//
// No translucent glass: this app is read outdoors in daylight, and Apple pulled Liquid Glass
// back in iOS 26 beta 3 precisely because people found it harder to read.
// ---------------------------------------------------------------------------------------------

internal val Pigment = Color(RoadPaintColor.DEFAULT)
internal val PigmentSoft = Color(0x1A0B8C77)

/** Primary text. Neutral, deliberately not green. */
internal val Ink = Color(0xFF11181B)
/** Secondary text and labels. */
internal val Muted = Color(0xFF5C6A6F)
/** Tertiary text, disabled states. */
internal val Faint = Color(0xFF8A969A)
/** Hairlines and card borders. */
internal val Line = Color(0xFFE6E9EA)
/** Card and sheet surface. */
internal val Canvas = Color(0xFFFFFFFF)
/** Page background behind the cards. */
internal val CanvasSoft = Color(0xFFF6F7F7)

// Names kept from the previous token set so the rest of the app keeps compiling.
internal val Accent = Pigment
internal val AccentSoft = Line
internal val PageBackground = CanvasSoft
internal val Panel = Canvas
internal val Lilac = Color(0xFF7C8C90)

// Map overlay colours. Storage, distance and drawing all share ExploredRoadArgb.
internal const val ExploredRoadArgb = RoadPaintColor.DEFAULT
internal const val UnexploredRoadArgb = 0xFFB8C3C0.toInt()
/**
 * Dark casing drawn under the recorded trail. The online basemap paints its own roads near-white,
 * so a white casing disappeared into them and the trail lost its edge on the map.
 */
internal const val TrailOutlineArgb = 0xFF23312D.toInt()

// Geometry. Large, consistent radii; cards are the only rounded container.
internal val CardShape = RoundedCornerShape(22.dp)
internal val PanelShape = RoundedCornerShape(18.dp)
internal val ChipShape = RoundedCornerShape(12.dp)

// Type scale. One family, deliberate sizes and weights.
internal val DisplaySize = 44.sp
internal val TitleSize = 28.sp
internal val SectionSize = 17.sp
internal val BodySize = 15.sp
internal val CaptionSize = 13.sp
internal val MicroSize = 12.sp

/**
 * Tabular figures, so a distance that changes while you watch does not shift the glyphs sideways.
 */
internal val TabularFigures = TextStyle(fontFeatureSettings = "tnum")
