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
    if(BuildConfig.DEVELOPER_BUILD) {
        BackHandler(onBack=onBack)
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {TrailHeader("检查更新","独立预览版 ${BuildConfig.VERSION_NAME}",onBack)}
            item {TrailPanel {
                Text("界面预览",color=Ink,fontSize=20.sp)
                Text("这是与正式版并存的开发包，数据各自保存。公开构建不含高德地图配置，不能代替完整正式版。",color=Muted,fontSize=14.sp,lineHeight=22.sp)
                Text("请在 GitHub 发布页下载后续预览。开发签名可能不同，不能保证覆盖其他开发包；安装前请先导出需要保留的测试足迹。",color=Muted,fontSize=14.sp,lineHeight=22.sp)
                Button(onClick=onBrowser,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("打开发布页")}
                TextButton(onClick=onSource){Text("查看源码与开源许可")}
            }}
            item {Text("基于正式版 ${release.version}，保留原有记录与数据处理规则。本轮只验证界面，未验证实体手机和户外记录。",color=Muted,fontSize=13.sp,lineHeight=21.sp)}
        }
        return
    }
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
                else if(state.candidate!=null) Button(onClick=onDownload,modifier=Modifier.fillMaxWidth()) { Text("下载新版") }
            }
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
