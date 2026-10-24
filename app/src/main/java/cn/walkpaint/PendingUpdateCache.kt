package cn.walkpaint

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Untrusted partial bytes, bound to freshly checked size/hash/version. Never installable. */
internal class PendingUpdateCache(private val directory:File,private val installed:Int) {
    val partial get()=File(directory,"pending.apk")
    private val metadata get()=File(directory,"pending.json")
    private val temporary get()=File(directory,"pending.json.tmp")
    private fun record():UpdateCandidate? {
        require(metadata.isFile && metadata.length() in 1..8192)
        require(metadata.canonicalFile==File(directory.canonicalFile,"pending.json"))
        require(partial.isFile && partial.canonicalFile==File(directory.canonicalFile,"pending.apk"))
        val candidate=ReadyUpdateRecord.decode(metadata.readText(Charsets.UTF_8),installed)
        require(partial.length() in 0..candidate.asset.size)
        return candidate
    }
    fun cleanup() {
        temporary.delete()
        if(runCatching {record()}.getOrNull()==null) clear()
    }
    fun hasPartial()=runCatching {record()!=null && partial.length()>0}.getOrDefault(false)
    fun bytes(candidate:UpdateCandidate):Long = runCatching {
        val old=record() ?: return@runCatching 0L
        if(ReadyUpdateRecord.encode(old)==ReadyUpdateRecord.encode(candidate)) partial.length() else 0L
    }.getOrDefault(0L)
    fun prepare(candidate:UpdateCandidate):Long {
        val saved=bytes(candidate)
        if(saved>0) return saved
        clear()
        // Apply the same bounded fixed-name record validation as the completed-cache path.
        val text=ReadyUpdateRecord.encode(candidate)
        ReadyUpdateRecord.decode(text,installed)
        check(directory.isDirectory || directory.mkdirs())
        try {
            FileOutputStream(temporary).use {it.write(text.toByteArray(Charsets.UTF_8));it.fd.sync()}
            Files.move(temporary.toPath(),metadata.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally {temporary.delete()}
        return 0L
    }
    fun clear() {metadata.delete();temporary.delete();partial.delete()}
}
