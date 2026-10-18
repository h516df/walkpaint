package cn.walkpaint

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable internal fun BackgroundRecordingSettings(automatic:Boolean,onPermissions:()->Unit,onPower:()->Unit) {
    val context=LocalContext.current
    val prefs=remember{context.getSharedPreferences("growth_v1",0)}
    var boot by remember{mutableStateOf(prefs.getBoolean("restore_after_boot",true))}
    var help by remember{mutableStateOf(false)}
    var refresh by remember{mutableIntStateOf(0)}
    LaunchedEffect(Unit){while(true){delay(2000);refresh++}}
    val fine=remember(refresh){AmbientTracking.hasFineLocation(context)}
    val background=remember(refresh){AmbientTracking.hasBackgroundLocation(context)}
    val power=remember(refresh){context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)}
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(if(automatic)"自动记录已开启" else "自动记录已暂停",style=MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick=onPermissions,modifier=Modifier.fillMaxWidth()){Text(if(fine && background)"定位权限已就绪" else "设置后台定位")}
        OutlinedButton(onClick=onPower,modifier=Modifier.fillMaxWidth()){Text(if(power)"省电限制已放宽 · 查看" else "设置不限制后台耗电")}
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("开机与升级后恢复记录");Text("需要自动记录开启和后台定位权限",style=MaterialTheme.typography.bodySmall)};Switch(boot,{boot=it;prefs.edit().putBoolean("restore_after_boot",it).apply()})}
        OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))},modifier=Modifier.fillMaxWidth()){Text("管理自启动与关联启动")}
        RecentTasksPreference()
        TextButton(onClick={help=true}){Text("后台记录帮助")}
    }
    if(help) AlertDialog(onDismissRequest={help=false},title={Text("后台记录帮助")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("在系统应用管理中允许自启动、关联启动和后台活动。不同品牌入口不同，这些系统开关需由你确认。")
        Text("隐藏最近任务不会增强保活。部分手机需要保留卡片并锁定后台；若记录中断，请先关闭隐藏。")
        Text("记录仍使用系统前台服务。强制停止、省电和厂商限制可能中断记录，不能保证永久常驻；重新打开后会检查并恢复已授权的记录。")
    }},confirmButton={TextButton(onClick={help=false}){Text("知道了")}})
}
