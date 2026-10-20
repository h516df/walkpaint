package cn.walkpaint

import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalVisualSkin=staticCompositionLocalOf { VisualSkin.CITY }
/** Synthetic layout only: no coordinates, personal tracks, tiles or third-party artwork. */
@Composable
private fun SkinMapPreview(skin:VisualSkin,modifier:Modifier=Modifier) {
    val dark=skin==VisualSkin.NIGHT
    val palette=skinPalette(skin,dark)
    val fog=Color(FogAppearance.color(skin))
    val land=if(dark) Color(0xFF394449) else Color(0xFFF3F4EF)
    val water=if(dark) Color(0xFF425E67) else Color(0xFFC8DBE2)
    val park=if(dark) Color(0xFF41534A) else Color(0xFFDFE7D9)
    val road=if(dark) Color(0xFF859397) else Color.White
    val roadEdge=if(dark) Color(0xFF59696E) else Color(0xFFCCD3D0)
    DrawCanvas(modifier.semantics { contentDescription="${skin.label}，合成地图示意，非真实足迹" }) {
        withTransform({ scale(size.width/160f,size.height/100f,pivot=Offset.Zero) }) {
            // Draw the opaque veil first; all map details are clipped to the same sample opening.
            drawRect(fog,size=Size(160f,100f))
            val explored=Path().apply {
                moveTo(18f,77f);lineTo(33f,55f);lineTo(56f,53f);lineTo(66f,27f)
                lineTo(91f,23f);lineTo(109f,43f);lineTo(139f,43f);lineTo(147f,63f)
                lineTo(103f,69f);lineTo(82f,50f);lineTo(74f,75f);lineTo(44f,76f)
                lineTo(32f,89f);close()
            }
            clipPath(explored) {
                drawRect(land,size=Size(160f,100f))
                drawRect(park,topLeft=Offset(68f,8f),size=Size(30f,36f))
                val river=Path().apply {moveTo(126f,0f);cubicTo(99f,39f,157f,57f,125f,100f)}
                drawPath(river,water,style=Stroke(15f))
                listOf(35f,65f,95f).forEach { y ->
                    drawLine(roadEdge,Offset(0f,y),Offset(160f,y),5f)
                    drawLine(road,Offset(0f,y),Offset(160f,y),3f)
                }
                listOf(30f,60f,90f,120f).forEach { x ->
                    drawLine(roadEdge,Offset(x,0f),Offset(x+12f,100f),5f)
                    drawLine(road,Offset(x,0f),Offset(x+12f,100f),3f)
                }
                val route=Path().apply {
                    moveTo(26f,80f);lineTo(40f,65f);lineTo(65f,65f)
                    lineTo(78f,36f);lineTo(86f,36f);lineTo(106f,55f);lineTo(136f,55f)
                }
                drawPath(route,palette.surface,style=Stroke(5f,cap=StrokeCap.Round))
                drawPath(route,Color(skin.paint),style=Stroke(2.6f,cap=StrokeCap.Round))
                drawCircle(palette.surface,4.5f,Offset(136f,55f))
                drawCircle(Color(skin.paint),2.5f,Offset(136f,55f))
            }
            // Miniature interface chrome previews the same surface and active-state tokens.
            drawRoundRect(palette.surface,Offset(7f,7f),Size(64f,15f),CornerRadius(4f,4f))
            drawLine(palette.ink,Offset(13f,12f),Offset(44f,12f),2f,StrokeCap.Round)
            drawLine(palette.muted,Offset(13f,17f),Offset(33f,17f),1.5f,StrokeCap.Round)
            drawCircle(palette.brand,3f,Offset(61f,14.5f))
            drawRoundRect(palette.surface,Offset(143f,75f),Size(11f,11f),CornerRadius(3f,3f))
            drawCircle(palette.brand,2.5f,Offset(148.5f,80.5f),style=Stroke(1f))
            drawRoundRect(palette.surface,Offset(48f,88f),Size(65f,8f),CornerRadius(3f,3f))
            drawRoundRect(palette.soft,Offset(51f,89.5f),Size(18f,5f),CornerRadius(2f,2f))
            listOf(60f,80f,100f).forEachIndexed { index,x ->
                drawCircle(if(index==0) palette.brand else palette.muted,1.5f,Offset(x,92f))
            }
        }
    }
}

