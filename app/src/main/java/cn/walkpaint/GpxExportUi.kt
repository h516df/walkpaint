package cn.walkpaint

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun GpxExportButton(day:DailyFootprint) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var confirming by rememberSaveable(day.key){mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var pendingDay by rememberSaveable {mutableStateOf<String?>(null)}
    var pendingZone by rememberSaveable {mutableStateOf<String?>(null)}
    var message by rememberSaveable(day.key){mutableStateOf<String?>(null)}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) {uri->
        val key=pendingDay
        val zone=pendingZone
        pendingDay=null;pendingZone=null
        if(uri!=null && key!=null && zone!=null) {
            busy=true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val repository=(context.applicationContext as WalkPaintApp).repository
                        val selected=DailyFootprints.build(repository.snapshot(),java.util.TimeZone.getTimeZone(zone))
                            .firstOrNull {it.key==key} ?: error("这天没有可导出的足迹")
                        val output=context.contentResolver.openOutputStream(uri,"wt") ?: error("无法写入目标位置")
                        output.bufferedWriter(Charsets.UTF_8).use {TrackGpx.write(selected.tracks,"足迹涂色 $key",it)}
                    }
                    message="GPX 已保存"
                } catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(_:Exception){message="未能保存 GPX，请检查目标位置和剩余空间后重试"}
                finally {busy=false}
            }
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        OutlinedButton(enabled=!busy,onClick={confirming=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(if(busy)"正在保存…" else "导出当天路线 GPX")
        }
        message?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
    }
    if(confirming) AlertDialog(onDismissRequest={confirming=false},title={Text("导出当天路线")},
        text={Text("${day.key} 的 GPX 包含精确位置和时间，可用于其他轨迹应用。只保存到你选择的位置；不会包含备注和标签，也不能代替完整足迹备份。")},
        confirmButton={TextButton(onClick={
            confirming=false;message=null;pendingDay=day.key;pendingZone=java.util.TimeZone.getDefault().id
            try {launcher.launch("WalkPaint-${day.key}.gpx")}
            catch(_:Exception){pendingDay=null;pendingZone=null;message="暂时无法打开保存位置，请稍后重试"}
        }){Text("选择保存位置")}},dismissButton={TextButton(onClick={confirming=false}){Text("取消")}})
}
