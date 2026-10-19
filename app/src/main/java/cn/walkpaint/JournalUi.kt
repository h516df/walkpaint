package cn.walkpaint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class JournalView(val store:FootprintJournal,val entries:Map<String,DayMemo>,val loading:Boolean,val error:String?)

@Composable internal fun rememberJournal():JournalView {
    val context=LocalContext.current.applicationContext as WalkPaintApp
    val store=remember(context){context.repository.journal}
    val revision by store.revision.collectAsState()
    var entries by remember {mutableStateOf<Map<String,DayMemo>>(emptyMap())}
    var loading by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(store,revision) {
        try {
            entries=withContext(Dispatchers.IO){store.snapshot()};error=null
        } catch(e:CancellationException) {throw e}
        catch(_:Exception) {error="行记暂时无法读取，原文件已保留。足迹仍可查看。"}
        finally {loading=false}
    }
    return JournalView(store,entries,loading,error)
}

@Composable internal fun MemoEditor(day:String,initial:DayMemo,store:FootprintJournal,onClose:()->Unit) {
    var title by rememberSaveable(day){mutableStateOf(initial.title)}
    var note by rememberSaveable(day){mutableStateOf(initial.note)}
    var tags by rememberSaveable(day){mutableStateOf(initial.tags.joinToString("，"))}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text("记录这一天")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(day,style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(title,{if(it.length<=80)title=it},label={Text("标题")},singleLine=true,
                modifier=Modifier.fillMaxWidth(),supportingText={Text("最多 80 字")})
            OutlinedTextField(note,{if(it.length<=2000)note=it},label={Text("一句备注")},minLines=3,maxLines=5,
                modifier=Modifier.fillMaxWidth(),supportingText={Text("${note.length} / 2000")})
            OutlinedTextField(tags,{if(it.length<=240)tags=it},label={Text("标签")},maxLines=3,
                modifier=Modifier.fillMaxWidth(),supportingText={Text("用逗号分隔，如：散步，旅行。最多 8 个。")})
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }},
        confirmButton={Button(enabled=!busy,onClick={
            val parsed=try {JournalCodec.tags(tags)} catch(e:IllegalArgumentException){error=e.message;return@Button}
            busy=true;error=null
            scope.launch {
                try {
                    withContext(Dispatchers.IO){store.update(day){it.copy(title=title.trim(),note=note.trim(),tags=parsed)}}
                    onClose()
                } catch(e:CancellationException){throw e}
                catch(_:Exception){error="保存未完成，请稍后再试。原有行记保留。"}
                finally {busy=false}
            }
        }){Text(if(busy)"正在保存…" else "保存")}},
        dismissButton={TextButton(enabled=!busy,onClick=onClose){Text("取消")}})
}

@Composable internal fun DayJournalActions(day:DailyFootprint,journal:JournalView,onPostcard:()->Unit) {
    val memo=journal.entries[day.key] ?: DayMemo()
    var editing by rememberSaveable(day.key){mutableStateOf(false)}
    var saving by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(memo.title.isNotBlank()) Text(memo.title,style=MaterialTheme.typography.titleLarge)
        if(memo.note.isNotBlank()) Text(memo.note,style=MaterialTheme.typography.bodyMedium)
        if(memo.tags.isNotEmpty()) Text(memo.tags.joinToString("  "){"#$it"},style=MaterialTheme.typography.bodySmall,color=Muted)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled=!journal.loading && journal.error==null && !saving,
                modifier=Modifier.weight(1f),onClick={
                    saving=true
                    scope.launch {
                        try {withContext(Dispatchers.IO){journal.store.update(day.key){it.copy(starred=!it.starred)}};error=null}
                        catch(e:CancellationException){throw e}
                        catch(_:Exception){error="收藏未保存，请稍后再试"}
                        finally {saving=false}
                    }
                }){Text(if(memo.starred)"已收藏" else "收藏这天")}
            OutlinedButton(enabled=!journal.loading && journal.error==null,modifier=Modifier.weight(1f),onClick={editing=true}){Text("写备注")}
        }
        Button(enabled=!journal.loading && journal.error==null,onClick=onPostcard,modifier=Modifier.fillMaxWidth()){Text("制作路线明信片")}
        (error ?: journal.error)?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
    }
    if(editing) MemoEditor(day.key,memo,journal.store){editing=false}
}
