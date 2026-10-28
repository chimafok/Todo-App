package com.chimafok.todo.data

import android.util.JsonReader
import android.util.JsonToken
import com.chimafok.todo.domain.*
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.*
import java.util.Locale

const val BACKUP_MAX_BYTES = 64 * 1024 * 1024
const val BACKUP_MAX_ROWS = 200_000
data class BackupDocument(val data: BackupGraph, val exportedAt: Long, val appVersion: String) {
    val fingerprint get() = backupHash(data.normalized())
}

/** Bound input before allocating the graph; reject duplicates, fractional numbers and invalid UTF-8. */
object BackupFormat {
    fun read(stream: InputStream): BackupDocument {
        val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val bounded=object:java.io.FilterInputStream(stream) {
            var count=0L
            fun account(n:Int){if(n>0){count+=n;require(count<=BACKUP_MAX_BYTES){"備份檔案不可超過 64 MiB"}}}
            override fun read():Int=super.read().also { if(it>=0)account(1) }
            override fun read(b:ByteArray,off:Int,len:Int):Int=`in`.read(b,off,len).also(::account)
        }
        return fromRoot(strictJsonReader(java.io.InputStreamReader(bounded,decoder)))
    }

    fun encode(graph: BackupGraph, now: Long, appVersion: String): String {
        val portable=graph.portable();validateBackup(portable,false)
        return canonical(mapOf("format" to "Todo-App", "formatVersion" to 5, "schemaVersion" to 9,
            "exportedAt" to now,"appVersion" to appVersion,"data" to portable.normalized()))
    }
    fun decode(text: String): BackupDocument {
        require(text.toByteArray(Charsets.UTF_8).size<=BACKUP_MAX_BYTES){"備份檔案不可超過 64 MiB"}
        return fromRoot(strictJson(text))
    }
    private fun fromRoot(value:Any?):BackupDocument {
        val root=value as? Map<*,*> ?: error("備份須為 JSON 物件")
        require(root.keys==setOf("format","formatVersion","schemaVersion","exportedAt","appVersion","data")){"備份欄位不完整或版本不支援"}
        val legacy = root["formatVersion"] == 1L && root["schemaVersion"] == 7L
        val current = root["formatVersion"] in listOf(4L, 5L) && root["schemaVersion"] == 9L
        require(root["format"]=="Todo-App" && (current || legacy || (root["formatVersion"] in listOf(2L, 3L) && root["schemaVersion"]==8L))){"不支援此備份格式或資料版本（本版支援格式 5／資料版本 9，並可匯入格式 1–4）"}
        val inputTables = if (legacy) legacyBackupTables else if (current) backupTables else previousBackupTables
        val exported=root["exportedAt"] as? Long ?: error("缺少匯出時間")
        val version=root["appVersion"] as? String ?: error("缺少 App 版本");require(version.length in 1..80)
        val raw=root["data"] as? Map<*,*> ?: error("缺少備份資料")
        require(raw.keys==inputTables.keys){"備份資料表不完整或含不支援的內容"}
        var count=0
        val parsed=BackupGraph(inputTables.mapValues { (table,spec) ->
            val rows=raw[table] as? List<*> ?: error("資料必須為陣列：$table")
            count+=rows.size;require(count<=BACKUP_MAX_ROWS){"備份最多支援 200,000 筆資料列"}
            rows.map { value ->
                val row=value as? Map<*,*> ?: error("資料列格式錯誤：$table")
                require(row.keys.all { k -> spec.fields.any { it.name==k } }){"備份含不支援的欄位：$table"}
                spec.fields.associate { f ->
                    val v=if(f.name=="updatedAt" && row[f.name]==null)0L else row[f.name]
                    require(v!=null || !f.required){"缺少必要欄位：$table.${f.name}"}
                    if(v!=null)require(when { f.name in backupBooleans -> v is Boolean;f.type=="INTEGER" -> v is Long;else->v is String }){"欄位型別錯誤：$table.${f.name}"}
                    f.name to v
                }
            }
        })
        val graph = if (current) parsed else BackupGraph(backupTables.mapValues { (table, spec) ->
            parsed.rows(table).map { row -> spec.fields.associate { it.name to (row[it.name] ?: if (it.name == "imageIds") "" else null) } }
        })
        val canonical=canonicalSystems(graph)
        if (root["formatVersion"] in listOf(1L, 2L)) require(canonical.rows("tasks").none {
            it["categoryId"] == STUDY_ID && it["status"] !in listOf(TaskStatus.OPEN.name, TaskStatus.COMPLETED.name)
        }) { "學習進行中狀態須使用備份格式 3" }
        if (!current) require(referencedImageIds(canonical).isEmpty()) { "圖片附件須使用備份格式 4" }
        validateBackup(canonical,false)
        return BackupDocument(canonical,exported,version)
    }
}

