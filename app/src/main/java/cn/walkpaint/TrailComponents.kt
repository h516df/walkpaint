package cn.walkpaint

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/** Shared native chrome; restrained surfaces leave the actual route as the visual focus. */
@Composable
internal fun TrailHeader(title:String, subtitle:String?=null, onBack:(()->Unit)?=null,
    action:@Composable RowScope.()->Unit={}) {
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        if(onBack!=null) {
            IconButton(onClick=onBack) {
                Icon(painterResource(R.drawable.ic_back),"返回",tint=Ink,modifier=Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(title,color=Ink,fontSize=26.sp,fontWeight=FontWeight.Bold)
            if(!subtitle.isNullOrEmpty()) Text(subtitle,color=Muted,fontSize=13.sp,lineHeight=19.sp)
        }
        action()
    }
}

@Composable
internal fun TrailPanel(modifier:Modifier=Modifier,spacing:Dp=12.dp,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),color=Canvas,shape=RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(spacing),content=content)
    }
}

@Composable
internal fun TrailSectionLabel(text:String) {
    Text(text,color=Muted,fontSize=13.sp,fontWeight=FontWeight.Medium,
        modifier=Modifier.padding(top=6.dp,bottom=2.dp))
}
