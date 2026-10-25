package com.chimafok.todo.data

import android.util.AtomicFile
import com.chimafok.todo.domain.UNCATEGORIZED_ID
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

/** Raw form text, not a validated task or scheduled reminder. App-private; never exported. */
data class NewTaskDraft(
    val id: String, val title: String = "", val notes: String = "", val date: String = "", val time: String = "",
    val zoneId: String? = null, val deadlineReference: Long = 0,
    val tagIds: List<String> = emptyList(), val categoryId: String = UNCATEGORIZED_ID, val statusName: String? = "OPEN",
    val frequency: String = "NONE", val interval: String = "1", val weekdays: List<Int> = emptyList(),
    val endMode: String = "NONE", val endDate: String = "", val endReference: String = "", val count: String = "10",
    val reminderInputs: String = "[]", val seriesReminderInputs: String = "[]",
    val inheritedReminderStamp: String? = null, val inheritedTemplateIds: List<String> = emptyList(),
)

class NewTaskDraftStore(directory: File) {
    private val atomic = AtomicFile(File(directory, "new-task-draft-v1.json"))
    companion object { const val MAX_BYTES = 1_048_576 }

    @Synchronized fun read(): NewTaskDraft? {
        val input = try { atomic.openRead() } catch (e: FileNotFoundException) {
            if (atomic.baseFile.exists() || File(atomic.baseFile.path + ".bak").exists()) throw e
            return null
        }
        val bytes = input.use {
            val data = it.readBytesBounded()
            require(data.size <= MAX_BYTES) { "草稿資料超出上限" }
            data
        }
        val j = JSONObject(bytes.toString(Charsets.UTF_8))
        require(j.getInt("version") == 1) { "草稿版本不支援" }
        fun strings(key: String) = j.getJSONArray(key).let { a -> (0 until a.length()).map(a::getString) }
        val days = j.getJSONArray("weekdays").let { a -> (0 until a.length()).map(a::getInt) }
        require(days.all { it in 1..7 } && days.distinct().size == days.size)
        val id = j.getString("id")
        require(id.isNotBlank() && id.length <= 200)
        return NewTaskDraft(id,j.getString("title"),j.getString("notes"),j.getString("date"),j.getString("time"),
            if (j.isNull("zoneId")) null else j.getString("zoneId"),j.getLong("deadlineReference"),
            strings("tagIds"),j.getString("categoryId"),if(j.isNull("statusName")) null else j.getString("statusName"),
            j.getString("frequency"),j.getString("interval"),days,j.getString("endMode"),j.getString("endDate"),
            j.getString("endReference"),j.getString("count"),j.getString("reminderInputs"),j.getString("seriesReminderInputs"),
            if(j.isNull("inheritedReminderStamp")) null else j.getString("inheritedReminderStamp"),strings("inheritedTemplateIds"))
    }

    @Synchronized fun save(d: NewTaskDraft) {
        val j = JSONObject().put("version",1).put("id",d.id).put("title",d.title).put("notes",d.notes)
            .put("date",d.date).put("time",d.time).put("zoneId",d.zoneId ?: JSONObject.NULL)
            .put("deadlineReference",d.deadlineReference).put("tagIds",JSONArray(d.tagIds)).put("categoryId",d.categoryId)
            .put("statusName",d.statusName ?: JSONObject.NULL).put("frequency",d.frequency).put("interval",d.interval)
            .put("weekdays",JSONArray(d.weekdays)).put("endMode",d.endMode).put("endDate",d.endDate)
            .put("endReference",d.endReference).put("count",d.count).put("reminderInputs",d.reminderInputs)
            .put("seriesReminderInputs",d.seriesReminderInputs)
            .put("inheritedReminderStamp",d.inheritedReminderStamp ?: JSONObject.NULL).put("inheritedTemplateIds",JSONArray(d.inheritedTemplateIds))
        val bytes = j.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "草稿太大，請縮短輸入後重試。" }
        val output = atomic.startWrite()
        try { output.write(bytes);atomic.finishWrite(output) }
        catch(e: Exception) { atomic.failWrite(output);throw e }
    }

    @Synchronized fun clear() {
        atomic.delete()
        check(!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) { "草稿未能移除，請重試。" }
    }

    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while(true) {
            val n=read(chunk)
            if(n<0) break
            require(buffer.size()+n<=MAX_BYTES) { "草稿資料超出上限" }
            buffer.write(chunk,0,n)
        }
        return buffer.toByteArray()
    }
}
