package cn.walkpaint

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RoadColorDialog(selected:Int,onSelect:(Int)->Unit,onDismiss:()->Unit) {
    var custom by remember { mutableStateOf(RoadPaintColor.hex(selected)) }
    var edited by remember { mutableStateOf(false) }
    val parsed=RoadPaintColor.parse(custom)
    AlertDialog(onDismissRequest=onDismiss,containerColor=PageBackground,title={Text("涂色外观",color=Ink)},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("已探索道路",color=Ink,fontWeight=FontWeight.SemiBold)
            Text("选择后即保存，过去已确认的道路也会换色。",color=Muted,fontSize=13.sp)
            RoadColorPreview(selected)
            Text("示意预览 · 下滑可输入自定义颜色",color=Muted,fontSize=12.sp)
            RoadPaintColor.presets.forEach { preset ->
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp)
                    .selectable(selected=selected==preset.argb,role=Role.RadioButton,onClick={
                        custom=RoadPaintColor.hex(preset.argb);edited=false;onSelect(preset.argb)
                    }),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).background(Color(preset.argb),CircleShape).border(1.dp,Muted, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(preset.name,color=Ink,fontSize=14.sp)
                        Text(RoadPaintColor.hex(preset.argb),color=Muted,fontSize=12.sp)
                    }
                    RadioButton(selected=selected==preset.argb,onClick=null)
                }
            }
            OutlinedTextField(value=custom,onValueChange={custom=it.take(16);edited=true},singleLine=true,
                label={Text("自定义颜色代码")},isError=edited && parsed==null,
                supportingText={Text(if(edited && parsed==null) "请输入6位颜色代码，例如 #579B8C" else "可输入或粘贴颜色代码，例如 #887A9D")},modifier=Modifier.fillMaxWidth())
            Button(onClick={parsed?.let { onSelect(it);custom=RoadPaintColor.hex(it);edited=false }},enabled=parsed!=null,modifier=Modifier.fillMaxWidth()) {
                Text("应用自定义颜色")
            }
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("完成")}},dismissButton={
        TextButton(onClick={onSelect(RoadPaintColor.DEFAULT);custom=RoadPaintColor.hex(RoadPaintColor.DEFAULT);edited=false}){Text("恢复青绿")}
    })
}

@Composable
private fun RoadColorPreview(argb:Int) {
    Canvas(Modifier.fillMaxWidth().height(64.dp).background(Panel)) {
        val start=Offset(size.width*.08f,size.height*.7f);val end=Offset(size.width*.92f,size.height*.3f)
        drawLine(Color(UnexploredRoadArgb),Offset(size.width*.08f,size.height*.3f),Offset(size.width*.92f,size.height*.7f),3.dp.toPx(),StrokeCap.Round)
        drawLine(Color.White,start,end,10.dp.toPx(),StrokeCap.Round)
        drawLine(Color(argb),start,end,6.dp.toPx(),StrokeCap.Round)
    }
}
