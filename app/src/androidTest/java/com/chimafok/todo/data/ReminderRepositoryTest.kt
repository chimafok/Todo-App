package com.chimafok.todo.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.withTransaction
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneId.of("Asia/Hong_Kong"))
    private class Fake : ReminderGateway {
        var permitted = true; var exact = true; var fail = false
        val alarms = mutableMapOf<String,Long>(); val shown = mutableListOf<String>(); val dismissed = mutableListOf<String>()
        override fun notificationsAllowed() = permitted
        override fun exactAllowed() = exact
        override fun schedule(reminder: ReminderEntity, key: String, triggerAt: Long): String {
            if (fail) error("injected platform failure")
            alarms[key] = triggerAt; return if (exact) "EXACT" else "INEXACT"
        }
        override fun cancel(reminder: ReminderEntity, key: String) { alarms.remove(key) }
        override fun show(reminder: ReminderEntity, task: TaskEntity, key: String) { shown.add(reminder.id) }
        override fun cancelRemoved(taskId: String, reminderId: String, key: String) { alarms.remove(key) }
        override fun dismiss(reminderId: String) { dismissed.add(reminderId) }
    }
    private fun fixed(id: String="f", minutes: Long=60) = ReminderSpec(id, ReminderKind.FIXED,"Asia/Hong_Kong",fixedAt=clock.millis()+minutes*60_000)
    private fun relative(id: String="r") = ReminderSpec(id,ReminderKind.RELATIVE_DUE,"Asia/Hong_Kong",offsetMinutes=60)
    private fun draft(specs: List<ReminderSpec> = listOf(fixed(), relative())) = TaskDraft("t","提醒測試",dueDate=LocalDate.of(2026,10,28),zoneId="Asia/Hong_Kong",reminders=specs)
    private fun withDb(block: suspend (TodoDatabase,TaskRepository,Fake)->Unit) = runBlocking {
        val name="reminder-${UUID.randomUUID()}.db"; val db=TodoDatabase.create(context,name)
        try { block(db,TaskRepository(db,clock),Fake()) } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun fixedStaysRelativeMovesAndRemovingDateRollsBackEverything() = withDb { db,repo,fake ->
        val d=draft();repo.save(d);val engine=ReminderEngine(db,fake,clock);engine.reconcile()
        val first=db.reminders().all();val task=db.dao().task("t")!!
        repo.save(d.copy(dueDate=d.dueDate!!.plusDays(2),expectedRevision=task.revision,reminders=null));engine.reconcile()
        assertEquals(first.first{it.id=="f"}.resolvedAt,db.reminders().get("f")!!.resolvedAt)
        assertEquals(first.first{it.id=="r"}.resolvedAt!!+Duration.ofDays(2).toMillis(),db.reminders().get("r")!!.resolvedAt)
        val before=db.dao().task("t")!!;val reminders=db.reminders().all();val outbox=db.reminders().pending()
        try { repo.save(d.copy(title="不應寫入",dueDate=null,zoneId=null,expectedRevision=before.revision,reminders=null));fail() } catch (_: IllegalArgumentException) {}
        assertEquals(before,db.dao().task("t"));assertEquals(reminders,db.reminders().all());assertEquals(outbox,db.reminders().pending())
        repo.save(d.copy(dueDate=null,zoneId=null,expectedRevision=before.revision,reminders=listOf(fixed())));engine.reconcile()
        assertFalse(db.reminders().get("r")!!.enabled);assertEquals(1,fake.alarms.size);assertNull(db.dao().task("t")!!.dueTime)
    }
    @Test fun taskAndReminderAndOutboxFailureAreOneTransaction() = withDb { db,repo,_ ->
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_reminder BEFORE INSERT ON reminders BEGIN SELECT RAISE(ABORT,'injected'); END")
        try { repo.save(draft());fail() } catch (_: android.database.sqlite.SQLiteException) {}
        assertNull(db.dao().task("t"));assertTrue(db.reminders().all().isEmpty());assertTrue(db.reminders().pending().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_reminder");repo.save(draft());repo.save(draft())
        assertEquals(2,db.reminders().all().size)
        val old=db.dao().task("t")!!
        try { repo.save(draft().copy(title="stale",expectedRevision=old.revision+1));fail() } catch (_: IllegalArgumentException) {}
        assertEquals(old,db.dao().task("t"))
    }
    @Test fun noPermissionSavesBlockedThenFutureSchedulesWithInexactFallback() = withDb { db,repo,fake ->
        fake.permitted=false;fake.exact=false;repo.save(draft());val engine=ReminderEngine(db,fake,clock);engine.reconcile()
        assertTrue(db.reminders().all().all{it.state=="BLOCKED"});assertTrue(fake.alarms.isEmpty())
        fake.permitted=true;engine.reconcile(force=true)
        assertTrue(db.reminders().all().all{it.state=="PENDING"&&it.alarmMode=="INEXACT"})
        fake.exact=true;engine.reconcile(force=true);assertTrue(db.reminders().all().all{it.alarmMode=="EXACT"})
    }
    @Test fun unsubmittedStillSchedulesCompletionCancelsAndReopenRestoresFutureOnly() = withDb { db,repo,fake ->
        repo.save(draft().copy(categoryId=ASSIGNMENTS_ID,status=TaskStatus.DONE_UNSUBMITTED));val engine=ReminderEngine(db,fake,clock);engine.reconcile()
        assertEquals(2,fake.alarms.size);val task=db.dao().task("t")!!;repo.setStatus(task.id,task.revision,TaskStatus.COMPLETED)
        assertTrue(db.reminders().all().all{it.state=="CANCELED"});engine.reconcile();assertTrue(fake.alarms.isEmpty())
        val done=db.dao().task("t")!!;repo.setStatus("t",done.revision,TaskStatus.IN_PROGRESS);engine.reconcile();assertEquals(2,fake.alarms.size)
        repo.trash("t",db.dao().task("t")!!.revision);engine.reconcile();assertTrue(fake.alarms.isEmpty())
        repo.restore("t");engine.reconcile();assertEquals(2,fake.alarms.size)
    }
    @Test fun deliveryAndNotificationCompletionAreIdempotentAndStaleCannotCompleteNewVersion() = withDb { db,repo,fake ->
        repo.save(draft(listOf(fixed(minutes=1))).copy(categoryId=ASSIGNMENTS_ID,status=TaskStatus.DONE_UNSUBMITTED))
        val engine=ReminderEngine(db,fake,clock);engine.reconcile();val key=db.reminders().get("f")!!.alarmKey!!
        engine.fire("f",key);assertTrue(fake.shown.isEmpty())
        val later=ReminderEngine(db,fake,Clock.offset(clock,Duration.ofMinutes(1)))
        later.reconcile(force=true);later.fire("f",key);later.fire("f",key);assertEquals(1,fake.shown.size)
        val task=db.dao().task("t")!!;repo.save(draft(listOf(fixed(minutes=1))).copy(notes="new",categoryId=ASSIGNMENTS_ID,
            status=TaskStatus.DONE_UNSUBMITTED,expectedRevision=task.revision))
        later.complete("f",key);assertEquals(TaskStatus.DONE_UNSUBMITTED,db.dao().task("t")!!.status)
        // A fresh notification on a second task completes assignments directly to COMPLETED.
        repo.save(draft(listOf(fixed("g",2))).copy(id="u",categoryId=ASSIGNMENTS_ID,status=TaskStatus.IN_PROGRESS))
        engine.reconcile();val next=db.reminders().get("g")!!.alarmKey!!
        val then=ReminderEngine(db,fake,Clock.offset(clock,Duration.ofMinutes(2)))
        then.fire("g",next);then.complete("g",next);val done=db.dao().task("u")!!
        then.complete("g",next);assertEquals(done,db.dao().task("u"));assertEquals(TaskStatus.COMPLETED,done.status)
    }
    @Test fun rescheduledOrRemovedOldAlarmCannotNotifyAndBatchTrashCancels() = withDb { db,repo,fake ->
        repo.save(draft());val engine=ReminderEngine(db,fake,clock);engine.reconcile();val old=db.reminders().get("f")!!.alarmKey!!
        repo.save(draft(listOf(fixed(minutes=120))).copy(expectedRevision=db.dao().task("t")!!.revision));engine.reconcile()
        ReminderEngine(db,fake,Clock.offset(clock,Duration.ofHours(1))).fire("f",old);assertTrue(fake.shown.isEmpty())
        repo.trashBatch(mapOf("t" to db.dao().task("t")!!.revision));engine.reconcile();assertTrue(fake.alarms.isEmpty())
        assertTrue(db.reminders().all().all{!it.enabled||it.state=="CANCELED"})
    }
    @Test fun recoverySkipsTwentyPastAndKeepsTwoFutureWithoutNotificationFlood() = withDb { db,repo,fake ->
        repo.save(draft((1..20).map{fixed("past$it",-it.toLong())}+listOf(fixed("future1"),fixed("future2",120))))
        val engine=ReminderEngine(db,fake,clock);engine.reconcile(force=true,recovery=true)
        assertEquals(20,db.reminders().all().count{it.state=="MISSED"});assertEquals(2,fake.alarms.size);assertTrue(fake.shown.isEmpty())
        engine.reconcile(force=true,recovery=true);assertEquals(2,fake.alarms.size);assertTrue(fake.shown.isEmpty())
    }
    @Test fun platformFailureRetainsDurableOutboxAndRetryDoesNotDuplicate() = withDb { db,repo,fake ->
        repo.save(draft());fake.fail=true;val engine=ReminderEngine(db,fake,clock)
        try { engine.reconcile();fail() } catch (_: IllegalStateException) {}
        assertTrue(db.reminders().pending().isNotEmpty());fake.fail=false;engine.reconcile()
        assertTrue(db.reminders().pending().isEmpty());assertEquals(2,fake.alarms.size);engine.reconcile();assertEquals(2,fake.alarms.size)
    }
    @Test fun recurrenceCannotSilentlyDropOrCloneSingleTaskReminders() = withDb { db,repo,_ ->
        repo.save(draft());val old=db.dao().task("t")!!
        try { repo.save(draft().copy(expectedRevision=old.revision,recurrence=RecurrenceRule(old.dueDate!!,RecurrenceFrequency.DAILY,"Asia/Hong_Kong")));fail() }
        catch (_: IllegalArgumentException) {}
        val conversion=draft().copy(expectedRevision=old.revision,recurrence=RecurrenceRule(old.dueDate!!,RecurrenceFrequency.DAILY,"Asia/Hong_Kong"))
        try { RecurringRepository(db,clock).convert(conversion);fail() } catch (_: IllegalArgumentException) {}
        try { RecurringRepository(db,clock).create(conversion.copy(id="new-series",expectedRevision=null));fail() } catch (_: IllegalArgumentException) {}
        assertEquals(old,db.dao().task("t"));assertTrue(db.dao().allSeries().isEmpty());assertEquals(2,db.reminders().all().size)
    }
    @Test fun delayedScheduledAlarmCanDeliverButRecoveryNeverBackfillsAndMovedRelativeRearms() = withDb { db,repo,fake ->
        val soon = draft(listOf(relative())).copy(dueDate=LocalDate.of(2026,10,23),dueTime=LocalTime.of(19,1))
        repo.save(soon);val engine=ReminderEngine(db,fake,clock);engine.reconcile();val key=db.reminders().get("r")!!.alarmKey!!
        val later=ReminderEngine(db,fake,Clock.offset(clock,Duration.ofMinutes(30)))
        later.reconcile(force=true);later.fire("r",key);assertEquals(listOf("r"),fake.shown)
        val task=db.dao().task("t")!!
        repo.save(soon.copy(dueDate=soon.dueDate!!.plusDays(1),expectedRevision=task.revision));later.reconcile()
        assertEquals("PENDING",db.reminders().get("r")!!.state)
        assertNotEquals(key,db.reminders().get("r")!!.alarmKey)
        later.fire("r",key);assertEquals(1,fake.shown.size)
    }
    @Test fun savePersistsDescendingTimesImmediatelyAndReordersAfterDeadlineChangeWithoutChangingIds() = withDb { db,repo,_ ->
        val end = resolveReminder(relative().copy(offsetMinutes=0),LocalDate.of(2026,10,28),null,"Asia/Hong_Kong")
        val specs=listOf(fixed("z-tie", (end-clock.millis())/60_000-60), relative("a-tie"),
            relative("day").copy(offsetMinutes=1440), relative("at").copy(offsetMinutes=0))
        val d=draft(specs);repo.save(d)
        val rows=db.reminders().forTask("t")
        assertEquals(listOf("at","a-tie","z-tie","day"),rows.map{it.id})
        assertTrue(rows.all{it.resolvedAt!=null});assertEquals(rows,db.reminders().all())
        val task=db.dao().task("t")!!
        // Even old unprocessed rows can be viewed correctly before scheduler bookkeeping.
        val legacy=rows.map{it.copy(resolvedAt=null)}.reversed()
        assertEquals(rows.map{it.id},legacy.orderedFor(task).map{it.id})
        repo.save(d.copy(dueDate=d.dueDate!!.minusDays(1),expectedRevision=task.revision))
        val moved=db.reminders().forTask("t")
        assertEquals(listOf("z-tie","at","a-tie","day"),moved.map{it.id})
        assertEquals(rows.map{it.id}.toSet(),moved.map{it.id}.toSet())
        assertEquals(rows.first{it.id=="z-tie"}.fixedAt,moved.first{it.id=="z-tie"}.fixedAt)
        assertEquals(moved.map{it.id},moved.orderedFor(db.dao().task("t")!!).map{it.id})
    }
    @Test fun savedOrderBookkeepingDoesNotRearmSameMissedOrDeliveredPointOrInactiveTasks() = withDb { db,repo,fake ->
        val d=draft(listOf(fixed("past",-1),fixed("future",1)));repo.save(d)
        val engine=ReminderEngine(db,fake,clock);engine.reconcile()
        val key=db.reminders().get("future")!!.alarmKey!!
        val later=ReminderEngine(db,fake,Clock.offset(clock,Duration.ofMinutes(1)));later.fire("future",key)
        assertEquals("DELIVERED",db.reminders().get("future")!!.state)
        assertEquals("MISSED",db.reminders().get("past")!!.state)
        repo.save(d.copy(notes="unrelated",expectedRevision=db.dao().task("t")!!.revision));later.reconcile()
        assertEquals("DELIVERED",db.reminders().get("future")!!.state)
        assertEquals("MISSED",db.reminders().get("past")!!.state)
        assertEquals(1,fake.shown.size)
        repo.setStatus("t",db.dao().task("t")!!.revision,TaskStatus.COMPLETED)
        repo.save(d.copy(status=TaskStatus.COMPLETED,expectedRevision=db.dao().task("t")!!.revision,reminders=listOf(fixed("new",120))))
        assertEquals("CANCELED",db.reminders().get("new")!!.state);later.reconcile();assertTrue(fake.alarms.isEmpty())
    }
    @Test fun realNonEmptySchemaFourMigratesToFiveAndInstallsAtomicTriggers() = runBlocking {
        val name="v4-reminders-${UUID.randomUUID()}.db";val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        val schema=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.chimafok.todo.data.TodoDatabase/4.json").bufferedReader().use{it.readText()}).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file,null).use { old ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) { val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=entity.getJSONArray("indices");for(j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO categories VALUES (?, '未分類','UNCATEGORIZED',0,1,1,1)",arrayOf(UNCATEGORIZED_ID))
            old.execSQL("INSERT INTO tasks VALUES ('legacy','原資料','保留內容',?,'2026-10-28',NULL,'Asia/Hong_Kong','OPEN',NULL,NULL,1,2,7)",arrayOf(UNCATEGORIZED_ID))
            old.version=4
        }
        val db=TodoDatabase.create(context,name)
        try {
            val original=db.dao().task("legacy")!!;assertEquals(7L,original.revision);assertEquals("保留內容",original.notes);assertEquals(7,db.openHelper.readableDatabase.version)
            assertTrue(db.reminders().all().isEmpty());TaskRepository(db,clock).save(TaskDraft("legacy","原資料",original.notes,dueDate=original.dueDate,
                zoneId=original.zoneId,expectedRevision=7,reminders=listOf(relative())))
            assertNull(db.dao().task("legacy")!!.dueTime);assertEquals(1,db.reminders().all().size);assertEquals(1,db.reminders().pending().size)
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
        } finally { db.close();context.deleteDatabase(name) }
    }
}
