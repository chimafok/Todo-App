package com.chimafok.todo.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimafok.todo.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StudyBackupV2Test {
    private val context get()=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val clock=Clock.fixed(Instant.parse("2030-02-03T10:00:00Z"),ZoneOffset.UTC)
    private val day=LocalDate.now(clock)
    private fun databases(block:suspend(TodoDatabase,TodoDatabase)->Unit)=runBlocking {
        assertEquals("com.chimafok.todo.qa",context.packageName)
        val names=List(2){"study-v2-${UUID.randomUUID()}.db"};val a=TodoDatabase.create(context,names[0]);val b=TodoDatabase.create(context,names[1])
        try{block(a,b)}finally{a.close();b.close();names.forEach(context::deleteDatabase)}
    }
    private suspend fun rejected(block:suspend()->Unit){var no=false;try{block()}catch(_:IllegalArgumentException){no=true};assertTrue(no)}
    private suspend fun document(db:TodoDatabase)=BackupFormat.decode(BackupRepository(db,clock).export("1.1.0-dev01"))
    private suspend fun replace(db:TodoDatabase,d:BackupDocument){val r=BackupRepository(db,clock);r.apply(d,r.preview(d,RestoreMode.REPLACE),emptyMap())}
    @Test fun cannotCreateCompletedAssignmentButCanCompleteAnExistingOne()=databases{a,_->
        val r=TaskRepository(a,clock)
        rejected{r.save(TaskDraft("assignment","作業",categoryId=ASSIGNMENTS_ID,status=TaskStatus.COMPLETED))}
        assertNull(a.dao().task("assignment"))
        r.save(TaskDraft("assignment","作業",categoryId=ASSIGNMENTS_ID,status=TaskStatus.NOT_STARTED))
        r.setStatus("assignment",a.dao().task("assignment")!!.revision,TaskStatus.COMPLETED)
        assertEquals(TaskStatus.COMPLETED,a.dao().task("assignment")!!.status)
    }
    @Test fun savesStartWithoutDeadlineAndPaginationUsesStartOrder()=databases{a,_->
        val r=TaskRepository(a,clock)
        r.save(TaskDraft("late","晚",categoryId=STUDY_ID,startDate=day.plusDays(1),startZoneId="UTC"))
        r.save(TaskDraft("early","早",categoryId=STUDY_ID,startDate=day,startTime=LocalTime.NOON,startZoneId="UTC"))
        r.save(TaskDraft("blank","無開始",categoryId=STUDY_ID))
        val window=TaskPageRepository(a).window(TaskScope.UNSCHEDULED,TaskFilter(),clock,null,1,1)
        assertEquals(listOf("early"),window.rows.tasks.map{it.id});assertEquals(3,window.revisions.size)
        assertNull(a.dao().task("early")!!.dueDate)
        rejected{r.save(TaskDraft("bad","錯誤",categoryId=STUDY_ID,startTime=LocalTime.NOON,startZoneId="UTC"))}
        assertNull(a.dao().task("bad"))
    }
    @Test fun recurringStartsKeepTheirRelativeDateAndCapturedZone()=databases{a,_->
        TaskRepository(a,clock).save(TaskDraft("series","閱讀",categoryId=STUDY_ID,dueDate=day,zoneId="UTC",
            startDate=day.minusDays(1),startTime=LocalTime.of(9,15),startZoneId="Asia/Hong_Kong",
            recurrence=RecurrenceRule(day,RecurrenceFrequency.DAILY,"UTC",maxCount=3)))
        RecurringRepository(a,Clock.offset(clock,Duration.ofDays(2))).reconcileAll()
        val tasks=a.dao().allTasks();assertEquals(3,tasks.size)
        tasks.forEach{assertEquals(it.dueDate!!.minusDays(1),it.startDate);assertEquals(LocalTime.of(9,15),it.startTime);assertEquals("Asia/Hong_Kong",it.startZoneId)}
    }
    @Test fun trashOrdersAndSettingsRoundTripAndSeriesRestoreKeepsEarlierSingleDeletion()=databases{a,b->
        val r=TaskRepository(a,clock)
        r.save(TaskDraft("gone","垃圾桶",categoryId=STUDY_ID,startDate=day,startTime=LocalTime.NOON,startZoneId="UTC"))
        r.trash("gone",a.dao().task("gone")!!.revision)
        r.save(TaskDraft("series","系列",dueDate=day.minusDays(1),zoneId="UTC",recurrence=RecurrenceRule(day.minusDays(1),RecurrenceFrequency.DAILY,"UTC",maxCount=2)))
        val refs=a.dao().seriesOccurrences("series");assertEquals(2,refs.size)
        val first=a.dao().task(refs.first().taskId)!!;r.trash(first.id,first.revision)
        val series=a.dao().series("series")!!
        RecurringRepository(a,clock).delete(series.id,series.revision,a.dao().seriesOccurrences(series.id).associate{it.taskId to a.dao().task(it.taskId)!!.revision},true)
        val order=a.dao().allCategories().map{it.id}
        OrganizationRepository(a,clock).move(STUDY_ID,false,1,order)
        OrganizationRepository(a,clock).saveTag("tag","排序標籤",null)
        HistoryRepository(a,clock).apply(HistoryRepository(a,clock).preview(90))
        val d=document(a);replace(b,d)
        assertEquals(d.data.content(),document(b).data.content())
        assertEquals(a.dao().allOccurrences(),b.dao().allOccurrences())
        assertEquals(a.dao().allCategories().map{it.id},b.dao().allCategories().map{it.id});assertEquals(90,b.history().policy()!!.days)
        assertEquals(a.dao().task("gone")!!.deletedAt,b.dao().task("gone")!!.deletedAt)
        val restored=b.dao().series("series")!!;RecurringRepository(b,clock).restoreSeries(restored.id,restored.revision)
        assertNotNull(b.dao().task(first.id)!!.deletedAt)
        assertNull(b.dao().task(refs.last().taskId)!!.deletedAt)
    }
    @Test fun differentSettingsRequireExplicitChoiceAndLegacyImportPreservesMissingPreferences()=databases{a,b->
        HistoryRepository(a,clock).apply(HistoryRepository(a,clock).preview(30))
        HistoryRepository(b,clock).apply(HistoryRepository(b,clock).preview(90))
        val r=BackupRepository(b,clock);val d=document(a);val p=r.preview(d,RestoreMode.MERGE)
        assertEquals(listOf("P/general"),p.plan.conflicts.map{it.id})
        rejected{r.apply(d,p,emptyMap())};assertEquals(90,b.history().policy()!!.days)
        r.apply(d,p,mapOf("P/general" to BackupChoice.FILE));assertEquals(30,b.history().policy()!!.days)
        HistoryRepository(b,clock).apply(HistoryRepository(b,clock).preview(365))
        val root=JSONObject(BackupRepository(a,clock).export("legacy"));root.put("formatVersion",1).put("schemaVersion",7)
        val data=root.getJSONObject("data");data.remove("settings")
        legacyBackupTables.forEach{(t,s)->val rows=data.getJSONArray(t);for(i in 0 until rows.length()){
            val row=rows.getJSONObject(i);row.keys().asSequence().toList().filter{key->s.fields.none{it.name==key}}.forEach(row::remove)
        }}
        replace(b,BackupFormat.decode(root.toString()));assertEquals(365,b.history().policy()!!.days)
    }
    @Test fun invalidNewBackupFieldsAreRejectedBeforeAnyWrite()=databases{a,b->
        TaskRepository(a,clock).save(TaskDraft("validate","驗證",categoryId=STUDY_ID))
        val encoded=BackupRepository(a,clock).export("test")
        val mutations:List<(JSONObject)->Unit> = listOf(
            {it.getJSONObject("data").getJSONArray("tasks").getJSONObject(0).put("startTime","09:00")},
            {it.getJSONObject("data").getJSONArray("categories").getJSONObject(0).put("sortOrder",-1)},
            {it.getJSONObject("data").getJSONArray("settings").getJSONObject(0).put("completedRetentionDays",-1)}
        )
        for(mutate in mutations){val value=JSONObject(encoded);mutate(value);rejected{replace(b,BackupFormat.decode(value.toString()))};assertTrue(b.dao().allTasks().isEmpty())}
    }
    @Test fun schema7MigrationPreservesTasksAndAddsNullableStarts()=runBlocking {
        val name="study-migration-${UUID.randomUUID()}.db";val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("com.chimafok.todo.data.TodoDatabase/7.json").bufferedReader().use{JSONObject(it.readText()).getJSONObject("database")}
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file,null).use{sql->
            val entities=schema.getJSONArray("entities");for(i in 0 until entities.length()){
                val e=entities.getJSONObject(i);sql.execSQL(e.getString("createSql").replace("${'$'}{TABLE_NAME}",e.getString("tableName")))
                val indexes=e.optJSONArray("indices") ?: JSONArray();for(j in 0 until indexes.length())sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}",e.getString("tableName")))
            }
            sql.execSQL("INSERT INTO categories VALUES(?, '未分類','UNCATEGORIZED',0,1,1,1)",arrayOf(UNCATEGORIZED_ID))
            sql.execSQL("INSERT INTO tasks(id,title,notes,categoryId,status,createdAt,updatedAt,revision) VALUES('legacy','保留任務','原內容',?,'OPEN',1,2,3)",arrayOf(UNCATEGORIZED_ID))
            sql.version=7
        }
        val db=TodoDatabase.create(context,name)
        try{val task=db.dao().task("legacy")!!;assertEquals("原內容",task.notes);assertEquals(3L,task.revision);assertNull(task.startDate);assertNull(task.startTime);assertEquals(8,db.openHelper.readableDatabase.version)}finally{db.close();context.deleteDatabase(name)}
    }
}
