package com.chimafok.todo.data

import java.security.MessageDigest

typealias BackupRow = Map<String, Any?>
data class Field(val name: String, val type: String, val required: Boolean)
data class TableSpec(val fields: List<Field>, val keys: List<String>)
val legacyBackupTables = linkedMapOf(
    "tasks" to TableSpec(listOf(Field("id", "TEXT", true), Field("title", "TEXT", true), Field("notes", "TEXT", true), Field("categoryId", "TEXT", true), Field("dueDate", "TEXT", false), Field("dueTime", "TEXT", false), Field("zoneId", "TEXT", false), Field("status", "TEXT", true), Field("completedAt", "INTEGER", false), Field("createdAt", "INTEGER", true), Field("updatedAt", "INTEGER", true)), listOf("id")),
    "categories" to TableSpec(listOf(Field("id", "TEXT", true), Field("name", "TEXT", true), Field("systemKind", "TEXT", false), Field("createdAt", "INTEGER", true), Field("updatedAt", "INTEGER", true)), listOf("id")),
    "tags" to TableSpec(listOf(Field("id", "TEXT", true), Field("name", "TEXT", true), Field("createdAt", "INTEGER", true), Field("updatedAt", "INTEGER", true)), listOf("id")),
    "task_tags" to TableSpec(listOf(Field("taskId", "TEXT", true), Field("tagId", "TEXT", true)), listOf("taskId", "tagId")),
    "recurring_series" to TableSpec(listOf(Field("id", "TEXT", true), Field("title", "TEXT", true), Field("notes", "TEXT", true), Field("categoryId", "TEXT", true), Field("lifecycle", "TEXT", true), Field("cursor", "INTEGER", true), Field("createdAt", "INTEGER", true), Field("updatedAt", "INTEGER", true)), listOf("id")),
    "series_rules" to TableSpec(listOf(Field("id", "TEXT", true), Field("seriesId", "TEXT", true), Field("version", "INTEGER", true), Field("effectiveFrom", "INTEGER", true), Field("anchor", "TEXT", true), Field("frequency", "TEXT", true), Field("intervalDays", "INTEGER", true), Field("weekdayMask", "INTEGER", true), Field("dueTime", "TEXT", false), Field("zoneId", "TEXT", true), Field("endDate", "TEXT", false), Field("maxCount", "INTEGER", false), Field("createdAt", "INTEGER", true)), listOf("id")),
    "series_tags" to TableSpec(listOf(Field("seriesId", "TEXT", true), Field("tagId", "TEXT", true)), listOf("seriesId", "tagId")),
    "occurrences" to TableSpec(listOf(Field("taskId", "TEXT", true), Field("seriesId", "TEXT", true), Field("sequence", "INTEGER", true), Field("ruleId", "TEXT", true), Field("snapshot", "TEXT", false), Field("excludedByRule", "INTEGER", true)), listOf("taskId")),
    "reminders" to TableSpec(listOf(Field("id", "TEXT", true), Field("taskId", "TEXT", true), Field("kind", "TEXT", true), Field("zoneId", "TEXT", true), Field("fixedAt", "INTEGER", false), Field("offsetMinutes", "INTEGER", false), Field("baseTime", "TEXT", true), Field("enabled", "INTEGER", true), Field("state", "TEXT", true), Field("resolvedAt", "INTEGER", false), Field("createdAt", "INTEGER", true), Field("updatedAt", "INTEGER", true)), listOf("id")),
    "series_reminders" to TableSpec(listOf(Field("id", "TEXT", true), Field("seriesId", "TEXT", true), Field("kind", "TEXT", true), Field("offsetMinutes", "INTEGER", false), Field("dayOffset", "INTEGER", false), Field("time", "TEXT", false), Field("baseTime", "TEXT", true), Field("enabled", "INTEGER", true), Field("createdAt", "INTEGER", true), Field("updatedAt", "INTEGER", true)), listOf("id")),
    "reminder_overrides" to TableSpec(listOf(Field("taskId", "TEXT", true)), listOf("taskId")),
)
val backupTables = linkedMapOf<String, TableSpec>().apply {
    putAll(legacyBackupTables)
    fun extend(table: String, vararg fields: Field) { val old = getValue(table); put(table, old.copy(fields = old.fields + fields)) }
    extend("tasks", Field("startDate", "TEXT", false), Field("startTime", "TEXT", false), Field("startZoneId", "TEXT", false), Field("deletedAt", "INTEGER", false))
    extend("categories", Field("sortOrder", "INTEGER", false))
    extend("tags", Field("sortOrder", "INTEGER", false))
    extend("recurring_series", Field("deletedAt", "INTEGER", false), Field("previousLifecycle", "TEXT", false))
    extend("series_rules", Field("startDayOffset", "INTEGER", false), Field("startTime", "TEXT", false), Field("startZoneId", "TEXT", false))
    extend("occurrences", Field("deletedWithSeriesAt", "INTEGER", false))
    put("settings", TableSpec(listOf(Field("id", "TEXT", true), Field("completedRetentionDays", "INTEGER", false), Field("updatedAt", "INTEGER", true)), listOf("id")))
}
val backupBooleans = setOf("enabled", "excludedByRule")
fun BackupRow.text(key: String) = this[key] as String
fun BackupRow.long(key: String) = (this[key] as Number).toLong()
fun BackupRow.int(key: String) = Math.toIntExact(long(key))
fun BackupRow.optionalText(key: String) = this[key] as String?
fun BackupRow.optionalLong(key: String) = (this[key] as Number?)?.toLong()
fun rowKey(table: String, row: BackupRow) = canonical(backupTables.getValue(table).keys.map { row[it] })

