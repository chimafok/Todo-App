package com.chimafok.todo.data

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
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
            fun account(n:Int){if(n>0){count+=n;require(count<=BACKUP_MAX_BYTES){tr(R.string.text_backups_cannot_exceed_mib_d3bf96)}}}
            override fun read():Int=super.read().also { if(it>=0)account(1) }
            override fun read(b:ByteArray,off:Int,len:Int):Int=`in`.read(b,off,len).also(::account)
        }
        return fromRoot(strictJsonReader(java.io.InputStreamReader(bounded,decoder)))
    }

    fun encode(graph: BackupGraph, now: Long, appVersion: String): String {
        val portable=graph.portable();validateBackup(portable,false)
        return canonical(mapOf("format" to "Todo-App", "formatVersion" to 6, "schemaVersion" to 10,
            "exportedAt" to now,"appVersion" to appVersion,"data" to portable.normalized()))
    }
    fun decode(text: String): BackupDocument {
        require(text.toByteArray(Charsets.UTF_8).size<=BACKUP_MAX_BYTES){tr(R.string.text_backups_cannot_exceed_mib_d3bf96)}
        return fromRoot(strictJson(text))
    }
    private fun fromRoot(value:Any?):BackupDocument {
        val root=value as? Map<*,*> ?: error(tr(R.string.text_the_backup_must_be_a_json_728a14))
        require(root.keys==setOf("format","formatVersion","schemaVersion","exportedAt","appVersion","data")){tr(R.string.text_incomplete_backup_fields_or_unsupported_version_4bccd7)}
        val legacy = root["formatVersion"] == 1L && root["schemaVersion"] == 7L
        val images = root["formatVersion"] in listOf(4L, 5L) && root["schemaVersion"] == 9L
        val current = root["formatVersion"] == 6L && root["schemaVersion"] == 10L
        require(root["format"]=="Todo-App" && (current || images || legacy || (root["formatVersion"] in listOf(2L, 3L) && root["schemaVersion"]==8L))){tr(R.string.text_unsupported_backup_format_or_schema_supports_c94239)}
        val inputTables = if (legacy) legacyBackupTables else if (current) backupTables else if (images) imageBackupTables else previousBackupTables
        val exported=root["exportedAt"] as? Long ?: error(tr(R.string.text_missing_export_time_728e18))
        val version=root["appVersion"] as? String ?: error(tr(R.string.text_missing_app_version_82e274));require(version.length in 1..80)
        val raw=root["data"] as? Map<*,*> ?: error(tr(R.string.text_missing_backup_data_fb76fc))
        require(raw.keys==inputTables.keys){tr(R.string.text_incomplete_backup_tables_or_unsupported_content_af3a4a)}
        var count=0
        val parsed=BackupGraph(inputTables.mapValues { (table,spec) ->
            val rows=raw[table] as? List<*> ?: error(tr(R.string.text_expected_an_array_s_daaae5, table))
            count+=rows.size;require(count<=BACKUP_MAX_ROWS){tr(R.string.text_backups_support_at_most_rows_3208af)}
            rows.map { value ->
                val row=value as? Map<*,*> ?: error(tr(R.string.text_invalid_row_format_s_d2cbee, table))
                require(row.keys.all { k -> spec.fields.any { it.name==k } }){tr(R.string.text_unsupported_backup_field_s_1f0339, table)}
                spec.fields.associate { f ->
                    val v=if(f.name=="updatedAt" && row[f.name]==null)0L else row[f.name]
                    require(v!=null || !f.required){tr(R.string.text_missing_required_field_s_s_df56dd, table, f.name)}
                    if(v!=null)require(when { f.name in backupBooleans -> v is Boolean;f.type=="INTEGER" -> v is Long;else->v is String }){tr(R.string.text_incorrect_field_type_s_s_b9b599, table, f.name)}
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
        }) { tr(R.string.text_in_progress_study_tasks_require_backup_226dec) }
        if (!current && !images) require(referencedImageIds(canonical).isEmpty()) { tr(R.string.text_image_attachments_require_backup_format_730ad2) }
        if (current) require(canonical.rows("settings").all { it["language"] != null }) { tr(R.string.text_the_backup_is_missing_its_language_f982ae) }
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
            var i=0;while(i<s.length){val c=s[i];if(c.isHighSurrogate()){require(i+1<s.length && s[i+1].isLowSurrogate()){ tr(R.string.text_invalid_unicode_character_bcbe68) };i+=2}
                else {require(!c.isLowSurrogate()){ tr(R.string.text_invalid_unicode_character_bcbe68) };i++}}
        }
        fun value(depth:Int,rowArray:Boolean=false):Any? {
            require(depth<=12 && ++values<=5_000_000){tr(R.string.text_backup_content_is_too_deeply_nested_46858c)}
            return when(reader.peek()) {
                JsonToken.BEGIN_OBJECT -> {reader.beginObject();val map=linkedMapOf<String,Any?>()
                    while(reader.hasNext()){val name=reader.nextName();require(name !in map){tr(R.string.text_duplicate_json_field_s_b9bde4, name.take(80))};map[name]=value(depth+1,depth==1 && name in backupTables)}
                    reader.endObject();map}
                JsonToken.BEGIN_ARRAY -> {reader.beginArray();val list=mutableListOf<Any?>();while(reader.hasNext()){if(rowArray)require(++rows<=BACKUP_MAX_ROWS){tr(R.string.text_too_many_backup_rows_26242e)};list+=value(depth+1)};reader.endArray();list}
                JsonToken.STRING -> string()
                JsonToken.BOOLEAN -> reader.nextBoolean()
                JsonToken.NULL -> {reader.nextNull();null}
                JsonToken.NUMBER -> {val n=reader.nextString();require(n.matches(Regex("-?(0|[1-9][0-9]*)"))){tr(R.string.text_numbers_must_be_integers_fbe6e9)};requireNotNull(n.toLongOrNull()){ tr(R.string.text_number_out_of_range_c7e106) }}
                else -> error(tr(R.string.text_invalid_json_format_1019f3))
            }
        }
        val result=value(0);require(reader.peek()==JsonToken.END_DOCUMENT){tr(R.string.text_unexpected_trailing_backup_content_a8d621)};return result
    }
}
private fun canonicalSystems(graph:BackupGraph):BackupGraph {
    val remap=mutableMapOf<String,String>()
    listOf(SystemCategory.UNCATEGORIZED to UNCATEGORIZED_ID,SystemCategory.ASSIGNMENTS to ASSIGNMENTS_ID).forEach { (kind,id) ->
        val rows=graph.rows("categories").filter { it["systemKind"]==kind.name };require(rows.size==1){tr(R.string.text_the_backup_must_contain_unique_system_99f6ea)}
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
        val rows=g.rows(t);size+=rows.size;require(size<=BACKUP_MAX_ROWS){tr(R.string.text_too_much_backup_data_1b6c72)}
        require(rows.map { rowKey(t,it) }.distinct().size==rows.size){tr(R.string.text_duplicate_id_or_relationship_s_b1b152, t)}
        rows.forEach { r -> if(!allowTrash)require(r.keys.all { key -> spec.fields.any { it.name==key } }){ tr(R.string.text_unsupported_backup_field_45a7f1) };spec.keys.forEach { key -> val id=r.text(key);require(id.isNotBlank() && id.length<=200 && id.none { it.code<32 }){tr(R.string.text_invalid_id_8fd808)} }
            listOf("createdAt","updatedAt").forEach { k -> (r[k] as Number?)?.toLong()?.let(Instant::ofEpochMilli) }
        }
    }
    fun idMap(t:String)=g.rows(t).associateBy { it.text("id") }
    fun name(r:BackupRow){val n=r.text("name");require(n==n.trim() && n.length in 1..50){tr(R.string.text_category_and_tag_names_must_contain_ce6911)}}
    val categories=idMap("categories");val tags=idMap("tags");val tasks=idMap("tasks");val series=idMap("recurring_series");val rules=idMap("series_rules")
    val images = idMap("note_images")
    images.values.forEach { validateImageData(it.text("id"), it.text("data")) }
    require(referencedImageIds(g).all { it in images }) { tr(R.string.text_missing_image_attachments_in_the_backup_135c59) }
    val rulesBySeries=rules.values.groupBy { it.text("seriesId") }
    g.rows("categories").forEach(::name);g.rows("tags").forEach(::name)
    val systems=mapOf(UNCATEGORIZED_ID to SystemCategory.UNCATEGORIZED.name,ASSIGNMENTS_ID to SystemCategory.ASSIGNMENTS.name)
    systems.forEach { (id,kind) -> require(categories[id]?.get("systemKind")==kind && g.rows("categories").count { it["systemKind"]==kind }==1){tr(R.string.text_missing_system_category_or_incorrect_id_37fb1e)} }
    require(categories.getValue(UNCATEGORIZED_ID).text("name")=="未分類"){tr(R.string.text_the_uncategorized_system_category_cannot_be_38056e)}
    categories.values.forEach { r ->require(r["systemKind"]==null || systems[r.text("id")]==r["systemKind"]){tr(R.string.text_invalid_system_category_b56009)} }
    tasks.values.forEach { r ->
        val c=requireNotNull(categories[r.text("categoryId")]){tr(R.string.text_the_task_category_does_not_exist_5721a9)}
        val date=r.optionalText("dueDate")?.let(LocalDate::parse);val time=r.optionalText("dueTime")?.let(LocalTime::parse)
        require(date==null || date.year in 1..9999){tr(R.string.text_due_date_out_of_range_b9860b)}
        val status=TaskStatus.valueOf(r.text("status"));validateDraft(TaskDraft(r.text("id"),r.text("title"),r.text("notes"),r.text("categoryId"),date,time,r.optionalText("zoneId"),status),c["systemKind"]==SystemCategory.ASSIGNMENTS.name)
        require(r.text("title")==validatedTitle(r.text("title")))
        require(status==TaskStatus.COMPLETED || r["completedAt"]==null){tr(R.string.text_an_incomplete_task_cannot_have_a_9b3def)}
        r.optionalLong("completedAt")?.let(Instant::ofEpochMilli)
        validateDraft(TaskDraft(r.text("id"),r.text("title"),startDate=r.optionalText("startDate")?.let(LocalDate::parse),
            startTime=r.optionalText("startTime")?.let(LocalTime::parse),startZoneId=r.optionalText("startZoneId")), false)
        r.optionalLong("deletedAt")?.let(Instant::ofEpochMilli)
    }
    series.values.forEach { r ->
        require(categories.containsKey(r.text("categoryId")) && r.int("cursor") in 0..10_000_000){tr(R.string.text_invalid_series_category_or_cursor_ab55f0)}
        require(r.text("title")==validatedTitle(r.text("title")) && r.text("notes").length<=20_000)
        val life=SeriesLifecycle.valueOf(r.text("lifecycle"))
        require((r["deletedAt"] != null) == (life == SeriesLifecycle.TRASHED)) { tr(R.string.text_inconsistent_series_deletion_state_d63474) }
        r.optionalLong("deletedAt")?.let(Instant::ofEpochMilli)
        val previous=r.optionalText("previousLifecycle")?.let(SeriesLifecycle::valueOf)
        require(if(life==SeriesLifecycle.TRASHED) previous!=null && previous!=SeriesLifecycle.TRASHED else previous==null) { tr(R.string.text_incomplete_series_restoration_state_c3f9ce) }
        val rr=rulesBySeries[r.text("id")].orEmpty();require(rr.isNotEmpty() && rr.map { it.int("version") }.distinct().size==rr.size){tr(R.string.text_missing_series_rules_or_duplicate_versions_1d1970)}
        val ordered=rr.sortedBy { it.int("version") };require(ordered.first().int("effectiveFrom")==1 && ordered.last().int("effectiveFrom").toLong()<=r.long("cursor")+1){tr(R.string.text_the_series_cursor_precedes_the_current_4e9b82)}
        require(ordered.zipWithNext().all { (a,b) -> a.int("effectiveFrom")<=b.int("effectiveFrom") }){tr(R.string.text_inconsistent_series_rule_order_a2e071)}
    }
    rules.values.forEach { r ->
        require(series.containsKey(r.text("seriesId")) && r.int("version") in 1 until Int.MAX_VALUE && r.int("effectiveFrom") in 1..10_000_001){tr(R.string.text_invalid_series_rule_relationship_ed19a9)}
        val max=r.optionalLong("maxCount")?.let(Math::toIntExact);require(max==null || max>=r.int("effectiveFrom"))
        val mask=r.int("weekdayMask");require(mask in 0..127)
        RecurrenceRule(LocalDate.parse(r.text("anchor")),RecurrenceFrequency.valueOf(r.text("frequency")),r.text("zoneId"),r.int("intervalDays"),
            DayOfWeek.entries.filter { mask and (1 shl(it.value-1))!=0 }.toSet(),r.optionalText("endDate")?.let(LocalDate::parse),max?.let { it-r.int("effectiveFrom")+1 })
        r.optionalText("dueTime")?.let(LocalTime::parse)
        val offset=r.optionalLong("startDayOffset")?.let(Math::toIntExact)
        require(offset == null || offset in -3_652_058..3_652_058) { tr(R.string.text_start_date_offset_out_of_range_79d244) }
        require(r["startTime"] == null || offset != null) { tr(R.string.text_the_start_time_requires_a_date_4759e7) }
        require(offset != null || r["startZoneId"] == null) { tr(R.string.text_the_start_time_zone_requires_a_643640) }
        r.optionalText("startZoneId")?.let(ZoneId::of)
        r.optionalText("startTime")?.let { LocalTime.parse(it);require(r["startZoneId"]!=null) { tr(R.string.text_the_start_time_requires_a_time_9b40e3) } }
    }
    g.rows("task_tags").forEach { require(it.text("taskId") in tasks && it.text("tagId") in tags){tr(R.string.text_the_task_tag_relationship_does_not_d30adb)} }
    g.rows("series_tags").forEach { require(it.text("seriesId") in series && it.text("tagId") in tags){tr(R.string.text_the_series_tag_relationship_does_not_02e968)} }
    val slots=mutableSetOf<Pair<String,Int>>()
    g.rows("occurrences").forEach { r ->
        val task=requireNotNull(tasks[r.text("taskId")]){tr(R.string.text_the_occurrence_task_does_not_exist_83553d)};val s=requireNotNull(series[r.text("seriesId")]){tr(R.string.text_the_occurrence_series_does_not_exist_476836)}
        val rule=requireNotNull(rules[r.text("ruleId")]){tr(R.string.text_the_occurrence_rule_does_not_exist_0ffe68)}
        require(rule["seriesId"]==s["id"] && r.int("sequence") in 1..s.int("cursor") && r.int("sequence")>=rule.int("effectiveFrom") && task["dueDate"]!=null){tr(R.string.text_inconsistent_occurrence_sequence_cursor_or_rule_5c8fe0)}
        if (s["deletedAt"] != null) require(task["deletedAt"] != null) { tr(R.string.text_a_deleted_series_contains_active_tasks_305980) }
        r.optionalLong("deletedWithSeriesAt")?.let { marker ->
            require(task["deletedAt"] == marker && s["deletedAt"] == marker) { tr(R.string.text_inconsistent_series_trash_relationship_328b90) }
        }
        require(slots.add(r.text("seriesId") to r.int("sequence"))){tr(R.string.text_duplicate_series_occurrence_cb41d5)}
        r.optionalText("snapshot")?.let { json ->
            require(json.length<=1_000_000);val snap=strictJson(json) as? Map<*,*> ?: error(tr(R.string.text_invalid_history_snapshot_format_e1d788))
            require(snap.keys == setOf("title","notes","category","tags","status","late") || snap.keys == setOf("title","notes","category","tags","status","late","imageIds"))
            require(snap["imageIds"] == null || snap["imageIds"] is String)
            decodeImageIds((snap["imageIds"] as String?).orEmpty())
            require(snap["title"] is String && snap["notes"] is String && snap["category"] is String && snap["status"] in TaskStatus.entries.map { it.name })
            require(snap["late"]==null || snap["late"] is Boolean)
            require((snap["tags"] as? List<*>)?.all { it is String }==true)
        }
    }
    val refs=g.rows("occurrences").associateBy { it.text("taskId") }
    g.rows("reminder_overrides").forEach { require(it.text("taskId") in refs){tr(R.string.text_a_reminder_override_requires_an_occurrence_c11df0)} }
    g.rows("reminders").forEach { r ->
        val t=requireNotNull(tasks[r.text("taskId")]){tr(R.string.text_the_reminder_task_does_not_exist_83df17)}
        require(r.text("state") in listOf("PENDING","BLOCKED","DELIVERED","MISSED","CANCELED")){tr(R.string.text_invalid_reminder_state_6e0870)}
        val spec=ReminderSpec(r.text("id"),ReminderKind.valueOf(r.text("kind")),r.text("zoneId"),r.optionalLong("fixedAt"),r.optionalLong("offsetMinutes")?.let(Math::toIntExact),LocalTime.parse(r.text("baseTime")))
        resolveReminder(spec,t.optionalText("dueDate")?.let(LocalDate::parse),t.optionalText("dueTime")?.let(LocalTime::parse),t.optionalText("zoneId"))
    }
    g.rows("reminders").groupBy { it.text("taskId") }.values.forEach { require(it.count { r -> r["enabled"]==true }<=32) }
    g.rows("series_reminders").forEach { r ->
        require(r.text("seriesId") in series){tr(R.string.text_the_reminder_template_series_does_not_c8499d)}
        validateSeriesReminder(SeriesReminderSpec(r.text("id"),SeriesReminderKind.valueOf(r.text("kind")),r.optionalLong("offsetMinutes")?.let(Math::toIntExact),
            r.optionalLong("dayOffset")?.let(Math::toIntExact),r.optionalText("time")?.let(LocalTime::parse),LocalTime.parse(r.text("baseTime"))))
    }
    g.rows("series_reminders").groupBy { it.text("seriesId") }.values.forEach { require(it.count { r -> r["enabled"]==true }<=32) }
    listOf("categories", "tags").forEach { table -> g.rows(table).forEach { r ->
        r.optionalLong("sortOrder")?.let { require(it in 0..Int.MAX_VALUE.toLong()) { tr(R.string.text_sort_order_out_of_range_b7cfac) } }
    } }
    require(g.rows("settings").size <= 1) { tr(R.string.text_duplicate_general_settings_ef6dcc) }
    g.rows("settings").forEach { r ->
        require(r["language"] == null || r["language"] in com.chimafok.todo.i18n.LanguageChoice.entries.map { it.name }) { tr(R.string.text_invalid_language_setting_8f7e33) }
        require(r.text("id") == "general") { tr(R.string.text_invalid_general_settings_id_dcf577) }
        r.optionalLong("completedRetentionDays")?.let { require(it in 1..Int.MAX_VALUE.toLong()) { tr(R.string.text_invalid_retention_period_8bd248) } }
    }
    val independent=g.rows("reminder_overrides").map { it.text("taskId") }.toSet()
    val templateIds=g.rows("series_reminders").groupBy { it.text("seriesId") }.mapValues { (_,rows)->rows.map { it.text("id") } }
    g.rows("reminders").filter { it["enabled"]==true }.forEach { r -> refs[r.text("taskId")]?.let { ref ->
        if(r.text("taskId") !in independent)require(templateIds[ref.text("seriesId")].orEmpty().any { occurrenceReminderId(r.text("taskId"),it)==r.text("id") }){tr(R.string.text_a_series_reminder_requires_a_template_3168dc)}
    } }

}
