package cn.walkpaint
import androidx.compose.foundation.isSystemInDarkTheme
import android.app.Activity
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
internal data class DesignPalette(val brand:Color,val soft:Color,val ink:Color,val muted:Color,
    val faint:Color,val line:Color,val surface:Color,val paper:Color)
// Semantic interface colours are independent of the saved trail colour. Fonts stay on-device.
internal val DayPalette=DesignPalette(Color(0xFF386F7A),Color(0xFFE0ECEE),Color(0xFF243438),
    Color(0xFF5C6B70),Color(0xFF5C6B70),Color(0xFFD3DDDF),Color(0xFFFCFDFD),Color(0xFFF2F5F4))
internal val NightPalette=DesignPalette(Color(0xFF8ABFC8),Color(0xFF3B5057),Color(0xFFEDF2F3),
    Color(0xFFB4C0C5),Color(0xFFB4C0C5),Color(0xFF526168),Color(0xFF303A40),Color(0xFF20282C))
internal val LocalDesignPalette=staticCompositionLocalOf { DayPalette }
/** 0 follows Android, 1 is daylight, 2 is night. Fonts remain local and work offline. */
@Composable
internal fun WalkPaintTheme(mode:Int=0,immersiveMap:Boolean=false,skin:VisualSkin=VisualSkin.CITY,content:@Composable ()->Unit) {
    val dark=mode==2 || (mode==0 && isSystemInDarkTheme())
    val p=skinPalette(skin,dark)
    val view=LocalView.current
    val activity=remember(view) {
        var context=view.context
        while(context is ContextWrapper && context !is Activity) context=context.baseContext
        context as? Activity
    }
    SideEffect {
        activity?.window?.let { window ->
            // Older Android draws opaque decor bars outside Compose; update those as well.
            if(android.os.Build.VERSION.SDK_INT < 35) {
                @Suppress("DEPRECATION")
                window.statusBarColor=p.paper.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor=p.paper.toArgb()
            }
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
    CompositionLocalProvider(LocalDesignPalette provides p,LocalVisualSkin provides skin) {
        MaterialTheme(colorScheme=scheme,shapes=Shapes(
            small=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            large=androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),typography=Typography(
            bodyLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=16.sp,lineHeight=24.sp),
            bodyMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=14.sp,lineHeight=21.sp)),content=content)
    }
}

internal fun skinPalette(skin:VisualSkin,dark:Boolean):DesignPalette {
    fun palette(brand:Long,soft:Long,ink:Long,muted:Long,line:Long,surface:Long,paper:Long)=
        DesignPalette(Color(brand),Color(soft),Color(ink),Color(muted),Color(muted),Color(line),Color(surface),Color(paper))
    return when(skin) {
        VisualSkin.CITY -> if(dark) NightPalette else DayPalette
        VisualSkin.DUSK -> if(dark) palette(0xFFA5C4A7,0xFF384A3E,0xFFEDF2ED,0xFFB7C3B7,0xFF526155,0xFF303B33,0xFF222B25)
            else palette(0xFF496F54,0xFFE4EBDD,0xFF29372E,0xFF616C60,0xFFD4DDD0,0xFFFCFCF9,0xFFF3F5EF)
        VisualSkin.NIGHT -> if(dark) NightPalette
            else palette(0xFF386F7A,0xFFE0ECEE,0xFF243438,0xFF5C6B70,0xFFD3DDDF,0xFFFCFDFD,0xFFF2F5F4)
    }
}
