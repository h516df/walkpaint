package cn.walkpaint

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

internal data class DayMemo(val title:String="", val note:String="", val tags:List<String> = emptyList(),
                            val starred:Boolean=false)

/** User annotations are separate from immutable GPS observations and the live recording cursor. */
internal object JournalCodec {
    const val MAX_BYTES=16*1024*1024
    const val MAX_DAYS=10000
    fun validDay(key:String):Boolean = runCatching {LocalDate.parse(key).toString()==key}.getOrDefault(false)
    fun tags(text:String):List<String> = text.split(Regex("[,，、\\n#]+"))
        .map {it.trim()}.filter {it.isNotEmpty()}.distinctBy {it.lowercase(Locale.ROOT)}.also {
            require(it.size<=8 && it.all {tag->tag.length<=24}) {"最多 8 个标签，每个不超过 24 字"}
        }
    fun validate(key:String,memo:DayMemo) {
        require(validDay(key)) {"行记日期无效"}
        require(memo.title.length<=80 && memo.note.length<=2000) {"标题或备注过长"}
        require(memo.tags.size<=8 && memo.tags.all {it.isNotBlank() && it.length<=24}) {"标签无效"}
    }
    fun encode(entries:Map<String,DayMemo>):JSONObject {
        require(entries.size<=MAX_DAYS)
        val days=JSONArray()
        entries.toSortedMap().forEach {(key,memo)->
            validate(key,memo)
            days.put(JSONObject().put("day",key).put("title",memo.title).put("note",memo.note)
                .put("tags",JSONArray(memo.tags)).put("starred",memo.starred))
        }
        return JSONObject().put("version",1).put("days",days)
    }
    fun decode(root:JSONObject):Map<String,DayMemo> {
        require(root.toString().toByteArray(Charsets.UTF_8).size<=MAX_BYTES) {"行记文件过大"}
        require(root.getInt("version")==1) {"不支持的行记格式"}
        val days=root.getJSONArray("days");require(days.length()<=MAX_DAYS)
        return buildMap {
            for(n in 0 until days.length()) {
                val item=days.getJSONObject(n);val key=item.getString("day")
                val tags=item.getJSONArray("tags");require(tags.length()<=8)
                val memo=DayMemo(item.getString("title"),item.getString("note"),
                    (0 until tags.length()).map {tags.getString(it)},item.getBoolean("starred"))
                validate(key,memo);require(key !in this) {"行记日期重复"};put(key,memo)
            }
        }
    }
}

internal class FootprintJournal(private val file:File) {
    val revision=MutableStateFlow(0L)
    private var loaded=false
    private var failure:Exception?=null
    private var entries:Map<String,DayMemo> = emptyMap()
    @Synchronized fun snapshot():Map<String,DayMemo> {
        if(!loaded) {
            try {
                if(file.exists()) {
                    require(file.length()<=JournalCodec.MAX_BYTES) {"行记文件过大"}
                    entries=JournalCodec.decode(JSONObject(file.readText(Charsets.UTF_8)))
                }
            } catch(e:Exception) {failure=e}
            loaded=true
        }
        check(failure==null) {"行记暂时无法读取，原文件已保留"}
        return entries.toMap()
    }
    @Synchronized fun update(key:String,change:(DayMemo)->DayMemo) {
        val old=snapshot();val memo=change(old[key] ?: DayMemo())
        JournalCodec.validate(key,memo)
        // Empty entries are intentional clears: restoring an older backup must not revive them.
        persist(old+(key to memo))
    }
    @Synchronized fun mergeMissing(incoming:Map<String,DayMemo>) {
        val old=snapshot();incoming.forEach {(key,value)->JournalCodec.validate(key,value)}
        val next=incoming+old
        if(next!=old) persist(next)
    }
    private fun persist(next:Map<String,DayMemo>) {
        val bytes=JournalCodec.encode(next).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size<=JournalCodec.MAX_BYTES) {"行记达到容量上限，请先导出备份"}
        val directory=file.absoluteFile.parentFile!!
        check(directory.isDirectory || directory.mkdirs()) {"无法保存行记"}
        val temporary=File(directory,file.name+".tmp")
        try {
            FileOutputStream(temporary).use {it.write(bytes);it.fd.sync()}
            Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally {temporary.delete()}
        entries=next.toMap();revision.value++
    }
}

internal object JournalSearch {
    fun filter(days:List<DailyFootprint>,entries:Map<String,DayMemo>,query:String,starredOnly:Boolean):List<DailyFootprint> {
        val words=query.trim().split(Regex("\\s+")).map {it.removePrefix("#")}.filter {it.isNotEmpty()}
        return days.filter {day->
            val memo=entries[day.key] ?: DayMemo()
            (!starredOnly || memo.starred) && (words.isEmpty() ||
                listOf(day.key,memo.title,memo.note,memo.tags.joinToString(" ")).joinToString(" ").let {text->
                    words.all {text.contains(it,ignoreCase=true)}
                })
        }
    }
}