/** Stable primitive-only representation. Platform alarm tokens are excluded; declared settings use a separate portable row. */
fun canonical(value: Any?): String = when (value) {
    null -> "null"
    is String -> buildString {
        append(34.toChar())
        value.forEach { c -> when {
            c.code == 34 || c.code == 92 -> { append(92.toChar()); append(c) }
            c.code < 32 -> { append(92.toChar()); append('u'); append(c.code.toString(16).padStart(4,'0')) }
            else -> append(c)
        } }
        append(34.toChar())
    }
    is Number, is Boolean -> value.toString()
    is Map<*,*> -> value.entries.sortedBy { it.key as String }.joinToString(",", "{", "}") { canonical(it.key) + ":" + canonical(it.value) }
    is List<*> -> value.joinToString(",", "[", "]") { canonical(it) }
    else -> error("Unsupported backup value")
}
fun backupBody(row:BackupRow)=row.filterKeys { it !in setOf("createdAt","updatedAt") }
fun backupHash(value: Any?) = MessageDigest.getInstance("SHA-256").digest(canonical(value).toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 255) }

data class BackupGraph(val tables: Map<String, List<BackupRow>>) {
    fun rows(table: String) = tables[table].orEmpty()
    fun portable(): BackupGraph {
        return BackupGraph(backupTables.mapValues { (table,spec) -> rows(table).map { row -> spec.fields.associate { it.name to row[it.name] } } })
    }
    fun normalized() = backupTables.keys.associateWith { t -> rows(t).sortedBy { rowKey(t,it) } }
    fun content()=semantic().mapValues { (_,rows)->rows.map(::backupBody) }
    fun semantic(): Map<String,List<BackupRow>> = backupTables.mapValues { (t,spec) -> rows(t).map { row ->
        spec.fields.filterNot { it.name in setOf("state","resolvedAt") }.associate { it.name to row[it.name] } +
            if(t in setOf("tasks","recurring_series")) mapOf("deletedAt" to row["deletedAt"]) else emptyMap()
    }.sortedBy { rowKey(t,it) } }
}

enum class RestoreMode { MERGE, REPLACE }
enum class BackupChoice { LOCAL, FILE }
data class BackupConflict(val id: String, val label: String, val reason: String, val local: BackupGraph, val incoming: BackupGraph)
data class BackupPlan(val graph: BackupGraph, val conflicts: List<BackupConflict>, val added: Int, val changed: Int,
    val removed: Int, val sameNames: Int, val localTaskCount: Int, val fileTaskCount: Int)

