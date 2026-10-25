package com.chimafok.todo.data

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
class ReminderConversionRepositoryTest {
    private val now=Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"),ZoneOffset.UTC)
    private val day=LocalDate.of(2026,10,25)
    private val sources=listOf(ReminderSpec("fixed",ReminderKind.FIXED,"America/New_York",fixedAt=Instant.parse("2026-10-24T10:30:00Z").toEpochMilli()),
        ReminderSpec("relative",ReminderKind.RELATIVE_DUE,"Asia/Hong_Kong",offsetMinutes=90,baseTime=LocalTime.of(18,15)))
    private class Fake:ReminderGateway {
        val alarms=mutableMapOf<String,Long>();val shown=mutableListOf<String>()
        override fun notificationsAllowed()=true
        override fun exactAllowed()=true
        override fun schedule(reminder:ReminderEntity,key:String,triggerAt:Long):String { alarms[key]=triggerAt;return "EXACT" }
        override fun cancel(reminder:ReminderEntity,key:String){alarms.remove(key)}
        override fun show(reminder:ReminderEntity,task:TaskEntity,key:String){shown+=reminder.id}
        override fun cancelRemoved(taskId: String, reminderId: String, key: String) { alarms.remove(key) }
        override fun dismiss(reminderId:String){}
    }
    private fun database(block:suspend(TodoDatabase,TaskRepository)->Unit)=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="conversion-reminders-${UUID.randomUUID()}.db";val db=TodoDatabase.create(context,name)
        try{block(db,TaskRepository(db,now))}finally{db.close();context.deleteDatabase(name)}
    }
    private fun draft()=TaskDraft("original","保留原提醒",dueDate=day,zoneId="Asia/Hong_Kong",reminders=sources)
    private suspend fun seed(db:TodoDatabase,repo:TaskRepository):TaskDraft {
        repo.save(draft());return conversion(db)
    }
    private suspend fun conversion(db:TodoDatabase)=draft().copy(expectedRevision=db.dao().task("original")!!.revision,
        recurrence=RecurrenceRule(day,RecurrenceFrequency.WEEKLY,"Asia/Hong_Kong",maxCount=3),
        seriesReminders=inheritReminderTemplates(sources,day,"Asia/Hong_Kong"),inheritSingleReminders=true)
    private suspend fun snapshot(db:TodoDatabase):List<Any> = listOf(db.dao().allTasks(),db.dao().allSeries(),db.dao().allRules(),db.dao().allOccurrences(),
        db.reminders().all(),db.reminders().templates(),db.reminders().overrides(),db.reminders().pending())
    private suspend fun rejected(block:suspend()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
    private suspend fun advance(db:TodoDatabase,repo:TaskRepository,id:String){val t=db.dao().task(id)!!;repo.setStatus(id,t.revision,TaskStatus.COMPLETED)}

    @Test fun conversionPreservesFirstReminderRowsAndNextInstancesGetIndependentTemplates()=database{db,repo->
        val draft=seed(db,repo);val original=db.dao().task(draft.id)!!;val before=db.reminders().forTask(draft.id)
        repo.save(draft)
        assertEquals(original.createdAt,db.dao().task(draft.id)!!.createdAt)
        assertEquals(before,db.reminders().forTask(draft.id));assertTrue(db.reminders().overridden(draft.id))
        advance(db,repo,draft.id)
        val second=db.dao().task(occurrenceId(draft.id,2))!!;val reminders=db.reminders().forTask(second.id)
        assertEquals(2,reminders.count{it.enabled});assertFalse(db.reminders().overridden(second.id))
        assertEquals(sources.first().fixedAt!!+7*86_400_000L,reminders.single{it.kind=="FIXED"}.fixedAt)
        assertEquals(90,reminders.single{it.kind=="RELATIVE_DUE"}.offsetMinutes)
        assertTrue(reminders.none{r->sources.any{it.id==r.id}})
    }
    @Test fun unconfirmedConversionAndStaleTaskRevisionsRollbackWithoutDroppingReminders()=database{db,repo->
        val draft=seed(db,repo);var before=snapshot(db)
        rejected{repo.save(draft.copy(inheritSingleReminders=false))};assertEquals(before,snapshot(db))
        rejected{repo.save(draft.copy(inheritSingleReminders=false,reminders=null))};assertEquals(before,snapshot(db))
        repo.save(draft.copy(recurrence=null,seriesReminders=null,inheritSingleReminders=false,notes="較新編輯"));before=snapshot(db)
        rejected{repo.save(draft)};assertEquals(before,snapshot(db))
    }
    @Test fun aFailureAfterAdoptingRemindersRollsBackTaskTemplatesOverrideAndOutbox()=database{db,repo->
        val draft=seed(db,repo);val before=snapshot(db)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_conversion BEFORE INSERT ON series_reminders BEGIN SELECT RAISE(ABORT,'injected'); END")
        try{repo.save(draft);fail()}catch(_:android.database.SQLException){}
        assertEquals(before,snapshot(db));db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_conversion")
        repo.save(draft);assertTrue(db.reminders().overridden(draft.id))
    }
    @Test fun newDraftConversionIsAtomicAndRepeatedCreateDoesNotDuplicateFirstOrReminders()=database{db,repo->
        val draft=draft().copy(recurrence=RecurrenceRule(day,RecurrenceFrequency.WEEKLY,"Asia/Hong_Kong",maxCount=3),
            seriesReminders=inheritReminderTemplates(sources,day,"Asia/Hong_Kong"),inheritSingleReminders=true)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_conversion BEFORE INSERT ON reminder_overrides BEGIN SELECT RAISE(ABORT,'injected'); END")
        val before=snapshot(db);try{repo.save(draft);fail()}catch(_:android.database.SQLException){}
        assertEquals(before,snapshot(db));db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_conversion")
        repo.save(draft);val saved=snapshot(db);repo.save(draft);assertEquals(saved,snapshot(db))
        assertEquals("original",db.dao().allOccurrences().single().taskId);assertEquals(2,db.reminders().all().size)
        rejected{repo.save(draft.copy(reminders=sources.drop(1)))}
        rejected{repo.save(draft.copy(inheritSingleReminders=false))}
        rejected{repo.save(draft.copy(dueDate=day.plusDays(1)))}
    }
    @Test fun firstOverrideSurvivesSeriesChangesAndCanExplicitlyResetToCurrentTemplates()=database{db,repo->
        val draft=seed(db,repo);repo.save(draft)
        val first=db.dao().task(draft.id)!!
        val changed=listOf(SeriesReminderSpec("new",SeriesReminderKind.RELATIVE_DUE,offsetMinutes=30))
        repo.save(draft.copy(expectedRevision=first.revision,expectedSeriesRevision=db.dao().series(first.id)!!.revision,
            expectedOccurrenceRevisions=db.dao().seriesOccurrences(first.id).associate{it.taskId to db.dao().task(it.taskId)!!.revision},
            reminders=null,inheritSingleReminders=false,seriesReminders=changed))
        assertEquals(sources.toSet(),db.reminders().forTask(first.id).filter{it.enabled}.map{it.spec()}.toSet())
        val current=db.dao().task(first.id)!!
        repo.save(draft.copy(expectedRevision=current.revision,recurrence=null,seriesReminders=null,reminders=null,inheritSingleReminders=false,
            expectedSeriesRevision=db.dao().series(first.id)!!.revision,resetSeriesReminders=true))
        assertFalse(db.reminders().overridden(first.id));assertEquals(30,db.reminders().forTask(first.id).single{it.enabled}.offsetMinutes)
    }
    @Test fun deliveredAndMissedFirstPointsAreNotReissuedOnConversionOrRecovery() {
        for(state in listOf("DELIVERED","MISSED")) database{db,repo->
            val old=now.millis()-60_000
            val source=ReminderSpec("past",ReminderKind.FIXED,"UTC",fixedAt=old)
            repo.save(draft().copy(reminders=listOf(source)))
            val draft=conversion(db).copy(reminders=listOf(source),seriesReminders=inheritReminderTemplates(listOf(source),day,"Asia/Hong_Kong"))
            db.reminders().update(db.reminders().get(source.id)!!.copy(state=state))
            repo.save(draft)
            val fake=Fake();ReminderEngine(db,fake,now).reconcile(force=true,recovery=true)
            assertEquals(state,db.reminders().get(source.id)!!.state);assertTrue(fake.shown.isEmpty())
            assertFalse(fake.alarms.values.any{it==old})
        }
    }
    @Test fun changingDeadlineKeepsFirstFixedInstantAndRecalculatesItsRelativeOffset()=database{db,repo->
        val draft=seed(db,repo);val before=db.reminders().get("relative")!!.resolvedAt!!
        val next=day.plusDays(1);repo.save(draft.copy(dueDate=next,recurrence=draft.recurrence!!.copy(anchor=next),
            seriesReminders=inheritReminderTemplates(sources,next,"Asia/Hong_Kong")))
        assertEquals(sources.first().fixedAt,db.reminders().get("fixed")!!.fixedAt)
        assertEquals(before+86_400_000L,db.reminders().get("relative")!!.resolvedAt)
        assertTrue(db.reminders().overridden("original"))
    }

    @Test fun theLaterDstFoldKeepsItsOriginalFirstInstantAndInvalidConversionDoesNotMutate()=database{db,repo->
        val firstDate=LocalDate.of(2026,11,1)
        val source=ReminderSpec("fold",ReminderKind.FIXED,"America/New_York",fixedAt=Instant.parse("2026-11-01T06:30:00Z").toEpochMilli())
        repo.save(TaskDraft("fold-task","DST",dueDate=firstDate,zoneId="America/New_York",reminders=listOf(source)))
        val first=db.dao().task("fold-task")!!
        val draft=TaskDraft(first.id,first.title,dueDate=firstDate,zoneId=first.zoneId,expectedRevision=first.revision,
            recurrence=RecurrenceRule(firstDate,RecurrenceFrequency.DAILY,first.zoneId!!,maxCount=3),reminders=listOf(source),
            seriesReminders=inheritReminderTemplates(listOf(source),firstDate,first.zoneId),inheritSingleReminders=true)
        val before=snapshot(db)
        rejected{repo.save(draft.copy(reminders=listOf(source.copy(fixedAt=source.fixedAt!!+1))))};assertEquals(before,snapshot(db))
        repo.save(draft);assertEquals(source.fixedAt,db.reminders().get("fold")!!.fixedAt)
        advance(db,repo,first.id)
        assertEquals(Instant.parse("2026-11-02T06:30:00Z").toEpochMilli(),db.reminders().forTask(occurrenceId(first.id,2)).single().fixedAt)
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }
}
