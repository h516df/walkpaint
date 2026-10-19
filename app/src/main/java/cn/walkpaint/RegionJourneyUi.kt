package cn.walkpaint

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.util.Locale

internal data class JourneyView(val visits:List<RegionVisit> = emptyList(),val resolved:Int=0,val total:Int=0,val busy:Boolean=false,val message:String?=null)

@Composable internal fun rememberRegionJourney(state:TrackState,active:Boolean,retry:Int):JourneyView {
    val context=LocalContext.current.applicationContext
    var view by remember(state){mutableStateOf(JourneyView())}
    LaunchedEffect(state,active,retry) {
        if(!active)return@LaunchedEffect
        RegionJourneyStore.indexMutex.withLock {
        val samples=withContext(Dispatchers.Default){RegionJourneyRules.samples(state)}
        val store=withContext(Dispatchers.IO){RegionJourneyStore(context).also{it.prune(samples.map{sample->sample.key}.toSet())}}
        suspend fun update(busy:Boolean,message:String?=null) {
            view=withContext(Dispatchers.Default){val places=store.snapshot()
                JourneyView(RegionJourneyRules.visits(samples,places),samples.count{it.key in places},samples.size,busy,message)}
        }
        update(true)
        var failures=0
        var processed=0
        try {
        for(sample in samples) {
            ensureActive()
            if(store.contains(sample.key))continue
            val result=withContext(Dispatchers.IO){store.resolve(sample)}
            if(result==null)failures++ else failures=0
            processed++
            if(processed%8==0 || result==null){withContext(Dispatchers.IO){store.flush()};update(true,store.lastFailure)}
            if(failures>=3 || store.deferred || !RegionService.allowed(context))break
            delay(180)
        }
        withContext(Dispatchers.IO){store.flush()}
        update(false,store.lastFailure)
        } finally {withContext(NonCancellable+Dispatchers.IO){store.flush()}}
        }
    }
    return view
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun RegionJourneyPanel(state:TrackState,view:JourneyView,onRetry:()->Unit) {
    var level by rememberSaveable{mutableStateOf("district")}
    var selected by remember{mutableStateOf<Pair<String,String>?>(null)}
    var expanded by rememberSaveable{mutableStateOf(false)}
    val regions=view.visits.filter{it.region.level==level}
    val colors=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().animateContentSize(),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("山河之间",fontSize=28.sp,fontWeight=FontWeight.SemiBold)
            Text(if(view.visits.isEmpty())if(view.total==0)"等待第一处足迹" else "已有足迹，地区待识别" else
                "${view.visits.count{it.region.level=="district"}} 区县  ·  ${view.visits.count{it.region.level=="city"}} 城市  ·  ${view.visits.count{it.region.level=="province"}} 省级地区",
                fontSize=14.sp,color=colors.onSurfaceVariant)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("district" to "区县","city" to "城市","province" to "省份").forEachIndexed{index,(key,label)->
                SegmentedButton(selected=level==key,onClick={level=key;expanded=false},shape=SegmentedButtonDefaults.itemShape(index,3)){Text(label)}
            }
        }
        if(regions.isEmpty()) {
            Text(if(view.busy)"正在整理足迹中的地区…" else if(view.total==0)"出发后，来这里看沿途的收获。" else "还没有识别到${RegionJourneyRules.levelLabel(level)}",fontSize=16.sp)
        }
        (if(expanded)regions else regions.take(5)).forEach{visit->
            Surface(onClick={selected=visit.region.level to visit.region.code},color=colors.background,modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(visit.region.name,fontSize=19.sp,fontWeight=FontWeight.Medium)
                        Text("${visit.days.size} 天足迹"+if(level!="district")" · 到过 ${visit.childSummary}" else " · ${visit.days.minOrNull()} 初访",fontSize=13.sp,color=colors.onSurfaceVariant)
                    }
                    Text(visit.stage,color=colors.primary,fontSize=14.sp,fontWeight=FontWeight.Medium)
                    Text("›",color=colors.onSurfaceVariant,fontSize=22.sp)
                }
            }
            HorizontalDivider(color=colors.outlineVariant.copy(alpha=.45f))
        }
        if(regions.size>5)TextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(0.dp)){Text(if(expanded)"收起地区" else "全部 ${regions.size} 个地区")}
        if(view.resolved<view.total || view.message!=null) {
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(if(view.busy)"正在补全历史地区 · ${view.resolved} / ${view.total}" else view.message ?: "部分历史地区尚待识别",fontSize=13.sp,color=colors.onSurfaceVariant)
                if(!view.busy)TextButton(onClick=onRetry,contentPadding=PaddingValues(0.dp)){Text("重新识别")}
            }
        }
    }
    selected?.let{key->view.visits.firstOrNull{it.region.level==key.first && it.region.code==key.second}?.let{visit->RegionVisitSheet(state,visit){selected=null}}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RegionVisitSheet(state:TrackState,visit:RegionVisit,onClose:()->Unit) {
    val context=LocalContext.current.applicationContext
    var share by remember{mutableStateOf<RegionShare?>(null)}
    var busy by remember{mutableStateOf(false)}
    var retry by remember{mutableIntStateOf(0)}
    var explanation by remember{mutableStateOf(false)}
    var earned by remember{mutableStateOf(GrowthStore(context).earned())}
    val county=visit.region.level=="district"
    LaunchedEffect(visit.region.code,state,retry) {
        if(!county)return@LaunchedEffect
        busy=true
        try {
            val boundary=withContext(Dispatchers.IO){runCatching{RegionService.boundary(context,visit.region.code,visit.region.name)}.getOrNull()}
            share=if(boundary==null)null else withContext(Dispatchers.Default){
                val scope=currentCoroutineContext()
                val paths=RecordedTrail.paths(state.realTracks).map{path->path.map{p->RegionService.display(context,RegionPoint(p.lat,p.lon))}}
                // Stable origin per boundary, independent of current location or when the page is opened.
                RegionCoverage.measure(paths,boundary.rings.first().first(),boundary){scope.ensureActive()}
            }
        } catch(e:CancellationException){throw e}catch(_:Exception){share=null}finally{busy=false}
    }
    LaunchedEffect(visit.days,share){earned=GrowthStore(context).settle(RegionJourneyRules.coverageBadges(visit,share?.percent))}
    val colors=MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest=onClose) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(visit.region.name,fontSize=26.sp,fontWeight=FontWeight.SemiBold)
                Text("${RegionJourneyRules.levelLabel(visit.region.level)}行记 · ${visit.days.minOrNull()} 初访",fontSize=13.sp,color=colors.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(visit.stage,fontSize=22.sp,fontWeight=FontWeight.Medium)
                    Text("留下了 ${visit.days.size} 天足迹",color=colors.onSurfaceVariant,fontSize=14.sp)
                }
                MedalMark(true,Modifier.size(30.dp))
            }
            visit.next?.let{(days,children)->Text("下一程 · $days 天足迹"+if(county)"" else if(visit.region.childCount==0 || (visit.region.level=="province" && visit.region.countyCount==0))" · 地区信息待补全" else "，到过 ${visit.nextPlaces(children)} ${visit.childKind}",fontSize=14.sp,color=colors.onSurfaceVariant)}
            if(!county)Text("已到过 ${visit.childSummary}",fontSize=14.sp,color=colors.onSurfaceVariant)
            if(county) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("点亮这片土地",Modifier.weight(1f),fontSize=18.sp,fontWeight=FontWeight.Medium)
                    Text(share?.let{String.format(Locale.ROOT,"%.2f%%",it.percent)} ?: if(busy)"计算中" else "暂未计算",fontSize=20.sp,color=colors.primary)
                }
                if(share==null) {
                    if(!busy)TextButton(onClick={retry++}){Text("重新计算")}
                }
                RegionJourneyRules.coverageBadges(visit,share?.percent).forEach{badge->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        MedalMark(badge.id in earned,Modifier.size(25.dp))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(badge.title,fontWeight=FontWeight.Medium)
                            Text(earned[badge.id]?.let{"收藏于 "+java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()} ?: badge.detail,fontSize=13.sp,color=colors.onSurfaceVariant)
                        }
                        Text(if(badge.id in earned)"已收藏" else "未解锁",fontSize=12.sp,color=colors.onSurfaceVariant)
                    }
                }
            }
            TextButton(onClick={explanation=!explanation},contentPadding=PaddingValues(0.dp)){Text(if(explanation)"收起说明" else "这些收获怎样记录？")}
            if(explanation)Text("同一天、同一地区只记一次。地区由高德识别，不按公里数换算，也不区分步行与驾车。缺少真实区县边界时，点亮面积暂未计算；市、省到访不代表全域面积覆盖。",fontSize=13.sp,color=colors.onSurfaceVariant)
        }
    }
}