/** Own a whole series, including its slots and reminder definitions, as one merge aggregate. */
fun planBackup(local: BackupGraph, file: BackupGraph, mode: RestoreMode, choices: Map<String,BackupChoice> = emptyMap()): BackupPlan {
    // Format 1 had no ordering or preferences. Preserve those unknown values instead of clearing them.
    val incoming = BackupGraph(file.tables.mapValues { (table, rows) ->
        if (table in listOf("categories", "tags")) {
            val known = local.rows(table).associateBy { it.text("id") }
            var next = (local.rows(table).mapNotNull { (it["sortOrder"] as Number?)?.toLong() }.maxOrNull() ?: -1L) + 1
            rows.map { r -> if (r["sortOrder"] != null) r else r + ("sortOrder" to (known[r.text("id")]?.get("sortOrder") ?: next++)) }
        } else rows
    } + ("settings" to file.rows("settings").ifEmpty { local.rows("settings") }))
    if(mode==RestoreMode.REPLACE) return planCounts(local,incoming,emptyList(),incoming)
    fun units(graph: BackupGraph): Map<String,BackupGraph> {
        val refs=graph.rows("occurrences").associate { it.text("taskId") to it.text("seriesId") }
        fun task(id:String) = refs[id]?.let { "S/$it" } ?: "T/$id"
        val rows=linkedMapOf<String,MutableMap<String,MutableList<BackupRow>>>()
        backupTables.keys.forEach { t -> graph.rows(t).forEach { row ->
            val id=when(t) {
                "settings" -> "P/${row.text("id")}"
                "categories" -> "C/${row.text("id")}"
                "tags" -> "G/${row.text("id")}"
                "recurring_series" -> "S/${row.text("id")}"
                "series_rules", "series_tags", "series_reminders", "occurrences" -> "S/${row.text("seriesId")}"
                "tasks" -> task(row.text("id"))
                else -> task(row.text("taskId"))
            }
            rows.getOrPut(id){linkedMapOf()}.getOrPut(t){mutableListOf()}.add(row)
        } }
        return rows.mapValues { BackupGraph(it.value) }
    }
    val a=units(local);val b=units(incoming);val ids=(a.keys+b.keys).sorted();val parent=ids.associateWith { it }.toMutableMap()
    fun root(key:String):String { var x=key;while(parent.getValue(x)!=x)x=parent.getValue(x);var n=key;while(parent.getValue(n)!=n){val next=parent.getValue(n);parent[n]=x;n=next};return x }
    val owner=mutableMapOf<String,String>()
    // Connect conversions or reused IDs across both sides before making any independent choice.
    (a.entries+b.entries).forEach { (id,g) -> backupTables.keys.forEach { t -> g.rows(t).forEach { row ->
        val resource="$t/${rowKey(t,row)}";val old=owner.putIfAbsent(resource,id)
        if(old!=null){val x=root(old);val y=root(id);if(x!=y)parent[maxOf(x,y)]=minOf(x,y)}
    } } }
    fun combine(units:List<BackupGraph>)=BackupGraph(backupTables.keys.associateWith { t -> units.flatMap { it.rows(t) } })
    fun display(g:BackupGraph,full:BackupGraph):BackupGraph {
        val categories=(g.rows("tasks")+g.rows("recurring_series")).map { it.text("categoryId") }.toSet()+g.rows("categories").map { it.text("id") }
        val tags=(g.rows("task_tags")+g.rows("series_tags")).map { it.text("tagId") }.toSet()+g.rows("tags").map { it.text("id") }
        return BackupGraph(g.tables+mapOf("categories" to full.rows("categories").filter { it.text("id") in categories },"tags" to full.rows("tags").filter { it.text("id") in tags }))
    }
    val conflicts=mutableListOf<BackupConflict>();val selected=mutableListOf<BackupGraph>()
    ids.groupBy(::root).toSortedMap().forEach { (key,members) ->
        val x=combine(members.mapNotNull(a::get));val y=combine(members.mapNotNull(b::get))
        if(members.none { it in b })selected+=x
        else if(members.none { it in a })selected+=y
        else if(x.content()==y.content())selected+=x
        else {
            val trash=(x.rows("tasks")+y.rows("tasks")+x.rows("recurring_series")+y.rows("recurring_series")).any { it["deletedAt"]!=null }
            val preferences=x.rows("settings").isNotEmpty() || y.rows("settings").isNotEmpty()
            val sx=x.semantic();val sy=y.semantic();val directions=mutableSetOf<Int>();var missing=false
            backupTables.keys.forEach { t ->
                val mx=sx.getValue(t).associateBy { rowKey(t,it) };val my=sy.getValue(t).associateBy { rowKey(t,it) }
                if(mx.keys!=my.keys)missing=true
                (mx.keys intersect my.keys).forEach { id -> val old=mx.getValue(id);val file=my.getValue(id)
                    if(backupBody(old)!=backupBody(file)){val u=(old["updatedAt"] ?: old["createdAt"]) as Number?;val v=(file["updatedAt"] ?: file["createdAt"]) as Number?
                        directions+=if(u==null || v==null || u.toLong()<=0 || v.toLong()<=0)0 else v.toLong().compareTo(u.toLong())}
                }
            }
            val structural=(missing && (x.rows("recurring_series").isNotEmpty() || y.rows("recurring_series").isNotEmpty())) || x.rows("tasks").map { it.text("id") }.toSet()!=y.rows("tasks").map { it.text("id") }.toSet()
            if(!structural && x.rows("tasks").size==1 && y.rows("tasks").size==1){
                val u=x.rows("tasks").single().long("updatedAt");val v=y.rows("tasks").single().long("updatedAt")
                directions+=if(u<=0 || v<=0)0 else v.compareTo(u)
            }

            val automatic=if(!preferences && !trash && !structural && directions.size==1) when(directions.single()){1->BackupChoice.FILE;-1->BackupChoice.LOCAL;else->null} else null
            if(automatic!=null)selected+=if(automatic==BackupChoice.FILE)y else x
            else {
                val label=(x.rows("tasks")+y.rows("tasks")).map { it.text("title") }.distinct().take(2).joinToString("、").ifEmpty {
                    (x.rows("categories")+y.rows("categories")+x.rows("tags")+y.rows("tags")).firstOrNull()?.text("name") ?: if(preferences) "一般設定：完成紀錄保留期限" else "重複系列"
                }
                conflicts+=BackupConflict(key,label,if(preferences)"保留期限不同，請明確選擇一般設定。" else if(trash)"本機或檔案含垃圾桶紀錄，請明確選擇保留的狀態。" else if(missing)"系列或關聯內容不同，需整組選擇以保留資料一致。" else "修改時間相同、未知或各部分較新的方向不同。",display(x,local),display(y,incoming))
                selected+=if(choices[key]==BackupChoice.FILE)y else x
            }
        }
    }
    return planCounts(local,incoming,conflicts,combine(selected))
}
private fun planCounts(local:BackupGraph,incoming:BackupGraph,conflicts:List<BackupConflict>,result:BackupGraph):BackupPlan {
    val a=local.rows("tasks").associateBy { it.text("id") };val b=result.rows("tasks").associateBy { it.text("id") }
    fun signatures(g:BackupGraph):Map<String,Map<String,List<BackupRow>>> {
        val entries=mutableMapOf<String,MutableMap<String,MutableList<BackupRow>>>()
        g.content().forEach { (t,rows)->if(t in listOf("tasks","task_tags","reminders","reminder_overrides","occurrences"))rows.forEach { row->
            val id=if(t=="tasks")row.text("id") else row.text("taskId")
            entries.getOrPut(id){mutableMapOf()}.getOrPut(t){mutableListOf()}.add(row)
        } };return entries
    }
    val sa=signatures(local);val sb=signatures(result)
    val sameNames=listOf("categories","tags").sumOf { table -> result.rows(table).groupBy { it.text("name").trim().lowercase(java.util.Locale.ROOT) }.values.count { it.size>1 } }
    return BackupPlan(result,conflicts,(b.keys-a.keys).size,(a.keys intersect b.keys).count { sa[it]!=sb[it] },(a.keys-b.keys).size,sameNames,a.size,incoming.rows("tasks").size)
}
