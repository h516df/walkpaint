package cn.walkpaint

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun PostcardDialog(day:DailyFootprint,memo:DayMemo,color:Int,onClose:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var themeName by rememberSaveable(day.key){mutableStateOf(CardTheme.PAPER.name)}
    var bitmap by remember {mutableStateOf<Bitmap?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    var pendingFile by rememberSaveable(day.key){mutableStateOf<String?>(null)}
    val directory=remember(context){File(context.cacheDir,"postcards")}
    LaunchedEffect(day,memo.title,color,themeName) {
        bitmap=null;error=null
        try {bitmap=withContext(Dispatchers.Default){
            RoutePostcard.render(PostcardSpec(day.key,memo.title,exploredDistance(day.meters),RouteOutline.from(day.tracks),color,CardTheme.valueOf(themeName)))
        }} catch(e:CancellationException){throw e}
        catch(_:Exception){error="图片暂时无法生成，请稍后再试"}
    }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")){uri->
        val name=pendingFile;pendingFile=null
        if(name!=null && RoutePostcard.validName(name)) {
            scope.launch {
                busy=true
                try {
                    if(uri!=null) withContext(Dispatchers.IO){
                        val file=File(directory,name)
                        file.inputStream().use {input->
                            (context.contentResolver.openOutputStream(uri) ?: error("Output unavailable")).use {output->input.copyTo(output)}
                        }
                    }
                    if(uri!=null) error="图片已保存"
                } catch(e:CancellationException){throw e}
                catch(_:Exception){error="图片未保存，请检查目标位置后重试"}
                finally {withContext(kotlinx.coroutines.NonCancellable+Dispatchers.IO){File(directory,name).delete()};busy=false}
            }
        }
    }
    fun export(sharing:Boolean) {
        val current=bitmap ?: return
        busy=true;error=null
        scope.launch {
            try {
                val file=withContext(Dispatchers.IO){RoutePostcard.cache(directory,current)}
                if(sharing) {
                    val uri=FileProvider.getUriForFile(context,"${context.packageName}.updates",file)
                    val intent=Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.clipData=ClipData.newUri(context.contentResolver,"路线明信片",uri)
                    context.startActivity(Intent.createChooser(intent,"分享路线明信片"))
                } else {pendingFile=file.name;save.launch("足迹涂色-${day.key}.png")}
            } catch(e:CancellationException){throw e}
            catch(_:Exception){error=if(sharing)"暂时无法分享，可以先保存图片" else "图片未保存，请稍后再试"}
            finally {busy=false}
        }
    }
    Dialog(onDismissRequest={if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(modifier=Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                TrailHeader("路线明信片",onBack={if(!busy)onClose()})
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                    bitmap?.let {Image(it.asImageBitmap(),"${day.key} 的路线明信片预览",modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit)}
                        ?: if(error==null) CircularProgressIndicator() else Unit
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    CardTheme.entries.forEach {theme->FilterChip(selected=theme.name==themeName,onClick={if(!busy)themeName=theme.name},label={Text(theme.label)})}
                }
                Text("图片包含这天的路线形状和日期，确认预览后再保存或分享。备注不会写入图片。",style=MaterialTheme.typography.bodySmall)
                error?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(enabled=bitmap!=null && !busy,onClick={export(false)},modifier=Modifier.weight(1f)){Text("保存图片")}
                    Button(enabled=bitmap!=null && !busy,onClick={export(true)},modifier=Modifier.weight(1f)){Text(if(busy)"正在准备…" else "分享图片")}
                }
            }
        }
    }
}
