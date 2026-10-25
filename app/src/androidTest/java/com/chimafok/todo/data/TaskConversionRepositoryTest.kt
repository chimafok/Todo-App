package com.chimafok.todo.data

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
class TaskConversionRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun clock(date: String = "2026-10-22") = Clock.fixed(Instant.parse("${date}T12:00:00Z"), ZoneOffset.UTC)
    private fun database(block: suspend (TodoDatabase) -> Unit) = runBlocking {
        val name = "test-convert-${UUID.randomUUID()}.db"
        val db = TodoDatabase.create(context, name)
        try { block(db) } finally { db.close(); context.deleteDatabase(name) }
    }
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }
    private suspend fun state(db: TodoDatabase): List<Any> = listOf(db.dao().allTasks(), db.dao().allTaskTags(),
        db.dao().allSeries(), db.dao().allRules(), db.dao().allOccurrences(), db.dao().allSeriesTags())
    private suspend fun seed(db: TodoDatabase, id: String = "original", status: TaskStatus = TaskStatus.OPEN,
        date: LocalDate? = LocalDate.of(2026, 10, 25), zone: String = "Asia/Hong_Kong", time: LocalTime? = LocalTime.of(20, 0),
        now: Clock = clock()): TaskEntity {
        OrganizationRepository(db, now).saveTag("tag", "保留標籤", if (db.dao().tag("tag") == null) null else db.dao().tag("tag")!!.revision)
        TaskRepository(db, Clock.fixed(now.instant().minusSeconds(3600), now.zone)).save(TaskDraft(id, "原任務", "原備註",
            if (status == TaskStatus.OPEN) UNCATEGORIZED_ID else ASSIGNMENTS_ID, date, time.takeIf { date != null },
            zone.takeIf { date != null }, status, tagIds = setOf("tag")))
        return db.dao().task(id)!!
    }
    private suspend fun conversion(db: TodoDatabase, original: TaskEntity,
        rule: RecurrenceRule = RecurrenceRule(original.dueDate!!, RecurrenceFrequency.WEEKLY, original.zoneId!!, maxCount = 3)) =
        TaskDraft(original.id, original.title, original.notes, original.categoryId, rule.dateAt(1), original.dueTime,
            rule.zoneId, original.status, original.revision, db.dao().taskTagIds(original.id).toSet(), rule)

    @Test fun conversionAdoptsTheOriginalIdAndKeepsContentTagsClockAndCreationTime() = database { db ->
        val original = seed(db)
        TaskRepository(db, clock()).save(conversion(db, original))
        assertEquals(original.copy(updatedAt = clock().millis(), revision = original.revision + 1), db.dao().task(original.id))
        val ref = db.dao().occurrence(original.id)!!
        assertEquals(1, ref.sequence); assertEquals(original.id, ref.seriesId)
        assertEquals(1, db.dao().allTasks().size)
        assertNull(db.dao().task(occurrenceId(ref.seriesId, 1)))
        assertEquals(listOf("tag"), db.dao().taskTagIds(original.id))
        assertEquals(listOf("tag"), db.dao().seriesTagIds(ref.seriesId))
        assertEquals("Asia/Hong_Kong", db.dao().latestRule(ref.seriesId)!!.zoneId)
        assertEquals(original.dueInstant(), db.dao().task(original.id)!!.dueInstant())
        assertEquals(1, db.dao().series(ref.seriesId)!!.cursor)
    }

    @Test fun assignmentProgressIsKeptButTheNextInstanceStartsNotStarted() = database { db ->
        val tasks = TaskRepository(db, clock())
        for (status in listOf(TaskStatus.IN_PROGRESS, TaskStatus.DONE_UNSUBMITTED)) {
            val original = seed(db, id = status.name, status = status)
            tasks.save(conversion(db, original))
            assertEquals(status, db.dao().task(original.id)!!.status)
            val first = db.dao().task(original.id)!!
            tasks.setStatus(first.id, first.revision, TaskStatus.COMPLETED)
            val next = db.dao().task(occurrenceId(original.id, 2))!!
            assertEquals(TaskStatus.NOT_STARTED, next.status)
            assertEquals(original.dueDate!!.plusWeeks(1), next.dueDate)
            assertEquals(original.createdAt, db.dao().task(first.id)!!.createdAt)
            assertEquals(2, db.dao().series(original.id)!!.cursor)
            assertEquals(2, db.dao().seriesOccurrences(original.id).size)
        }
    }

    @Test fun aConvertedMonthlyDeadlineKeepsItsOriginalZoneAndRestoresTheMonthEndAnchor() = database { db ->
        val now = clock("2026-01-22")
        val original = seed(db, date = LocalDate.of(2026, 1, 31), zone = "America/New_York", now = now)
        val tasks = TaskRepository(db, now)
        tasks.save(conversion(db, original, RecurrenceRule(original.dueDate!!, RecurrenceFrequency.MONTHLY, original.zoneId!!, maxCount = 3)))
        for (id in listOf(original.id, occurrenceId(original.id, 2))) {
            val task = db.dao().task(id)!!
            tasks.setStatus(id, task.revision, TaskStatus.COMPLETED)
        }
        assertEquals(LocalDate.of(2026, 2, 28), db.dao().task(occurrenceId(original.id, 2))!!.dueDate)
        val march = db.dao().task(occurrenceId(original.id, 3))!!
        assertEquals(LocalDate.of(2026, 3, 31), march.dueDate)
        assertEquals("America/New_York", march.zoneId)
        assertEquals(Instant.parse("2026-04-01T00:00:00Z"), march.dueInstant())
        assertEquals(3, db.dao().series(original.id)!!.cursor)
    }

    @Test fun theOriginalCountsTowardTheLimitAndReplayedConversionCannotDuplicateIt() = database { db ->
        val original = seed(db)
        val draft = conversion(db, original, RecurrenceRule(original.dueDate!!, RecurrenceFrequency.DAILY, original.zoneId!!, maxCount = 1))
        val tasks = TaskRepository(db, clock())
        tasks.save(draft)
        assertEquals(SeriesLifecycle.ENDED, db.dao().series(original.id)!!.lifecycle)
        val committed = state(db)
        rejected { tasks.save(draft) }
        RecurringRepository(db, clock()).reconcileAll()
        assertEquals(committed, state(db))
        assertEquals(1, db.dao().allTasks().size)
    }

    @Test fun staleAndMissingSourcesCannotCreateSeriesOrOverwriteNewerNotes() = database { db ->
        val original = seed(db)
        val draft = conversion(db, original)
        val tasks = TaskRepository(db, clock())
        tasks.save(draft.copy(recurrence = null, notes = "另一處較新的備註"))
        val before = state(db)
        rejected { tasks.save(draft) }
        rejected { tasks.save(draft.copy(id = "missing")) }
        assertEquals(before, state(db))
        assertEquals("另一處較新的備註", db.dao().task(original.id)!!.notes)
    }

    @Test fun completedDeletedAndCompletedDraftsStayOutsideNewSeries() = database { db ->
        val original = seed(db, status = TaskStatus.IN_PROGRESS)
        val draft = conversion(db, original)
        val tasks = TaskRepository(db, clock())
        var before = state(db)
        rejected { tasks.save(draft.copy(status = TaskStatus.COMPLETED)) }
        assertEquals(before, state(db))
        tasks.setStatus(original.id, original.revision, TaskStatus.COMPLETED)
        val completed = db.dao().task(original.id)!!
        before = state(db)
        rejected { tasks.save(draft.copy(expectedRevision = completed.revision, status = TaskStatus.NOT_STARTED)) }
        assertEquals(before, state(db))
        tasks.trash(completed.id, completed.revision)
        before = state(db)
        rejected { tasks.save(draft.copy(expectedRevision = db.dao().task(original.id)!!.revision)) }
        assertEquals(before, state(db))
    }

    @Test fun invalidTagsOrRulePreviewMismatchDoNotModifyTheSource() = database { db ->
        val original = seed(db)
        val draft = conversion(db, original)
        val before = state(db)
        val tasks = TaskRepository(db, clock())
        rejected { tasks.save(draft.copy(tagIds = setOf("missing"))) }
        rejected { tasks.save(draft.copy(dueDate = original.dueDate!!.plusDays(1))) }
        assertEquals(before, state(db))
    }

    @Test fun firstLinkAndLaterGeneratedRowFailuresRollbackEverythingAndAllowRetry() = database { db ->
        val original = seed(db, date = LocalDate.of(2026, 10, 21))
        val draft = conversion(db, original, RecurrenceRule(original.dueDate!!, RecurrenceFrequency.DAILY, original.zoneId!!, maxCount = 3))
        val before = state(db)
        val triggers = listOf(
            "CREATE TRIGGER fail_convert BEFORE INSERT ON occurrences BEGIN SELECT RAISE(ABORT, 'injected link failure'); END",
            "CREATE TRIGGER fail_convert BEFORE INSERT ON tasks WHEN NEW.id <> 'original' BEGIN SELECT RAISE(ABORT, 'injected later failure'); END")
        for (trigger in triggers) {
            db.openHelper.writableDatabase.execSQL(trigger)
            try { TaskRepository(db, clock()).save(draft); fail("Expected DB failure") } catch (_: android.database.SQLException) { }
            assertEquals(before, state(db))
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_convert")
        }
        TaskRepository(db, clock()).save(draft)
        assertEquals(2, db.dao().allTasks().size)
        assertEquals(original.id, db.dao().seriesOccurrences(original.id).first().taskId)
        assertEquals(1, db.dao().allRules().size)
    }

    @Test fun longOfflineCatchupUsesTheSavedZoneWithoutDuplicatingTheAdoptedFirstSlot() = database { db ->
        val now = Clock.fixed(Instant.parse("2026-10-22T00:30:00Z"), ZoneOffset.UTC)
        val zone = "America/Los_Angeles"
        val localToday = LocalDate.now(now.withZone(ZoneId.of(zone)))
        val original = seed(db, date = localToday.minusDays(599), zone = zone, time = LocalTime.of(23, 0), now = now)
        val draft = conversion(db, original, RecurrenceRule(original.dueDate!!, RecurrenceFrequency.DAILY, zone, maxCount = 601))
        TaskRepository(db, now).save(draft)
        assertTrue(db.dao().allTasks().size <= 257)
        val recurring = RecurringRepository(db, now)
        recurring.reconcileAll(); recurring.reconcileAll()
        val refs = db.dao().seriesOccurrences(original.id)
        assertEquals(600, refs.size); assertEquals((1..600).toList(), refs.map { it.sequence })
        assertEquals(original.id, refs.first().taskId)
        assertEquals(localToday, db.dao().task(occurrenceId(original.id, 600))!!.dueDate)
        assertEquals(600, db.dao().series(original.id)!!.cursor)
        assertNotNull(db.dao().occurrence(original.id)!!.snapshot)
        assertEquals(setOf(occurrenceId(original.id, 599)), recurringHomeTaskIds(db.dao().allTasks(),
            refs.map { RecurringTaskRef(it.taskId, it.seriesId, it.sequence) }, now))
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    @Test fun adoptedFirstIdSurvivesFutureReplanAndWholeSeriesTrashRestore() = database { db ->
        val original = seed(db)
        val tasks = TaskRepository(db, clock())
        val recurring = RecurringRepository(db, clock())
        tasks.save(conversion(db, original))
        tasks.trash(original.id, db.dao().task(original.id)!!.revision)
        tasks.restore(original.id)
        val first = db.dao().task(original.id)!!
        val revised = conversion(db, first, RecurrenceRule(first.dueDate!!, RecurrenceFrequency.MONTHLY, first.zoneId!!, maxCount = 3))
            .copy(title = "轉換後重排", expectedSeriesRevision = db.dao().series(original.id)!!.revision,
                expectedOccurrenceRevisions = db.dao().seriesOccurrences(original.id).associate { it.taskId to db.dao().task(it.taskId)!!.revision })
        tasks.save(revised)
        assertEquals(original.id, db.dao().seriesOccurrences(original.id).first().taskId)
        assertEquals(LocalDate.of(2026, 11, 25), db.dao().task(occurrenceId(original.id, 2))!!.dueDate)
        val refs = db.dao().seriesOccurrences(original.id)
        val ids = refs.map { it.taskId }.toSet()
        recurring.delete(original.id, db.dao().series(original.id)!!.revision,
            refs.associate { it.taskId to db.dao().task(it.taskId)!!.revision }, includeHistory = true)
        assertTrue(ids.all { db.dao().task(it)!!.deletedAt != null })
        recurring.restoreSeries(original.id, db.dao().series(original.id)!!.revision)
        assertEquals(ids, db.dao().seriesOccurrences(original.id).map { it.taskId }.toSet())
        assertTrue(ids.all { db.dao().task(it)!!.deletedAt == null })
        assertEquals(original.createdAt, db.dao().task(original.id)!!.createdAt)
        assertEquals(2, db.dao().series(original.id)!!.cursor)
        assertEquals("轉換後重排", db.dao().task(original.id)!!.title)
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }
}
