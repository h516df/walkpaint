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
        VisualSkin.CITY -> if(dark) palette(0xFF93B5FF,0xFF25334F,0xFFF0F3FA,0xFFAEB9CA,0xFF323D4D,0xFF202836,0xFF121721)
            else palette(0xFF326BFA,0xFFECF2FF,0xFF161922,0xFF626D7E,0xFFE9ECF2,0xFFFFFFFF,0xFFF5F6F9)
        VisualSkin.DUSK -> if(dark) palette(0xFFC7A4FA,0xFF403052,0xFFF7F1FF,0xFFB4A8C8,0xFF3B3049,0xFF282231,0xFF191621)
            else palette(0xFF7954B4,0xFFEFE6FB,0xFF2D243D,0xFF736882,0xFFE9E1F1,0xFFFFFFFF,0xFFF5F2FB)
        VisualSkin.NIGHT -> if(dark) palette(0xFFF4C76D,0xFF403728,0xFFF4F6FB,0xFFAEB9CA,0xFF323D4D,0xFF202836,0xFF121721)
            else palette(0xFF946915,0xFFF4ECDA,0xFF292724,0xFF797469,0xFFE8E5DF,0xFFFFFFFF,0xFFF7F6F3)
    }
}
