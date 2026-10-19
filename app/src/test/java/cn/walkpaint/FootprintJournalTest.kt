package cn.walkpaint

import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FootprintJournalTest {
    private fun file()=Files.createTempDirectory("walkpaint-journal-test").resolve("journal.json").toFile()
    @Test fun annotationsPersistWithoutChangingTheirUnicodeOrLineBreaks() {
        val file=file();val memo=DayMemo("河畔散步 🌿","第一行\n第二行",listOf("周末","散步"),true)
        FootprintJournal(file).update("2026-10-19"){memo}
        assertEquals(memo,FootprintJournal(file).snapshot()["2026-10-19"])
    }
    @Test fun restoreKeepsLocalEditsAndExplicitClearsAndIsIdempotent() {
        val store=FootprintJournal(file());val local=DayMemo("本机标题",starred=true)
        store.update("2026-10-18"){local};store.update("2026-10-19"){DayMemo()}
        val incoming=mapOf("2026-10-17" to DayMemo("导入"),"2026-10-18" to DayMemo("旧标题"),"2026-10-19" to DayMemo("已清除的旧备注"))
        store.mergeMissing(incoming);val once=store.snapshot();store.mergeMissing(incoming)
        assertEquals(once,store.snapshot());assertEquals(local,once["2026-10-18"])
        assertEquals(DayMemo(),once["2026-10-19"]);assertEquals("导入",once["2026-10-17"]?.title)
    }
    @Test fun corruptFileIsRetainedAndCannotBeOverwrittenByAnEdit() {
        val file=file().apply {writeText("not valid json")};val original=file.readBytes();val store=FootprintJournal(file)
        assertThrows(IllegalStateException::class.java){store.snapshot()}
        assertThrows(IllegalStateException::class.java){store.update("2026-10-19"){DayMemo("新标题")}}
        assertArrayEquals(original,file.readBytes())
    }
    @Test fun failedDiskWriteDoesNotPublishAnUnsavedEdit() {
        val old=DayMemo("已保存")
        // A directory at the temporary output path makes writing fail even under privileged CI.
        val actual=file();val diskStore=FootprintJournal(actual);diskStore.update("2026-10-19"){old}
        actual.resolveSibling(actual.name+".tmp").mkdir()
        assertThrows(Exception::class.java){diskStore.update("2026-10-19"){DayMemo("未保存")}}
        assertEquals(old,diskStore.snapshot()["2026-10-19"])
        assertEquals(old,FootprintJournal(actual).snapshot()["2026-10-19"])
    }
    @Test fun malformedAndDuplicateDatesAreRejectedBeforeRestore() {
        assertThrows(IllegalArgumentException::class.java){JournalCodec.encode(mapOf("2026-02-30" to DayMemo()))}
        val root=JournalCodec.encode(mapOf("2026-10-19" to DayMemo("有效")))
        root.getJSONArray("days").put(root.getJSONArray("days").getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java){JournalCodec.decode(root)}
    }
    @Test fun tagLimitsApplyAndDuplicateSpellingIsNormalized() {
        assertEquals(listOf("散步","旅行","Test"),JournalCodec.tags("#散步，旅行,散步、Test,test"))
        assertThrows(IllegalArgumentException::class.java){JournalCodec.tags("a".repeat(25))}
        assertThrows(IllegalArgumentException::class.java){JournalCodec.tags((1..9).joinToString(","))}
        assertThrows(IllegalArgumentException::class.java){JournalCodec.encode(mapOf("2026-10-19" to DayMemo(note="a".repeat(2001))))}
    }
    @Test fun backupAnnotationPayloadRoundTripsAndUnknownVersionFails() {
        val input=mapOf("2026-10-19" to DayMemo("旅行","合成备注",listOf("出游"),true))
        assertEquals(input,JournalCodec.decode(JSONObject(JournalCodec.encode(input).toString())))
        assertThrows(IllegalArgumentException::class.java){JournalCodec.decode(JSONObject().put("version",2).put("days",JSONArray()))}
    }
    @Test fun queryCombinesDateNoteTagsAndFavoriteWithoutInventingDates() {
        val days=listOf("2026-10-19","2026-10-18").map {DailyFootprint(it,emptyList())}
        val memos=mapOf("2026-10-19" to DayMemo("河边","晚间散步",listOf("周末"),true),"2026-10-17" to DayMemo("不可凭备注生成足迹",starred=true))
        assertEquals(listOf(days[0]),JournalSearch.filter(days,memos,"2026-10 #周末 河边",true))
        assertEquals(listOf(days[0]),JournalSearch.filter(days,memos,"散步",false))
        assertTrue(JournalSearch.filter(days,memos,"不可凭备注",false).isEmpty())
        assertEquals(days,JournalSearch.filter(days,memos,"",false))
    }
}
