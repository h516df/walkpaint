package cn.walkpaint

import android.app.Instrumentation
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Only same-signature instrumentation can invoke this; no production Intent is needed. */
internal object FieldReportProbe {
    fun capture(instrumentation: Instrumentation): String {
        val activity = UiEntry.main(instrumentation)
        val ready = CountDownLatch(1)
        var report = ""
        instrumentation.runOnMainSync {
            val method = MainActivity::class.java.getDeclaredMethod("prepareFieldReport", Function1::class.java)
            method.isAccessible = true
            method.invoke(activity, { text: String -> report = text; ready.countDown(); Unit })
        }
        check(ready.await(15, TimeUnit.SECONDS)) { "Instrumented report did not complete" }
        return report
    }

    fun run(instrumentation: Instrumentation): String {
        File(instrumentation.targetContext.getExternalFilesDir(null), "field-report.txt")
            .writeText(capture(instrumentation))
        return "FIELD_REPORT_PROBE_PASS"
    }
}
