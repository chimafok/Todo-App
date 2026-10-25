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

@RunWith(AndroidJUnit4::class)
class HistoryBrowserTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC)
    private fun database(block: suspend (TodoDatabase) -> Unit) = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "browser-${UUID.randomUUID()}.db"
        val db = TodoDatabase.create(context, name)
        try { seed(db); block(db) } finally { db.close(); context.deleteDatabase(name) }
    }
    private suspend fun seed(db: TodoDatabase) = db.withTransaction {
        val dao = db.dao()
        dao.insertSeries(RecurringSeriesEntity("s", "測試系列", "", UNCATEGORIZED_ID, SeriesLifecycle.ENDED, 123, createdAt=1, updatedAt=1))
        dao.insertRule(SeriesRuleEntity("r", "s", 1, 1, LocalDate.of(2026,1,1), RecurrenceFrequency.DAILY, 1, 0, null, "UTC", null, 123, 1))
        (1..123).forEach { n ->
            dao.insert(TaskEntity("t$n", "entry$n", "", UNCATEGORIZED_ID, LocalDate.of(2026,1,1).plusDays((n/2).toLong()), zoneId="UTC",
                status=if(n==123) TaskStatus.OPEN else TaskStatus.COMPLETED, completedAt=if(n==123) null else 2,
                deletedAt=if(n==122) clock.millis() else null, createdAt=1, updatedAt=1))
            val snapshot = OccurrenceSnapshot(if(n==1) "ÄRGER 舊名稱" else "entry$n", if(n==1) "原備註" else "", "舊分類", listOf("原標籤"), "COMPLETED", false).json()
            dao.insertOccurrence(OccurrenceEntity("t$n", "s", n, "r", snapshot, excludedByRule=n==121))
        }
        dao.insert(TaskEntity("ordinary", "一般完成", "", UNCATEGORIZED_ID, status=TaskStatus.COMPLETED, completedAt=1, createdAt=1, updatedAt=1))
        dao.insertTag(TagEntity("tag", "當前標籤", "當前標籤", 0, 1, 1))
        dao.insertTaskTags(listOf(TaskTagEntity("t1","tag"), TaskTagEntity("t123","tag")))
        listOf("t1","t123").forEach { id ->
            db.reminders().insert(ReminderEntity("rem-$id",id,"RELATIVE","UTC",null,30,LocalTime.NOON,createdAt=1,updatedAt=1))
            db.reminders().override(ReminderOverrideEntity(id))
        }
    }
    @Test fun scopedReadsExcludeCompletedHistoryAndKeepEveryRelationConsistent() = database { db ->
        val read = db.browser()
        assertEquals(setOf("ordinary","t123"), read.tasks(false,null).map { it.id }.toSet())
        assertEquals(listOf("t123"),read.occurrences(false,null).map { it.taskId })
        assertEquals(listOf("t123"),read.taskTags(false,null).map { it.taskId })
        assertEquals(listOf("t123"),read.reminders(false,null).map { it.taskId })
        assertEquals(listOf("t123"),read.overrides(false,null).map { it.taskId })
        assertEquals(124,read.tasks(false,"s").size)
        assertEquals(123,read.occurrences(false,"s").size)
        assertEquals(2,read.reminders(false,"s").size)
        assertEquals(124,read.tasks(true,null).size)
        assertEquals(2,read.overrides(true,null).size)
    }
    @Test fun windowsUseCompleteCountsStableTiesAndPrefixRefreshAfterDeletion() = database { db ->
        val repo=HistoryBrowserRepository(db)
        val first=repo.window("s","",50)
        assertEquals(50,first.rows.size); assertEquals(122,first.total)
        assertEquals(SeriesCounts("s",120,1,1),first.counts["s"])
        val all=db.dao().seriesOccurrences("s").map { it to db.dao().task(it.taskId)!! }
            .filter { it.second.deletedAt==null }.sortedByDescending { it.second.dueDate }.map { it.first.taskId }
        assertEquals(all.take(50),first.rows.map { it.task.id })
        val second=repo.window("s","",100)
        assertEquals(all.take(100),second.rows.map { it.task.id })
        TaskRepository(db,clock).trash(first.rows.first().task.id,1)
        val refreshed=repo.window("s","",100)
        assertEquals(121,refreshed.total);assertEquals(100,refreshed.rows.map { it.task.id }.toSet().size)
        assertEquals(all.drop(1).take(100),refreshed.rows.map { it.task.id })
    }
    @Test fun fullSearchFindsOffPageSnapshotsUnicodeAndLiveTagsWithoutJsonKeyMatches() = database { db ->
        val repo=HistoryBrowserRepository(db)
        assertFalse(repo.window("s","",50).rows.any { it.task.id=="t1" })
        for (query in listOf("ärger","原備註","當前標籤")) {
            assertTrue(repo.window("s",query,50).rows.any { it.task.id=="t1" })
            assertEquals(setOf("s"),repo.window(null,query,50).matchingSeries)
        }
        assertEquals(122,repo.window("s","原標籤",50).total)
        assertEquals(50,repo.window("s","原標籤",50).rows.size)
        assertEquals(0,repo.window("s","late",50).total)
        assertEquals(0,repo.window("s","entry122",50).total)
        val tag=db.dao().tag("tag")!!;db.dao().updateTag(tag.copy(name="已重新命名",revision=2))
        assertEquals(0,repo.window("s","當前標籤",50).total)
        assertEquals(2,repo.window("s","已重新命名",50).total)
        // Exercise multiple 512-row scan chunks, beyond the visible page and
        // beyond the first temporary search batch in the same series.
        db.withTransaction {
            val series=db.dao().series("s")!!
            db.dao().updateSeries(series.copy(cursor=1100))
            db.openHelper.writableDatabase.execSQL("UPDATE series_rules SET maxCount=1100 WHERE id='r'")
            (124..1100).forEach { n ->
                val task=TaskEntity("t$n","entry$n",if(n==124) "第二批尾端" else if(n==1027) "序號尾端" else "",UNCATEGORIZED_ID,
                    LocalDate.of(2026,1,1).plusDays((n/2).toLong()),zoneId="UTC",status=TaskStatus.COMPLETED,completedAt=2,createdAt=1,updatedAt=1)
                db.dao().insert(task)
                db.dao().insertOccurrence(OccurrenceEntity(task.id,"s",n,"r",OccurrenceSnapshot(task.title,task.notes,"舊分類",emptyList(),"COMPLETED",false).json()))
            }
        }
        // Edited deadlines can disagree with sequence order; seek by the
        // deadline and sequence tuple rather than assuming monotonically dated slots.
        val moved=db.dao().task("t500")!!
        db.dao().update(moved.copy(dueDate=LocalDate.of(2036,1,1)))
        val movedBack=db.dao().task("t700")!!
        db.dao().update(movedBack.copy(dueDate=LocalDate.of(2026,1,2)))
        val expected=db.dao().seriesOccurrences("s").map { db.dao().task(it.taskId)!! }
            .filter { it.deletedAt==null }.sortedByDescending { it.dueDate }.map { it.id }
        val across=repo.window("s","entry",600)
        assertEquals(1099,across.total);assertEquals(600,across.rows.size)
        assertEquals(600,across.rows.map { it.task.id }.toSet().size)
        assertEquals(expected.take(600),across.rows.map { it.task.id })
        assertEquals(expected,repo.window("s","entry",1200).rows.map { it.task.id })
        assertEquals(setOf("s"),repo.window(null,"序號尾端",50).matchingSeries)
        assertEquals(listOf("t124"),repo.window("s","第二批尾端",50).rows.map { it.task.id })
        assertEquals(setOf("s"),repo.window(null,"第二批尾端",50).matchingSeries)
    }
    @Test fun overviewSearchIncludesSeriesNamesAndFiltersDeletedParentsAndRows() = database { db ->
        val repo=HistoryBrowserRepository(db)
        assertEquals(setOf("s"),repo.window(null,"測試系列",50).matchingSeries)
        assertEquals(0,repo.window("s","測試系列",50).total)
        assertTrue(repo.window(null,"entry122",50).matchingSeries.isEmpty())
        val dao=db.dao()
        dao.insertSeries(RecurringSeriesEntity("empty","空的保留系列","",UNCATEGORIZED_ID,SeriesLifecycle.ENDED,createdAt=1,updatedAt=1))
        dao.insertRule(SeriesRuleEntity("empty-r","empty",1,1,LocalDate.of(2026,1,1),RecurrenceFrequency.DAILY,1,0,null,"UTC",null,1,1))
        assertEquals(setOf("empty"),repo.window(null,"空的保留系列",50).matchingSeries)
        dao.insertSeries(RecurringSeriesEntity("gone","被刪系列","",UNCATEGORIZED_ID,SeriesLifecycle.TRASHED,1,
            deletedAt=clock.millis(),previousLifecycle=SeriesLifecycle.ENDED,createdAt=1,updatedAt=1))
        dao.insertRule(SeriesRuleEntity("gone-r","gone",1,1,LocalDate.of(2026,1,1),RecurrenceFrequency.DAILY,1,0,null,"UTC",null,1,1))
        dao.insert(TaskEntity("gone-task","被刪系列文字","",UNCATEGORIZED_ID,LocalDate.of(2026,1,1),zoneId="UTC",createdAt=1,updatedAt=1))
        dao.insertOccurrence(OccurrenceEntity("gone-task","gone",1,"gone-r"))
        assertTrue(repo.window(null,"被刪系列",50).matchingSeries.isEmpty())
        assertEquals(0,repo.window("gone","被刪系列",50).total)
    }
    @Test fun substringSearchKeepsUnicodeLiteralSymbolsAndSeparateSnapshotFields() = database { db ->
        val repo=HistoryBrowserRepository(db)
        val original=db.dao().task("t1")!!
        val ref=db.dao().occurrence("t1")!!
        db.dao().updateOccurrence(ref.copy(snapshot=OccurrenceSnapshot("","","",emptyList(),"COMPLETED",false).json()))
        for ((source,query) in listOf("I" to "ı","İ" to "i","K" to "k","Σ" to "ς","𐐀" to "𐐨","Ä" to "ä")) {
            db.dao().update(original.copy(title=source))
            val expected=source.contains(query,ignoreCase=true)
            assertEquals("Unicode $source/$query",expected,repo.window("s",query,200).rows.any { it.task.id=="t1" })
        }
        db.dao().update(original.copy(title="alpha",notes="beta"))
        db.dao().updateOccurrence(ref.copy(snapshot=OccurrenceSnapshot("foo","bar","",listOf("merge","word"),"COMPLETED",false).json()))
        for (query in listOf("hab","foobar","mergeword","late","false","COMPLETED")) {
            assertEquals(query,0,repo.window("s",query,200).total)
        }
        db.dao().update(original.copy(title="literal %_ quote's 中文"))
        for (query in listOf("%_","quote's","中文")) {
            assertEquals(listOf("t1"),repo.window("s",query,50).rows.map { it.task.id })
            assertEquals(setOf("s"),repo.window(null,query,50).matchingSeries)
        }
        // Reusing the repository across edits must not cache a previous query's snapshot.
        db.dao().updateOccurrence(ref.copy(snapshot=OccurrenceSnapshot("新快照","","",emptyList(),"COMPLETED",false).json()))
        assertEquals(1,repo.window("s","新快照",50).total)
        db.dao().updateOccurrence(ref.copy(snapshot=OccurrenceSnapshot("另一版本","","",emptyList(),"COMPLETED",false).json()))
        assertEquals(0,repo.window("s","新快照",50).total)
        assertEquals(1,repo.window("s","另一版本",50).total)
    }
    @Test fun seriesActionPreviewIncludesOffPageTrashRevisionsAndRejectsStaleScope() = database { db ->
        val repo=HistoryBrowserRepository(db)
        val preview=repo.actionPreview("s",clock)
        assertEquals(123,preview.tasks.size);assertEquals(122,preview.activeCount);assertEquals(0,preview.futureCount)
        assertEquals(1L,preview.tasks["t122"])
        val original=db.dao().task("t1")!!;db.dao().update(original.copy(notes="updated",revision=2))
        try { RecurringRepository(db,clock).delete("s",preview.revision,preview.tasks,true);fail("stale preview accepted") }
        catch (_:IllegalArgumentException) { }
        assertNull(db.dao().task("t1")!!.deletedAt)
        val fresh=repo.actionPreview("s",clock)
        RecurringRepository(db,clock).delete("s",fresh.revision,fresh.tasks,true)
        assertEquals(0,repo.window("s","",50).total)
        assertEquals(123,db.browser().trashTasks().size)
    }
    @Test fun maintenanceCandidatesPreserveRecurringHistoryAndRestoredRetentionAnchors() = database { db ->
        assertEquals(listOf("ordinary"),db.browser().retentionTasks().map { it.id })
        assertTrue(db.browser().hasTrash());assertTrue(db.browser().hasReminders())
        val history=HistoryRepository(db,clock)
        val preview=history.preview(30);assertEquals(setOf("ordinary"),preview.tasks.keys)
        history.apply(preview)
        TaskRepository(db,clock).restore("ordinary")
        assertTrue(history.preview(30).tasks.isEmpty())
        history.maintain()
        assertNull(db.dao().task("ordinary")!!.deletedAt)
        assertNull(db.dao().task("t1")!!.deletedAt)
        assertTrue(db.browser().unsnapshotted("s").isEmpty())
    }
}
