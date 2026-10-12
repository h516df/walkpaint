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
internal val DayPalette=DesignPalette(Color(0xFF0C7266),Color(0xFFD9EEE8),Color(0xFF10201E),
    Color(0xFF55655F),Color(0xFF71837A),Color(0xFFDCE4E1),Color.White,Color(0xFFEEF2F0))
internal val NightPalette=DesignPalette(Color(0xFF45C3B1),Color(0xFF17332E),Color(0xFFE9F2EF),
    Color(0xFF9DB0AA),Color(0xFF91A79E),Color(0xFF33443F),Color(0xFF15201E),Color(0xFF0C1312))
internal val LocalDesignPalette=staticCompositionLocalOf { DayPalette }
/** 0 follows Android, 1 is daylight, 2 is night. Fonts remain local and work offline. */
@Composable
internal fun WalkPaintTheme(mode:Int=0,content:@Composable ()->Unit) {
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
                isAppearanceLightStatusBars=!dark
                isAppearanceLightNavigationBars=!dark
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
