package cn.walkpaint

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AutomaticFieldExportTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun officialReleaseDoesNotCollectOrWriteRequestedDiagnostics() {
        blocked(debuggable = false, developer = false)
    }

    @Test fun officialDebugDoesNotCollectOrWriteRequestedDiagnostics() {
        blocked(debuggable = true, developer = false)
    }

    @Test fun developerReleaseDoesNotCollectOrWriteRequestedDiagnostics() {
        blocked(debuggable = false, developer = true)
    }

    private fun blocked(debuggable: Boolean, developer: Boolean) {
        val file = File(temporary.root, "field-report.txt")
        AutomaticFieldExport(debuggable, developer, file).handle(true) {
            fail("A protected build must not even collect diagnostic material")
        }
        assertFalse(file.exists())
    }

    @Test fun protectedLaunchRemovesOnlyTheLegacyReportEvenWithoutAnExportRequest() {
        val file = temporary.newFile("field-report.txt").apply { writeText("synthetic diagnostic") }
        val data = temporary.newFile("footprints.db").apply { writeText("preserve stored data") }
        AutomaticFieldExport(false, false, file).handle(false) { fail("Unexpected collection") }
        assertFalse(file.exists())
        assertEquals("preserve stored data", data.readText())
    }

    @Test fun developerDebugExportRemainsExplicitAndUsesThePreparedText() {
        val file = File(temporary.root, "field-report.txt")
        val exporter = AutomaticFieldExport(true, true, file)
        exporter.handle(false) { fail("No request was made") }
        assertFalse(file.exists())
        exporter.handle(true) { ready -> ready("synthetic diagnostic") }
        assertEquals("synthetic diagnostic", file.readText())
    }
}
