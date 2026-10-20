package cn.walkpaint

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun LandmarkList(points:List<ExplorationLandmark>,error:String?,mapAvailable:Boolean,
                          center:RegionPoint?,historicalCenter:Boolean,
                          onSelect:(ExplorationLandmark)->Unit,onClose:()->Unit,
                          load:suspend (android.content.Context,RegionPoint)->List<NearbyCandidate> = {c,p -> NearbyExplorationService.search(c,p)}) {
    val context=LocalContext.current.applicationContext
    // Freeze the center for this opening, so GPS updates cannot create request storms.
    var anchor by remember {mutableStateOf(center)}
    var historical by remember {mutableStateOf(historicalCenter)}
    LaunchedEffect(center) {
        if(anchor==null && center!=null) {anchor=center;historical=historicalCenter}
    }
    var retry by remember {mutableIntStateOf(0)}
    var busy by remember {mutableStateOf(false)}
    var candidates by remember {mutableStateOf<List<NearbyCandidate>>(emptyList())}
    var searchError by remember {mutableStateOf<String?>(null)}
    fun refresh() {
        if(center!=null) {anchor=center;historical=historicalCenter}
        retry++
    }
    LaunchedEffect(anchor,mapAvailable,retry) {
        candidates=emptyList();searchError=null
        if(anchor!=null && mapAvailable) {
            busy=true
            try {
                candidates=withContext(Dispatchers.IO) {load(context,anchor!!)}
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {searchError=NearbyExplorationService.message(e)}
            finally {busy=false}
        }
    }
    AlertDialog(onDismissRequest=onClose,title={Text("附近探索点")},text={
        LazyColumn(Modifier.heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {Text(if(historical) "最近足迹周围 2 公里 · 高德地点" else "周围 2 公里 · 高德地点",
                style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}
            item {Text("为你找公园、景点和文化场馆。推荐不代表到访；请留意开放时间与现场通行条件。",
                style=MaterialTheme.typography.bodyMedium)}
            when {
                !mapAvailable -> item {Text("开启高德地图后可自动发现附近地点。已收藏地点仍保留。")}
                anchor==null -> item {Text("正在等待定位，获得位置后会自动发现附近地点；不会猜测你的位置。")}
                busy -> item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp);Text("正在寻找附近值得探索的地方…")}}
                searchError!=null -> item {Text(searchError!!,color=MaterialTheme.colorScheme.error)
                    TextButton(onClick={refresh()}) {Text("重试")}}
                candidates.isEmpty() -> item {Text("这个范围暂未找到符合条件的地点。走到别处再试，或点击地图地点补充收藏。")
                    TextButton(onClick={refresh()}) {Text("重新查找")}}
                else -> {
                    items(candidates,key={"nearby:"+it.point.id}) {candidate ->
                        val saved=points.firstOrNull {it.id==candidate.point.id}
                        ListItem(headlineContent={Text(candidate.point.name)},supportingContent={Text(
                            "${candidate.meters} 米 · ${candidate.category.ifBlank {"地点"}}"+
                            if(saved?.visitedAt!=null) " · 已到访" else if(saved!=null) " · 已收藏" else " · 推荐")},
                            modifier=Modifier.clickable {onSelect(saved ?: candidate.point)})
                    }
                    item {TextButton(onClick={refresh()}) {Text("重新查找")}}
                }
            }
            item {HorizontalDivider();Text("我的收藏 · ${points.size}",style=MaterialTheme.typography.titleSmall)}
            error?.let {item {Text(it,color=MaterialTheme.colorScheme.error)}}
            if(points.isEmpty()) item {Text("发现喜欢的地点后可收藏，也可点击地图上的地点补充。收藏不解锁区域。")}
            items(points,key={"saved:"+it.id}) {point ->
                ListItem(headlineContent={Text(point.name)},supportingContent={Text(if(point.visitedAt==null) "待到访" else "已到访")},
                    modifier=Modifier.clickable {onSelect(point)})
            }
        }
    },confirmButton={TextButton(onClick=onClose){Text("完成")}})
}

@Composable
internal fun LandmarkDetail(point:ExplorationLandmark,saved:Boolean,error:String?,
                           onSave:()->Unit,onRemove:()->Unit,onClose:()->Unit) {
    AlertDialog(onDismissRequest=onClose,title={Text(point.name)},text={Column {
        Text(if(saved) "此探索点仅保存在本机，并随足迹备份导出。" else "收藏这个地图地点？名称和位置只保存在本机，并随足迹备份导出。")
        Text(if(point.visitedAt!=null) "已到访" else "到达附近后，前台连续可靠 GPS 才会记为到访。收藏本身不算到访。")
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick=if(saved) onClose else onSave){Text(if(saved)"完成" else "收藏探索点")}},
        dismissButton={TextButton(onClick=if(saved)onRemove else onClose){Text(if(saved)"移除" else "取消")}})
}
