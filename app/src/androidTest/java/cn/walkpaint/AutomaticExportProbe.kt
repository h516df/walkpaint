package cn.walkpaint

import android.app.Instrumentation
import android.content.Intent
import java.io.File

internal object AutomaticExportProbe {
    fun run(i: Instrumentation): String {
        check(!BuildConfig.DEBUG && !BuildConfig.DEVELOPER_BUILD) { "Test the official release build" }
        val context = i.targetContext
        val file = File(context.getExternalFilesDir(null), "field-report.txt")
        fun request(flags: Int) {
            file.writeText("synthetic legacy diagnostic")
            i.runOnMainSync {
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(flags)
                    .putExtra("cn.walkpaint.extra.EXPORT_FIELD_REPORT", true))
            }
            i.waitForIdleSync()
            val deadline = android.os.SystemClock.uptimeMillis() + 8000
            while (file.exists() && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
            check(!file.exists()) { "Legacy generated diagnostic survived" }
            Thread.sleep(1500)
            check(!file.exists()) { "A protected build generated an automatic diagnostic" }
        }
        request(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        request(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return "AUTOMATIC_EXPORT_PASS: release startup and existing activity reject automatic export; legacy diagnostic removed"
    }
}
