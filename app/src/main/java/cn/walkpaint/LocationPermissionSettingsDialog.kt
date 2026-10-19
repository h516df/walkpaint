package cn.walkpaint

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun LocationPermissionSettingsDialog(
    purpose:LocationPermissionRecovery.Purpose,onDismiss:()->Unit,onSettings:()->Unit
) {
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("需要允许精确定位")},
        text={Text(if(purpose==LocationPermissionRecovery.Purpose.LOCATE)
            "请在系统位置权限中开启精确定位，再返回查看当前位置。这不会开启自动记录。"
            else "目前自动记录未开启。你可以在系统位置权限中开启精确定位；不授权也能查看原有足迹。")},
        confirmButton={Button(onClick=onSettings) {Text("去设置")}},
        dismissButton={TextButton(onClick=onDismiss) {Text("稍后")}}
    )
}
