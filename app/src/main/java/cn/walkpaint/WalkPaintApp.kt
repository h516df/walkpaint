package cn.walkpaint

import android.app.Application
import java.io.File

class WalkPaintApp : Application() {
    lateinit var store: TrackStore
    var recordingMessage = "准备好，把走过的路留成颜色"
    override fun onCreate() {
        super.onCreate()
        store = TrackStore(File(filesDir, "tracks.json"))
        if (store.snapshot().activeId != null) {
            runCatching { store.update { it.copy(activeId = null) } }
            recordingMessage = "上次记录已中断，已保存的足迹仍在；可手动开始新记录"
        }
    }
}
