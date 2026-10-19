package cn.walkpaint

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Original, local vector artwork. Decorative landscape, never a map or a claimed route. */
@Composable
internal fun LandscapeVignette(modifier: Modifier = Modifier) {
    val palette = LocalDesignPalette.current
    val dark = palette.paper.luminance() < .5f
    val distant = if (dark) Color(0xFF3B514F) else Color(0xFFDDE7DF)
    val middle = if (dark) Color(0xFF526862) else Color(0xFFBDCEC1)
    val near = if (dark) Color(0xFF728E80) else Color(0xFF8FAE9A)
    val sun = if (dark) Color(0xFFE4CEA0) else Color(0xFFD6BB81)
    DrawCanvas(modifier) {
        val w=size.width; val h=size.height
        drawCircle(sun.copy(alpha=.8f), h*.075f, Offset(w*.74f,h*.27f))
        fun ridge(color:Color, start:Float, peak:Float, end:Float, height:Float) {
            val path=Path().apply {
                moveTo(w*start,h)
                cubicTo(w*(start+.08f),h*.74f,w*(peak-.10f),h*(height+.12f),w*peak,h*height)
                cubicTo(w*(peak+.10f),h*(height+.16f),w*(end-.12f),h*.75f,w*end,h)
                close()
            }
            drawPath(path,color)
        }
        ridge(distant,.10f,.53f,1.18f,.30f)
        ridge(middle,.42f,.79f,1.35f,.42f)
        ridge(near,.02f,.35f,.94f,.61f)
        val trail=Path().apply {
            moveTo(w*.40f,h)
            cubicTo(w*.36f,h*.89f,w*.58f,h*.86f,w*.52f,h*.77f)
            cubicTo(w*.50f,h*.74f,w*.43f,h*.73f,w*.40f,h*.71f)
        }
        drawPath(trail,palette.surface.copy(alpha=.80f),style=Stroke(1.5.dp.toPx(),cap=StrokeCap.Round))
    }
}

@Composable
internal fun JourneyCover(title:String,state:TrackState,onClick:()->Unit,modifier:Modifier=Modifier) {
    Surface(onClick=onClick,modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=Canvas) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start=22.dp,top=22.dp,end=20.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text(title,color=Ink,fontSize=24.sp,fontWeight=FontWeight.SemiBold)
                    Text(if(state.dayCount>0) "到过的地方，留下的日子" else "从第一次出发开始",color=Muted,fontSize=13.sp)
                }
                Icon(painterResource(R.drawable.ic_chevron),null,tint=Muted,modifier=Modifier.size(18.dp))
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom) {
                Column(Modifier.weight(.48f).padding(start=22.dp,top=24.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("${state.dayCount} 天",color=Ink,fontSize=30.sp,fontWeight=FontWeight.Medium,style=TabularFigures)
                    Text("记录 ${exploredDistance(state.totalMeters)}",color=Muted,fontSize=12.sp,lineHeight=18.sp)
                }
                LandscapeVignette(Modifier.weight(.52f).height(126.dp))
            }
        }
    }
}

@Composable
internal fun ProfileSettingRow(title:String,icon:Int,detail:String="",onClick:()->Unit) {
    Surface(onClick=onClick,color=Color.Transparent,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min=62.dp).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(34.dp).background(CanvasSoft,RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center) {
                Icon(painterResource(icon),null,tint=Pigment,modifier=Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(title,color=Ink,fontSize=16.sp,fontWeight=FontWeight.Medium)
                if(detail.isNotEmpty()) Text(detail,color=Muted,fontSize=12.sp,lineHeight=18.sp)
            }
            Icon(painterResource(R.drawable.ic_chevron),null,tint=Faint,modifier=Modifier.size(15.dp))
        }
    }
}

@Composable
internal fun LandscapeDock(selectedTab:Int,onTab:(Int)->Unit,modifier:Modifier=Modifier) {
    val isMap=selectedTab==0
    val stacked=LocalDensity.current.fontScale>1.2f
    val surface=if(isMap) Color(0xFF17212B) else Canvas
    val inactive=if(isMap) Color(0xFFB9C4C6) else Muted
    val active=if(isMap) skinPalette(LocalVisualSkin.current,true).brand else Pigment
    Surface(color=surface,shadowElevation=5.dp,shape=RoundedCornerShape(26.dp),
        modifier=modifier.widthIn(max=480.dp).fillMaxWidth()) {
        Row(Modifier.selectableGroup().padding(6.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            val labels=listOf("探索","足迹","我的")
            val icons=listOf(R.drawable.ic_compass,R.drawable.ic_route,R.drawable.ic_person)
            labels.forEachIndexed { index,label ->
                val selected=selectedTab==index
                val tint by animateColorAsState(if(selected) active else inactive,label="dock ink")
                val labelInk=if(selected && !isMap) Ink else tint
                val fill by animateColorAsState(if(selected) active.copy(alpha=.13f) else Color.Transparent,label="dock selection")
                Box(Modifier.weight(1f).heightIn(min=50.dp).background(fill,RoundedCornerShape(20.dp))
                    .selectable(selected=selected,role=Role.Tab,onClick={if(!selected)onTab(index)})
                    .padding(horizontal=6.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
                    if(stacked) Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Icon(painterResource(icons[index]),null,tint=tint,modifier=Modifier.size(21.dp))
                        Text(label,color=labelInk,fontSize=13.sp,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal)
                    } else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        Icon(painterResource(icons[index]),null,tint=tint,modifier=Modifier.size(21.dp))
                        Text(label,color=labelInk,fontSize=13.sp,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
