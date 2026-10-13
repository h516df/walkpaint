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
internal data class DesignPalette(val brand:Color,val soft:Color,val ink:Color,val muted:Color,
    val faint:Color,val line:Color,val surface:Color,val paper:Color)
// OpenDesign design-system.md: accent-ink for text, cold paper surfaces, independent saved paint.
internal val DayPalette=DesignPalette(Color(0xFF006653),Color(0xFFD5F1E9),Color(0xFF1D2C26),
    Color(0xFF505E5B),Color(0xFF7D8988),Color(0xFFD8E0DF),Color(0xFFFCFEFE),Color(0xFFF2F7F7))
internal val NightPalette=DesignPalette(Color(0xFF7BD1BB),Color(0xFF123A31),Color(0xFFE2EAE8),
    Color(0xFF9DA7A6),Color(0xFF75807F),Color(0xFF2B3537),Color(0xFF151E20),Color(0xFF0C1415))
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
        surfaceVariant=p.paper,onSurfaceVariant=p.muted,outline=p.line,outlineVariant=p.line,
        surfaceContainerLowest=p.surface,surfaceContainerLow=p.surface,surfaceContainer=p.paper)
    else lightColorScheme(
        primary=p.brand,onPrimary=Color.White,primaryContainer=p.soft,onPrimaryContainer=p.ink,
        secondary=p.brand,onSecondary=Color.White,secondaryContainer=p.soft,onSecondaryContainer=p.ink,
        background=p.paper,onBackground=p.ink,surface=p.surface,onSurface=p.ink,
        surfaceVariant=p.paper,onSurfaceVariant=p.muted,outline=p.line,outlineVariant=p.line,
        surfaceContainerLowest=p.surface,surfaceContainerLow=p.surface,surfaceContainer=p.paper)
    CompositionLocalProvider(LocalDesignPalette provides p) {
        MaterialTheme(colorScheme=scheme,typography=Typography(
            bodyLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=16.sp,lineHeight=24.sp),
            bodyMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=14.sp,lineHeight=21.sp)),content=content)
    }
}
