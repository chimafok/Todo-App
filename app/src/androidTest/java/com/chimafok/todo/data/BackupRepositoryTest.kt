package com.chimafok.todo.data

import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    private val context get()=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val now=Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"),ZoneOffset.UTC)
    private fun databases(block:suspend(TodoDatabase,TodoDatabase)->Unit)=runBlocking {
        val names=List(2){"backup-${UUID.randomUUID()}.db"};val a=TodoDatabase.create(context,names[0]);val b=TodoDatabase.create(context,names[1])
        try{block(a,b)}finally{a.close();b.close();names.forEach(context::deleteDatabase)}
    }
    private suspend fun seed(db:TodoDatabase,id:String="one",title:String="任務",at:Clock=now,status:TaskStatus=TaskStatus.OPEN,reminders:List<ReminderSpec>?=null) {
        TaskRepository(db,at).save(TaskDraft(id,title,status=status,dueDate=LocalDate.now(now).plusDays(2),dueTime=LocalTime.of(20,0),zoneId="Asia/Hong_Kong",reminders=reminders))
    }
    private suspend fun doc(db:TodoDatabase)=BackupFormat.decode(BackupRepository(db,now).export("test"))
    private suspend fun apply(target:TodoDatabase,document:BackupDocument,mode:RestoreMode=RestoreMode.REPLACE,choices:Map<String,BackupChoice> = emptyMap()) {
        val repo=BackupRepository(target,now);repo.apply(document,repo.preview(document,mode,choices),choices)
    }
    private suspend fun rejected(block:suspend()->Unit){var failed=false;try{block()}catch(_:Exception){failed=true};assertTrue("Expected rejection",failed)}
    private fun snapshot(db:TodoDatabase):String {
        val sql=db.openHelper.readableDatabase;val names=sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table') ORDER BY name").use { c->buildList { while(c.moveToNext())add(c.getString(0)) } }
        return backupHash(names.associateWith { t->sql.query("SELECT * FROM $t").use { c->buildList { while(c.moveToNext())add(c.columnNames.associateWith { k->c.getColumnIndexOrThrow(k).let { if(c.isNull(it))null else c.getString(it) } }) }.sortedBy(::canonical) } })
    }
    private fun fixed(id:String="r",days:Long=1)=ReminderSpec(id,ReminderKind.FIXED,"UTC",fixedAt=now.millis()+days*86_400_000+123)
    private class Fake:ReminderGateway {
        val scheduled=mutableMapOf<String,Long>();val canceled=mutableListOf<String>();val shown=mutableListOf<String>();var fail=false
        override fun notificationsAllowed()=true
        override fun exactAllowed()=true
        override fun schedule(reminder:ReminderEntity,key:String,triggerAt:Long):String{scheduled[reminder.id+":"+key]=triggerAt;return "EXACT"}
        override fun cancel(reminder:ReminderEntity,key:String){scheduled.remove(reminder.id+":"+key)}
        override fun cancelRemoved(taskId:String,reminderId:String,key:String){if(fail)error("injected");canceled+=key;scheduled.remove(reminderId+":"+key)}
        override fun dismiss(reminderId:String){}
        override fun show(reminder:ReminderEntity,task:TaskEntity,key:String){shown+=reminder.id}
    }
    @Test fun roundTripPreservesUnicodeDeadlinePrecisionTagsRulesAndDeliveryHistory()=databases{a,b->
        seed(a,title="漢😀 quotes",reminders=listOf(fixed(),ReminderSpec("relative",ReminderKind.RELATIVE_DUE,"Asia/Hong_Kong",offsetMinutes=120)))
        OrganizationRepository(a,now).saveTag("tag","重要",null)
        TaskRepository(a,now).addTagsBatch(mapOf("one" to a.dao().task("one")!!.revision),setOf("tag"))
        val date=LocalDate.now(now).plusDays(1)
        TaskRepository(a,now).save(TaskDraft("series","系列",notes="備註",dueDate=date,zoneId="UTC",tagIds=setOf("tag"),
            recurrence=RecurrenceRule(date,RecurrenceFrequency.MONTHLY,"UTC",maxCount=4),seriesReminders=listOf(SeriesReminderSpec("template",SeriesReminderKind.OCCURRENCE_TIME,dayOffset=-1,time=LocalTime.of(9,30)))))
        val d=doc(a);apply(b,d)
        assertEquals(d.data.content(),doc(b).data.content())
        assertEquals(a.dao().allOccurrences(),b.dao().allOccurrences());assertEquals(a.dao().allRules(),b.dao().allRules())
        assertEquals(a.reminders().get("r")!!.fixedAt,b.reminders().get("r")!!.fixedAt)
        assertEquals("ok",b.openHelper.readableDatabase.query("PRAGMA integrity_check").use{it.moveToFirst();it.getString(0)})
    }
    @Test fun exportIncludesTrashSortAndPolicyButOmitsNativeAlarmTokens()=databases{a,_->
        seed(a,reminders=listOf(fixed()));seed(a,"gone");TaskRepository(a,now).trash("gone",a.dao().task("gone")!!.revision)
        HistoryRepository(a,now).apply(HistoryRepository(a,now).preview(30))
        ReminderEngine(a,Fake(),now).reconcile();val text=BackupRepository(a,now).export("test");val root=JSONObject(text);val data=root.getJSONObject("data")
        assertEquals(2,data.getJSONArray("tasks").length());assertFalse(text.contains("alarmKey"));assertTrue(text.contains("sortOrder"));assertEquals(30,data.getJSONArray("settings").getJSONObject(0).getInt("completedRetentionDays"));assertFalse(text.contains("retention_"));assertFalse(text.contains("schedule_outbox"));assertFalse(text.contains("purge_cancellations"))
        assertFalse(data.getJSONArray("tasks").getJSONObject(0).has("revision"))
    }
    @Test fun replaceRestoresFilePreferencesSortAndOriginalCompletionThenGivesRestoreGrace()=databases{a,b->
        seed(a,"complete",at=Clock.offset(now,Duration.ofDays(-45)),status=TaskStatus.COMPLETED)
        HistoryRepository(a,now).apply(HistoryRepository(a,now).preview(90))
        OrganizationRepository(b,now).saveCategory("mine","本機分類",null);seed(b,"old")
        val h=HistoryRepository(b,now);h.apply(h.preview(30));val policy=h.policy();val sort=b.dao().category(UNCATEGORIZED_ID)!!.sortOrder
        apply(b,doc(a));assertNull(b.dao().task("old"));assertEquals(90,h.policy().days);assertTrue(h.policy().revision>policy.revision);assertEquals(a.dao().category(UNCATEGORIZED_ID)!!.sortOrder,b.dao().category(UNCATEGORIZED_ID)!!.sortOrder)
        assertEquals(a.dao().task("complete")!!.completedAt,b.dao().task("complete")!!.completedAt);assertNotNull(b.history().anchors().singleOrNull());h.maintain();assertNull(b.dao().task("complete")!!.deletedAt)
        HistoryRepository(b,Clock.offset(now,Duration.ofDays(90))).maintain();assertNotNull(b.dao().task("complete")!!.deletedAt)
    }
    @Test fun olderMergeCannotOverwriteNewerProgressAndNewerFileCanUpdateIt()=databases{a,b->
        seed(a,title="較舊");seed(b,title="本機較新",at=Clock.offset(now,Duration.ofHours(1)))
        val before=snapshot(b);apply(b,doc(a),RestoreMode.MERGE);assertEquals(before,snapshot(b))
        val old=a.dao().task("one")!!;TaskRepository(a,Clock.offset(now,Duration.ofHours(2))).save(TaskDraft("one","檔案最新",expectedRevision=old.revision))
        apply(b,doc(a),RestoreMode.MERGE);assertEquals("檔案最新",b.dao().task("one")!!.title)
    }
    @Test fun equalTimeRequiresChoiceAndCancelOrLocalChoiceWritesNothing()=databases{a,b->
        seed(a,title="檔案");seed(b,title="本機");val d=doc(a);val repo=BackupRepository(b,now);val p=repo.preview(d,RestoreMode.MERGE)
        assertEquals(1,p.plan.conflicts.size);val before=snapshot(b);rejected{repo.apply(d,p,emptyMap())};assertEquals(before,snapshot(b))
        val key=p.plan.conflicts.single().id;repo.apply(d,p,mapOf(key to BackupChoice.LOCAL));assertEquals(before,snapshot(b))
        repo.apply(d,p,mapOf(key to BackupChoice.FILE));assertEquals("檔案",b.dao().task("one")!!.title)
    }
    @Test fun unknownModificationTimeStaysUnknownAndRequiresChoice()=databases{a,b->
        seed(a,title="檔案");seed(b,title="本機");val json=JSONObject(BackupRepository(a,now).export("test"));json.getJSONObject("data").getJSONArray("tasks").getJSONObject(0).remove("updatedAt")
        val d=BackupFormat.decode(json.toString());assertEquals(0L,d.data.rows("tasks").single()["updatedAt"])
        assertEquals(1,BackupRepository(b,now).preview(d,RestoreMode.MERGE).plan.conflicts.size)
    }
    @Test fun sameIdInTrashNeverResurrectsWithoutExplicitFileChoice()=databases{a,b->
        seed(a,at=Clock.offset(now,Duration.ofDays(1)));seed(b);TaskRepository(b,now).trash("one",b.dao().task("one")!!.revision)
        val d=doc(a);val repo=BackupRepository(b,now);val p=repo.preview(d,RestoreMode.MERGE);assertEquals(1,p.plan.conflicts.size);rejected{repo.apply(d,p,emptyMap())};assertNotNull(b.dao().task("one")!!.deletedAt)
        repo.apply(d,p,mapOf(p.plan.conflicts.single().id to BackupChoice.FILE));assertNull(b.dao().task("one")!!.deletedAt)
    }
    @Test fun staleLocalOrDifferentFilePreviewRejectsBeforeAnyMutation()=databases{a,b->
        seed(a,"incoming");seed(b,"local");val repo=BackupRepository(b,now);val d=doc(a);val p=repo.preview(d,RestoreMode.REPLACE)
        TaskRepository(b,now).setStatus("local",b.dao().task("local")!!.revision,TaskStatus.COMPLETED);var before=snapshot(b);rejected{repo.apply(d,p,emptyMap())};assertEquals(before,snapshot(b))
        val fresh=repo.preview(d,RestoreMode.REPLACE);seed(a,"different");before=snapshot(b);rejected{repo.apply(doc(a),fresh,emptyMap())};assertEquals(before,snapshot(b))
    }
    @Test fun injectedInsertFailureRollsBackDeletedRowsPreferencesAndCancellationQueue()=databases{a,b->
        seed(a,"new");seed(b,reminders=listOf(fixed()));ReminderEngine(b,Fake(),now).reconcile()
        val d=doc(a);val repo=BackupRepository(b,now);val p=repo.preview(d,RestoreMode.REPLACE)
        b.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_backup BEFORE INSERT ON tasks BEGIN SELECT RAISE(ABORT,'injected'); END")
        val before=snapshot(b);rejected{repo.apply(d,p,emptyMap())};assertEquals(before,snapshot(b));assertTrue(b.history().cancellations().isEmpty())
        b.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_backup");repo.apply(d,p,emptyMap());assertNotNull(b.dao().task("new"))
    }
    @Test fun replacementPersistsNativeCancellationAfterOldOutboxWasProcessedAndRetries()=databases{a,b->
        seed(a,"future",reminders=listOf(fixed("new-r")));seed(b,reminders=listOf(fixed()))
        val fake=Fake();val engine=ReminderEngine(b,fake,now);engine.reconcile();val old=b.reminders().get("r")!!.alarmKey!!;assertTrue(b.reminders().pending().isEmpty())
        apply(b,doc(a));assertTrue(b.history().cancellations().any { it.alarmKey==old });fake.fail=true;rejected{engine.reconcile(recovery=true)}
        assertTrue(b.history().cancellations().isNotEmpty());assertTrue(fake.scheduled.keys.any { it.startsWith("new-r:") });fake.fail=false;engine.reconcile(recovery=true)
        assertTrue(b.history().cancellations().isEmpty());assertTrue(old in fake.canceled);assertTrue(fake.shown.isEmpty())
    }
    @Test fun oldNotificationTokenCannotCompleteReplacedTaskEvenWhenIdIsTheSame()=databases{a,b->
        seed(a,title="檔案",reminders=listOf(fixed()));seed(b,title="本機",reminders=listOf(fixed()))
        val fake=Fake();val engine=ReminderEngine(b,fake,now);engine.reconcile();val token=b.reminders().get("r")!!.alarmKey!!;val revision=b.dao().task("one")!!.revision
        apply(b,doc(a));engine.reconcile(recovery=true);assertTrue(b.dao().task("one")!!.revision>revision)
        engine.complete("r",token);assertEquals(TaskStatus.OPEN,b.dao().task("one")!!.status);assertTrue(fake.shown.isEmpty())
    }
    @Test fun importedPastRemindersAreMissedAndTerminalDeliveryNeverReplays()=databases{a,b->
        seed(a,reminders=listOf(fixed("past",-1),fixed("delivered")));val old=a.reminders().get("delivered")!!
        a.reminders().update(old.copy(state="DELIVERED",resolvedAt=old.fixedAt));apply(b,doc(a));assertEquals("MISSED",b.reminders().get("past")!!.state);assertEquals("DELIVERED",b.reminders().get("delivered")!!.state)
        val fake=Fake();val engine=ReminderEngine(b,fake,now);engine.reconcile(recovery=true);assertTrue(fake.scheduled.isEmpty());assertTrue(fake.shown.isEmpty())
    }
    @Test fun partiallyPurgedSeriesKeepsItsCursorAndNeverRecreatesAnOldSlot()=databases{a,b->
        val date=LocalDate.now(now).plusDays(1);TaskRepository(a,now).save(TaskDraft("series","系列",dueDate=date,zoneId="UTC",recurrence=RecurrenceRule(date,RecurrenceFrequency.DAILY,"UTC",maxCount=8)))
        val first=a.dao().allOccurrences().first().taskId;TaskRepository(a,now).trash(first,a.dao().task(first)!!.revision)
        HistoryRepository(a,now).purge(PurgePreview(mapOf(first to a.dao().task(first)!!.revision)));val cursor=a.dao().series("series")!!.cursor
        apply(b,doc(a));assertEquals(cursor,b.dao().series("series")!!.cursor);RecurringRepository(b,Clock.offset(now,Duration.ofDays(5))).reconcileAll()
        assertNull(b.dao().task(first));assertTrue(b.dao().series("series")!!.cursor>=cursor)
    }
    @Test fun seriesWithDifferentSlotSetsMustBeChosenAsAnAggregate()=databases{a,b->
        val date=LocalDate.now(now).plusDays(1);TaskRepository(a,now).save(TaskDraft("series","系列",dueDate=date,zoneId="UTC",recurrence=RecurrenceRule(date,RecurrenceFrequency.DAILY,"UTC",maxCount=8)))
        apply(b,doc(a));val earlier=doc(a);RecurringRepository(b,Clock.offset(now,Duration.ofDays(4))).reconcileAll()
        val repo=BackupRepository(b,now);val p=repo.preview(earlier,RestoreMode.MERGE);assertEquals(1,p.plan.conflicts.size);val before=snapshot(b);repo.apply(earlier,p,mapOf(p.plan.conflicts.single().id to BackupChoice.LOCAL));assertEquals(before,snapshot(b))
    }
    @Test fun systemKindsRemapToCanonicalIdsAndSameNameTagsRemainDistinct()=databases{a,b->
        seed(a);OrganizationRepository(a,now).saveTag("tag","同名",null);val root=JSONObject(BackupRepository(a,now).export("test"));val data=root.getJSONObject("data")
        val cats=data.getJSONArray("categories");for(i in 0 until cats.length()){val c=cats.getJSONObject(i);if(!c.isNull("systemKind")){val old=c.getString("id");val new="external-$i";c.put("id",new);val tasks=data.getJSONArray("tasks");for(j in 0 until tasks.length())if(tasks.getJSONObject(j).getString("categoryId")==old)tasks.getJSONObject(j).put("categoryId",new)}}
        val tags=data.getJSONArray("tags");tags.put(JSONObject(tags.getJSONObject(0).toString()).put("id","different"));val d=BackupFormat.decode(root.toString());apply(b,d)
        assertEquals(UNCATEGORIZED_ID,b.dao().task("one")!!.categoryId);assertEquals(2,b.dao().allTags().size);assertEquals(1,BackupRepository(b,now).preview(d,RestoreMode.MERGE).plan.sameNames)
    }
    @Test fun malformedVersionsTypesIdsDatesAndForeignKeysAreRejectedWithoutTouchingLocalData()=databases{a,b->
        seed(a);seed(b,"local");val text=BackupRepository(a,now).export("test");val before=snapshot(b)
        val mutations:List<(JSONObject)->Unit> = listOf(
            {r->val c=r.getJSONObject("data").getJSONArray("categories");for(i in 0 until c.length())if(c.getJSONObject(i).optString("systemKind")=="UNCATEGORIZED")c.getJSONObject(i).put("name","不可改名")},
            {it.put("formatVersion",99)},{it.put("schemaVersion",99)},{it.getJSONObject("data").put("extra",JSONArray())},
            {it.getJSONObject("data").getJSONArray("tasks").getJSONObject(0).put("alarmKey","not portable")},
            {it.getJSONObject("data").getJSONArray("tasks").getJSONObject(0).put("dueDate","2026-02-30")},
            {it.getJSONObject("data").getJSONArray("tasks").getJSONObject(0).put("status","IN_PROGRESS")},
            {it.getJSONObject("data").getJSONArray("tasks").getJSONObject(0).put("categoryId","missing")},
            {it.getJSONObject("data").getJSONArray("tasks").getJSONObject(0).put("zoneId","Wrong/Zone")},
            {it.getJSONObject("data").getJSONArray("tasks").getJSONObject(0).put("updatedAt",1.5)},
            {it.getJSONObject("data").getJSONArray("tasks").put(JSONObject(it.getJSONObject("data").getJSONArray("tasks").getJSONObject(0).toString()))})
        mutations.forEach { mutate->val value=JSONObject(text);mutate(value);rejected{BackupFormat.decode(value.toString())};assertEquals(before,snapshot(b)) }
    }
    @Test fun strictJsonRejectsDuplicateKeysTrailingPayloadAndMalformedUtf8()=databases{a,_->
        val text=BackupRepository(a,now).export("test")
        rejected{BackupFormat.decode(text.replaceFirst("{","{\"formatVersion\":1,"))};rejected{BackupFormat.decode(text+" {}")}
        rejected{BackupFormat.read(byteArrayOf(0xc3.toByte(),0x28).inputStream())}
        rejected{strictJson("[".repeat(13)+"0"+"]".repeat(13))}
        rejected{strictJson("9223372036854775808")}
    }
    @Test fun boundedStreamingRejectsOversizeBeforeAllocatingTheWholeFile()=databases{_,_->
        val stream=object:java.io.InputStream(){var left=BACKUP_MAX_BYTES.toLong()+1
            override fun read():Int=if(left-- >0)32 else -1
            override fun read(b:ByteArray,off:Int,len:Int):Int{if(left<=0)return -1;val n=minOf(left,len.toLong()).toInt();java.util.Arrays.fill(b,off,off+n,32.toByte());left-=n;return n}}
        rejected{BackupFormat.read(stream)}
    }
    @Test fun malformedSeriesCursorOrUnownedReminderFailsGraphValidation()=databases{a,_->
        val date=LocalDate.now(now).plusDays(1);TaskRepository(a,now).save(TaskDraft("series","系列",dueDate=date,zoneId="UTC",recurrence=RecurrenceRule(date,RecurrenceFrequency.DAILY,"UTC"),
            seriesReminders=listOf(SeriesReminderSpec("template",SeriesReminderKind.RELATIVE_DUE,offsetMinutes=60))))
        val text=BackupRepository(a,now).export("test");var root=JSONObject(text);root.getJSONObject("data").getJSONArray("recurring_series").getJSONObject(0).put("cursor",0);rejected{BackupFormat.decode(root.toString())}
        root=JSONObject(text);root.getJSONObject("data").getJSONArray("reminders").getJSONObject(0).put("id","foreign");rejected{BackupFormat.decode(root.toString())}
    }
}
