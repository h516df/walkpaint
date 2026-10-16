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
@OptIn(ExperimentalLayoutApi::class)
internal fun FeedbackPage(history:RecordingDiagnostics.HistoryView,report:String?,busy:Boolean,
    onBack:()->Unit,onPreview:(String,String)->Unit,onClosePreview:()->Unit,
    onCopy:()->Unit,onShare:()->Unit,onCollection:(Boolean)->Unit,onClear:()->Unit,
    gpsSurvey:String="",gpsSurveyActive:Boolean=false,onGpsSurvey:()->Unit={}) {
    var category by rememberSaveable { mutableStateOf("定位") }
    var description by rememberSaveable { mutableStateOf("") }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var surveyDetails by rememberSaveable { mutableStateOf(false) }
    var fieldDetails by rememberSaveable { mutableStateOf(false) }
    val listState=remember(report!=null) { LazyListState() }
    fun back() { if(report!=null) onClosePreview() else onBack() }
    BackHandler { back() }
    LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { TrailHeader(if(report==null) "问题反馈" else "报告预览",onBack={back()}) }
        if(report!=null) {
            item { Text("检查内容后再复制或分享。不会自动上传；分享目标由你选择。",color=Muted,fontSize=13.sp) }
            item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Button(onClick=onCopy) { Text("复制报告") }
                OutlinedButton(onClick=onShare) { Text("选择分享目标") }
            } }
            item { SelectionContainer { Text(report,color=Ink,fontSize=13.sp,lineHeight=21.sp) } }
        } else {
            item { TrailPanel {
                Text("定位检查",color=Ink,fontSize=17.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text("保持应用在前台，观察 60 秒定位信号。自动记录开关保持原状。",color=Muted,fontSize=13.sp,lineHeight=21.sp)
                Button(onClick={surveyDetails=false;onGpsSurvey()},enabled=!gpsSurveyActive,shape=PanelShape,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Text(if(gpsSurveyActive) "正在短测定位…" else "开始 60 秒定位诊断")
                }
                if(gpsSurveyActive) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                if(gpsSurvey.isNotBlank()) {
                    Text(gpsSurvey.lineSequence().take(2).joinToString("\n"),color=Ink,fontSize=13.sp,lineHeight=21.sp)
                    TextButton(onClick={surveyDetails=!surveyDetails}) { Text(if(surveyDetails) "收起短测详情" else "查看短测详情") }
                    if(surveyDetails) SelectionContainer { Text(gpsSurvey,color=Muted,fontSize=13.sp,lineHeight=21.sp) }
                }
            } }
            item { TrailSectionLabel("描述遇到的问题") }
            item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),maxItemsInEachRow=3) {
                    FeedbackReport.categories.forEach { c -> FilterChip(selected=category==c,onClick={category=c},label={Text(c)},
                        colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Accent.copy(alpha=.13f),selectedLabelColor=Accent)) }
                }
                OutlinedTextField(description,{description=it.take(1000)},label={Text("描述问题（可选）")},
                    supportingText={Text("描述会随报告分享，请勿填写地址、账号或密码")},
                    modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=6)
                Button(onClick={onPreview(category,description)},enabled=!busy,shape=PanelShape,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Text(if(busy) "正在整理…" else "预览反馈报告")
                }
                // The raw material, for when the person can tell something is wrong but not what.
                // A described problem is a summary; this is the evidence behind it.
                TextButton(onClick={fieldDetails=!fieldDetails},modifier=Modifier.fillMaxWidth()) {
                    Text(if(fieldDetails) "收起高级反馈" else "高级反馈与记录现场")
                }
                if(fieldDetails) {
                    Text("记录现场含最近 30 分钟的坐标、过滤原因与地图状态。先检查内容，再决定是否分享。不会自动上传。",color=Muted,fontSize=13.sp,lineHeight=21.sp)
                    OutlinedButton(onClick={onPreview(FieldReport.CATEGORY,"")},enabled=!busy,
                        modifier=Modifier.fillMaxWidth()) { Text("导出记录现场") }
                }
            } }
            item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("留存故障线索",color=Ink)
                    Text("最多7天；关闭不影响自动记录",color=Muted,fontSize=12.sp)
                }
                Switch(history.enabled,onCollection,modifier=Modifier.semantics { contentDescription="留存故障线索" })
            } }
            item { TrailSectionLabel("近期线索 · 只存本机") }
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
