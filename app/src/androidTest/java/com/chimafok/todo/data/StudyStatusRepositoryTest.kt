package com.chimafok.todo.data

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

@RunWith(AndroidJUnit4::class)
class StudyStatusRepositoryTest {
    private val context get()=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val clock=Clock.fixed(Instant.parse("2030-02-03T10:00:00Z"),ZoneOffset.UTC)
    private val day=LocalDate.now(clock)
    private fun databases(block:suspend(TodoDatabase,TodoDatabase)->Unit)=runBlocking {
        assertEquals("com.chimafok.todo.qa",context.packageName)
        val names=List(2){"study-status-${UUID.randomUUID()}.db"}
        val a=TodoDatabase.create(context,names[0]);val b=TodoDatabase.create(context,names[1])
        try {block(a,b)}finally{a.close();b.close();names.forEach(context::deleteDatabase)}
    }
    private suspend fun rejected(block:suspend()->Unit){var rejected=false;try{block()}catch(_:IllegalArgumentException){rejected=true};assertTrue(rejected)}
    @Test fun learningCreationStatusChangesCompletionReopeningAndStaleWrites()=databases { a,_ ->
        val r=TaskRepository(a,clock)
        rejected { r.save(TaskDraft("complete","不准直接完成",categoryId=STUDY_ID,status=TaskStatus.COMPLETED)) }
        assertNull(a.dao().task("complete"))
        r.save(TaskDraft("study","學習",categoryId=STUDY_ID,status=TaskStatus.IN_PROGRESS))
        val initial=a.dao().task("study")!!;assertNull(initial.completedAt)
        r.setStatus(initial.id,initial.revision,TaskStatus.COMPLETED)
        val done=a.dao().task(initial.id)!!;assertEquals(clock.millis(),done.completedAt)
        rejected {r.setStatus(initial.id,initial.revision,TaskStatus.OPEN)}
        r.setStatus(done.id,done.revision,TaskStatus.IN_PROGRESS)
        val reopened=a.dao().task(done.id)!!;assertNull(reopened.completedAt)
        r.setStatus(reopened.id,reopened.revision,TaskStatus.OPEN)
        rejected {r.setStatus(reopened.id,a.dao().task(reopened.id)!!.revision,TaskStatus.DONE_UNSUBMITTED)}
    }
    @Test fun existingOpenLearningTasksAndDeletedProgressArePreserved()=databases { a,_ ->
        val r=TaskRepository(a,clock)
        a.dao().insert(TaskEntity("old","舊學習",notes="",categoryId=STUDY_ID,createdAt=1,updatedAt=1))
        val old=a.dao().task("old")!!;r.setStatus(old.id,old.revision,TaskStatus.IN_PROGRESS)
        r.trash(old.id,a.dao().task(old.id)!!.revision)
        assertEquals(TaskStatus.IN_PROGRESS,a.dao().task(old.id)!!.status)
        rejected {r.setStatus(old.id,a.dao().task(old.id)!!.revision,TaskStatus.COMPLETED)}
        r.restore(old.id);assertEquals(TaskStatus.IN_PROGRESS,a.dao().task(old.id)!!.status)
    }
    @Test fun recurringLearningFirstProgressDoesNotMarkFutureOccurrencesStarted()=databases { a,_ ->
        val r=TaskRepository(a,clock)
        val draft=TaskDraft("series","重複學習",categoryId=STUDY_ID,dueDate=day,zoneId="UTC",status=TaskStatus.IN_PROGRESS,
            recurrence=RecurrenceRule(day,RecurrenceFrequency.DAILY,"UTC",maxCount=3))
        r.save(draft)
        val first=a.dao().task(occurrenceId("series",1))!!;assertEquals(TaskStatus.IN_PROGRESS,first.status)
        r.setStatus(first.id,first.revision,TaskStatus.COMPLETED)
        val second=a.dao().task(occurrenceId("series",2))!!;assertEquals(TaskStatus.OPEN,second.status)
        r.save(draft);assertEquals(TaskStatus.COMPLETED,a.dao().task(first.id)!!.status)
        rejected {r.save(draft.copy(id="bad",status=TaskStatus.COMPLETED))};assertNull(a.dao().series("bad"))
    }
    @Test fun batchMovesAndCategoryDeletionConvertLearningProgress()=databases { a,_ ->
        val r=TaskRepository(a,clock)
        r.save(TaskDraft("study","學習",categoryId=STUDY_ID,status=TaskStatus.IN_PROGRESS))
        fun current()=runBlocking {a.dao().task("study")!!}
        r.moveBatch(mapOf("study" to current().revision),STUDY_ID,emptyMap());assertEquals(TaskStatus.IN_PROGRESS,current().status)
        r.moveBatch(mapOf("study" to current().revision),ASSIGNMENTS_ID,mapOf("study" to TaskStatus.DONE_UNSUBMITTED))
        r.moveBatch(mapOf("study" to current().revision),STUDY_ID,emptyMap());assertEquals(TaskStatus.IN_PROGRESS,current().status)
        val study=a.dao().category(STUDY_ID)!!
        OrganizationRepository(a,clock).deleteCategory(STUDY_ID,study.revision,UNCATEGORIZED_ID,mapOf("study" to current().revision),emptyMap())
        assertEquals(TaskStatus.OPEN,current().status);assertEquals(UNCATEGORIZED_ID,current().categoryId)
    }
    @Test fun formatThreeRoundTripsLearningProgressAndTrashAndReadsFormatTwo()=databases { a,b ->
        val r=TaskRepository(a,clock)
        r.save(TaskDraft("study","學習",categoryId=STUDY_ID,status=TaskStatus.IN_PROGRESS))
        r.trash("study",a.dao().task("study")!!.revision)
        val exported=BackupRepository(a,clock).export("1.2.0-dev01")
        val root=JSONObject(exported);assertEquals(4,root.getInt("formatVersion"));assertEquals(9,root.getInt("schemaVersion"))
        val backup=BackupFormat.decode(exported);val target=BackupRepository(b,clock)
        target.apply(backup,target.preview(backup,RestoreMode.REPLACE),emptyMap())
        assertEquals(TaskStatus.IN_PROGRESS,b.dao().task("study")!!.status);assertNotNull(b.dao().task("study")!!.deletedAt)
        val old=JSONObject(exported);old.put("formatVersion",2).put("schemaVersion",8)
        val data = old.getJSONObject("data"); data.remove("note_images")
        listOf("tasks", "recurring_series").forEach { table -> val rows = data.getJSONArray(table); for (n in 0 until rows.length()) rows.getJSONObject(n).remove("imageIds") }
        rejected {BackupFormat.decode(old.toString())}
        old.getJSONObject("data").getJSONArray("tasks").getJSONObject(0).put("status","OPEN")
        assertEquals("OPEN",BackupFormat.decode(old.toString()).data.rows("tasks").single().text("status"))
    }
    @Test fun futureTemplateEditKeepsLearningProgressAndStartDate()=databases {a,_->
        val r=TaskRepository(a,clock)
        r.save(TaskDraft("edit","學習",categoryId=STUDY_ID,dueDate=day,zoneId="UTC",startDate=day.minusDays(1),
            recurrence=RecurrenceRule(day,RecurrenceFrequency.DAILY,"UTC",maxCount=3)))
        val first=a.dao().task(occurrenceId("edit",1))!!;r.setStatus(first.id,first.revision,TaskStatus.IN_PROGRESS)
        val current=a.dao().task(first.id)!!;val series=a.dao().series("edit")!!
        val rule=a.dao().latestRule(series.id)!!
        val revisions=a.dao().seriesOccurrences(series.id).associate{it.taskId to a.dao().task(it.taskId)!!.revision}
        r.save(TaskDraft(current.id,"學習更新",categoryId=STUDY_ID,dueDate=day,zoneId="UTC",status=TaskStatus.IN_PROGRESS,
            expectedRevision=current.revision,expectedSeriesRevision=series.revision,
            expectedOccurrenceRevisions=revisions,
            recurrence=rule.rule(),startDate=day.minusDays(1)))
        assertEquals(TaskStatus.IN_PROGRESS,a.dao().task(first.id)!!.status)
        assertEquals(day.minusDays(1),a.dao().task(first.id)!!.startDate)
    }
}
