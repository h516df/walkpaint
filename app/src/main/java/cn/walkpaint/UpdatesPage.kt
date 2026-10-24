package cn.walkpaint

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UpdatesPage(release:CurrentRelease,state:PrivateUpdateState,onBack:()->Unit,onCheck:()->Unit,
    onDownload:()->Unit,onInstall:()->Unit,onCancel:()->Unit,onLogout:()->Unit,onBrowser:()->Unit,onSource:()->Unit) {
    var confirmLogout by remember { mutableStateOf(false) }
    BackHandler(onBack=onBack)
    if(confirmLogout) AlertDialog(onDismissRequest={confirmLogout=false},title={Text("清除旧更新授权？")},
        text={Text("清除本机旧版授权和临时安装包，已有足迹不受影响。公开更新无需登录；旧授权也可在 GitHub 设置中撤销。")},
        confirmButton={TextButton(onClick={confirmLogout=false;onLogout()}){Text("清除")}},
        dismissButton={TextButton(onClick={confirmLogout=false}){Text("保留")}})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { TrailHeader("检查更新","当前版本 ${release.version}",onBack) }
        item { TrailPanel {
            Text(state.message,color=Ink,fontSize=16.sp,lineHeight=24.sp)
            if(state.progress!=null) {
                LinearProgressIndicator(progress={state.progress/100f},modifier=Modifier.fillMaxWidth())
                Text("已下载 ${state.progress}%",color=Muted,fontSize=13.sp)
            } else if(state.busy && state.userCode==null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state.busy) OutlinedButton(onClick=onCancel,modifier=Modifier.fillMaxWidth()) { Text("取消") }
            else {
                Button(onClick=onCheck,shape=PanelShape,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("检查新版") }
                if(state.ready!=null) Button(onClick=onInstall,modifier=Modifier.fillMaxWidth()) { Text("交给系统安装") }
                else if(state.candidate!=null) Button(onClick=onDownload,modifier=Modifier.fillMaxWidth()) {
                    Text(if(state.resumableBytes>0) "继续下载 · ${(state.resumableBytes*100/state.candidate.asset.size).coerceIn(0,100)}%" else "下载新版 · ${UpdateDownloadNotice.size(state.candidate.asset.size)}")
                }
            }
            if(!state.busy && state.resumableBytes>0) Text("已保留 ${UpdateDownloadNotice.size(state.resumableBytes)}，重试会从已下载位置继续。",color=Muted,fontSize=13.sp,lineHeight=20.sp)
            state.candidate?.let {Text(UpdateDownloadNotice.text(it.asset.size),color=Muted,fontSize=13.sp,lineHeight=20.sp)}
            Text("离开此页或旋转屏幕时下载会继续；可随时回来查看或取消。",color=Muted,fontSize=13.sp,lineHeight=20.sp)
            Text("从公开发布页检查和下载安装包，无需 GitHub 登录，不会上传足迹或位置。下载后核对版本和签名，再由系统确认安装，原足迹保留。",color=Muted,fontSize=13.sp,lineHeight=20.sp)
            TextButton(onClick=onBrowser) { Text("打开发布页") }
            Text("Copyright © 2026 h516df 和贡献者。GPL-3.0，含高德SDK链接例外；允许按许可再分发，程序不提供担保。",color=Muted,fontSize=12.sp,lineHeight=18.sp)
            TextButton(onClick=onSource) { Text("查看源码与开源许可") }
            if(state.authorized && !state.busy) TextButton(onClick={confirmLogout=true}) { Text("清除旧更新授权") }
        } }
        if(state.candidate!=null) {
            item { HorizontalDivider(color=Muted.copy(alpha=.15f));Text("新版 ${state.candidate.notes.version} 更新说明",color=Ink,fontSize=20.sp) }
            state.candidate.notes.sections.forEach { (heading,notes) -> item { NoteSection(heading,notes) } }
        }
        item { HorizontalDivider(color=Muted.copy(alpha=.15f)) }
        item { TrailSectionLabel("本版更新说明") }
        release.sections.forEach { (heading,notes) -> item {
            NoteSection(heading,notes)
        } }
    }
}
@Composable private fun NoteSection(heading:String,notes:List<String>) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(heading,color=Ink,fontSize=16.sp)
        notes.forEach { Text(it,color=Muted,fontSize=13.sp,lineHeight=21.sp) }
    }
}
