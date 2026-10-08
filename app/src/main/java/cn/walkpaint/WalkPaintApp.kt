package cn.walkpaint

import android.app.Application

class WalkPaintApp : Application() {
    lateinit var repository: FootprintDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        repository = FootprintDatabase(this)
    }
}
