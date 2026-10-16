package cn.walkpaint

import java.io.File

/** Automation is confined to the separate developer debug app, never an installed official app. */
internal class AutomaticFieldExport(
    private val debuggable: Boolean,
    private val developerBuild: Boolean,
    private val destination: File,
) {
    fun handle(requested: Boolean, prepare: ((String) -> Unit) -> Unit) {
        if (!debuggable || !developerBuild) {
            // Remove only the legacy generated diagnostic, without opening its private contents.
            destination.delete()
            return
        }
        if (requested) prepare { text -> destination.writeText(text) }
    }
}
