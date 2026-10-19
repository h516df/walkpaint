package cn.walkpaint

import android.app.Application
import androidx.lifecycle.AndroidViewModel

/** Configuration changes retain the existing download; no Activity is held by the updater. */
internal class UpdateSession(application:Application):AndroidViewModel(application) {
    val updater=PrivateUpdater(application)
    override fun onCleared() {updater.close()}
}
