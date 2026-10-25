package com.chimafok.todo.data

import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@com.chimafok.todo.Regression104
@RunWith(AndroidJUnit4::class)
class SelectionRepositoryTest {
    private val clock=Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"),ZoneOffset.UTC)
    private fun database(block:suspend(TodoDatabase)->Unit)=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("com.chimafok.todo.qa",context.packageName)
        val name="selection-${UUID.randomUUID()}.db";val db=TodoDatabase.create(context,name)
        try {block(db)} finally {db.close();context.deleteDatabase(name)}
    }
    private suspend fun rejected(block:suspend()->Unit) {var rejected=false;try{block()}catch(_:IllegalArgumentException){rejected=true};assertTrue("Expected atomic rejection",rejected)}
    private suspend fun task(db:TodoDatabase,id:String,deleted:Long?=null,completed:Boolean=false) {
        db.dao().insert(TaskEntity(id,id,"",UNCATEGORIZED_ID,status=if(completed)TaskStatus.COMPLETED else TaskStatus.OPEN,
            completedAt=if(completed)clock.millis()-86400000 else null,deletedAt=deleted,createdAt=1,updatedAt=1))
    }
    private suspend fun series(db:TodoDatabase,id:String,count:Int=4,ended:Boolean=false) {
        val dao=db.dao();val anchor=LocalDate.now(clock).minusDays(1)
        dao.insertSeries(RecurringSeriesEntity(id,id,"",UNCATEGORIZED_ID,lifecycle=if(ended)SeriesLifecycle.ENDED else SeriesLifecycle.ACTIVE,
            cursor=count,createdAt=1,updatedAt=1))
        dao.insertRule(SeriesRuleEntity("$id-rule",id,1,1,anchor,RecurrenceFrequency.DAILY,1,0,null,"UTC",null,if(ended)count else count+2,1))
        (1..count).forEach {n->
            val completed=ended || n==count
            dao.insert(TaskEntity("$id-$n","歷史-$n","",UNCATEGORIZED_ID,dueDate=anchor.plusDays(n-1L),zoneId="UTC",
                status=if(completed)TaskStatus.COMPLETED else TaskStatus.OPEN,completedAt=if(completed)clock.millis() else null,createdAt=n.toLong(),updatedAt=1))
            dao.insertOccurrence(OccurrenceEntity("$id-$n",id,n,"$id-rule"))
        }
    }
    @Test fun restoreAllIsAtomicForStaleOrExpiredTasksAndPreservesCompletionAnchor()=database {db->
        task(db,"a",clock.millis(),true);task(db,"b",clock.millis());task(db,"keep",clock.millis())
        val repo=SelectionRepository(db,clock)
        val preview=repo.previewTrash(mapOf("a" to 1L,"b" to 1L),emptyMap())
        db.dao().update(db.dao().task("b")!!.copy(revision=2))
        rejected {repo.restore(preview)};assertNotNull(db.dao().task("a")!!.deletedAt)
        val expired=db.dao().task("b")!!.copy(deletedAt=clock.millis()-31L*86400000,revision=3);db.dao().update(expired)
        rejected {repo.restore(repo.previewTrash(mapOf("a" to 1L,"b" to 3L),emptyMap()))};assertNotNull(db.dao().task("a")!!.deletedAt)
        repo.restore(repo.previewTrash(mapOf("a" to 1L),emptyMap()))
        assertNull(db.dao().task("a")!!.deletedAt);assertEquals(TaskStatus.COMPLETED,db.dao().task("a")!!.status)
        assertEquals(clock.millis(),db.history().anchors().single {it.taskId=="a"}.restoredAt)
        assertNotNull(db.dao().task("keep")!!.deletedAt)
    }
    @Test fun deletedParentCannotBeBypassedAndWholeSeriesRestoreKeepsEarlierIndividualDeletes()=database {db->
        series(db,"s");task(db,"a",clock.millis())
        db.dao().update(db.dao().task("s-1")!!.copy(deletedAt=clock.millis()-1000,revision=2))
        val recurring=RecurringRepository(db,clock);val action=HistoryBrowserRepository(db).actionPreview("s",clock)
        recurring.delete("s",action.revision,action.tasks,true)
        val repo=SelectionRepository(db,clock)
        val selected=repo.previewTrash(mapOf("a" to 1L,"s-2" to db.dao().task("s-2")!!.revision),emptyMap())
        rejected {repo.restore(selected)};assertNotNull(db.dao().task("a")!!.deletedAt)
        val preview=repo.previewTrash(mapOf("a" to 1L),mapOf("s" to db.dao().series("s")!!.revision))
        assertFalse("s-1" in preview.restoreTasks);assertTrue("s-1" in preview.purge.tasks)
        repo.restore(preview)
        assertNull(db.dao().task("a")!!.deletedAt);assertNull(db.dao().series("s")!!.deletedAt)
        assertNotNull(db.dao().task("s-1")!!.deletedAt);assertNull(db.dao().task("s-2")!!.deletedAt)
    }
    @Test fun trashSeriesPreviewRejectsNewChildrenAndPurgePreservesUnselectedTasks()=database {db->
        series(db,"s");task(db,"keep",clock.millis())
        val recurring=RecurringRepository(db,clock);val action=HistoryBrowserRepository(db).actionPreview("s",clock)
        recurring.delete("s",action.revision,action.tasks,true)
        val repo=SelectionRepository(db,clock);val preview=repo.previewTrash(emptyMap(),mapOf("s" to db.dao().series("s")!!.revision))
        db.dao().update(db.dao().task("s-3")!!.copy(revision=db.dao().task("s-3")!!.revision+1))
        rejected {repo.restore(preview)};rejected {repo.purge(preview)}
        assertNotNull(db.dao().task("s-2"))
        val fresh=repo.previewTrash(emptyMap(),mapOf("s" to db.dao().series("s")!!.revision))
        repo.purge(fresh)
        assertNull(db.dao().series("s"));assertNull(db.dao().task("s-2"));assertNotNull(db.dao().task("keep"))
    }
    @Test fun stoppingSeveralSeriesRejectsAnyStaleChildBeforeChangingTheFirstSeries()=database {db->
        series(db,"a");series(db,"b")
        val repo=SelectionRepository(db,clock);val preview=repo.previewSeries(mapOf("a" to 1L,"b" to 1L))
        db.dao().update(db.dao().task("b-3")!!.copy(revision=2))
        rejected {repo.applySeries(preview,true,false)}
        assertEquals(SeriesLifecycle.ACTIVE,db.dao().series("a")!!.lifecycle)
        val before=db.dao().allTasks()
        repo.applySeries(repo.previewSeries(mapOf("a" to 1L,"b" to 1L)),true,false)
        assertEquals(before,db.dao().allTasks());assertEquals(SeriesLifecycle.STOPPED,db.dao().series("b")!!.lifecycle)
    }
    @Test fun futureOnlySeriesDeletionKeepsTodayPastAndCompletedAndRejectsCrossDayPreview()=database {db->
        series(db,"a");series(db,"b");val repo=SelectionRepository(db,clock)
        val preview=repo.previewSeries(mapOf("a" to 1L,"b" to 1L))
        rejected {SelectionRepository(db,Clock.offset(clock,Duration.ofDays(1))).applySeries(preview,false,false)}
        assertEquals(SeriesLifecycle.ACTIVE,db.dao().series("a")!!.lifecycle)
        repo.applySeries(preview,false,false)
        for(id in listOf("a","b")) {
            assertNull(db.dao().task("$id-1")!!.deletedAt);assertNull(db.dao().task("$id-2")!!.deletedAt)
            assertNotNull(db.dao().task("$id-3")!!.deletedAt);assertNull(db.dao().task("$id-4")!!.deletedAt)
            assertNull(db.dao().series(id)!!.deletedAt)
        }
    }
    @Test fun explicitIncludeHistoryDeletesOnlySelectedSeriesAndNoPartialWriteOnStaleParent()=database {db->
        series(db,"a");series(db,"b");series(db,"keep")
        val repo=SelectionRepository(db,clock);val preview=repo.previewSeries(mapOf("a" to 1L,"b" to 1L))
        db.dao().updateSeries(db.dao().series("b")!!.copy(revision=2))
        rejected {repo.applySeries(preview,false,true)};assertNull(db.dao().task("a-1")!!.deletedAt)
        repo.applySeries(repo.previewSeries(mapOf("a" to 1L,"b" to 2L)),false,true)
        assertNotNull(db.dao().series("a")!!.deletedAt);assertNotNull(db.dao().task("b-1")!!.deletedAt)
        assertNull(db.dao().series("keep")!!.deletedAt);assertNull(db.dao().task("keep-1")!!.deletedAt)
    }
    @Test fun historySelectAllUsesEverySearchMatchBeyondFirstPageAndExcludesReadOnlyRows()=database {db->
        series(db,"s",60,true)
        db.dao().updateOccurrence(db.dao().occurrence("s-1")!!.copy(excludedByRule=true))
        db.dao().update(db.dao().task("s-60")!!.copy(deletedAt=clock.millis(),revision=2))
        db.dao().update(db.dao().task("s-5")!!.copy(title="舊文字",revision=2))
        RecurringRepository(db,clock).snapshot("s-5")
        db.dao().update(db.dao().task("s-5")!!.copy(title="新文字",revision=3))
        val browser=HistoryBrowserRepository(db);val window=browser.window("s","",50)
        val all=browser.selection("s","")
        assertEquals(58,all.size);assertFalse("s-1" in all);assertFalse("s-60" in all)
        assertTrue(all.keys.any {id->window.rows.none {it.task.id==id}})
        assertEquals(mapOf("s-5" to 3L),browser.selection("s","舊文字"))
    }

    @Test fun previewUsesOneInstantEvenIfTheClockCrossesMidnightDuringTheRead()=database {db->
        series(db,"a");series(db,"b")
        val calls=java.util.concurrent.atomic.AtomicInteger()
        val moving=object:Clock() {
            override fun getZone():ZoneId=ZoneOffset.UTC
            override fun withZone(zone:ZoneId):Clock=this
            override fun instant():Instant=if(calls.getAndIncrement()==0)Instant.parse("2026-10-25T23:59:59Z") else Instant.parse("2026-10-26T00:00:01Z")
        }
        val repo=SelectionRepository(db,moving)
        val preview=repo.previewSeries(mapOf("a" to 1L,"b" to 1L))
        assertEquals(setOf(LocalDate.of(2026,10,25)),preview.days.values.toSet())
        assertEquals(listOf(1,1),preview.items.values.map {it.futureCount})
        rejected {repo.applySeries(preview,false,false)}
        assertEquals(SeriesLifecycle.ACTIVE,db.dao().series("a")!!.lifecycle)
        assertNull(db.dao().task("a-3")!!.deletedAt)
    }
}
