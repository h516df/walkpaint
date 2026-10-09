package cn.walkpaint

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UpdatesPage(release:CurrentRelease,notice:String?,onBack:()->Unit,onCheck:()->Unit) {
    BackHandler(onBack=onBack)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick=onBack) { Text("返回") } }
        item { Text("检查更新",color=Ink,fontSize=26.sp) }
        item { Text("当前版本 ${release.version}",color=Ink,fontSize=17.sp) }
        item { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(UpdateDestination.UNKNOWN,color=Ink,fontSize=17.sp)
            Text("安装包保存在私有仓库。点击后打开 GitHub 发布页，需要登录有权限的账号，在浏览器核对新版并下载。",color=Muted,fontSize=13.sp,lineHeight=20.sp)
            Button(onClick=onCheck,modifier=Modifier.fillMaxWidth()) { Text("去发布页检查更新") }
            if(notice!=null) Text(notice,color=Ink,fontSize=13.sp,lineHeight=20.sp)
        } }
        item { HorizontalDivider(color=Muted.copy(alpha=.15f)) }
        item { Text("本版更新说明",color=Ink,fontSize=20.sp) }
        release.sections.forEach { (heading,notes) -> item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(heading,color=Ink,fontSize=16.sp)
                notes.forEach { Text(it,color=Muted,fontSize=13.sp,lineHeight=21.sp) }
            }
        } }
    }
}