@Composable
internal fun SkinHero(distance:String,days:String,onSkin:()->Unit,
    distanceLabel:String="累计里程",daysLabel:String="探索天数") {
    val skin=LocalVisualSkin.current
    Surface(color=Canvas,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Line)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f)) {
                SkinMapPreview(skin,Modifier.fillMaxSize())
                Surface(color=Canvas,shape=RoundedCornerShape(8.dp),
                    modifier=Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Text("${skin.label} · 地图示意",color=Ink,fontSize=11.sp,
                        modifier=Modifier.padding(horizontal=8.dp,vertical=5.dp))
                }
                Surface(onClick=onSkin,color=Canvas,shape=RoundedCornerShape(12.dp),
                    modifier=Modifier.align(Alignment.TopEnd).padding(10.dp).heightIn(min=48.dp)) {
                    Row(Modifier.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        Icon(painterResource(R.drawable.ic_paint),null,tint=Pigment,modifier=Modifier.size(17.dp))
                        Text("换主题",color=Ink,fontSize=12.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                DesignStat(distance,distanceLabel,Modifier.weight(1.25f))
                VerticalDivider(Modifier.height(36.dp),color=Line)
                DesignStat(days,daysLabel,Modifier.weight(1f).padding(start=20.dp))
            }
        }
    }
}

@Composable
internal fun AppearanceSelector(mode:Int,onMode:(Int)->Unit) {
    Column(Modifier.padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("外观",color=Ink,fontSize=16.sp,fontWeight=FontWeight.Medium)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf(1 to "浅色",0 to "跟随系统",2 to "深色").forEach { (value,label) ->
                val active=mode==value
                Surface(onClick={onMode(value)},color=if(active) PigmentSoft else CanvasSoft,
                    shape=RoundedCornerShape(12.dp),modifier=Modifier.weight(1f).heightIn(min=48.dp)
                        .semantics { selected=active;contentDescription="外观：$label" }) {
                    Box(Modifier.padding(vertical=12.dp,horizontal=4.dp),contentAlignment=Alignment.Center) {
                        Text(label,color=if(active) Pigment else Muted,fontSize=12.sp,
                            fontWeight=if(active) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SkinPicker(selectedSkin:VisualSkin,onSelect:(VisualSkin)->Unit,onDismiss:()->Unit) {
    val largeText=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f
    AlertDialog(onDismissRequest=onDismiss,title={Text("主题皮肤")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("统一界面、雾面与足迹配色。下方为合成地图示意，足迹颜色可单独调整。",color=Muted,fontSize=13.sp)
            VisualSkin.entries.forEach { skin ->
                Surface(onClick={onSelect(skin)},color=if(skin==selectedSkin) PigmentSoft else Canvas,
                    shape=RoundedCornerShape(14.dp),border=BorderStroke(if(skin==selectedSkin) 2.dp else 1.dp,if(skin==selectedSkin) Pigment else Line),
                    modifier=Modifier.fillMaxWidth().semantics { selected=skin==selectedSkin }) {
                    if(largeText) Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                            SkinMapPreview(skin,Modifier.size(width=104.dp,height=68.dp).clip(RoundedCornerShape(9.dp)))
                            if(skin==selectedSkin) Text("✓",color=Pigment,fontSize=18.sp)
                        }
                        Text(skin.label,color=Ink,fontSize=16.sp,fontWeight=FontWeight.Medium)
                        Text(skin.description,color=Muted,fontSize=12.sp)
                    } else Row(Modifier.heightIn(min=96.dp).padding(10.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        SkinMapPreview(skin,Modifier.size(width=92.dp,height=68.dp)
                            .clip(RoundedCornerShape(9.dp)))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(skin.label,color=Ink,fontSize=16.sp,fontWeight=FontWeight.Medium)
                            Text(skin.description,color=Muted,fontSize=12.sp)
                        }
                        if(skin==selectedSkin) Text("✓",color=Pigment,fontSize=18.sp)
                    }
                }
            }
        }
    },confirmButton={TextButton(onClick=onDismiss) {Text("完成")}})
}
