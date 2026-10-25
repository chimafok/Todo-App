package com.chimafok.todo.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SeriesReminderRepositoryTest {
    private val context get()=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val clock=Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"),ZoneOffset.UTC)
    private val day=LocalDate.of(2026,10,27)
    private val templates=listOf(SeriesReminderSpec("r",SeriesReminderKind.RELATIVE_DUE,offsetMinutes=60),
        SeriesReminderSpec("f",SeriesReminderKind.OCCURRENCE_TIME,dayOffset=-1,time=LocalTime.of(17,0)))
    private class Fake:ReminderGateway {
        val alarms=mutableMapOf<String,Long>();val shown=mutableListOf<String>()
        override fun notificationsAllowed()=true
        override fun exactAllowed()=true
        override fun schedule(reminder:ReminderEntity,key:String,triggerAt:Long):String{alarms[key]=triggerAt;return "EXACT"}
        override fun cancel(reminder:ReminderEntity,key:String){alarms.remove(key)}
        override fun show(reminder:ReminderEntity,task:TaskEntity,key:String){shown+=reminder.id}
        override fun cancelRemoved(taskId: String, reminderId: String, key: String) { alarms.remove(key) }
        override fun dismiss(reminderId:String){}
    }
    private fun database(block:suspend(TodoDatabase,TaskRepository,RecurringRepository,Fake)->Unit)=runBlocking {
        val name="series-reminder-${UUID.randomUUID()}.db";val db=TodoDatabase.create(context,name)
        try{block(db,TaskRepository(db,clock),RecurringRepository(db,clock),Fake())}finally{db.close();context.deleteDatabase(name)}
    }
    private fun draft(id:String="s",specs:List<SeriesReminderSpec> = templates)=TaskDraft(id,"系列提醒",dueDate=day,zoneId="UTC",
        recurrence=RecurrenceRule(day,RecurrenceFrequency.DAILY,"UTC",maxCount=6),seriesReminders=specs)
    private suspend fun seed(db:TodoDatabase,repo:TaskRepository) {
        repo.save(draft())
        for(seq in 1..2){val t=db.dao().task(occurrenceId("s",seq))!!;repo.trash(t.id,t.revision);repo.restore(t.id)}
    }
    private suspend fun edit(db:TodoDatabase,specs:List<SeriesReminderSpec>?=null,count:Int=6,date:LocalDate=day):TaskDraft {
        val first=db.dao().task(occurrenceId("s",1))!!
        return TaskDraft(first.id,first.title,first.notes,first.categoryId,date,first.dueTime,"UTC",first.status,first.revision,
            recurrence=RecurrenceRule(date,RecurrenceFrequency.DAILY,"UTC",maxCount=count),expectedSeriesRevision=db.dao().series("s")!!.revision,
            expectedOccurrenceRevisions=db.dao().seriesOccurrences("s").associate{it.taskId to db.dao().task(it.taskId)!!.revision},seriesReminders=specs)
    }
    private suspend fun snapshot(db:TodoDatabase):List<Any> = listOf(db.dao().allTasks(),db.dao().allSeries(),db.dao().allRules(),db.dao().allOccurrences(),
        db.reminders().all(),db.reminders().templatesFor("s"),db.reminders().overrides(),db.reminders().pending())
    @Test fun everyOccurrenceGetsOwnReminderIdsAndBackgroundCatchupIsIdempotent()=database{db,repo,_,fake->
        repo.save(draft());repo.save(draft());assertEquals(1,db.dao().allTasks().size)
        val later=Clock.offset(clock,Duration.ofDays(6));val engine=ReminderEngine(db,fake,later)
        val work=ReminderMaintenanceWork(db,engine,later);work.run(recovery=true);work.run(recovery=true)
        assertEquals(6,db.dao().allTasks().size);assertEquals(12,db.reminders().all().size)
        assertEquals(12,db.reminders().all().map{it.id}.distinct().size)
        db.dao().allTasks().forEach { t->
            val rows=db.reminders().forTask(t.id);assertEquals(2,rows.size);assertNull(t.dueTime)
            assertEquals(t.dueDate!!.minusDays(1).atTime(17,0).toInstant(ZoneOffset.UTC).toEpochMilli(),rows.first{it.kind=="FIXED"}.fixedAt)
        }
        assertTrue(db.reminders().all().any{it.state=="MISSED"});assertTrue(fake.shown.isEmpty())
        assertEquals(db.reminders().all().count{it.resolvedAt!!>later.millis()},fake.alarms.size)
    }
    @Test fun relativeTemplatesGenerateBeforeTheirReminderRatherThanWaitingForDueDate()=database{db,repo,_,fake->
        val early=listOf(SeriesReminderSpec("early",SeriesReminderKind.RELATIVE_DUE,offsetMinutes=7*1440))
        repo.save(draft(specs=early).copy(recurrence=RecurrenceRule(day,RecurrenceFrequency.DAILY,"UTC",maxCount=30)))
        assertTrue(db.dao().allTasks().size>=6)
        ReminderMaintenanceWork(db,ReminderEngine(db,fake,clock),clock).run()
        assertTrue(db.reminders().all().filter{it.resolvedAt!!>clock.millis()}.all{it.alarmKey!=null})
        assertTrue(db.reminders().all().any{it.resolvedAt!!>clock.millis() && it.resolvedAt<clock.millis()+2*86_400_000L})
    }
    @Test fun changingDeadlineMovesRelativeButLeavesExistingFixedInstantsUntilTemplateExplicitlyChanges()=database{db,repo,_,_->
        seed(db,repo);val before=db.reminders().all().associateBy{it.id}
        repo.save(edit(db,date=day.plusDays(2)))
        db.reminders().all().filter{it.enabled}.forEach{row->val old=before[row.id]!!
            if(row.kind=="FIXED")assertEquals(old.fixedAt,row.fixedAt) else assertEquals(old.resolvedAt!!+2*86_400_000L,row.resolvedAt)}
        val changed=templates.map{if(it.kind==SeriesReminderKind.OCCURRENCE_TIME)it.copy(time=LocalTime.of(18,0)) else it}
        repo.save(edit(db,specs=changed,date=day.plusDays(2)))
        assertTrue(db.reminders().all().filter{it.kind=="FIXED"&&it.enabled}.all{row->row.fixedAt!=before[row.id]!!.fixedAt})
    }
    @Test fun singleOverrideSurvivesFutureTemplateEditsAndExplicitResetRestoresCurrentTemplates()=database{db,repo,_,_->
        seed(db,repo);val id=occurrenceId("s",2);val task=db.dao().task(id)!!
        val custom=ReminderSpec("custom",ReminderKind.FIXED,"UTC",fixedAt=clock.millis()+10*86_400_000L)
        repo.save(TaskDraft(id,task.title,dueDate=task.dueDate,zoneId="UTC",expectedRevision=task.revision,reminders=listOf(custom)))
        assertTrue(db.reminders().overridden(id))
        repo.save(edit(db,specs=listOf(templates.first().copy(offsetMinutes=120))))
        assertEquals(listOf(custom),db.reminders().forTask(id).filter{it.enabled}.map{it.spec()})
        val updated=db.dao().task(id)!!
        repo.save(TaskDraft(id,updated.title,dueDate=updated.dueDate,zoneId="UTC",expectedRevision=updated.revision,
            expectedSeriesRevision=db.dao().series("s")!!.revision,resetSeriesReminders=true))
        assertFalse(db.reminders().overridden(id));assertEquals(120,db.reminders().forTask(id).single{it.enabled}.offsetMinutes)
        val clean=db.dao().task(id)!!
        repo.save(TaskDraft(id,clean.title,dueDate=clean.dueDate,zoneId="UTC",expectedRevision=clean.revision,reminders=emptyList()))
        repo.save(edit(db,specs=templates));assertTrue(db.reminders().overridden(id));assertTrue(db.reminders().forTask(id).none{it.enabled})
    }
    @Test fun shorteningCancelsAndExtendingRestoresAlarmsWithoutChangingOccurrenceOrReminderIds()=database{db,repo,_,fake->
        seed(db,repo);val engine=ReminderEngine(db,fake,clock);engine.reconcile();val old=db.reminders().all().associateBy{it.id}
        repo.save(edit(db,count=1));engine.reconcile()
        val second=occurrenceId("s",2);assertTrue(db.dao().occurrence(second)!!.excludedByRule)
        assertTrue(db.reminders().forTask(second).all{it.state=="CANCELED"})
        repo.save(edit(db,count=6));engine.reconcile()
        assertFalse(db.dao().occurrence(second)!!.excludedByRule)
        assertEquals(old.keys,db.reminders().all().map{it.id}.toSet());assertTrue(db.reminders().forTask(second).all{it.state=="PENDING"})
        val stale=old.values.first{it.taskId==second};ReminderEngine(db,fake,Clock.offset(clock,Duration.ofDays(20))).fire(stale.id,stale.alarmKey!!)
        assertTrue(fake.shown.isEmpty())
    }
    @Test fun stopRetainsExistingRemindersWhileSeriesTrashRestorePreservesSingleTrashAndCancels()=database{db,repo,rec,fake->
        seed(db,repo);val engine=ReminderEngine(db,fake,clock);engine.reconcile();val count=db.dao().allTasks().size
        rec.stop("s",db.dao().series("s")!!.revision)
        RecurringRepository(db,Clock.offset(clock,Duration.ofDays(20))).reconcileAll()
        assertEquals(6,fake.alarms.size)
        assertEquals(count,db.dao().allTasks().size)
        val first=db.dao().task(occurrenceId("s",1))!!;repo.trash(first.id,first.revision)
        rec.delete("s",db.dao().series("s")!!.revision,db.dao().allTasks().associate{it.id to it.revision},true);engine.reconcile()
        assertTrue(fake.alarms.isEmpty());rec.restoreSeries("s",db.dao().series("s")!!.revision);engine.reconcile()
        assertNotNull(db.dao().task(first.id)!!.deletedAt);assertTrue(db.reminders().forTask(first.id).all{it.state=="CANCELED"||it.state=="MISSED"})
        assertNull(db.dao().task(occurrenceId("s",2))!!.deletedAt);assertEquals(4,fake.alarms.size)
    }
    @Test fun reminderInsertionFailureRollsBackSeriesTemplatesTasksAndDirtyMarkers()=database{db,repo,_,_->
        val before=snapshot(db)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_template_reminder BEFORE INSERT ON reminders WHEN NEW.kind='FIXED' BEGIN SELECT RAISE(ABORT,'injected'); END")
        try{repo.save(draft());fail()}catch(_:android.database.SQLException){}
        assertEquals(before,snapshot(db));db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_template_reminder")
        seed(db,repo);val stable=snapshot(db)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_replan BEFORE UPDATE ON tasks WHEN NEW.id='${occurrenceId("s",2)}' BEGIN SELECT RAISE(ABORT,'injected'); END")
        try{repo.save(edit(db,specs=templates.map{it.copy(baseTime=LocalTime.of(19,0))}));fail()}catch(_:android.database.SQLException){}
        assertEquals(stable,snapshot(db))
    }
    @Test fun staleFutureEditAndForeignTemplateIdsCannotOverwriteCurrentData()=database{db,repo,_,_->
        seed(db,repo);val stale=edit(db,specs=emptyList());val second=db.dao().task(occurrenceId("s",2))!!
        repo.save(TaskDraft(second.id,"changed",dueDate=second.dueDate,zoneId="UTC",expectedRevision=second.revision,reminders=emptyList()))
        val before=snapshot(db)
        try{repo.save(stale);fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,snapshot(db))
        try{repo.save(draft("other"));fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,snapshot(db))
    }
    @Test fun conversionWithNewTemplatesKeepsAdoptedTaskIdentityAndNeverClonesAbsoluteSingleReminder()=database{db,repo,_,_->
        repo.save(TaskDraft("original","原任務",dueDate=day,zoneId="UTC"));val old=db.dao().task("original")!!
        repo.save(draft("original").copy(expectedRevision=old.revision))
        assertEquals("original",db.dao().seriesOccurrences("original").first().taskId)
        assertEquals(old.createdAt,db.dao().task("original")!!.createdAt);assertEquals(2,db.reminders().forTask("original").size)
    }
    @Test fun failedSeriesDoesNotBlockOtherSeriesOrPersistedStandaloneAlarms()=database{db,repo,_,fake->
        repo.save(draft("bad").copy(title="bad"))
        repo.save(draft("good",templates.map{it.copy(id="good-${it.id}")}))
        repo.save(TaskDraft("standalone","獨立提醒",reminders=listOf(ReminderSpec("independent",ReminderKind.FIXED,"UTC",fixedAt=clock.millis()+30*86_400_000L))))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_one_series BEFORE INSERT ON tasks WHEN NEW.title='bad' BEGIN SELECT RAISE(ABORT,'injected'); END")
        val later=Clock.offset(clock,Duration.ofDays(6));val work=ReminderMaintenanceWork(db,ReminderEngine(db,fake,later),later)
        try{work.run(recovery=true);fail()}catch(_:android.database.SQLException){}
        assertEquals(1,db.dao().seriesOccurrences("bad").size);assertEquals(6,db.dao().seriesOccurrences("good").size)
        assertNotNull(db.reminders().get("independent")!!.alarmKey);assertTrue(fake.shown.isEmpty())
    }
    @Test fun nonEmptySchemaFiveUpgradePreservesReminderDeliveryAndOutboxThroughLatestSchema():Unit=runBlocking {
        val name="v5-templates-${UUID.randomUUID()}.db";val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        val schema=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.chimafok.todo.data.TodoDatabase/5.json").bufferedReader().use{it.readText()}).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file,null).use{old->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()){val entity=entities.getJSONObject(i);val table=entity.getString("tableName");old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=entity.optJSONArray("indices") ?: org.json.JSONArray();for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))}
            val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length())old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO categories VALUES (?,'未分類','UNCATEGORIZED',0,1,1,1)",arrayOf(UNCATEGORIZED_ID))
            old.execSQL("INSERT INTO tasks VALUES ('old','舊任務','保留',?,'2026-10-27',NULL,'UTC','OPEN',NULL,NULL,1,2,7)",arrayOf(UNCATEGORIZED_ID))
            old.execSQL("INSERT INTO reminders VALUES ('r-old','old','FIXED','UTC',1793095200000,NULL,'20:00',1,'DELIVERED',1793095200000,'7:3:1793095200000',NULL,3,1,2)")
            old.execSQL("INSERT INTO schedule_outbox VALUES ('old',9,123)");old.version=5
        }
        val db=TodoDatabase.create(context,name)
        try{assertEquals(7,db.openHelper.readableDatabase.version);assertEquals(7L,db.dao().task("old")!!.revision)
            val reminder=db.reminders().get("r-old")!!;assertEquals("DELIVERED",reminder.state);assertEquals(3L,reminder.revision)
            assertEquals("7:3:1793095200000",reminder.alarmKey);assertTrue(db.reminders().templates().isEmpty());assertTrue(db.reminders().overrides().isEmpty())
            db.openHelper.readableDatabase.query("SELECT generation,processedAt FROM schedule_outbox WHERE taskId='old'").use{assertTrue(it.moveToFirst());assertEquals(9L,it.getLong(0));assertEquals(123L,it.getLong(1))}
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
        }finally{db.close();context.deleteDatabase(name)}
    }
}
