package cn.walkpaint

import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Debug-build measurement of the work the map screen does per state update, split so the cost can
 * be attributed rather than guessed:
 *
 * * `prep` — the database snapshot and history grouping that already existed before this change,
 * * `veil` — repainting the fog image (the per-fix increment, and the rare full rebuild),
 * * `ui`   — the only part that runs on the UI thread: handing the finished image to the map,
 * * `upload` — the handover itself, measured inside that UI work.
 *
 * The map draws on its own GL surface, so `dumpsys gfxinfo` reports nothing for it. These counters
 * are what says whether an update can block a drag. A release build never calls any of it.
 */
internal object PerfTrace {
    private const val TAG = "WalkPaintPerf"
    private const val SAMPLES = 64

    private val updateCount = AtomicInteger()
    private val updateNanos = AtomicLong()
    private val updateMaxNanos = AtomicLong()
    private val updateSamples = LongArray(SAMPLES)
    private val updateSlot = AtomicInteger()
    /**
     * The first update after a launch pays for opening the database, reading every stored point and
     * warming the JIT. That is a cold-start cost, not what a fix during a walk costs, so it is
     * reported on its own and left out of the steady-state numbers.
     */
    private val firstUpdateNanos = AtomicLong(-1)
    private val firstUpdateCount = AtomicInteger()

    private val prepSamples = LongArray(SAMPLES)
    private val prepSlot = AtomicInteger()

    private val incrementCount = AtomicInteger()
    private val incrementNanos = AtomicLong()
    private val incrementMaxNanos = AtomicLong()
    private val incrementSamples = LongArray(SAMPLES)
    private val incrementSlot = AtomicInteger()

    private val rebuildCount = AtomicInteger()
    private val rebuildMaxNanos = AtomicLong()

    private val uiCount = AtomicInteger()
    private val uiNanos = AtomicLong()
    private val uiMaxNanos = AtomicLong()
    private val uiSamples = LongArray(SAMPLES)
    private val uiSlot = AtomicInteger()

    private val uploadCount = AtomicInteger()
    private val uploadNanos = AtomicLong()
    private val uploadMaxNanos = AtomicLong()
    private val uploadSamples = LongArray(SAMPLES)
    private val uploadSlot = AtomicInteger()
    /**
     * Panning redraws the veil for the new view, which is separate from a state update and is
     * measured separately: it is the cost of the fog following the map.
     */
    private val panCount = AtomicInteger()
    private val panMaxNanos = AtomicLong()
    private val panSamples = LongArray(SAMPLES)
    private val panSlot = AtomicInteger()

    fun update(prepNanos: Long, veilNanos: Long, rebuilt: Boolean) {
        updateCount.incrementAndGet()
        val total = prepNanos + veilNanos
        if (firstUpdateCount.incrementAndGet() == 1) {
            firstUpdateNanos.set(total)
        } else {
            updateNanos.addAndGet(total)
            updateMaxNanos.updateAndGet { maxOf(it, total) }
            store(updateSamples, updateSlot, total)
            store(prepSamples, prepSlot, prepNanos)
        }
        if (rebuilt) {
            rebuildCount.incrementAndGet()
            rebuildMaxNanos.updateAndGet { maxOf(it, veilNanos) }
        } else {
            incrementCount.incrementAndGet()
            incrementNanos.addAndGet(veilNanos)
            incrementMaxNanos.updateAndGet { maxOf(it, veilNanos) }
            store(incrementSamples, incrementSlot, veilNanos)
        }
        report()
    }

    fun ui(nanos: Long) {
        uiCount.incrementAndGet()
        uiNanos.addAndGet(nanos)
        uiMaxNanos.updateAndGet { maxOf(it, nanos) }
        store(uiSamples, uiSlot, nanos)
        report()
    }

    fun upload(nanos: Long) {
        uploadCount.incrementAndGet()
        uploadNanos.addAndGet(nanos)
        uploadMaxNanos.updateAndGet { maxOf(it, nanos) }
        store(uploadSamples, uploadSlot, nanos)
        report()
    }

    /** One veil redraw for a new viewport (a pan or a zoom), with the time it took. */
    fun pan(nanos: Long) {
        panCount.incrementAndGet()
        panMaxNanos.updateAndGet { maxOf(it, nanos) }
        store(panSamples, panSlot, nanos)
        report()
    }

    private fun store(samples: LongArray, slot: AtomicInteger, nanos: Long) {
        val index = slot.getAndIncrement()
        if (index < samples.size) samples[index] = nanos
    }

    private fun median(samples: LongArray): Long {
        val filled = samples.filter { it > 0 }.sorted()
        return if (filled.isEmpty()) 0L else filled[filled.size / 2]
    }

    fun report() {
        val steady = maxOf(1, updateCount.get() - 1)
        val increments = maxOf(1, incrementCount.get())
        val uis = maxOf(1, uiCount.get())
        val uploads = maxOf(1, uploadCount.get())
        val first = firstUpdateNanos.get().coerceAtLeast(0L)
        Log.d(TAG, String.format(Locale.ROOT,
            "PERF update_n=%d update_med_ms=%.1f update_avg_ms=%.1f update_max_ms=%.1f " +
                "first_update_ms=%.1f prep_med_ms=%.1f " +
                "veil_inc_n=%d veil_inc_med_ms=%.1f veil_inc_avg_ms=%.1f veil_inc_max_ms=%.1f " +
                "veil_rebuild_n=%d veil_rebuild_max_ms=%.1f " +
                "ui_n=%d ui_med_ms=%.1f ui_max_ms=%.1f " +
                "upload_n=%d upload_med_ms=%.1f upload_avg_ms=%.1f upload_max_ms=%.1f " +
                "pan_n=%d pan_med_ms=%.1f pan_max_ms=%.1f",
            updateCount.get(), median(updateSamples) / 1e6, updateNanos.get() / 1e6 / steady,
            updateMaxNanos.get() / 1e6,
            first / 1e6, median(prepSamples) / 1e6,
            incrementCount.get(), median(incrementSamples) / 1e6,
            incrementNanos.get() / 1e6 / increments, incrementMaxNanos.get() / 1e6,
            rebuildCount.get(), rebuildMaxNanos.get() / 1e6,
            uiCount.get(), median(uiSamples) / 1e6, uiMaxNanos.get() / 1e6,
            uploadCount.get(), median(uploadSamples) / 1e6, uploadNanos.get() / 1e6 / uploads,
            uploadMaxNanos.get() / 1e6,
            panCount.get(), median(panSamples) / 1e6, panMaxNanos.get() / 1e6))
    }
}
