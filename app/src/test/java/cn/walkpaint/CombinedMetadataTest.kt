package cn.walkpaint

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CombinedMetadataTest {
    @Test fun bothOptionalSectionsRoundTripWithoutRenamingOrCoordinateConversion() {
        val journal=mapOf("2026-10-19" to DayMemo("合成备注"))
        val landmarks=mapOf("fixture" to ExplorationLandmark("fixture","合成地点",0.0,0.0))
        val root=JSONObject().put("journal",JournalCodec.encode(journal)).put("landmarks",LandmarkCodec.encode(landmarks))
        val restored=JSONObject(root.toString())
        assertEquals(journal,JournalCodec.decode(restored.getJSONObject("journal")))
        assertEquals(landmarks,LandmarkCodec.decode(restored.getJSONObject("landmarks")))
    }
    @Test fun localEditsAndExplicitDeletionBothWinRepeatedRestore() {
        val dir=Files.createTempDirectory("combined-metadata-test").toFile()
        try {
            val journal=FootprintJournal(File(dir,"journal.json"));val landmarks=LandmarkStore(File(dir,"landmarks.json"))
            journal.update("2026-10-18"){DayMemo("本机编辑")};journal.update("2026-10-19"){DayMemo()}
            landmarks.save(ExplorationLandmark("edit","本机名称",1.0,1.0));landmarks.remove("deleted")
            repeat(2) {
                journal.mergeMissing(mapOf("2026-10-18" to DayMemo("旧备注"),"2026-10-19" to DayMemo("已清空备注")))
                landmarks.mergeMissing(mapOf("edit" to ExplorationLandmark("edit","旧名称",0.0,0.0),
                    "deleted" to ExplorationLandmark("deleted","已删除地点",0.0,0.0)))
            }
            assertEquals("本机编辑",journal.snapshot()["2026-10-18"]?.title)
            assertEquals(DayMemo(),journal.snapshot()["2026-10-19"])
            assertEquals("本机名称",landmarks.snapshot()["edit"]?.name)
            assertTrue(landmarks.snapshot().getValue("deleted").deleted)
        } finally {dir.deleteRecursively()}
    }
    @Test fun partialRestoreWarningsComposeIndependentlyAndTogether() {
        for(journal in listOf(false,true)) for(landmarks in listOf(false,true)) {
            val result=RestoreResult(1,2,3,0,0,journalPending=journal,landmarksPending=landmarks)
            assertEquals(journal,result.message().contains("行记尚未恢复"))
            assertEquals(landmarks,result.message().contains("探索点恢复未完成"))
            assertTrue(result.message().contains("原记录保留"))
        }
    }
}
