package com.chimafok.todo.data

import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ScheduleAcknowledgementsTest {
    @Test fun multipleChunksKeepNewGenerationsPendingAndAcknowledgeDeletedTasks(): Unit = runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="ack-${UUID.randomUUID()}.db";val db=TodoDatabase.create(context,name)
        val clock=Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"),ZoneOffset.UTC)
        try {
            db.withTransaction {
                val sql=db.openHelper.writableDatabase
                repeat(1200) { i -> sql.execSQL("INSERT INTO tasks(id,title,notes,categoryId,status,createdAt,updatedAt,revision) VALUES(?,?,'',?,'OPEN',1,1,1)",arrayOf("dirty-$i","待辦$i",UNCATEGORIZED_ID)) }
                sql.execSQL("INSERT INTO schedule_outbox(taskId,generation,processedAt) VALUES('already-deleted',7,NULL)")
            }
            TaskRepository(db,clock).save(TaskDraft("alarm-task","提醒",reminders=listOf(ReminderSpec("reminder",ReminderKind.FIXED,"UTC",fixedAt=clock.millis()+60_000))))
            var schedules=0
            val gateway=object:ReminderGateway {
                override fun notificationsAllowed()=true
                override fun exactAllowed()=true
                override fun schedule(reminder:ReminderEntity,key:String,triggerAt:Long):String {
                    schedules++
                    // Simulate new work arriving after the captured outbox generation.
                    db.openHelper.writableDatabase.execSQL("UPDATE schedule_outbox SET generation=generation+1 WHERE taskId='dirty-450'")
                    return "EXACT"
                }
                override fun cancel(reminder:ReminderEntity,key:String) {}
                override fun show(reminder:ReminderEntity,task:TaskEntity,key:String) {}
                override fun cancelRemoved(taskId:String,reminderId:String,key:String) {}
                override fun dismiss(reminderId:String) {}
            }
            val engine=ReminderEngine(db,gateway,clock)
            engine.reconcile()
            assertEquals(1,schedules)
            assertEquals(listOf("dirty-450"),db.reminders().pending().map { it.taskId })
            val count=db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM schedule_outbox WHERE processedAt IS NOT NULL").use { it.moveToFirst();it.getInt(0) }
            assertEquals(1201,count)
            engine.reconcile()
            assertTrue(db.reminders().pending().isEmpty())
            assertEquals(1,schedules)
            assertEquals("PENDING",db.reminders().get("reminder")!!.state)
        } finally { db.close();context.deleteDatabase(name) }
    }
}
