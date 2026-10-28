package com.chimafok.todo.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.chimafok.todo.domain.*
import java.time.*
import java.util.UUID
import java.util.Locale

private val backupInsertOrder=listOf("categories","tags","recurring_series","series_rules","tasks","occurrences","series_tags","task_tags","series_reminders","reminders","reminder_overrides")
private val backupDeleteOrder=listOf("occurrences","tasks","recurring_series","tags","categories")
data class BackupPreview(val localFingerprint:String,val fileFingerprint:String,val mode:RestoreMode,val plan:BackupPlan)
private data class BackupStore(val graph:BackupGraph,val anchors:List<RetentionAnchorEntity>,val fingerprint:String)

class BackupRepository(private val db:TodoDatabase,private val clock:Clock=DeviceClock) {
    private fun rows(table:String):List<BackupRow> {
        if(table=="settings") return db.openHelper.readableDatabase.query("SELECT days,updatedAt FROM retention_policy WHERE id=1").use { c ->
            listOf(if(c.moveToFirst()) mapOf("id" to "general", "completedRetentionDays" to if(c.isNull(0)) null else c.getLong(0), "updatedAt" to c.getLong(1))
                else mapOf("id" to "general", "completedRetentionDays" to null, "updatedAt" to 0L))
        }
        require(table in backupTables || table=="retention_policy" || table=="retention_anchors")
        return db.openHelper.readableDatabase.query("SELECT * FROM $table").use { c -> buildList {
            while(c.moveToNext())add(c.columnNames.associateWith { name -> val i=c.getColumnIndexOrThrow(name)
                when { c.isNull(i)->null;name in backupBooleans -> c.getInt(i)!=0;c.getType(i)==Cursor.FIELD_TYPE_INTEGER->c.getLong(i);else->c.getString(i) }
            })
        } }
    }
    private suspend fun capture():BackupStore {
        val base = BackupGraph(backupTables.keys.filter { it != "note_images" }.associateWith(::rows))
        val imageIds = referencedImageIds(base)
        var total = 0L
        imageIds.forEach { total += requireNotNull(db.noteImages().size(it)) { "本機圖片附件不存在" }; require(total <= BACKUP_MAX_BYTES) { "含圖片備份超過 64 MiB，請減少附件後再匯出" } }
        val images = imageIds.map { id -> val row = requireNotNull(db.noteImages().image(id)) { "本機圖片附件不存在" }; mapOf("id" to row.id, "data" to row.data) }
        val graph = BackupGraph(base.tables + ("note_images" to images))
        val stable=graph.tables.mapValues { (t,items) -> items.map { r -> if(t=="reminders") r.filterKeys { it !in setOf("state","resolvedAt","alarmKey","alarmMode") } else r }.sortedBy { rowKey(t,it) } }
        return BackupStore(graph,db.history().anchors(),backupHash(mapOf("data" to stable,"policy" to rows("retention_policy"),"anchors" to rows("retention_anchors").sortedBy { it.text("taskId") })))
    }
    suspend fun export(appVersion:String):String=db.withTransaction {
        val graph=capture().graph;validateBackup(graph,true);BackupFormat.encode(graph,clock.millis(),appVersion)
    }
    suspend fun preview(document:BackupDocument,mode:RestoreMode,choices:Map<String,BackupChoice> = emptyMap()):BackupPreview=db.withTransaction {
        validateBackup(document.data,false);val local=capture();validateBackup(local.graph,true)
        val plan=planBackup(local.graph,document.data,mode,choices);validateBackup(plan.graph,true)
        BackupPreview(local.fingerprint,document.fingerprint,mode,plan)
    }
    suspend fun apply(document:BackupDocument,preview:BackupPreview,choices:Map<String,BackupChoice>):Unit=db.withTransaction {
        validateBackup(document.data,false);val local=capture()
        require(local.fingerprint==preview.localFingerprint && document.fingerprint==preview.fileFingerprint){"資料或備份檔已更新，請重新預覽"}
        val plan=planBackup(local.graph,document.data,preview.mode,choices)
        require(plan.conflicts.all { it.id in choices }){"請先為每個衝突選擇保留的版本"}
        require(choices.keys.all { k -> plan.conflicts.any { it.id==k } }){"衝突範圍已更新，請重新預覽"}
        validateBackup(plan.graph,true)
        if(preview.mode==RestoreMode.MERGE && plan.graph.content()==local.graph.content())return@withTransaction
        val now=clock.millis();val before=local.graph;val target=plan.graph
        val oldPolicy=db.history().policy() ?: RetentionPolicyEntity()
        val restoredPolicy=target.rows("settings").singleOrNull()
        val restoredDays=restoredPolicy?.optionalLong("completedRetentionDays")?.let(Math::toIntExact)
        val policyChanged=restoredPolicy!=null && restoredDays!=oldPolicy.days
        fun byId(graph:BackupGraph,t:String)=graph.rows(t).associateBy { it.text("id") }
        val oldTasks=byId(before,"tasks");val desiredTasks=byId(target,"tasks")
        val oldSemantic=before.content();val desiredSemantic=target.content()
        val oldIds=oldSemantic.mapValues { (_,rows)->rows.associateBy { it["id"] } };val newIds=desiredSemantic.mapValues { (_,rows)->rows.associateBy { it["id"] } }
        fun semanticRow(t:String,id:String,old:Boolean)=(if(old)oldIds else newIds).getValue(t)[id]
        fun changed(t:String,id:String)=semanticRow(t,id,true)!=semanticRow(t,id,false)
        val taskRelations=listOf("task_tags","occurrences","reminders","reminder_overrides")
        val oldLinks=taskRelations.associateWith { oldSemantic.getValue(it).groupBy { r->r.text("taskId") } };val newLinks=taskRelations.associateWith { desiredSemantic.getValue(it).groupBy { r->r.text("taskId") } }
        fun taskChanged(id:String)=changed("tasks",id) || taskRelations.any { t -> oldLinks.getValue(t)[id].orEmpty()!=newLinks.getValue(t)[id].orEmpty() }
        val seriesTables=listOf("series_rules","series_tags","series_reminders")
        val oldSeriesLinks=seriesTables.associateWith { oldSemantic.getValue(it).groupBy { r->r.text("seriesId") } };val newSeriesLinks=seriesTables.associateWith { desiredSemantic.getValue(it).groupBy { r->r.text("seriesId") } }
        val seriesRefs=target.rows("occurrences").groupBy { it.text("seriesId") }
        val taskChanges=desiredTasks.keys.associateWith(::taskChanged)
        val finalRows=target.tables.mapValues { (_,r)->r.toMutableList() }.toMutableMap()
        for(t in listOf("categories","tags","tasks","recurring_series")) {
            val old=byId(before,t);var nextOrder=(old.values.maxOfOrNull { (it["sortOrder"] as Number?)?.toLong() ?: -1L } ?: -1L)+1
            finalRows[t]=target.rows(t).map { r ->
                val id=r.text("id");val previous=old[id]
                val differs=if(t=="tasks")taskChanges.getValue(id) else if(t=="recurring_series") changed(t,id) ||
                    seriesRefs[id].orEmpty().any { taskChanges[it.text("taskId")]==true } ||
                    seriesTables.any { tab -> oldSeriesLinks.getValue(tab)[id].orEmpty()!=newSeriesLinks.getValue(tab)[id].orEmpty() }
                    else changed(t,id)
                val revision=if(previous==null)1L else if(differs || preview.mode==RestoreMode.REPLACE)Math.incrementExact(previous.long("revision")) else previous.long("revision")
                val extra=mutableMapOf<String,Any?>("revision" to revision)
                if(t in listOf("categories","tags")){extra["sortOrder"]=r["sortOrder"] ?: previous?.get("sortOrder") ?: nextOrder.also { require(it<=Int.MAX_VALUE){"排序數量超出範圍"};nextOrder++ }}
                if(t=="tags")extra["nameKey"]=r.text("name").trim().lowercase(Locale.ROOT)
                if(t=="tasks")extra["deletedAt"]=r["deletedAt"]
                if(t=="recurring_series"){extra["deletedAt"]=r["deletedAt"];extra["previousLifecycle"]=r["previousLifecycle"]}
                r+extra
            }.toMutableList()
        }
        val finalTasks=finalRows.getValue("tasks").associateBy { it.text("id") }
        val oldReminders=byId(before,"reminders")
        finalRows["reminders"]=target.rows("reminders").map { r ->
            val old=oldReminders[r.text("id")];val task=finalTasks.getValue(r.text("taskId"))
            val same=old!=null && !changed("reminders",r.text("id")) && task.long("revision")==oldTasks[task.text("id")]?.long("revision")
            if(same)old!! else {
                val at=resolveReminder(ReminderSpec(r.text("id"),ReminderKind.valueOf(r.text("kind")),r.text("zoneId"),r.optionalLong("fixedAt"),r.optionalLong("offsetMinutes")?.let(Math::toIntExact),LocalTime.parse(r.text("baseTime"))),
                    task.optionalText("dueDate")?.let(LocalDate::parse),task.optionalText("dueTime")?.let(LocalTime::parse),task.optionalText("zoneId"))
                val terminal=old?.takeIf { it["resolvedAt"]==at && it["state"] in listOf("DELIVERED","MISSED") &&
                    listOf("kind","zoneId","fixedAt","offsetMinutes","baseTime","enabled").all { k -> it[k]==r[k] } }
                    ?: r.takeIf { it["resolvedAt"]==at && it["state"] in listOf("DELIVERED","MISSED") }
                r+mapOf("revision" to if(old==null)1L else Math.incrementExact(old.long("revision")),"alarmKey" to null,"alarmMode" to null,"resolvedAt" to at,
                    "state" to (terminal?.text("state") ?: if(task["deletedAt"]!=null || task["status"]==TaskStatus.COMPLETED.name || r["enabled"]!=true)"CANCELED" else if(at<=now)"MISSED" else "PENDING"))
            }
        }.toMutableList()
        val finalReminders=finalRows.getValue("reminders").associateBy { it.text("id") }
        // Preserve every old native identity before removing rows, including already disabled definitions.
        oldReminders.values.filter { it!=finalReminders[it.text("id")] }.forEach { old ->
            val id=UUID.nameUUIDFromBytes("todo/backup-cancel/v1/${old.text("taskId")}/${old.text("id")}/${old["alarmKey"]}".toByteArray(Charsets.UTF_8)).toString()
            db.history().cancel(PurgeCancellationEntity(id,old.text("taskId"),old.text("id"),old.optionalText("alarmKey"),now))
        }
        val sql=db.openHelper.writableDatabase
        // Immutable content-addressed images are inserted in the same transaction as all restored references.
        // Keep unreferenced editor/draft images until the editor closes, never remove them during preview/restore.
        target.rows("note_images").forEach { db.noteImages().insert(NoteImageEntity(it.text("id"), it.text("data"))) }
        backupDeleteOrder.forEach { sql.execSQL("DELETE FROM $it") }
        sql.execSQL("DELETE FROM schedule_outbox")
        backupInsertOrder.forEach { table -> finalRows[table].orEmpty().forEach { row ->
            val values=ContentValues();row.forEach { (key,value) -> when(value) {
                null->values.putNull(key);is String->values.put(key,value);is Boolean->values.put(key,if(value)1 else 0)
                is Number->values.put(key,value.toLong());else->error("Invalid SQL value")
            } };sql.insert(table,SQLiteDatabase.CONFLICT_ABORT,values)
        } }
        val refs=target.rows("occurrences").map { it.text("taskId") }.toSet();val anchors=local.anchors.associateBy { it.taskId }
        finalTasks.values.forEach { task ->
            val id=task.text("id");val completed=task.optionalLong("completedAt")
            if(!policyChanged && !taskChanges.getValue(id) && preview.mode==RestoreMode.MERGE)anchors[id]?.let { db.history().anchor(it) }
            else if(id !in refs && task["deletedAt"]==null && task["status"]==TaskStatus.COMPLETED.name && completed!=null)
                db.history().anchor(RetentionAnchorEntity(id,completed,now))
        }
        if (restoredPolicy != null && (policyChanged || preview.mode == RestoreMode.REPLACE)) {
            db.history().policy(RetentionPolicyEntity(days=restoredDays, revision=Math.incrementExact(oldPolicy.revision), updatedAt=restoredPolicy.long("updatedAt")))
        }
        // A missed reminder is never made PENDING merely because a file was imported.
        // Existing cancellation work survives; file preferences are applied in this same transaction.
    }
}
