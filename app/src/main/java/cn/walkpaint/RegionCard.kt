package cn.walkpaint

import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.util.Locale

@Composable internal fun RegionExplorationCard(state:TrackState,fix:Location?,place:String?,modifier:Modifier=Modifier) {
    val context=LocalContext.current.applicationContext
    val last=state.realTracks.lastOrNull()?.points?.lastOrNull()
    val raw=fix?.let{RegionPoint(it.latitude,it.longitude)} ?: last?.let{RegionPoint(it.lat,it.lon)}
    val allowed=RegionService.allowed(context)
    var boundary by remember{mutableStateOf<RegionBoundary?>(null)}
    var regional by rememberSaveable{mutableStateOf(false)}
    var details by rememberSaveable{mutableStateOf(false)}
    var share by remember{mutableStateOf<RegionShare?>(null)}
    var boundaryBusy by remember{mutableStateOf(false)}
    var calculating by remember{mutableStateOf(false)}
    var retry by remember{mutableIntStateOf(0)}
    // Resolve after crossing a ~1km tile, not on every GPS fix.
    val anchor=raw?.let{(it.lat*100).toInt() to (it.lon*100).toInt()}
    LaunchedEffect(anchor,allowed,retry){
        boundary=null
        if(raw!=null && allowed){boundaryBusy=true
            try{boundary=withContext(Dispatchers.IO){runCatching{RegionService.resolve(context,raw)}.getOrNull()}}
            finally{boundaryBusy=false}
        }
    }
    LaunchedEffect(state,anchor,boundary,regional,allowed){
        share=null
        if(raw!=null){calculating=true
            try{share=withContext(Dispatchers.Default){
                val scope=currentCoroutineContext()
                val selected=boundary.takeIf{regional}
                val center=if(selected!=null)RegionService.display(context,raw) else raw
                val paths=RecordedTrail.paths(state.realTracks).map{path->path.map{p->val point=RegionPoint(p.lat,p.lon);if(selected!=null)RegionService.display(context,point) else point}}
                RegionCoverage.measure(paths,center,selected){scope.ensureActive()}
            }}catch(e:CancellationException){throw e}catch(_:Exception){share=null}finally{calculating=false}
        }
    }
    val county=regional && boundary!=null
    Surface(onClick={details=true},modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=Color(0xEE172334),contentColor=Color(0xFFE9EFFA)) {
        Column(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text(if(county)boundary!!.name else (place ?: "当前位置")+"附近",Modifier.weight(1f),fontWeight=FontWeight.Medium,fontSize=14.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(if(share!=null)"${String.format(Locale.ROOT,"%.2f",share!!.percent)}%" else if(calculating)"统计中" else if(raw==null)"待定位" else "暂不可用",fontWeight=FontWeight.SemiBold,fontSize=18.sp)
            }
            Text(if(county)"地区探索 · 区县面积估算" else "地区探索 · 周围 2 公里",fontSize=11.sp,color=Color(0xFFB8C7DE))
        }
    }
    if(details) AlertDialog(onDismissRequest={details=false},title={Text("地区探索度")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("按已保存足迹周围50米的点亮面积去重估算，不是道路完成率。重复走同一段不会重复增加面积。")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=!county,onClick={regional=false},label={Text("附近2公里")})
            FilterChip(selected=county,onClick={regional=true},enabled=boundary!=null,label={Text(boundary?.name ?: "区县待获取")})
        }
        share?.let{Text(String.format(Locale.ROOT,"已点亮 %.3f km² / 范围 %.2f km²",it.squareMeters/1e6,it.totalSquareMeters/1e6))}
        Text(if(county)"边界来自高德，计算为25米网格估算。" else "附近范围以本次定位区域为中心；移动到其他地方，范围也会变化。村/乡镇边界未提供时，不把附近范围称作整个村或乡镇。")
        if(raw==null)Text("开启自动记录并允许定位后，会显示你当前附近的进度。")
        if(boundary==null)Text(if(boundaryBusy)"正在获取区县边界…" else if(!allowed)"同意高德服务后可获取区县边界；附近统计仍在本机完成。" else "暂未取得地区边界，先使用附近范围。")
    }},confirmButton={TextButton(onClick={details=false}){Text("知道了")}},dismissButton={if(allowed && boundary==null)TextButton(onClick={retry++},enabled=!boundaryBusy){Text("重试边界")}})
}
