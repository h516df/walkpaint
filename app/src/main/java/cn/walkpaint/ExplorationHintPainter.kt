package cn.walkpaint

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** Draw only the curated hints, after opaque fog and before the current position. */
internal object ExplorationHintPainter {
    fun draw(canvas:Canvas,hints:List<ExplorationHint>,density:Float,night:Boolean,hintPaint:Paint) {
        hints.forEach {hint ->
            hintPaint.style=Paint.Style.FILL
            hintPaint.color=if(night) Color.rgb(69,77,81) else Color.rgb(215,221,216)
            canvas.drawCircle(hint.x,hint.y,11*density,hintPaint)
            hintPaint.color=when {
                hint.point.visitedAt!=null -> Color.rgb(77,133,113)
                hint.saved -> Color.rgb(105,122,147)
                night -> Color.rgb(171,181,172)
                else -> Color.rgb(115,133,119)
            }
            hintPaint.style=Paint.Style.STROKE;hintPaint.strokeWidth=1.5f*density
            canvas.drawCircle(hint.x,hint.y,7*density,hintPaint)
            hintPaint.style=Paint.Style.FILL
            canvas.drawCircle(hint.x,hint.y,if(hint.saved) 3*density else 1.5f*density,hintPaint)
        }
    }
}
