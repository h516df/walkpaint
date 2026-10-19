package cn.walkpaint

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun LandmarkList(points:List<ExplorationLandmark>,error:String?,mapAvailable:Boolean,
                          onSelect:(ExplorationLandmark)->Unit,onClose:()->Unit) {
    AlertDialog(onDismissRequest=onClose,title={Text("探索点")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(mapAvailable) "点击底图上的景点或地点名称，可收藏为探索点。到访仅在前台用可靠 GPS 判断，不会解锁整片区域。"
                else "地图尚不可用。启用地图后可点击地点名称收藏；已存探索点仍保留。")
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            if(points.isEmpty()) Text("还没有探索点；这里不会自动搜索或推荐地点。")
            LazyColumn(Modifier.heightIn(max=280.dp)) {
                items(points,key={it.id}) {point ->
                    ListItem(headlineContent={Text(point.name)},supportingContent={Text(if(point.visitedAt==null) "待到访" else "已到访")},
                        modifier=Modifier.clickable {onSelect(point)})
                }
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
