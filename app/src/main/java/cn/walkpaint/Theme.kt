package cn.walkpaint
import androidx.compose.foundation.isSystemInDarkTheme
import android.app.Activity
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
internal data class DesignPalette(val brand:Color,val soft:Color,val ink:Color,val muted:Color,
    val faint:Color,val line:Color,val surface:Color,val paper:Color)
// Interface blue is independent of the user's saved trail colour. All fonts remain on-device.
internal val DayPalette=DesignPalette(Color(0xFF205BC5),Color(0xFFE8EFFC),Color(0xFF18253B),
    Color(0xFF59667A),Color(0xFF66738A),Color(0xFFE1E6EF),Color.White,Color(0xFFF3F5F9))
internal val NightPalette=DesignPalette(Color(0xFFA1C4FF),Color(0xFF233959),Color(0xFFE9EFFA),
    Color(0xFFB0BCD0),Color(0xFF98A8C1),Color(0xFF2D3D55),Color(0xFF182538),Color(0xFF0F1929))
internal val LocalDesignPalette=staticCompositionLocalOf { DayPalette }
/** 0 follows Android, 1 is daylight, 2 is night. Fonts remain local and work offline. */
@Composable
internal fun WalkPaintTheme(mode:Int=0,immersiveMap:Boolean=false,content:@Composable ()->Unit) {
    val dark=mode==2 || (mode==0 && isSystemInDarkTheme())
    val p=if(dark) NightPalette else DayPalette
    val view=LocalView.current
    val activity=remember(view) {
        var context=view.context
        while(context is ContextWrapper && context !is Activity) context=context.baseContext
        context as? Activity
    }
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window,view).apply {
                isAppearanceLightStatusBars=!dark && !immersiveMap
                isAppearanceLightNavigationBars=!dark && !immersiveMap
            }
        }
    }
    val scheme=if(dark) darkColorScheme(
        primary=p.brand,onPrimary=p.paper,primaryContainer=p.soft,onPrimaryContainer=p.ink,
        secondary=p.brand,onSecondary=p.paper,secondaryContainer=p.soft,onSecondaryContainer=p.ink,
        background=p.paper,onBackground=p.ink,surface=p.surface,onSurface=p.ink,
        surfaceVariant=p.paper,onSurfaceVariant=p.muted,outline=p.muted,outlineVariant=p.line,
        surfaceContainerLowest=p.surface,surfaceContainerLow=p.surface,surfaceContainer=p.paper,
        surfaceContainerHigh=p.surface,surfaceContainerHighest=p.surface)
    else lightColorScheme(
        primary=p.brand,onPrimary=Color.White,primaryContainer=p.soft,onPrimaryContainer=p.ink,
        secondary=p.brand,onSecondary=Color.White,secondaryContainer=p.soft,onSecondaryContainer=p.ink,
        background=p.paper,onBackground=p.ink,surface=p.surface,onSurface=p.ink,
        surfaceVariant=p.paper,onSurfaceVariant=p.muted,outline=p.muted,outlineVariant=p.line,
        surfaceContainerLowest=p.surface,surfaceContainerLow=p.surface,surfaceContainer=p.paper,
        surfaceContainerHigh=p.surface,surfaceContainerHighest=p.surface)
    CompositionLocalProvider(LocalDesignPalette provides p) {
        MaterialTheme(colorScheme=scheme,shapes=Shapes(
            small=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            large=androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),typography=Typography(
            bodyLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=16.sp,lineHeight=24.sp),
            bodyMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=14.sp,lineHeight=21.sp)),content=content)
    }
}
