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
        OutlinedButton(onClick=onPower,modifier=Modifier.fillMaxWidth()){Text(if(power)"后台运行已允许" else "允许后台运行")}
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("开机后继续记录");Text("自动记录开启时生效",style=MaterialTheme.typography.bodySmall)};Switch(boot,{boot=it;prefs.edit().putBoolean("restore_after_boot",it).apply()})}
        OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))},modifier=Modifier.fillMaxWidth()){Text("打开系统设置")}
        RecentTasksPreference()
        Text("默认关闭。隐藏不取消记录通知；系统省电或强制停止仍可能中断记录。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={help=true}){Text("后台记录帮助")}
    }
    if(help) AlertDialog(onDismissRequest={help=false},title={Text("后台记录帮助")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("在系统设置中，允许这个 App 自启动和后台运行。")
        Text("如果记录总中断，先关闭后台隐藏，再试试锁定后台。")
        Text("强制停止或省电模式可能打断记录。重新打开 App 可恢复。")
    }},confirmButton={TextButton(onClick={help=false}){Text("知道了")}})
}
