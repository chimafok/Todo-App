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
class HistoryRepositoryTest {
    private val context get()=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val now=Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"),ZoneOffset.UTC)
    private fun later(days:Long)=Clock.offset(now,Duration.ofDays(days))
    private fun database(block:suspend(TodoDatabase,TaskRepository,HistoryRepository)->Unit)=runBlocking {
        val name="history-${UUID.randomUUID()}.db";val db=TodoDatabase.create(context,name)
        try{block(db,TaskRepository(db,now),HistoryRepository(db,now))}finally{db.close();context.deleteDatabase(name)}
    }
    private suspend fun completed(db:TodoDatabase,id:String="complete",age:Long=45):TaskEntity {
        TaskRepository(db,later(-age)).save(TaskDraft(id,"完成-$id",status=TaskStatus.COMPLETED))
        return db.dao().task(id)!!
    }
    private suspend fun rejected(block:suspend()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
    private suspend fun snapshot(db:TodoDatabase):List<Any?> = listOf(db.dao().allTasks(),db.dao().allOccurrences(),db.dao().allSeries(),db.dao().allRules(),
        db.reminders().all(),db.reminders().templates(),db.reminders().overrides(),db.reminders().pending(),db.history().policy(),db.history().anchors(),db.history().cancellations())
    private suspend fun seedSeries(db:TodoDatabase,repo:TaskRepository,id:String="series") {
        val day=LocalDate.now(now).plusDays(1)
        repo.save(TaskDraft(id,"循環",dueDate=day,zoneId="UTC",recurrence=RecurrenceRule(day,RecurrenceFrequency.DAILY,"UTC",maxCount=6),
            seriesReminders=listOf(SeriesReminderSpec("template-$id",SeriesReminderKind.RELATIVE_DUE,offsetMinutes=60))))
    }
    private class Fake:ReminderGateway {
        val alarms=mutableMapOf<String,Long>();val canceled=mutableListOf<String>();val dismissed=mutableListOf<String>();val shown=mutableListOf<String>()
        var fail=false
        override fun notificationsAllowed()=true
        override fun exactAllowed()=true
        override fun schedule(reminder:ReminderEntity,key:String,triggerAt:Long):String{alarms["${reminder.id}:$key"]=triggerAt;return "EXACT"}
        override fun cancel(reminder:ReminderEntity,key:String){alarms.remove("${reminder.id}:$key")}
        override fun cancelRemoved(taskId:String,reminderId:String,key:String){if(fail)error("injected cancellation failure");alarms.remove("$reminderId:$key");canceled+=key}
        override fun dismiss(reminderId:String){dismissed+=reminderId}
        override fun show(reminder:ReminderEntity,task:TaskEntity,key:String){shown+=reminder.id}
    }
    @Test fun defaultPermanentRetentionKeepsCompletedOpenAndRecurringHistory()=database{db,repo,history->
        val old=completed(db,age=1000);repo.save(TaskDraft("open","未完成"));seedSeries(db,repo)
        val first=db.dao().allOccurrences().first().taskId
        repo.setStatus(first,db.dao().task(first)!!.revision,TaskStatus.COMPLETED)
        val before=snapshot(db);history.maintain();assertEquals(before,snapshot(db));assertNull(history.policy().days)
        assertEquals(old,db.dao().task(old.id))
    }
    @Test fun confirmedPolicyArchivesOnlyOrdinaryExpiredCompletionsAndStartsThirtyDaysNow()=database{db,repo,history->
        val old=completed(db);completed(db,"fresh",29);repo.save(TaskDraft("open","未完成"));seedSeries(db,repo)
        val first=db.dao().allOccurrences().first().taskId
        repo.setStatus(first,db.dao().task(first)!!.revision,TaskStatus.COMPLETED)
        val done=db.dao().task(first)!!;db.dao().update(done.copy(completedAt=later(-100).millis()))
        val before=snapshot(db);val preview=history.preview(30);assertEquals(mapOf(old.id to old.revision),preview.tasks);assertEquals(before,snapshot(db))
        history.apply(preview);val archived=db.dao().task(old.id)!!
        assertEquals(now.millis(),archived.deletedAt);assertEquals(old.completedAt,archived.completedAt)
        assertNull(db.dao().task("fresh")!!.deletedAt);assertNull(db.dao().task(first)!!.deletedAt);assertNull(db.dao().task("open")!!.deletedAt)
        assertEquals(30,history.policy().days);history.maintain();assertNotNull(db.dao().task(old.id))
        HistoryRepository(db,Clock.offset(later(30),Duration.ofMillis(-1))).maintain();assertNotNull(db.dao().task(old.id))
        HistoryRepository(db,later(30)).maintain();assertNull(db.dao().task(old.id))
    }
    @Test fun stalePreviewTaskPolicyAndBoundaryChangesCannotPartiallyApply()=database{db,repo,history->
        val old=completed(db);val preview=history.preview(30)
        repo.setStatus(old.id,old.revision,TaskStatus.OPEN);var before=snapshot(db)
        rejected{history.apply(preview)};assertEquals(before,snapshot(db))
        val newer=history.preview(90);history.apply(history.preview(365));before=snapshot(db)
        rejected{history.apply(newer)};assertEquals(before,snapshot(db))
        completed(db,"boundary",29);val boundary=history.preview(30);before=snapshot(db)
        rejected{HistoryRepository(db,later(1)).apply(boundary)};assertEquals(before,snapshot(db))
    }
    @Test fun applyingPolicyRollsBackTheSettingAndAllArchivesOnInjectedFailure()=database{db,_,history->
        completed(db,"one");completed(db,"two");val preview=history.preview(30);val before=snapshot(db)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_archive BEFORE UPDATE ON tasks WHEN NEW.id='two' BEGIN SELECT RAISE(ABORT,'injected'); END")
        try{history.apply(preview);fail()}catch(_:android.database.SQLException){}
        assertEquals(before,snapshot(db));db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_archive")
        history.apply(preview);assertTrue(db.dao().allTasks().all{it.deletedAt==now.millis()})
    }
    @Test fun restoringAnExpiredCompletionPreservesItsActualCompletionAndGetsANewRetentionWindow()=database{db,repo,history->
        val old=completed(db);history.apply(history.preview(30));repo.restore(old.id)
        val restored=db.dao().task(old.id)!!;assertEquals(old.completedAt,restored.completedAt)
        history.maintain();assertEquals(restored,db.dao().task(old.id))
        HistoryRepository(db,later(29)).maintain();assertNull(db.dao().task(old.id)!!.deletedAt)
        HistoryRepository(db,later(30)).maintain();assertEquals(later(30).millis(),db.dao().task(old.id)!!.deletedAt)
        HistoryRepository(db,later(60)).maintain();assertNull(db.dao().task(old.id));assertTrue(db.history().anchors().isEmpty())
    }
    @Test fun reopeningAndRecompletingStartsANewCycleAndDropsTheRestoreAnchor()=database{db,repo,history->
        val old=completed(db);history.apply(history.preview(30));repo.restore(old.id)
        val t=db.dao().task(old.id)!!;repo.setStatus(t.id,t.revision,TaskStatus.OPEN)
        assertTrue(db.history().anchors().isEmpty());val open=db.dao().task(t.id)!!
        TaskRepository(db,later(5)).setStatus(open.id,open.revision,TaskStatus.COMPLETED)
        HistoryRepository(db,later(34)).maintain();assertNull(db.dao().task(t.id)!!.deletedAt)
        HistoryRepository(db,later(35)).maintain();assertEquals(later(35).millis(),db.dao().task(t.id)!!.deletedAt)
    }
    @Test fun purgeRejectsActiveStaleAndChangedWholeTrashPreviewsWithoutTouchingOthers()=database{db,repo,history->
        repo.save(TaskDraft("one","一"));repo.save(TaskDraft("two","二"));val one=db.dao().task("one")!!
        var before=snapshot(db);rejected{history.purge(PurgePreview(mapOf(one.id to one.revision)))};assertEquals(before,snapshot(db))
        repo.trash(one.id,one.revision);val deleted=db.dao().task(one.id)!!;val preview=PurgePreview(mapOf(one.id to deleted.revision),allTrash=true)
        val two=db.dao().task("two")!!;repo.trash(two.id,two.revision);before=snapshot(db)
        rejected{history.purge(preview)};assertEquals(before,snapshot(db))
        repo.restore(one.id);before=snapshot(db);rejected{history.purge(PurgePreview(preview.tasks))};assertEquals(before,snapshot(db))
    }
    @Test fun purgePersistsDetachedCancellationAndAPlatformFailureRetriesWithoutBlockingOtherAlarms()=database{db,repo,history->
        val spec=ReminderSpec("alarm",ReminderKind.FIXED,"UTC",fixedAt=now.millis()+3_600_000)
        repo.save(TaskDraft("one","一",reminders=listOf(spec)));val fake=Fake();val engine=ReminderEngine(db,fake,now);engine.reconcile()
        val oldKey=db.reminders().get(spec.id)!!.alarmKey!!;val task=db.dao().task("one")!!;repo.trash(task.id,task.revision)
        history.purge(PurgePreview(mapOf(task.id to db.dao().task(task.id)!!.revision)))
        assertNull(db.dao().task(task.id));assertNull(db.reminders().get(spec.id));assertTrue(db.reminders().pending().isEmpty())
        assertEquals(oldKey,db.history().cancellations().single().alarmKey)
        repo.save(TaskDraft("other","保留提醒",reminders=listOf(spec.copy(id="other-alarm"))))
        fake.fail=true;try{engine.reconcile();fail()}catch(_:IllegalStateException){}
        assertNotNull(db.reminders().get("other-alarm")!!.alarmKey);assertFalse(db.history().cancellations().isEmpty())
        fake.fail=false;engine.reconcile(force=true);assertTrue(db.history().cancellations().isEmpty());assertTrue(oldKey in fake.canceled)
        val other=db.reminders().get("other-alarm")!!;assertTrue("${other.id}:${other.alarmKey}" in fake.alarms)
        assertTrue(spec.id in fake.dismissed);assertTrue(fake.shown.isEmpty());engine.fire(spec.id,oldKey);engine.complete(spec.id,oldKey)
        assertEquals(TaskStatus.OPEN,db.dao().task("other")!!.status)
    }
    @Test fun aPurgeFailureRollsBackRemindersLinksDirtyMarkersAndDetachedCancellations()=database{db,repo,history->
        repo.save(TaskDraft("one","保留",reminders=listOf(ReminderSpec("r",ReminderKind.FIXED,"UTC",fixedAt=now.millis()+60_000))))
        val task=db.dao().task("one")!!;repo.trash(task.id,task.revision);val preview=PurgePreview(mapOf(task.id to db.dao().task(task.id)!!.revision));val before=snapshot(db)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_purge BEFORE DELETE ON tasks BEGIN SELECT RAISE(ABORT,'injected'); END")
        try{history.purge(preview);fail()}catch(_:android.database.SQLException){}
        assertEquals(before,snapshot(db));db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_purge")
        history.purge(preview);assertNull(db.dao().task(task.id));assertFalse(db.history().cancellations().isEmpty())
    }
    @Test fun deletingARecurringSlotKeepsCursorAndNeverRegeneratesTheDeletedIdentity()=database{db,repo,history->
        seedSeries(db,repo);val recurring=RecurringRepository(db,now)
        val second=db.dao().task(occurrenceId("series",2))!!;repo.trash(second.id,second.revision)
        val cursor=db.dao().series("series")!!.cursor
        history.purge(PurgePreview(mapOf(second.id to db.dao().task(second.id)!!.revision)))
        recurring.reconcileAll();assertNull(db.dao().task(second.id));assertEquals(cursor,db.dao().series("series")!!.cursor)
        val newest=db.dao().seriesOccurrences("series").maxBy{it.sequence};val current=db.dao().task(newest.taskId)!!
        repo.setStatus(current.id,current.revision,TaskStatus.COMPLETED)
        assertNull(db.dao().task(second.id));assertTrue(db.dao().series("series")!!.cursor>=cursor)
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }
    @Test fun partialSeriesPurgeRestoresOnlyRemainingAndAnEmptyTrashGroupCanBeRestored()=database{db,repo,history->
        seedSeries(db,repo);val recurring=RecurringRepository(db,now);val group=db.dao().series("series")!!
        val tasks=db.dao().seriesOccurrences(group.id).associate{it.taskId to db.dao().task(it.taskId)!!.revision}
        recurring.delete(group.id,group.revision,tasks,true);val oldRevision=db.dao().series(group.id)!!.revision
        val first=tasks.keys.first();history.purge(PurgePreview(mapOf(first to db.dao().task(first)!!.revision)))
        rejected{recurring.restoreSeries(group.id,oldRevision)}
        recurring.restoreSeries(group.id,db.dao().series(group.id)!!.revision)
        assertNull(db.dao().task(first));assertTrue(db.dao().seriesOccurrences(group.id).all{db.dao().task(it.taskId)!!.deletedAt==null})
        val current=db.dao().series(group.id)!!;recurring.delete(group.id,current.revision,db.dao().seriesOccurrences(group.id).associate{it.taskId to db.dao().task(it.taskId)!!.revision},true)
        for(ref in db.dao().seriesOccurrences(group.id)){history.purge(PurgePreview(mapOf(ref.taskId to db.dao().task(ref.taskId)!!.revision)))}
        assertNotNull(db.dao().series(group.id));recurring.restoreSeries(group.id,db.dao().series(group.id)!!.revision)
        assertNull(db.dao().series(group.id)!!.deletedAt);assertNull(db.dao().task(first))
    }
    @Test fun backgroundMaintenancePurgesExpiredGroupsAndAllDependentsWhilePreservingOtherSeries()=database{db,repo,_->
        seedSeries(db,repo);seedSeries(db,repo,"other")
        val recurring=RecurringRepository(db,now);val group=db.dao().series("series")!!
        recurring.delete(group.id,group.revision,db.dao().seriesOccurrences(group.id).associate{it.taskId to db.dao().task(it.taskId)!!.revision},true)
        val fake=Fake();ReminderMaintenanceWork(db,ReminderEngine(db,fake,later(30)),later(30)).run(recovery=true)
        assertNull(db.dao().series(group.id));assertTrue(db.dao().allOccurrences().none{it.seriesId==group.id})
        assertTrue(db.dao().allRules().none{it.seriesId==group.id});assertTrue(db.reminders().templatesFor(group.id).isEmpty());assertTrue(db.history().cancellations().isEmpty())
        assertNotNull(db.dao().series("other"));assertTrue(db.dao().allTasks().any{it.title=="循環"})
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }
    @Test fun versionSixMigrationAddsOnlyEmptyHistoryTablesAndKeepsExistingData():Unit=runBlocking {
        val name="history-v6-${UUID.randomUUID()}.db";val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        val schema=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.chimafok.todo.data.TodoDatabase/6.json").bufferedReader().use{it.readText()}).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file,null).use{old->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()){val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=entity.optJSONArray("indices")?:org.json.JSONArray();for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))}
            val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length())old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO categories VALUES (?,'未分類','UNCATEGORIZED',0,1,1,1)",arrayOf(UNCATEGORIZED_ID))
            old.execSQL("INSERT INTO tasks VALUES ('old','舊完成','保留',?,NULL,NULL,NULL,'COMPLETED',100,NULL,1,2,7)",arrayOf(UNCATEGORIZED_ID))
            old.execSQL("INSERT INTO reminders VALUES ('r-old','old','FIXED','UTC',100,NULL,'20:00',1,'DELIVERED',100,'7:3:100',NULL,3,1,2)")
            old.execSQL("INSERT INTO schedule_outbox VALUES ('old',9,123)");old.version=6
        }
        val db=TodoDatabase.create(context,name)
        try{assertEquals(7,db.openHelper.readableDatabase.version);assertEquals(7L,db.dao().task("old")!!.revision);assertEquals(100L,db.dao().task("old")!!.completedAt)
            val r=db.reminders().get("r-old")!!;assertEquals("DELIVERED",r.state);assertEquals("7:3:100",r.alarmKey)
            assertNull(db.history().policy());assertTrue(db.history().anchors().isEmpty());assertTrue(db.history().cancellations().isEmpty())
            HistoryRepository(db,now).maintain();assertNotNull(db.dao().task("old"));assertEquals(r,db.reminders().get("r-old"))
            db.openHelper.readableDatabase.query("SELECT generation,processedAt FROM schedule_outbox WHERE taskId='old'").use{assertTrue(it.moveToFirst());assertEquals(9L,it.getLong(0));assertEquals(123L,it.getLong(1))}
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
        }finally{db.close();context.deleteDatabase(name)}
    }

    @Test fun detachedCancellationSurvivesDatabaseCloseAndDrainsWithoutAnyTaskOutbox():Unit=runBlocking {
        val name="history-reopen-${UUID.randomUUID()}.db";var db=TodoDatabase.create(context,name);val fake=Fake()
        try {
            val repo=TaskRepository(db,now);repo.save(TaskDraft("old","可刪",reminders=listOf(ReminderSpec("r",ReminderKind.FIXED,"UTC",fixedAt=now.millis()+60_000))))
            ReminderEngine(db,fake,now).reconcile();val key=db.reminders().get("r")!!.alarmKey!!
            val task=db.dao().task("old")!!;repo.trash(task.id,task.revision)
            HistoryRepository(db,now).purge(PurgePreview(mapOf(task.id to db.dao().task(task.id)!!.revision)))
            db.close();db=TodoDatabase.create(context,name)
            assertNull(db.dao().task(task.id));assertTrue(db.reminders().pending().isEmpty());assertEquals(key,db.history().cancellations().single().alarmKey)
            ReminderEngine(db,fake,now).reconcile();assertTrue(db.history().cancellations().isEmpty());assertTrue(fake.alarms.isEmpty())
        }finally{db.close();context.deleteDatabase(name)}
    }
    @Test fun purgingCompletedTrashCascadesOnlyItsLinksAndGraceAnchorNotTheTagsOrCategories()=database{db,repo,history->
        val old=completed(db);OrganizationRepository(db,now).saveTag("tag","保留標籤",null)
        repo.save(TaskDraft(old.id,old.title,status=TaskStatus.COMPLETED,expectedRevision=old.revision,tagIds=setOf("tag")))
        history.apply(history.preview(30));repo.restore(old.id);val restored=db.dao().task(old.id)!!;repo.trash(old.id,restored.revision)
        assertFalse(db.history().anchors().isEmpty());assertFalse(db.dao().allTaskTags().isEmpty())
        history.purge(PurgePreview(mapOf(old.id to db.dao().task(old.id)!!.revision)))
        assertTrue(db.dao().allTaskTags().isEmpty());assertTrue(db.history().anchors().isEmpty());assertNotNull(db.dao().tag("tag"))
        assertEquals(4,db.dao().allCategories().size)
    }
}
