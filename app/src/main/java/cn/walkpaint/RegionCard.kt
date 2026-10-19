package cn.walkpaint

import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
    var measuredScope by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(state,anchor,boundary,regional,allowed){
        val key="$anchor:${if(regional)boundary?.code else null}"
        if(measuredScope!=key){share=null;measuredScope=key}
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
    val percent=share?.percent ?: 0.0
    val stage=explorationStage(percent)
    val title=if(county)boundary!!.name else place?.let{"${it}附近"} ?: "附近"
    var rules by remember{mutableStateOf(false)}
    var newlyLit by remember{mutableStateOf(false)}
    var pulse by remember{mutableIntStateOf(0)}
    var previousArea by remember(anchor,county){mutableStateOf<Double?>(null)}
    LaunchedEffect(share,anchor,county){
        val area=share?.squareMeters ?: return@LaunchedEffect
        val previous=previousArea
        previousArea=area
        if(previous!=null && area>previous+625)pulse++
    }
    LaunchedEffect(pulse){if(pulse>0){newlyLit=true;delay(1800);newlyLit=false}}
    val paper=MaterialTheme.colorScheme.surface
    val gold=if(paper.luminance()<.5f)Color(0xFFE2C894) else Color(0xFF7C5C2D)
    val ink=MaterialTheme.colorScheme.onSurface
    val muted=MaterialTheme.colorScheme.onSurfaceVariant
    val value=if(share!=null)String.format(Locale.ROOT,"%.1f%%",percent) else if(raw==null)"待定位" else if(calculating)"计算中" else "暂不可用"
    Column(modifier.widthIn(max=260.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Surface(onClick={details=true},modifier=Modifier.semantics{contentDescription="附近探索"},shape=RoundedCornerShape(16.dp),color=paper,contentColor=ink,shadowElevation=3.dp) {
            Row(Modifier.padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                ExplorationMark(gold,Modifier.size(26.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(title,fontWeight=FontWeight.Medium,fontSize=14.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(if(county)"区县探索" else "周围 2 公里",fontSize=12.sp,color=muted)
                }
                Text(value,fontSize=16.sp,fontWeight=FontWeight.Medium,style=TabularFigures)
                Icon(painterResource(R.drawable.ic_back),null,Modifier.size(12.dp).rotate(180f),tint=muted)
            }
        }
        AnimatedVisibility(newlyLit){Surface(shape=RoundedCornerShape(18.dp),color=paper){
            Row(Modifier.padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_leaf),null,tint=gold,modifier=Modifier.size(16.dp))
                Text("又点亮了一小片",color=gold,fontSize=13.sp)
            }
        }}
    }
    if(details) ModalBottomSheet(onDismissRequest={details=false},containerColor=paper,contentColor=ink) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                ExplorationMark(gold,Modifier.size(32.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(title,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                    Text(if(county)"${boundary!!.name}的真实区县范围" else if(raw==null)"定位后查看附近探索" else if(fix!=null)"当前位置周围 2 公里" else "最近足迹周围 2 公里",fontSize=13.sp,color=muted)
                }
            }
            Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(if(share==null)"等待出发" else stage.title,Modifier.weight(1f),fontSize=18.sp,fontWeight=FontWeight.Medium)
                        Text(value,fontSize=22.sp,fontWeight=FontWeight.Medium,style=TabularFigures)
                    }
                    Text(if(share==null)"等定位就绪，再看看你点亮的地方。" else if(percent==0.0)"从这里出发，走过的地方会慢慢亮起来。" else "已点亮 ${String.format(Locale.ROOT,"%.2f",share!!.squareMeters/1_000_000)} 平方公里",fontSize=14.sp,color=muted)
                }
            }
            if(boundary!=null) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=!county,onClick={regional=false},label={Text("附近 2 公里")})
                FilterChip(selected=county,onClick={regional=true},label={Text(boundary!!.name)})
            }
            Text("长期地区成就，可在「我的 · 山河行记」查看。",fontSize=14.sp,color=muted)
            TextButton(onClick={rules=!rules},contentPadding=PaddingValues(0.dp)){Text(if(rules)"收起探索说明" else "探索范围与说明",color=muted)}
            AnimatedVisibility(rules) {Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("每段足迹点亮周围50米，走过的地方只算一次。百分比按25米网格估算。",fontSize=14.sp,color=muted)
                Text(if(county)"当前使用高德提供的区县边界。" else "附近范围会随当前位置移动，不代表村、街道或区县。",fontSize=14.sp,color=muted)
                stage.next?.let{Text("下一个发现：${explorationStage(it.toDouble()).title} · $it%",fontSize=14.sp,color=muted)}
                if(boundary==null) TextButton(onClick={retry++},enabled=allowed&&!boundaryBusy){Text(if(boundaryBusy)"查找地区中…" else "查找所在区县")}
            }}
        }
    }
}

/** A quiet compass needle: navigation, rather than an achievement star. */
@Composable private fun ExplorationMark(ink:Color,modifier:Modifier=Modifier) {
    Canvas(modifier){
        val w=size.width;val h=size.height
        drawCircle(ink.copy(alpha=.45f),w*.46f,style=Stroke(1.dp.toPx()))
        val north=Path().apply{moveTo(w*.69f,h*.22f);lineTo(w*.55f,h*.55f);lineTo(w*.31f,h*.78f);lineTo(w*.45f,h*.45f);close()}
        drawPath(north,ink)
        drawCircle(ink,w*.045f,Offset(w*.5f,h*.5f))
    }
}
