package cn.walkpaint

import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalVisualSkin=staticCompositionLocalOf { VisualSkin.CITY }
private val VisualSkin.cover:Int get()=when(this) {
    VisualSkin.CITY->R.drawable.cover_city
    VisualSkin.DUSK->R.drawable.cover_dusk
    VisualSkin.NIGHT->R.drawable.cover_night
}

@Composable
internal fun SkinHero(distance:String,days:String,onSkin:()->Unit,
    distanceLabel:String="累计里程",daysLabel:String="探索天数") {
    val skin=LocalVisualSkin.current
    Surface(color=Canvas,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Line)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f)) {
                Image(painterResource(skin.cover),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                Surface(color=Color(0xEEFFFFFF),shape=RoundedCornerShape(8.dp),
                    modifier=Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Text("${skin.label} · 主题插画",color=Color(0xFF323B4C),fontSize=11.sp,
                        modifier=Modifier.padding(horizontal=8.dp,vertical=5.dp))
                }
                Surface(onClick=onSkin,color=Color.White,shape=RoundedCornerShape(12.dp),
                    modifier=Modifier.align(Alignment.TopEnd).padding(10.dp).heightIn(min=48.dp)) {
                    Row(Modifier.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        Icon(painterResource(R.drawable.ic_paint),null,tint=skinPalette(skin,false).brand,modifier=Modifier.size(17.dp))
                        Text("换主图",color=Color(0xFF26374F),fontSize=12.sp)
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
    AlertDialog(onDismissRequest=onDismiss,title={Text("主题皮肤")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("更换界面与足迹配色，也能单独调整足迹颜色。",color=Muted,fontSize=13.sp)
            VisualSkin.entries.forEach { skin ->
                Surface(onClick={onSelect(skin)},color=if(skin==selectedSkin) PigmentSoft else Canvas,
                    shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,if(skin==selectedSkin) Pigment else Line),
                    modifier=Modifier.fillMaxWidth().semantics { selected=skin==selectedSkin }) {
                    Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Image(painterResource(skin.cover),null,Modifier.size(width=80.dp,height=58.dp)
                            .clip(RoundedCornerShape(9.dp)),contentScale=ContentScale.Crop)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(skin.label,color=Ink,fontSize=15.sp,fontWeight=FontWeight.Medium)
                            Text(skin.description,color=Muted,fontSize=12.sp)
                        }
                        if(skin==selectedSkin) Text("✓",color=Pigment,fontSize=18.sp)
                    }
                }
            }
        }
    },confirmButton={TextButton(onClick=onDismiss) {Text("完成")}})
}
