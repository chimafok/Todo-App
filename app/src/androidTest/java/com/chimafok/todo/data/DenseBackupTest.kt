package com.chimafok.todo.data

import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class DenseBackupTest {
    @Test fun longDistinctNotesDenseTagsAndRemindersRoundTripWithoutChangingSource(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val names = List(2) { "dense-${UUID.randomUUID()}.db" }
        val source = TodoDatabase.create(context, names[0]); val target = TodoDatabase.create(context, names[1])
        val clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC)
        try {
            val sql = source.openHelper.writableDatabase
            source.withTransaction {
                repeat(10) { t -> sql.execSQL("INSERT INTO tags(id,name,nameKey,sortOrder,createdAt,updatedAt,revision) VALUES(?,?,?, ?,1,1,1)", arrayOf<Any>("dense-tag-$t", "標籤$t", "標籤$t", t)) }
                repeat(1000) { i ->
                    val id = "dense-task-$i"
                    val notes = "$i：" + "長備註、不同內容、跨時區與資料復原。".repeat(100)
                    sql.execSQL("INSERT INTO tasks(id,title,notes,categoryId,status,createdAt,updatedAt,revision) VALUES(?,?,?,?,'OPEN',1,1,1)", arrayOf(id, "密集任務$i", notes, UNCATEGORIZED_ID))
                    repeat(10) { t -> sql.execSQL("INSERT INTO task_tags(taskId,tagId) VALUES(?,?)", arrayOf(id, "dense-tag-$t")) }
                    saveReminders(source, source.dao().task(id)!!, List(5) { n -> ReminderSpec("$id-reminder-$n", ReminderKind.FIXED, "UTC", fixedAt=clock.millis() + 86_400_000 + n*60_000) }, clock.millis())
                }
            }
            val repo = BackupRepository(source, clock)
            lateinit var text: String
            val exportMs = measureNanoTime { text = repo.export("qa-dense") } / 1_000_000.0
            val size = text.toByteArray(Charsets.UTF_8).size
            assertTrue(size in 1..BACKUP_MAX_BYTES)
            lateinit var document: BackupDocument
            val parseMs = measureNanoTime { document = BackupFormat.decode(text) } / 1_000_000.0
            val receiver = BackupRepository(target, clock)
            val restoreMs = measureNanoTime {
                val preview = receiver.preview(document, RestoreMode.REPLACE)
                receiver.apply(document, preview, emptyMap())
            } / 1_000_000.0
            val restored = BackupFormat.decode(receiver.export("qa-dense"))
            assertEquals(document.data.content(), restored.data.content())
            assertEquals(1000, restored.data.rows("tasks").size)
            assertEquals(10_000, restored.data.rows("task_tags").size)
            assertEquals(5000, restored.data.rows("reminders").size)
            assertEquals(text, repo.export("qa-dense"))
            assertEquals("ok", target.openHelper.readableDatabase.query("PRAGMA integrity_check").use { it.moveToFirst(); it.getString(0) })
            assertEquals(0, target.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { it.count })
            val runtime = Runtime.getRuntime()
            android.util.Log.i("TodoDenseBackup", JSONObject().put("tasks",1000).put("distinctNotes",1000).put("links",10_000)
                .put("reminders",5000).put("bytes",size).put("exportMs",exportMs).put("parseMs",parseMs).put("restoreMs",restoreMs)
                .put("retainedHeapBytes",runtime.totalMemory()-runtime.freeMemory()).put("maxHeapBytes",runtime.maxMemory())
                .put("peakMemoryMeasured",false).toString())
        } finally { source.close(); target.close(); names.forEach(context::deleteDatabase) }
    }
}
