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
        require(table in backupTables || table=="retention_policy" || table=="retention_anchors")
        return db.openHelper.readableDatabase.query("SELECT * FROM $table").use { c -> buildList {
            while(c.moveToNext())add(c.columnNames.associateWith { name -> val i=c.getColumnIndexOrThrow(name)
                when { c.isNull(i)->null;name in backupBooleans -> c.getInt(i)!=0;c.getType(i)==Cursor.FIELD_TYPE_INTEGER->c.getLong(i);else->c.getString(i) }
            })
        } }
    }
    private suspend fun capture():BackupStore {
        val graph=BackupGraph(backupTables.keys.associateWith(::rows))
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
                if(t in listOf("categories","tags")){extra["sortOrder"]=previous?.get("sortOrder") ?: nextOrder.also { require(it<=Int.MAX_VALUE){"排序數量超出範圍"};nextOrder++ }}
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
            if(!taskChanges.getValue(id) && preview.mode==RestoreMode.MERGE)anchors[id]?.let { db.history().anchor(it) }
            else if(id !in refs && task["deletedAt"]==null && task["status"]==TaskStatus.COMPLETED.name && completed!=null)
                db.history().anchor(RetentionAnchorEntity(id,completed,now))
        }
        // A missed reminder is never made PENDING merely because a file was imported.
        // Existing cancellation work and local retention policy deliberately survive this transaction.
    }
}
