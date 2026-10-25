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
class TaskPageRepositoryTest {
    private val clock=Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"),ZoneId.of("Asia/Hong_Kong"))
    private fun database(block:suspend(TodoDatabase)->Unit)=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="pages-${UUID.randomUUID()}.db";val db=TodoDatabase.create(context,name)
        try { seed(db);block(db) } finally { db.close();context.deleteDatabase(name) }
    }
    private suspend fun seed(db:TodoDatabase)=db.withTransaction {
        val dao=db.dao()
        dao.insertTag(TagEntity("a","甲標籤","甲標籤",0,1,1));dao.insertTag(TagEntity("b","乙標籤","乙標籤",1,1,1))
        (1..130).forEach { n->
            dao.insert(TaskEntity("d$n","待辦$n",if(n==130) "頁外 ÄRGER 目標" else "內容$n",UNCATEGORIZED_ID,
                LocalDate.of(2026,10,24).plusDays((n/3).toLong()),dueTime=if(n%2==0)LocalTime.NOON else null,zoneId="Asia/Hong_Kong",
                createdAt=n.toLong(),updatedAt=1))
            dao.insert(TaskEntity("u$n","未安排$n","真正備註$n",UNCATEGORIZED_ID,createdAt=n.toLong(),updatedAt=1))
            dao.insert(TaskEntity("c$n","已完成$n","完成備註$n",UNCATEGORIZED_ID,LocalDate.of(2026,1,1).plusDays((n/2).toLong()),
                zoneId="UTC",status=TaskStatus.COMPLETED,completedAt=n.toLong(),createdAt=n.toLong(),updatedAt=1))
            if(n%2==0)dao.insertTaskTags(listOf(TaskTagEntity("d$n","a")))
            if(n%3==0)dao.insertTaskTags(listOf(TaskTagEntity("d$n","b")))
        }
    }
    @Test fun independentDatedAndUndatedPrefixesKeepCompleteCountsAndRealPayloads()=database {db->
        val repo=TaskPageRepository(db)
        val first=repo.window(TaskScope.AGENDA,TaskFilter(),clock,null,50,50)
        assertEquals(260,first.revisions.size);assertEquals(100,first.rows.tasks.size)
        assertTrue(first.moreDated);assertTrue(first.moreUndated)
        val expected=agendaGroups(db.dao().allTasks(),clock)
        assertEquals(expected.associate { it.date to it.tasks.size },first.groupCounts)
        val dated=expected.filter{it.date!=null}.flatMap{it.tasks}.take(50).map{it.id}.toSet()
        val undated=expected.last().tasks.take(50).map{it.id}.toSet()
        assertEquals(dated+undated,first.rows.tasks.map{it.id}.toSet())
        assertTrue(first.rows.tasks.all{it.title.isNotEmpty()&&it.notes.isNotEmpty()})
        val more=repo.window(TaskScope.AGENDA,TaskFilter(),clock,null,100,50)
        assertEquals(150,more.rows.tasks.size);assertEquals(260,more.revisions.size)
        assertEquals(first.revisions,more.revisions)
        assertEquals(first.groupCounts,more.groupCounts)
        val page=repo.window(TaskScope.UNSCHEDULED,TaskFilter(),clock,null,50,50)
        assertEquals(130,page.revisions.size);assertEquals(50,page.rows.tasks.size)
        assertEquals(mapOf<LocalDate?,Int>(null to 130),page.groupCounts)
        assertEquals("u130",page.rows.tasks.maxBy{it.createdAt}.id)
    }
    @Test fun completeSearchTagAndOrAndRenameFindRowsOutsideTheLoadedPrefix()=database {db->
        val repo=TaskPageRepository(db)
        assertFalse(repo.window(TaskScope.AGENDA,TaskFilter(),clock,null,50,50).rows.tasks.any{it.id=="d130"})
        assertEquals(setOf("d130"),repo.window(TaskScope.AGENDA,TaskFilter("ärger"),clock,null,50,50).revisions.keys)
        val and=repo.window(TaskScope.AGENDA,TaskFilter(tagIds=setOf("a","b")),clock,null,50,50)
        assertEquals((1..130).filter{it%6==0}.map{"d$it"}.toSet(),and.revisions.keys)
        val or=repo.window(TaskScope.AGENDA,TaskFilter(tagIds=setOf("a","b"),matchAllTags=false),clock,null,50,50)
        assertEquals((1..130).filter{it%2==0||it%3==0}.map{"d$it"}.toSet(),or.revisions.keys)
        val tag=db.dao().tag("a")!!;db.dao().updateTag(tag.copy(name="更新標籤",revision=2))
        assertTrue(repo.window(TaskScope.AGENDA,TaskFilter("甲標籤"),clock,null,50,50).revisions.isEmpty())
        assertEquals(65,repo.window(TaskScope.AGENDA,TaskFilter("更新標籤"),clock,null,50,50).revisions.size)
    }
    @Test fun jumpDateAndCrossZoneOverdueOrderingMatchTheOriginalRules()=database {db->
        val dao=db.dao();val repo=TaskPageRepository(db)
        dao.insert(TaskEntity("zone","跨區","原截止保留",UNCATEGORIZED_ID,LocalDate.of(2026,10,24),LocalTime.of(23,30),"America/Los_Angeles",createdAt=0,updatedAt=1))
        dao.insert(TaskEntity("past","逾期","原截止保留",UNCATEGORIZED_ID,LocalDate.of(2026,10,20),LocalTime.NOON,"UTC",createdAt=0,updatedAt=1))
        val jump=LocalDate.of(2026,12,6)
        val result=repo.window(TaskScope.AGENDA,TaskFilter(),clock,jump,5,5)
        val expected=agendaGroups(dao.allTasks(),clock,jump)
        val wanted=expected.filter{it.date!=null}.flatMap{it.tasks}.take(5).map{it.id}.toSet()+
            expected.filter{it.date==jump}.flatMap{it.tasks}.take(5).map{it.id}+expected.last().tasks.take(5).map{it.id}
        assertEquals(wanted,result.rows.tasks.map{it.id}.toSet())
        assertTrue(result.rows.tasks.any{it.dueDate==jump})
        val earlier=LocalDate.of(2026,10,20)
        val past=repo.window(TaskScope.AGENDA,TaskFilter(),clock,earlier,5,5)
        val projected=agendaGroups(past.rows.tasks,clock,earlier)
        assertTrue(projected.single{it.date==earlier}.tasks.any{it.id=="past"})
        assertFalse(projected.first().tasks.any{it.id=="past"})
        assertEquals(LocalDate.of(2026,10,20),dao.task("past")!!.dueDate)
    }
    @Test fun allResultBatchIncludesUnloadedRowsAndStaleUpdatesRemainAtomic()=database {db->
        val repo=TaskPageRepository(db)
        val page=repo.window(TaskScope.UNSCHEDULED,TaskFilter(),clock,null,50,50)
        assertEquals(130,page.revisions.size);assertEquals(50,page.rows.tasks.size)
        TaskRepository(db,clock).addTagsBatch(page.revisions,setOf("a"))
        assertTrue((1..130).all{db.dao().taskTagIds("u$it")==listOf("a")})
        try {TaskRepository(db,clock).trashBatch(page.revisions);fail("accepted stale full selection")}
        catch(_:IllegalArgumentException){}
        assertTrue((1..130).all{db.dao().task("u$it")!!.deletedAt==null})
        val fresh=repo.window(TaskScope.UNSCHEDULED,TaskFilter(),clock,null,50,50)
        TaskRepository(db,clock).trashBatch(fresh.revisions)
        assertTrue(repo.window(TaskScope.UNSCHEDULED,TaskFilter(),clock,null,50,50).revisions.isEmpty())
        assertNotNull(db.dao().task("u1")!!.deletedAt)
        assertNull(db.dao().task("d130")!!.deletedAt)
    }
    @Test fun completedRowsUseOriginalDateAndRecurrenceSelectionPrecedesFiltering()=database {db->
        val repo=TaskPageRepository(db);val dao=db.dao()
        val completed=repo.window(TaskScope.COMPLETED,TaskFilter(),clock,null,50,50)
        assertEquals(130,completed.revisions.size);assertEquals(50,completed.rows.tasks.size)
        assertEquals(completedGroups(dao.allTasks()).filter{it.date!=null}.flatMap{it.tasks}.take(50).map{it.id}.toSet(),completed.rows.tasks.map{it.id}.toSet())
        dao.insertSeries(RecurringSeriesEntity("s","系列","",UNCATEGORIZED_ID,SeriesLifecycle.ENDED,3,createdAt=1,updatedAt=1))
        dao.insertRule(SeriesRuleEntity("r","s",1,1,LocalDate.of(2026,10,20),RecurrenceFrequency.DAILY,1,0,null,"UTC",null,3,1))
        (1..3).forEach{n->dao.insert(TaskEntity("r$n",if(n==1)"隱藏舊項" else "目前系列項","",UNCATEGORIZED_ID,
            LocalDate.of(2026,10,20).plusDays(n.toLong()),zoneId="UTC",createdAt=1,updatedAt=1))
            dao.insertOccurrence(OccurrenceEntity("r$n","s",n,"r",excludedByRule=n==3))}
        assertTrue(repo.window(TaskScope.AGENDA,TaskFilter("隱藏舊項"),clock,null,50,50).revisions.isEmpty())
        assertEquals(setOf("r2"),repo.window(TaskScope.AGENDA,TaskFilter("目前系列項"),clock,null,50,50).revisions.keys)
        assertFalse(repo.window(TaskScope.COMPLETED,TaskFilter(),clock,null,500,500).revisions.containsKey("r2"))
    }
}
