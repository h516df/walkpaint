package cn.walkpaint

import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
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
    val progress by animateFloatAsState((percent/100).toFloat(),tween(420),label="exploration")
    val gold=Color(0xFFE2C894)
    Column(modifier.widthIn(max=268.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Surface(onClick={details=true},modifier=Modifier.fillMaxWidth().semantics{contentDescription="附近探索"},shape=RoundedCornerShape(16.dp),color=Color(0xF0202B3B),contentColor=Color(0xFFF7F3EA)) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    ExplorationMark(gold,Modifier.size(26.dp))
                    Text(title,Modifier.weight(1f),fontWeight=FontWeight.Medium,fontSize=16.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(stage.title,Modifier.weight(1f),fontSize=12.sp,color=gold)
                    Text(if(share!=null)String.format(Locale.ROOT,"%.1f%%",percent) else if(raw==null)"定位中" else "…",fontSize=17.sp,fontWeight=FontWeight.Medium)
                }
                LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(3.dp),color=gold,trackColor=Color(0xFF4B5360))
            }
        }
        AnimatedVisibility(newlyLit){Text("新区域已点亮",color=gold,fontSize=13.sp,modifier=Modifier.padding(horizontal=16.dp))}
    }
    if(details) ModalBottomSheet(onDismissRequest={details=false},containerColor=Color(0xFFF6F3EC),contentColor=Color(0xFF273246)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=28.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                ExplorationMark(Color(0xFF91703A),Modifier.size(40.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(title,fontSize=23.sp,fontWeight=FontWeight.SemiBold)
                    Text(stage.title,fontSize=14.sp,color=Color(0xFF736344))
                }
                Text(String.format(Locale.ROOT,"%.1f%%",percent),fontSize=28.sp,fontWeight=FontWeight.Medium)
            }
            LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(4.dp),color=Color(0xFF91703A),trackColor=Color(0xFFE2DDCF))
            Text(stage.next?.let{"再点亮到 ${it}% · ${explorationStage(it.toDouble()).title}"} ?: "这一片，已留下满满足迹",fontSize=15.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                listOf(5,20,50,80,100).forEach{target->Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    ExplorationMark(if(percent>=target)Color(0xFF91703A) else Color(0xFF8B8D91),Modifier.size(24.dp),percent>=target)
                    Text("$target%",fontSize=12.sp)
                }}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=!county,onClick={regional=false},label={Text("附近")})
                boundary?.let{area->FilterChip(selected=county,onClick={regional=true},label={Text(area.name)})}
            }
            TextButton(onClick={rules=!rules},contentPadding=PaddingValues(0.dp)){Text(if(rules)"收起" else "怎么算？",color=Color(0xFF59677B))}
            if(rules) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(county)"走过的地方越多，点亮的地图越大。" else "看看周围2公里，你点亮了多少地方。",fontSize=14.sp)
                Text("每段足迹点亮周围50米，走过的地方只算一次。",fontSize=14.sp)
                if(boundary==null) TextButton(onClick={retry++},enabled=allowed&&!boundaryBusy){Text(if(boundaryBusy)"查找地区中…" else "查找所在区县")}
            }
        }
    }
}

@Composable private fun ExplorationMark(ink:Color,modifier:Modifier=Modifier,filled:Boolean=true) {
    Canvas(modifier){
        val w=size.width;val h=size.height
        val p=Path().apply{moveTo(w*.5f,0f);lineTo(w*.65f,h*.35f);lineTo(w,h*.5f);lineTo(w*.65f,h*.65f);lineTo(w*.5f,h);lineTo(w*.35f,h*.65f);lineTo(0f,h*.5f);lineTo(w*.35f,h*.35f);close()}
        if(filled)drawPath(p,ink) else drawPath(p,ink,style=Stroke(1.4.dp.toPx()))
        if(filled)drawCircle(Color(0xFFF6F3EC),w*.08f,Offset(w*.5f,h*.5f))
    }
}
