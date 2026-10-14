package cn.walkpaint

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UpdatesPage(release:CurrentRelease,state:PrivateUpdateState,onBack:()->Unit,onCheck:()->Unit,
    onAuthorize:()->Unit,onVerify:()->Unit,onDownload:()->Unit,onInstall:()->Unit,onCancel:()->Unit,onLogout:()->Unit,onBrowser:()->Unit) {
    var confirmLogout by remember { mutableStateOf(false) }
    BackHandler(onBack=onBack)
    if(confirmLogout) AlertDialog(onDismissRequest={confirmLogout=false},title={Text("退出更新授权？")},
        text={Text("清除本机的更新授权和临时安装包，已有足迹不受影响。可在 GitHub 设置中彻底撤销授权。")},
        confirmButton={TextButton(onClick={confirmLogout=false;onLogout()}){Text("退出授权")}},
        dismissButton={TextButton(onClick={confirmLogout=false}){Text("保留")}})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { TrailHeader("检查更新","当前版本 ${release.version}",onBack) }
        item { TrailPanel {
            Text(state.message,color=Ink,fontSize=16.sp,lineHeight=24.sp)
            if(state.userCode!=null) {
                SelectionContainer { Text(state.userCode,color=Ink,fontSize=28.sp) }
                Button(onClick=onVerify,modifier=Modifier.fillMaxWidth()) { Text("打开 GitHub 授权") }
                Text("在网页输入授权码并确认，然后返回这里。不会上传足迹或位置。",color=Muted,fontSize=13.sp,lineHeight=20.sp)
            }
            if(state.progress!=null) {
                LinearProgressIndicator(progress={state.progress/100f},modifier=Modifier.fillMaxWidth())
                Text("已下载 ${state.progress}%",color=Muted,fontSize=13.sp)
            } else if(state.busy && state.userCode==null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state.busy) OutlinedButton(onClick=onCancel,modifier=Modifier.fillMaxWidth()) { Text("取消") }
            else {
                Button(onClick=onCheck,shape=PanelShape,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("检查新版") }
                if(state.ready!=null) Button(onClick=onInstall,modifier=Modifier.fillMaxWidth()) { Text("交给系统安装") }
                else if(state.candidate!=null) Button(onClick=onDownload,modifier=Modifier.fillMaxWidth()) { Text("下载新版") }
                if(!state.authorized) OutlinedButton(onClick=onAuthorize,modifier=Modifier.fillMaxWidth()) { Text("授权私有更新") }
                else TextButton(onClick=onAuthorize) { Text("重新授权") }
            }
            Text("安装包仍保存在私有仓库。首次使用需要 GitHub 账号授权读取本项目；授权加密保存在本机，可随时退出。安装由系统确认，原足迹保留。",color=Muted,fontSize=13.sp,lineHeight=20.sp)
            TextButton(onClick=onBrowser) { Text("打开私有发布页") }
            if(state.authorized && !state.busy) TextButton(onClick={confirmLogout=true}) { Text("退出更新授权") }
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
