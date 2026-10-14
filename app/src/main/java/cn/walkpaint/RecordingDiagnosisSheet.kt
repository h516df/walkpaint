package cn.walkpaint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordingDiagnosisSheet(facts:DiagnosisFacts?,report:String,onCopy:()->Unit,onClose:()->Unit) {
    var details by remember { mutableStateOf(false) }
    val presentation=remember(facts) { facts?.let { DiagnosisPresentation.of(it) } }
    ModalBottomSheet(onDismissRequest=onClose,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=CanvasSoft) {
        Column(Modifier.fillMaxWidth().heightIn(max=620.dp).verticalScroll(rememberScrollState())
            .padding(start=20.dp,end=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            TrailHeader("记录状态","诊断不含坐标，足迹仍保存在本机")
            TrailPanel {
                Text(presentation?.title ?: "记录诊断",color=Ink,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                Text(presentation?.detail ?: "可复制完整诊断查看记录状态。",color=Muted,fontSize=14.sp,lineHeight=22.sp)
                presentation?.rows?.forEach { item ->
                    HorizontalDivider(color=Line)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top) {
                        Text(item.title,color=Muted,fontSize=13.sp,modifier=Modifier.width(72.dp))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(item.detail,color=Ink,fontSize=14.sp,lineHeight=21.sp)
                            item.action?.let { Text(it,color=Muted,fontSize=13.sp,lineHeight=20.sp) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick=onClose,shape=PanelShape,modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text("关闭") }
                Button(onClick=onCopy,shape=PanelShape,modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text("复制诊断") }
            }
            TextButton(onClick={details=!details},modifier=Modifier.fillMaxWidth()) {
                Text(if(details) "收起开发者信息" else "展开开发者信息")
            }
            if(details) SelectionContainer { Text(report,color=Muted,fontSize=13.sp,lineHeight=21.sp) }
        }
    }
}
