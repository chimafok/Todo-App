package com.chimafok.todo

import android.app.NotificationManager
import androidx.room.withTransaction
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Build
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Shell-only acceptance fixture, excluded from debug/release. Never accesses the normal package. */
class QaSystemProbe : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = runBlocking {
        val app = requireNotNull(context)
        check(app.packageName == "com.chimafok.todo.qa")
        val db = ReminderRuntime.database
        val id = "qa-system-acceptance"
        val reminderId = "$id-reminder"
        val gateway = AndroidReminderGateway(app)
        gateway.ensureChannel()
        when (method) {
            "clear", "seed" -> {
                db.reminders().get(reminderId)?.let { row -> row.alarmKey?.let { gateway.cancel(row, it) } }
                gateway.dismiss(reminderId)
                db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id=?", arrayOf(id))
                if (method == "seed") {
                    val at = System.currentTimeMillis() + requireNotNull(arg).toLong() * 1000
                    val zone = ZoneId.systemDefault()
                    val due = Instant.ofEpochMilli(at + 3_600_000).atZone(zone)
                    TaskRepository(db).save(TaskDraft(id, "QA 系統驗收", dueDate = due.toLocalDate(),
                        dueTime = due.toLocalTime().withSecond(0).withNano(0), zoneId = zone.id,
                        reminders = listOf(ReminderSpec(reminderId, ReminderKind.FIXED, zone.id, fixedAt = at))))
                    ReminderRuntime.engine.reconcile(force = true)
                }
            }
            "clear-scale", "seed-scale" -> db.withTransaction {
                val sql = db.openHelper.writableDatabase
                sql.execSQL("DELETE FROM tasks WHERE id LIKE 'qa-scale-%'")
                sql.execSQL("DELETE FROM recurring_series WHERE id LIKE 'qa-scale-%'")
                if (method == "seed-scale") {
                    val date = java.time.LocalDate.now()
                    val statement = sql.compileStatement("INSERT INTO tasks(id,title,notes,categoryId,dueDate,zoneId,status,completedAt,createdAt,updatedAt,revision) VALUES(?,?,?,?,?,'UTC',?,?,1,1,1)")
                    fun insert(i: Int, history: Boolean) {
                        val taskId = "qa-scale-${if(history) "history" else "ordinary"}-$i"
                        statement.clearBindings(); statement.bindString(1,taskId); statement.bindString(2,"${if(history) "歷史" else "任務"}$i")
                        statement.bindString(3,"唯一內容$i：" + if(history) "紀錄與截止。".repeat(30) else "長備註與跨時區截止。".repeat(150))
                        statement.bindString(4,UNCATEGORIZED_ID); statement.bindString(5,if(history)date.minusDays((500-i%500).toLong()).toString() else date.plusDays((i%365).toLong()).toString())
                        statement.bindString(6,if(history)"COMPLETED" else "OPEN")
                        if(history)statement.bindLong(7,System.currentTimeMillis()) else statement.bindNull(7)
                        statement.executeInsert()
                    }
                    repeat(10_000) { insert(it,false) }
                    repeat(100) { i ->
                        sql.execSQL("INSERT INTO recurring_series(id,title,notes,categoryId,lifecycle,cursor,createdAt,updatedAt,revision) VALUES(?,?,'',?,'ENDED',500,1,1,1)",arrayOf("qa-scale-series-$i","測試系列$i",UNCATEGORIZED_ID))
                        sql.execSQL("INSERT INTO series_rules(id,seriesId,version,effectiveFrom,anchor,frequency,intervalDays,weekdayMask,zoneId,maxCount,createdAt) VALUES(?,?,1,1,?,'DAILY',1,0,'UTC',500,1)",arrayOf("qa-scale-rule-$i","qa-scale-series-$i",date.minusDays(500).toString()))
                    }
                    val occurrence = sql.compileStatement("INSERT INTO occurrences(taskId,seriesId,sequence,ruleId,snapshot,excludedByRule) VALUES(?,?,?,?,?,0)")
                    repeat(50_000) { i ->
                        insert(i,true); val series = i/500
                        occurrence.clearBindings();occurrence.bindString(1,"qa-scale-history-$i");occurrence.bindString(2,"qa-scale-series-$series")
                        occurrence.bindLong(3,(i%500+1).toLong());occurrence.bindString(4,"qa-scale-rule-$series")
                        occurrence.bindString(5,OccurrenceSnapshot("快照$i","不同的舊備註$i："+"歷史內容。".repeat(30),"原分類",listOf("封存標籤$i"),"COMPLETED",false).json())
                        occurrence.executeInsert()
                    }
                    statement.close(); occurrence.close()
                }
            }
            "refresh" -> ReminderRuntime.engine.reconcile(force = true, recovery = true)
            "inspect" -> Unit
            else -> error("Unknown QA method")
        }
        val row = db.reminders().get(reminderId)
        val task = db.dao().task(id)
        val notification = app.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull { it.tag == reminderId }
        val data = JSONObject().put("sdk", Build.VERSION.SDK_INT).put("pid", android.os.Process.myPid())
            .put("now", System.currentTimeMillis()).put("state", row?.state ?: "NONE")
            .put("at", row?.resolvedAt ?: 0).put("mode", row?.alarmMode ?: "NONE")
            .put("key", row?.alarmKey ?: "NONE").put("zone", ZoneId.systemDefault().id)
            .put("taskZone", task?.zoneId ?: "NONE").put("revision", task?.revision ?: 0)
            .put("notification", notification != null).put("postTime", notification?.postTime ?: 0)
            .put("exactAllowed", gateway.exactAllowed()).put("notificationsAllowed", gateway.notificationsAllowed())
        Bundle().apply { putString("evidence", data.toString()) }
    }
    override fun getType(uri: Uri): String? = null
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