/** Shared by envelope and historical snapshots. It never enables JsonReader's lenient mode. */
fun strictJson(text:String):Any?=strictJsonReader(StringReader(text))
private fun strictJsonReader(input:java.io.Reader):Any? {
    JsonReader(input).use { reader ->
        var values=0;var rows=0
        fun string():String=reader.nextString().also { s ->
            var i=0;while(i<s.length){val c=s[i];if(c.isHighSurrogate()){require(i+1<s.length && s[i+1].isLowSurrogate()){ "無效 Unicode 字元" };i+=2}
                else {require(!c.isLowSurrogate()){ "無效 Unicode 字元" };i++}}
        }
        fun value(depth:Int,rowArray:Boolean=false):Any? {
            require(depth<=12 && ++values<=5_000_000){"備份內容過深或過大"}
            return when(reader.peek()) {
                JsonToken.BEGIN_OBJECT -> {reader.beginObject();val map=linkedMapOf<String,Any?>()
                    while(reader.hasNext()){val name=reader.nextName();require(name !in map){"JSON 欄位重複：${name.take(80)}"};map[name]=value(depth+1,depth==1 && name in backupTables)}
                    reader.endObject();map}
                JsonToken.BEGIN_ARRAY -> {reader.beginArray();val list=mutableListOf<Any?>();while(reader.hasNext()){if(rowArray)require(++rows<=BACKUP_MAX_ROWS){"備份資料列過多"};list+=value(depth+1)};reader.endArray();list}
                JsonToken.STRING -> string()
                JsonToken.BOOLEAN -> reader.nextBoolean()
                JsonToken.NULL -> {reader.nextNull();null}
                JsonToken.NUMBER -> {val n=reader.nextString();require(n.matches(Regex("-?(0|[1-9][0-9]*)"))){"數字須為整數"};requireNotNull(n.toLongOrNull()){ "數字超出範圍" }}
                else -> error("JSON 格式錯誤")
            }
        }
        val result=value(0);require(reader.peek()==JsonToken.END_DOCUMENT){"備份有多餘內容"};return result
    }
}
private fun canonicalSystems(graph:BackupGraph):BackupGraph {
    val remap=mutableMapOf<String,String>()
    listOf(SystemCategory.UNCATEGORIZED to UNCATEGORIZED_ID,SystemCategory.ASSIGNMENTS to ASSIGNMENTS_ID).forEach { (kind,id) ->
        val rows=graph.rows("categories").filter { it["systemKind"]==kind.name };require(rows.size==1){"備份須有唯一的系統分類"}
        remap[rows.single().text("id")]=id
    }
    return BackupGraph(graph.tables.mapValues { (t,rows) -> rows.map { row -> when(t) {
        "categories" -> row+("id" to (remap[row.text("id")] ?: row.text("id")))
        "tasks", "recurring_series" -> row+("categoryId" to (remap[row.text("categoryId")] ?: row.text("categoryId")))
        else -> row
    } } })
}

