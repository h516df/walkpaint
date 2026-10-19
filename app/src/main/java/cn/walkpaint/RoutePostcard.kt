package cn.walkpaint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal enum class CardTheme(val label:String,val background:Int,val ink:Int,val muted:Int) {
    PAPER("纸白",0xFFFAF7F0.toInt(),0xFF1C2932.toInt(),0xFF65727C.toInt()),
    NIGHT("夜色",0xFF12232B.toInt(),0xFFF4F6EF.toInt(),0xFFA8BDBD.toInt())
}
internal data class PostcardSpec(val day:String,val title:String,val distance:String,val outline:RouteOutline,
                                 val color:Int,val theme:CardTheme)

/** Renders only saved route geometry. No map tiles, precise coordinate labels, or photo EXIF. */
internal object RoutePostcard {
    const val WIDTH=1080
    const val HEIGHT=1440
    fun render(spec:PostcardSpec):Bitmap {
        val bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);val theme=spec.theme
        canvas.drawColor(theme.background)
        val text=TextPaint(Paint.ANTI_ALIAS_FLAG)
        fun line(value:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean=false) {
            text.textSize=size;text.color=color
            text.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
            canvas.drawText(TextUtils.ellipsize(value.replace('\n',' '),text,904f,TextUtils.TruncateAt.END).toString(),x,y,text)
        }
        line("足迹涂色",88f,104f,30f,theme.muted)
        line(spec.day,88f,158f,30f,theme.muted)
        line(spec.title.ifBlank {"走过的一天"},88f,245f,58f,theme.ink,true)
        val route=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style=Paint.Style.STROKE;strokeWidth=8f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND
            color=spec.color or 0xFF000000.toInt()
            val light=android.graphics.Color.luminance(color)
            if(theme==CardTheme.PAPER && light>.65f) color=0xFF237B70.toInt()
            if(theme==CardTheme.NIGHT && light<.12f) color=0xFF69D6BA.toInt()
        }
        val paths=spec.outline.fit(904.0,760.0)
        canvas.save();canvas.translate(88f,305f)
        paths.forEach {points->
            if(points.isEmpty())return@forEach
            val path=Path();path.moveTo(points.first().x.toFloat(),points.first().y.toFloat())
            points.drop(1).forEach {path.lineTo(it.x.toFloat(),it.y.toFloat())}
            if(points.size==1) {route.style=Paint.Style.FILL;canvas.drawCircle(points[0].x.toFloat(),points[0].y.toFloat(),5f,route);route.style=Paint.Style.STROKE}
            else canvas.drawPath(path,route)
        }
        canvas.restore()
        val divider=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=theme.muted;alpha=70;strokeWidth=2f}
        canvas.drawLine(88f,1140f,992f,1140f,divider)
        line(spec.distance,88f,1230f,58f,theme.ink,true)
        line("当天记录里程",88f,1280f,28f,theme.muted)
        line("真实路线 · 定位断档保留分段",88f,1360f,25f,theme.muted)
        return bitmap
    }
    fun cache(directory:File,bitmap:Bitmap):File {
        check(directory.isDirectory || directory.mkdirs())
        // Clean only this feature's expired temporary exports, never backups or saved images.
        val cutoff=System.currentTimeMillis()-24*60*60*1000L
        directory.listFiles()?.filter {validName(it.name) && it.isFile && it.lastModified()<cutoff}?.forEach {it.delete()}
        val file=File(directory,"route-${UUID.randomUUID()}.png")
        try {FileOutputStream(file).use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it));it.fd.sync()}}
        catch(e:Exception){file.delete();throw e}
        return file
    }
    fun validName(name:String)=name.matches(Regex("route-[a-f0-9-]{36}\\.png"))
}
