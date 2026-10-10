package cn.walkpaint

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * One saturated pigment on a neutral paper canvas.
 *
 * Every colour below is deliberately neutral except [Pigment], which is reserved for live data:
 * the recorded distance, the heatmap, the trail, and the one selected state on a screen. Depth
 * comes from a white card with a hairline border and a soft edge rather than a translucent blur,
 * because this app is read outdoors in daylight and blur is both harder to read outside and more
 * expensive to draw over a live map.
 */
@Composable
internal fun WalkPaintTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Pigment,
            onPrimary = Color.White,
            primaryContainer = PigmentSoft,
            onPrimaryContainer = Ink,
            secondary = Ink,
            onSecondary = Color.White,
            secondaryContainer = CanvasSoft,
            onSecondaryContainer = Ink,
            background = CanvasSoft,
            onBackground = Ink,
            surface = Canvas,
            onSurface = Ink,
            surfaceVariant = CanvasSoft,
            onSurfaceVariant = Muted,
            surfaceContainerLowest = Canvas,
            surfaceContainerLow = Canvas,
            surfaceContainer = CanvasSoft,
            outline = Line,
            outlineVariant = Line,
            error = Color(0xFFB3261E),
            onError = Color.White,
        ),
        content = content,
    )
}