/** Validate the whole graph before any SQL mutation. Local graphs may also contain trash. */
fun validateBackup(g:BackupGraph, allowTrash:Boolean) {
    require(g.tables.keys.all { it in backupTables })
    var size=0
    backupTables.forEach { (t,spec) ->
        val rows=g.rows(t);size+=rows.size;require(size<=BACKUP_MAX_ROWS){"備份資料過多"}
        require(rows.map { rowKey(t,it) }.distinct().size==rows.size){"識別碼或關聯重複：$t"}
        rows.forEach { r -> if(!allowTrash)require(r.keys.all { key -> spec.fields.any { it.name==key } }){ "不支援的備份欄位" };spec.keys.forEach { key -> val id=r.text(key);require(id.isNotBlank() && id.length<=200 && id.none { it.code<32 }){"識別碼無效"} }
            listOf("createdAt","updatedAt").forEach { k -> (r[k] as Number?)?.toLong()?.let(Instant::ofEpochMilli) }
        }
    }
    fun idMap(t:String)=g.rows(t).associateBy { it.text("id") }
    fun name(r:BackupRow){val n=r.text("name");require(n==n.trim() && n.length in 1..50){"分類／標籤名稱須為 1–50 字元"}}
    val categories=idMap("categories");val tags=idMap("tags");val tasks=idMap("tasks");val series=idMap("recurring_series");val rules=idMap("series_rules")
    val images = idMap("note_images")
    images.values.forEach { validateImageData(it.text("id"), it.text("data")) }
    require(referencedImageIds(g).all { it in images }) { "備份缺少圖片附件" }
    val rulesBySeries=rules.values.groupBy { it.text("seriesId") }
    g.rows("categories").forEach(::name);g.rows("tags").forEach(::name)
    val systems=mapOf(UNCATEGORIZED_ID to SystemCategory.UNCATEGORIZED.name,ASSIGNMENTS_ID to SystemCategory.ASSIGNMENTS.name)
    systems.forEach { (id,kind) -> require(categories[id]?.get("systemKind")==kind && g.rows("categories").count { it["systemKind"]==kind }==1){"系統分類缺失或 ID 不正確"} }
    require(categories.getValue(UNCATEGORIZED_ID).text("name")=="未分類"){"系統未分類的名稱不可變更"}
    categories.values.forEach { r ->require(r["systemKind"]==null || systems[r.text("id")]==r["systemKind"]){"系統分類無效"} }
    tasks.values.forEach { r ->
        val c=requireNotNull(categories[r.text("categoryId")]){"任務的分類不存在"}
        val date=r.optionalText("dueDate")?.let(LocalDate::parse);val time=r.optionalText("dueTime")?.let(LocalTime::parse)
        require(date==null || date.year in 1..9999){"截止日期超出範圍"}
        val status=TaskStatus.valueOf(r.text("status"));validateDraft(TaskDraft(r.text("id"),r.text("title"),r.text("notes"),r.text("categoryId"),date,time,r.optionalText("zoneId"),status),c["systemKind"]==SystemCategory.ASSIGNMENTS.name)
        require(r.text("title")==validatedTitle(r.text("title")))
        require(status==TaskStatus.COMPLETED || r["completedAt"]==null){"未完成任務不能帶完成時間"}
        r.optionalLong("completedAt")?.let(Instant::ofEpochMilli)
        validateDraft(TaskDraft(r.text("id"),r.text("title"),startDate=r.optionalText("startDate")?.let(LocalDate::parse),
            startTime=r.optionalText("startTime")?.let(LocalTime::parse),startZoneId=r.optionalText("startZoneId")), false)
        r.optionalLong("deletedAt")?.let(Instant::ofEpochMilli)
    }
    series.values.forEach { r ->
        require(categories.containsKey(r.text("categoryId")) && r.int("cursor") in 0..10_000_000){"系列分類或游標無效"}
        require(r.text("title")==validatedTitle(r.text("title")) && r.text("notes").length<=20_000)
        val life=SeriesLifecycle.valueOf(r.text("lifecycle"))
        require((r["deletedAt"] != null) == (life == SeriesLifecycle.TRASHED)) { "系列刪除狀態不一致" }
        r.optionalLong("deletedAt")?.let(Instant::ofEpochMilli)
        val previous=r.optionalText("previousLifecycle")?.let(SeriesLifecycle::valueOf)
        require(if(life==SeriesLifecycle.TRASHED) previous!=null && previous!=SeriesLifecycle.TRASHED else previous==null) { "系列復原狀態不完整" }
        val rr=rulesBySeries[r.text("id")].orEmpty();require(rr.isNotEmpty() && rr.map { it.int("version") }.distinct().size==rr.size){"系列規則缺失或版本重複"}
        val ordered=rr.sortedBy { it.int("version") };require(ordered.first().int("effectiveFrom")==1 && ordered.last().int("effectiveFrom").toLong()<=r.long("cursor")+1){"系列游標早於目前規則"}
        require(ordered.zipWithNext().all { (a,b) -> a.int("effectiveFrom")<=b.int("effectiveFrom") }){"系列規則順序不一致"}
    }
    rules.values.forEach { r ->
        require(series.containsKey(r.text("seriesId")) && r.int("version") in 1 until Int.MAX_VALUE && r.int("effectiveFrom") in 1..10_000_001){"系列規則關聯無效"}
        val max=r.optionalLong("maxCount")?.let(Math::toIntExact);require(max==null || max>=r.int("effectiveFrom"))
        val mask=r.int("weekdayMask");require(mask in 0..127)
        RecurrenceRule(LocalDate.parse(r.text("anchor")),RecurrenceFrequency.valueOf(r.text("frequency")),r.text("zoneId"),r.int("intervalDays"),
            DayOfWeek.entries.filter { mask and (1 shl(it.value-1))!=0 }.toSet(),r.optionalText("endDate")?.let(LocalDate::parse),max?.let { it-r.int("effectiveFrom")+1 })
        r.optionalText("dueTime")?.let(LocalTime::parse)
        val offset=r.optionalLong("startDayOffset")?.let(Math::toIntExact)
        require(offset == null || offset in -3_652_058..3_652_058) { "開始日期偏移超出範圍" }
        require(r["startTime"] == null || offset != null) { "開始時間缺少日期規則" }
        require(offset != null || r["startZoneId"] == null) { "開始時區缺少日期規則" }
        r.optionalText("startZoneId")?.let(ZoneId::of)
        r.optionalText("startTime")?.let { LocalTime.parse(it);require(r["startZoneId"]!=null) { "開始時間缺少時區" } }
    }
    g.rows("task_tags").forEach { require(it.text("taskId") in tasks && it.text("tagId") in tags){"任務標籤關聯不存在"} }
    g.rows("series_tags").forEach { require(it.text("seriesId") in series && it.text("tagId") in tags){"系列標籤關聯不存在"} }
    val slots=mutableSetOf<Pair<String,Int>>()
    g.rows("occurrences").forEach { r ->
        val task=requireNotNull(tasks[r.text("taskId")]){"實例任務不存在"};val s=requireNotNull(series[r.text("seriesId")]){"實例系列不存在"}
        val rule=requireNotNull(rules[r.text("ruleId")]){"實例規則不存在"}
        require(rule["seriesId"]==s["id"] && r.int("sequence") in 1..s.int("cursor") && r.int("sequence")>=rule.int("effectiveFrom") && task["dueDate"]!=null){"實例序號／游標／規則不一致"}
        if (s["deletedAt"] != null) require(task["deletedAt"] != null) { "已刪系列含有效任務" }
        r.optionalLong("deletedWithSeriesAt")?.let { marker ->
            require(task["deletedAt"] == marker && s["deletedAt"] == marker) { "系列垃圾桶關聯不一致" }
        }
        require(slots.add(r.text("seriesId") to r.int("sequence"))){"系列次數重複"}
        r.optionalText("snapshot")?.let { json ->
            require(json.length<=1_000_000);val snap=strictJson(json) as? Map<*,*> ?: error("歷史快照格式錯誤")
            require(snap.keys == setOf("title","notes","category","tags","status","late") || snap.keys == setOf("title","notes","category","tags","status","late","imageIds"))
            require(snap["imageIds"] == null || snap["imageIds"] is String)
            decodeImageIds((snap["imageIds"] as String?).orEmpty())
            require(snap["title"] is String && snap["notes"] is String && snap["category"] is String && snap["status"] in TaskStatus.entries.map { it.name })
            require(snap["late"]==null || snap["late"] is Boolean)
            require((snap["tags"] as? List<*>)?.all { it is String }==true)
        }
    }
    val refs=g.rows("occurrences").associateBy { it.text("taskId") }
    g.rows("reminder_overrides").forEach { require(it.text("taskId") in refs){"單次提醒覆寫缺少實例"} }
    g.rows("reminders").forEach { r ->
        val t=requireNotNull(tasks[r.text("taskId")]){"提醒任務不存在"}
        require(r.text("state") in listOf("PENDING","BLOCKED","DELIVERED","MISSED","CANCELED")){"提醒狀態無效"}
        val spec=ReminderSpec(r.text("id"),ReminderKind.valueOf(r.text("kind")),r.text("zoneId"),r.optionalLong("fixedAt"),r.optionalLong("offsetMinutes")?.let(Math::toIntExact),LocalTime.parse(r.text("baseTime")))
        resolveReminder(spec,t.optionalText("dueDate")?.let(LocalDate::parse),t.optionalText("dueTime")?.let(LocalTime::parse),t.optionalText("zoneId"))
    }
    g.rows("reminders").groupBy { it.text("taskId") }.values.forEach { require(it.count { r -> r["enabled"]==true }<=32) }
    g.rows("series_reminders").forEach { r ->
        require(r.text("seriesId") in series){"提醒模板的系列不存在"}
        validateSeriesReminder(SeriesReminderSpec(r.text("id"),SeriesReminderKind.valueOf(r.text("kind")),r.optionalLong("offsetMinutes")?.let(Math::toIntExact),
            r.optionalLong("dayOffset")?.let(Math::toIntExact),r.optionalText("time")?.let(LocalTime::parse),LocalTime.parse(r.text("baseTime"))))
    }
    g.rows("series_reminders").groupBy { it.text("seriesId") }.values.forEach { require(it.count { r -> r["enabled"]==true }<=32) }
    listOf("categories", "tags").forEach { table -> g.rows(table).forEach { r ->
        r.optionalLong("sortOrder")?.let { require(it in 0..Int.MAX_VALUE.toLong()) { "排序超出範圍" } }
    } }
    require(g.rows("settings").size <= 1) { "一般設定重複" }
    g.rows("settings").forEach { r ->
        require(r.text("id") == "general") { "一般設定識別碼無效" }
        r.optionalLong("completedRetentionDays")?.let { require(it in 1..Int.MAX_VALUE.toLong()) { "保留期限無效" } }
    }
    val independent=g.rows("reminder_overrides").map { it.text("taskId") }.toSet()
    val templateIds=g.rows("series_reminders").groupBy { it.text("seriesId") }.mapValues { (_,rows)->rows.map { it.text("id") } }
    g.rows("reminders").filter { it["enabled"]==true }.forEach { r -> refs[r.text("taskId")]?.let { ref ->
        if(r.text("taskId") !in independent)require(templateIds[ref.text("seriesId")].orEmpty().any { occurrenceReminderId(r.text("taskId"),it)==r.text("id") }){"系列提醒缺少模板或明確的單次覆寫"}
    } }

}
