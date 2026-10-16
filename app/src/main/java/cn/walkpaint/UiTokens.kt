package cn.walkpaint
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// The selected mobile design. Saved map paint and interface appearance stay independent.
internal val Pigment:Color @Composable @ReadOnlyComposable get()=LocalDesignPalette.current.brand
internal val PigmentSoft:Color @Composable @ReadOnlyComposable get()=LocalDesignPalette.current.soft
internal val Ink:Color @Composable @ReadOnlyComposable get()=LocalDesignPalette.current.ink
internal val Muted:Color @Composable @ReadOnlyComposable get()=LocalDesignPalette.current.muted
internal val Faint:Color @Composable @ReadOnlyComposable get()=LocalDesignPalette.current.faint
internal val Line:Color @Composable @ReadOnlyComposable get()=LocalDesignPalette.current.line
internal val Canvas:Color @Composable @ReadOnlyComposable get()=LocalDesignPalette.current.surface
internal val CanvasSoft:Color @Composable @ReadOnlyComposable get()=LocalDesignPalette.current.paper
internal val Accent:Color @Composable @ReadOnlyComposable get()=Pigment
internal val AccentSoft:Color @Composable @ReadOnlyComposable get()=PigmentSoft
internal val PageBackground:Color @Composable @ReadOnlyComposable get()=CanvasSoft
internal val Panel:Color @Composable @ReadOnlyComposable get()=Canvas
internal val Lilac:Color @Composable @ReadOnlyComposable get()=Muted
internal const val ExploredRoadArgb=RoadPaintColor.DEFAULT
internal const val UnexploredRoadArgb=0xFFB8C3C0.toInt()
internal const val TrailOutlineArgb=0xFF23312D.toInt()
internal val CardShape=RoundedCornerShape(18.dp)
internal val PanelShape=RoundedCornerShape(13.dp)
internal val ChipShape=RoundedCornerShape(9.dp)
internal val DisplaySize=28.sp
internal val TitleSize=25.sp
internal val SectionSize=17.sp
internal val BodySize=15.sp
internal val CaptionSize=13.sp
internal val MicroSize=12.sp
internal val TabularFigures=TextStyle(fontFeatureSettings="tnum")
