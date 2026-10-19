package cn.walkpaint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Empty-history guidance only; never substitutes synthetic exploration or removes the veil. */
@Composable
internal fun InitialExplorationHint(viewReady:Boolean,automatic:Boolean,hasFix:Boolean,
                                    onLocate:()->Unit,onStart:()->Unit) {
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
        Surface(modifier=Modifier.padding(horizontal=32.dp).widthIn(max=360.dp),
            shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("第一段足迹，等你出发",style=MaterialTheme.typography.titleMedium)
                Text(ExplorationReadiness.message(viewReady,automatic,hasFix),
                    style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("定位后跟随位置与朝向，拖图暂停。",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick=onLocate){Text("定位我")}
                    if(!automatic) Button(onClick=onStart){Text("开始记录")}
                }
            }
        }
    }
}
