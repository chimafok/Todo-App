package com.chimafok.todo.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimafok.todo.domain.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecurringRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun clock(date: String) = Clock.fixed(Instant.parse("${date}T12:00:00Z"), ZoneOffset.UTC)
    private fun database(block: suspend (TodoDatabase) -> Unit) = runBlocking {
        val name = "test-recurring-${UUID.randomUUID()}.db"
        val db = TodoDatabase.create(context, name)
        try { block(db) } finally { db.close(); context.deleteDatabase(name) }
    }
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }
    private fun draft(id: String, date: String, frequency: RecurrenceFrequency = RecurrenceFrequency.DAILY, count: Int = 5,
        categoryId: String = UNCATEGORIZED_ID, tags: Set<String> = emptySet()): TaskDraft {
        val start = LocalDate.parse(date)
        return TaskDraft(id, "重複 $id", categoryId = categoryId, dueDate = start, zoneId = "UTC",
            status = if (categoryId == ASSIGNMENTS_ID) TaskStatus.NOT_STARTED else TaskStatus.OPEN, tagIds = tags,
            recurrence = RecurrenceRule(start, frequency, "UTC", maxCount = count))
    }

    @Test fun offlineCatchupKeepsUnfinishedHistoryAndConcurrentCompletionCreatesOneNext() = database { db ->
        val initial = draft("s", "2026-10-20", categoryId = ASSIGNMENTS_ID)
        TaskRepository(db, clock("2026-10-20")).save(initial)
        TaskRepository(db, clock("2026-10-20")).save(initial)
        val recurring = RecurringRepository(db, clock("2026-10-22"))
        recurring.reconcileAll(); recurring.reconcileAll()
        var refs = db.dao().seriesOccurrences("s")
        assertEquals(3, refs.size)
        val tasks = db.dao().allTasks()
        assertTrue(tasks.all { it.status == TaskStatus.NOT_STARTED })
        assertNotNull(refs.first().snapshot)
        assertEquals(setOf(refs[1].taskId), recurringHomeTaskIds(tasks, refs.map { RecurringTaskRef(it.taskId, it.seriesId, it.sequence) }, clock("2026-10-22")))
        val last = db.dao().task(refs.last().taskId)!!
        coroutineScope { (1..8).map { async { TaskRepository(db, clock("2026-10-22")).setStatus(last.id, last.revision, TaskStatus.COMPLETED) } }.awaitAll() }
        refs = db.dao().seriesOccurrences("s")
        assertEquals(4, refs.size)
        assertEquals(4, db.dao().series("s")!!.cursor)
        assertEquals(TaskStatus.NOT_STARTED, db.dao().task(refs.last().taskId)!!.status)
        assertEquals(LocalDate.of(2026, 10, 23), db.dao().task(refs.last().taskId)!!.dueDate)
        assertEquals(3, db.dao().allTasks().count { it.status != TaskStatus.COMPLETED })
        RecurringRepository(db, clock("2026-10-25")).reconcileAll()
        assertEquals(5, db.dao().seriesOccurrences("s").size)
        assertEquals(SeriesLifecycle.ENDED, db.dao().series("s")!!.lifecycle)
    }

    @Test fun deleteSingleRestoreAndStopNeverRewindOrConsumeExtraSlots() = database { db ->
        val time = clock("2026-10-22")
        val tasks = TaskRepository(db, time)
        val recurring = RecurringRepository(db, time)
        tasks.save(draft("s", "2026-10-25"))
        val first = db.dao().task(occurrenceId("s", 1))!!
        tasks.trash(first.id, first.revision); tasks.trash(first.id, first.revision)
        assertEquals(2, db.dao().seriesOccurrences("s").size)
        tasks.restore(first.id)
        tasks.setStatus(first.id, db.dao().task(first.id)!!.revision, TaskStatus.COMPLETED)
        assertEquals(2, db.dao().series("s")!!.cursor)
        val series = db.dao().series("s")!!
        recurring.stop("s", series.revision)
        val second = db.dao().task(occurrenceId("s", 2))!!
        tasks.setStatus(second.id, second.revision, TaskStatus.COMPLETED)
        RecurringRepository(db, clock("2026-11-01")).reconcileAll()
        assertEquals(2, db.dao().seriesOccurrences("s").size)
        assertEquals(SeriesLifecycle.STOPPED, db.dao().series("s")!!.lifecycle)
    }

    @Test fun monthlyTemplateEditPreserves31stAndFailedAdvanceRollsBackCompletion() = database { db ->
        val time = clock("2026-01-01")
        val tasks = TaskRepository(db, time)
        tasks.save(draft("s", "2026-01-31", RecurrenceFrequency.MONTHLY, 4))
        val first = db.dao().task(occurrenceId("s", 1))!!
        tasks.setStatus(first.id, first.revision, TaskStatus.COMPLETED)
        val second = db.dao().task(occurrenceId("s", 2))!!
        val series = db.dao().series("s")!!
        tasks.save(TaskDraft(second.id, "更新未來名稱", categoryId = second.categoryId, dueDate = second.dueDate, zoneId = second.zoneId,
            expectedRevision = second.revision, recurrence = RecurrenceRule(second.dueDate!!, RecurrenceFrequency.MONTHLY, "UTC", maxCount = 6),
            expectedSeriesRevision = series.revision, expectedOccurrenceRevisions = db.dao().allTasks().associate { it.id to it.revision }))
        assertEquals(LocalDate.of(2026, 1, 31), db.dao().latestRule("s")!!.anchor)
        assertEquals("重複 s", db.dao().task(first.id)!!.title)
        assertEquals("重複 s", OccurrenceSnapshot.read(db.dao().occurrence(first.id)!!.snapshot)!!.title)
        val before = db.dao().task(second.id)!!
        val cursor = db.dao().series("s")!!
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_next BEFORE INSERT ON occurrences WHEN NEW.sequence = 3 BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        try { tasks.setStatus(before.id, before.revision, TaskStatus.COMPLETED); fail("Expected failure") } catch (_: android.database.SQLException) { }
        assertEquals(before, db.dao().task(before.id))
        assertEquals(cursor, db.dao().series("s"))
        assertNull(db.dao().task(occurrenceId("s", 3)))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_next")
        tasks.setStatus(before.id, before.revision, TaskStatus.COMPLETED)
        val third = db.dao().task(occurrenceId("s", 3))!!
        assertEquals(LocalDate.of(2026, 3, 31), third.dueDate)
        assertEquals("更新未來名稱", third.title)
        assertEquals(2, db.dao().allRules().size)
    }

    @Test fun seriesDeleteRestoreKeepsPreviouslyDeletedOccurrenceInTrash() = database { db ->
        val time = clock("2026-10-22")
        val tasks = TaskRepository(db, time)
        val recurring = RecurringRepository(db, time)
        tasks.save(draft("s", "2026-10-25"))
        val first = db.dao().task(occurrenceId("s", 1))!!
        tasks.trash(first.id, first.revision)
        val before = db.dao().series("s")!!
        recurring.delete("s", before.revision, db.dao().allTasks().associate { it.id to it.revision }, true)
        assertEquals(SeriesLifecycle.TRASHED, db.dao().series("s")!!.lifecycle)
        rejected { tasks.restore(occurrenceId("s", 2)) }
        val deleted = db.dao().series("s")!!
        recurring.restoreSeries("s", deleted.revision)
        assertNotNull(db.dao().task(first.id)!!.deletedAt)
        assertNull(db.dao().task(occurrenceId("s", 2))!!.deletedAt)
        assertEquals(2, db.dao().series("s")!!.cursor)
        assertEquals(SeriesLifecycle.ACTIVE, db.dao().series("s")!!.lifecycle)
        val restored = db.dao().series("s")!!
        recurring.delete("s", restored.revision, db.dao().allTasks().associate { it.id to it.revision }, false)
        assertNull(db.dao().series("s")!!.deletedAt)
        assertEquals(SeriesLifecycle.STOPPED, db.dao().series("s")!!.lifecycle)
        assertNotNull(db.dao().task(occurrenceId("s", 2))!!.deletedAt)
    }

    @Test fun templateCategoryTagsAndSnapshotsSurviveOrganizationChanges() = database { db ->
        val time = clock("2026-10-22")
        val tasks = TaskRepository(db, time)
        val org = OrganizationRepository(db, time)
        org.saveCategory("source", "來源", null)
        org.saveTag("a", "原標籤", null); org.saveTag("b", "新標籤", null)
        tasks.save(draft("s", "2026-10-25", categoryId = "source", tags = setOf("a")))
        val firstId = occurrenceId("s", 1)
        tasks.addTagsBatch(mapOf(firstId to db.dao().task(firstId)!!.revision), setOf("b"))
        tasks.moveBatch(mapOf(firstId to db.dao().task(firstId)!!.revision), ASSIGNMENTS_ID, mapOf(firstId to TaskStatus.IN_PROGRESS))
        tasks.setStatus(firstId, db.dao().task(firstId)!!.revision, TaskStatus.COMPLETED)
        val snapshot = OccurrenceSnapshot.read(db.dao().occurrence(firstId)!!.snapshot)!!
        org.deleteTag("a", db.dao().tag("a")!!.revision)
        assertEquals(setOf("原標籤", "新標籤"), OccurrenceSnapshot.read(db.dao().occurrence(firstId)!!.snapshot)!!.tags.toSet())
        assertEquals(snapshot, OccurrenceSnapshot.read(db.dao().occurrence(firstId)!!.snapshot))
        val second = db.dao().task(occurrenceId("s", 2))!!
        assertEquals(ASSIGNMENTS_ID, second.categoryId)
        assertEquals(TaskStatus.NOT_STARTED, second.status)
        assertEquals(setOf("b"), db.dao().seriesTagIds("s").toSet())
        tasks.setStatus(second.id, second.revision, TaskStatus.COMPLETED)
        assertEquals(setOf("b"), db.dao().taskTagIds(occurrenceId("s", 3)).toSet())
        tasks.save(draft("another", "2026-10-25", categoryId = "source"))
        org.deleteCategory("source", db.dao().category("source")!!.revision, UNCATEGORIZED_ID,
            db.dao().categoryTasks("source").associate { it.id to it.revision }, emptyMap())
        assertEquals(UNCATEGORIZED_ID, db.dao().series("another")!!.categoryId)
        val another = db.dao().task(occurrenceId("another", 1))!!
        tasks.setStatus(another.id, another.revision, TaskStatus.COMPLETED)
        assertEquals(UNCATEGORIZED_ID, db.dao().task(occurrenceId("another", 2))!!.categoryId)
    }

    @Test fun automaticDstGapMovesForwardAndUnchangedEditDoesNotRejectTheOccurrence() = database { db ->
        val time = clock("2026-03-06")
        val tasks = TaskRepository(db, time)
        val rule = RecurrenceRule(LocalDate.of(2026, 3, 7), RecurrenceFrequency.DAILY, "America/New_York", maxCount = 3)
        tasks.save(TaskDraft("dst", "夏令時間", dueDate = rule.anchor, dueTime = LocalTime.of(2, 30), zoneId = rule.zoneId, recurrence = rule))
        val first = db.dao().task(occurrenceId("dst", 1))!!
        tasks.setStatus(first.id, first.revision, TaskStatus.COMPLETED)
        val second = db.dao().task(occurrenceId("dst", 2))!!
        assertEquals(LocalDate.of(2026, 3, 8), second.dueDate)
        assertEquals(LocalTime.of(2, 30), second.dueTime)
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), second.dueInstant())
        tasks.save(TaskDraft(second.id, "只改名稱", dueDate = second.dueDate, dueTime = second.dueTime, zoneId = second.zoneId,
            expectedRevision = second.revision, recurrence = rule.copy(anchor = second.dueDate!!), expectedSeriesRevision = db.dao().series("dst")!!.revision,
            expectedOccurrenceRevisions = db.dao().allTasks().associate { it.id to it.revision }))
        assertEquals(second.dueInstant(), db.dao().task(second.id)!!.dueInstant())
    }

    @Test fun dateOnlyCompletionUsesCurrentZoneAndLaterCategoryMoveKeepsHistory() = database { db ->
        val instant = Instant.parse("2026-10-23T01:00:00Z")
        val west = Clock.fixed(instant, ZoneId.of("America/Los_Angeles"))
        val tasks = TaskRepository(db, west)
        val rule = RecurrenceRule(LocalDate.of(2026, 10, 22), RecurrenceFrequency.DAILY, "Asia/Hong_Kong", maxCount = 3)
        tasks.save(TaskDraft("floating", "只有日期", dueDate = rule.anchor, zoneId = rule.zoneId, recurrence = rule))
        val first = db.dao().task(occurrenceId("floating", 1))!!
        tasks.setStatus(first.id, first.revision, TaskStatus.COMPLETED)
        val snapshot = OccurrenceSnapshot.read(db.dao().occurrence(first.id)!!.snapshot)!!
        assertEquals(false, snapshot.late) // Still October 22 in Los Angeles.
        val east = Clock.fixed(instant, ZoneId.of("Asia/Hong_Kong"))
        val org = OrganizationRepository(db, east)
        org.saveCategory("target", "新分類", null)
        TaskRepository(db, east).moveBatch(mapOf(first.id to db.dao().task(first.id)!!.revision), "target", emptyMap())
        assertEquals(snapshot, OccurrenceSnapshot.read(db.dao().occurrence(first.id)!!.snapshot))
        assertEquals("target", db.dao().series("floating")!!.categoryId)
    }

    @Test fun singleOccurrenceCannotLoseItsDateOrPartiallyChangeTheSeries() = database { db ->
        val tasks = TaskRepository(db, clock("2026-10-22"))
        tasks.save(draft("s", "2026-10-25"))
        val first = db.dao().task(occurrenceId("s", 1))!!
        val series = db.dao().series("s")!!
        rejected { tasks.save(TaskDraft(first.id, "清除日期", expectedRevision = first.revision)) }
        assertEquals(first, db.dao().task(first.id))
        assertEquals(series, db.dao().series("s"))
    }

    @Test fun renameAfterMidnightCapturesOldNamesAndCategoryCompletionAdvancesAtomically() = database { db ->
        val start = clock("2026-10-20")
        val org = OrganizationRepository(db, start)
        org.saveCategory("source", "舊分類", null)
        org.saveTag("tag", "舊標籤", null)
        TaskRepository(db, start).save(draft("s", "2026-10-20", categoryId = "source", tags = setOf("tag")))
        val id = occurrenceId("s", 1)
        assertNull(db.dao().occurrence(id)!!.snapshot)
        val laterOrg = OrganizationRepository(db, clock("2026-10-22"))
        laterOrg.saveCategory("source", "新分類", db.dao().category("source")!!.revision)
        laterOrg.saveTag("tag", "新標籤", db.dao().tag("tag")!!.revision)
        val history = OccurrenceSnapshot.read(db.dao().occurrence(id)!!.snapshot)!!
        assertEquals("舊分類", history.category)
        assertEquals(listOf("舊標籤"), history.tags)
        laterOrg.deleteCategory("source", db.dao().category("source")!!.revision, ASSIGNMENTS_ID,
            mapOf(id to db.dao().task(id)!!.revision), mapOf(id to TaskStatus.COMPLETED))
        assertEquals(TaskStatus.COMPLETED, db.dao().task(id)!!.status)
        assertEquals(true, OccurrenceSnapshot.read(db.dao().occurrence(id)!!.snapshot)!!.late)
        assertEquals(3, db.dao().series("s")!!.cursor)
        assertTrue(db.dao().allTasks().filter { it.id != id }.all { it.categoryId == ASSIGNMENTS_ID && it.status == TaskStatus.NOT_STARTED })
    }

    @Test fun actualNonEmptyVersionTwoDatabaseMigratesWithoutChangingItsFourTables() = runBlocking {
        val name = "test-v2-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.chimafok.todo.data.TodoDatabase/2.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.getJSONArray("indices")
                for (position in 0 until indices.length()) old.execSQL(indices.getJSONObject(position).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
            old.execSQL("INSERT INTO categories VALUES (?, '未分類', 'UNCATEGORIZED', 0, 1, 1, 1)", arrayOf(UNCATEGORIZED_ID))
            old.execSQL("INSERT INTO tasks VALUES ('legacy','舊任務','保留備註',?,'2026-10-22','20:00','Asia/Hong_Kong','OPEN',NULL,NULL,1,2,3)", arrayOf(UNCATEGORIZED_ID))
            old.execSQL("INSERT INTO tags VALUES ('tag','舊標籤','舊標籤',0,1,1,1)")
            old.execSQL("INSERT INTO task_tags VALUES ('legacy','tag')")
            old.version = 2
        }
        val db = TodoDatabase.create(context, name)
        try {
            val legacy = db.dao().task("legacy")!!
            assertEquals("保留備註", legacy.notes)
            assertEquals(3L, legacy.revision)
            assertEquals("Asia/Hong_Kong", legacy.zoneId)
            assertEquals(listOf("tag"), db.dao().taskTagIds("legacy"))
            assertTrue(db.dao().allSeries().isEmpty())
            TaskRepository(db, clock("2026-10-22")).save(draft("s", "2026-10-25"))
            assertEquals(legacy, db.dao().task("legacy"))
            assertEquals(8, db.openHelper.readableDatabase.version)
            assertNull(legacy.startDate); assertNull(legacy.startTime); assertNull(legacy.startZoneId)
            assertEquals(1, db.dao().seriesOccurrences("s").size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
