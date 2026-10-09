package cn.walkpaint

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

@Composable
internal fun FeedbackPage(history:RecordingDiagnostics.HistoryView,report:String?,busy:Boolean,
    onBack:()->Unit,onPreview:(String,String)->Unit,onClosePreview:()->Unit,
    onCopy:()->Unit,onShare:()->Unit,onCollection:(Boolean)->Unit,onClear:()->Unit) {
    var category by rememberSaveable { mutableStateOf("定位") }
    var description by rememberSaveable { mutableStateOf("") }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val listState=remember(report!=null) { LazyListState() }
    fun back() { if(report!=null) onClosePreview() else onBack() }
    BackHandler { back() }
    LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick={back()}) { Text("返回") } }
        item { Text(if(report==null) "问题反馈" else "报告预览",color=Ink,fontSize=26.sp) }
        if(report!=null) {
            item { Text("检查内容后再复制或分享。不会自动上传；分享目标由你选择。",color=Muted,fontSize=13.sp) }
            item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Button(onClick=onCopy) { Text("复制报告") }
                OutlinedButton(onClick=onShare) { Text("选择分享目标") }
            } }
            item { SelectionContainer { Text(report,color=Ink,fontSize=13.sp,lineHeight=21.sp) } }
        } else {
            item { Text("线索只保存在本机。这里能帮助排查已观察到的问题，未出现线索不表示没有故障。",color=Muted,fontSize=13.sp,lineHeight=20.sp) }
            item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("遇到了什么问题？",color=Ink)
                FeedbackReport.categories.chunked(3).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    row.forEach { c -> FilterChip(selected=category==c,onClick={category=c},label={Text(c)},
                        colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Accent.copy(alpha=.13f),selectedLabelColor=Accent)) }
                } }
                OutlinedTextField(description,{description=it.take(1000)},label={Text("描述问题（可选）")},
                    supportingText={Text("描述会随报告分享，请勿填写地址、账号或密码")},
                    modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=6)
                Button(onClick={onPreview(category,description)},enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                    Text(if(busy) "正在整理…" else "预览反馈报告")
                }
            } }
            item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("留存故障线索",color=Ink)
                    Text("最多7天；关闭不影响自动记录",color=Muted,fontSize=12.sp)
                }
                Switch(history.enabled,onCollection,modifier=Modifier.semantics { contentDescription="留存故障线索" })
            } }
            item { Text("近期线索",color=Ink,fontSize=18.sp) }
            if(history.saveFailed) item { Text("本机线索保存失败，现有信息可能不完整",color=Ink) }
            if(history.entries.isEmpty()) item { Text("暂无线索，可填写问题并预览报告。",color=Muted) }
            history.entries.take(30).forEach { e -> item {
                Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("${e.code.label} · ${e.count} 次",color=Ink)
                    Text("${FeedbackReport.age(e.last,System.currentTimeMillis())} · ${if(e.code.problem) "已观察，恢复情况看当前状态" else "操作记录"}",color=Muted,fontSize=12.sp)
                    Text(e.code.hint,color=Muted,fontSize=13.sp)
                }
                HorizontalDivider(Modifier.padding(top=12.dp),color=Muted.copy(alpha=.15f))
            } }
            item { TextButton(onClick={confirmClear=true}) { Text("清空故障线索") } }
        }
    }
    if(confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text("清空故障线索？")},
        text={Text("只删除这里的本机线索。足迹、行程、道路涂色和备份全部保留。")},
        confirmButton={TextButton(onClick={confirmClear=false;onClear()}) {Text("确认清空线索")}},
        dismissButton={TextButton(onClick={confirmClear=false}) {Text("取消")}})
}
