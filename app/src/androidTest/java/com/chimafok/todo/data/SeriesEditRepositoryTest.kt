package com.chimafok.todo.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimafok.todo.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SeriesEditRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun clock(date: String = "2026-10-22") = Clock.fixed(Instant.parse("${date}T12:00:00Z"), ZoneOffset.UTC)
    private fun database(block: suspend (TodoDatabase) -> Unit) = runBlocking {
        val name = "test-edit-${UUID.randomUUID()}.db"
        val db = TodoDatabase.create(context, name)
        try { block(db) } finally { db.close(); context.deleteDatabase(name) }
    }
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }
    private suspend fun seed(db: TodoDatabase, assignment: Boolean = false, date: String = "2026-10-25",
        frequency: RecurrenceFrequency = RecurrenceFrequency.DAILY, time: Clock = clock()) {
        val rule = RecurrenceRule(LocalDate.parse(date), frequency, "UTC", maxCount = 6)
        val tasks = TaskRepository(db, time)
        tasks.save(TaskDraft("s", "舊名稱", categoryId = if (assignment) ASSIGNMENTS_ID else UNCATEGORIZED_ID,
            status = if (assignment) TaskStatus.NOT_STARTED else TaskStatus.OPEN, dueDate = rule.anchor, zoneId = rule.zoneId, recurrence = rule))
        for (sequence in 1..2) {
            val task = db.dao().task(occurrenceId("s", sequence))!!
            tasks.trash(task.id, task.revision); tasks.restore(task.id)
        }
    }
    private suspend fun edit(db: TodoDatabase, id: String = occurrenceId("s", 1), frequency: RecurrenceFrequency = RecurrenceFrequency.DAILY,
        count: Int = 6, title: String = "新名稱", time: LocalTime? = null, zone: String? = null): TaskDraft {
        val task = db.dao().task(id)!!
        val ref = db.dao().occurrence(id)!!
        return TaskDraft(id, title, task.notes, task.categoryId, task.dueDate, time ?: task.dueTime, zone ?: task.zoneId,
            task.status, task.revision, db.dao().taskTagIds(id).toSet(),
            RecurrenceRule(task.dueDate!!, frequency, zone ?: task.zoneId!!, maxCount = count), db.dao().series(ref.seriesId)!!.revision,
            db.dao().seriesOccurrences(ref.seriesId).associate { it.taskId to db.dao().task(it.taskId)!!.revision })
    }

    @Test fun futureDatesTagsAndCategoryChangeKeepIdsAndExistingAssignmentStates() = database { db ->
        seed(db, assignment = true)
        val tasks = TaskRepository(db, clock())
        val second = db.dao().task(occurrenceId("s", 2))!!
        val third = db.dao().task(occurrenceId("s", 3))!!
        tasks.setStatus(second.id, second.revision, TaskStatus.IN_PROGRESS)
        tasks.setStatus(third.id, third.revision, TaskStatus.DONE_UNSUBMITTED)
        OrganizationRepository(db, clock()).saveTag("tag", "新標籤", null)
        val before = db.dao().allTasks().associateBy { it.id }
        tasks.save(edit(db, frequency = RecurrenceFrequency.WEEKLY, time = LocalTime.of(20, 0)).copy(tagIds = setOf("tag")))
        val after = db.dao().allTasks().associateBy { it.id }
        assertEquals(before.keys, after.keys)
        assertEquals(LocalDate.of(2026, 11, 1), after.getValue(second.id).dueDate)
        assertEquals(LocalDate.of(2026, 11, 8), after.getValue(third.id).dueDate)
        assertEquals(TaskStatus.IN_PROGRESS, after.getValue(second.id).status)
        assertEquals(TaskStatus.DONE_UNSUBMITTED, after.getValue(third.id).status)
        for ((id, task) in after) {
            assertEquals(before.getValue(id).createdAt, task.createdAt)
            assertEquals("新名稱", task.title)
            assertEquals(LocalTime.of(20, 0), task.dueTime)
            assertEquals(listOf("tag"), db.dao().taskTagIds(id))
        }
        tasks.save(edit(db, frequency = RecurrenceFrequency.WEEKLY).copy(categoryId = UNCATEGORIZED_ID, status = TaskStatus.OPEN))
        assertTrue(db.dao().allTasks().all { it.categoryId == UNCATEGORIZED_ID && it.status == TaskStatus.OPEN })
        assertEquals(3, db.dao().series("s")!!.cursor)
    }

    @Test fun shorteningCancelsWithoutDeletingAndExtensionReactivatesTheSameSlots() = database { db ->
        seed(db, assignment = true)
        val tasks = TaskRepository(db, clock())
        val second = db.dao().task(occurrenceId("s", 2))!!
        tasks.setStatus(second.id, second.revision, TaskStatus.IN_PROGRESS)
        val original = db.dao().allTasks().associateBy { it.id }
        tasks.save(edit(db, count = 1))
        assertEquals(2, db.dao().allOccurrences().count { it.excludedByRule })
        assertTrue(db.dao().allTasks().all { it.deletedAt == null })
        assertEquals(SeriesLifecycle.ENDED, db.dao().series("s")!!.lifecycle)
        assertEquals(3, db.dao().series("s")!!.cursor)
        val refs = db.dao().allOccurrences().map { RecurringTaskRef(it.taskId, it.seriesId, it.sequence, it.excludedByRule) }
        assertEquals(setOf(occurrenceId("s", 1)), recurringHomeTaskIds(db.dao().allTasks(), refs, clock()))
        rejected { tasks.setStatus(second.id, db.dao().task(second.id)!!.revision, TaskStatus.COMPLETED) }
        tasks.save(edit(db, frequency = RecurrenceFrequency.WEEKLY, count = 4))
        assertTrue(db.dao().allOccurrences().none { it.excludedByRule })
        assertEquals(original.keys, db.dao().allTasks().map { it.id }.toSet())
        assertEquals(TaskStatus.IN_PROGRESS, db.dao().task(second.id)!!.status)
        assertEquals(LocalDate.of(2026, 11, 1), db.dao().task(second.id)!!.dueDate)
        assertNull(db.dao().occurrence(second.id)!!.snapshot)
        assertEquals(3, db.dao().allRules().size)
        assertEquals(3, db.dao().series("s")!!.cursor)
    }

    @Test fun pastCompletedAndIndividuallyDeletedFutureRowsNeverGetRewritten() = database { db ->
        val tasks = TaskRepository(db, clock())
        val initial = RecurrenceRule(LocalDate.of(2026, 10, 20), RecurrenceFrequency.DAILY, "UTC", maxCount = 8)
        TaskRepository(db, clock("2026-10-20")).save(TaskDraft("s", "舊名稱", dueDate = initial.anchor, zoneId = "UTC", recurrence = initial))
        val first = db.dao().task(occurrenceId("s", 1))!!
        TaskRepository(db, clock("2026-10-20")).setStatus(first.id, first.revision, TaskStatus.COMPLETED)
        RecurringRepository(db, clock()).reconcileAll()
        val current = db.dao().task(occurrenceId("s", 3))!!
        tasks.trash(current.id, current.revision); tasks.restore(current.id)
        val fourth = db.dao().task(occurrenceId("s", 4))!!
        tasks.setStatus(fourth.id, fourth.revision, TaskStatus.COMPLETED)
        val fifth = db.dao().task(occurrenceId("s", 5))!!
        tasks.trash(fifth.id, fifth.revision)
        val protected = listOf(1, 2, 4, 5).associate { occurrenceId("s", it).let { id -> id to (db.dao().task(id) to db.dao().occurrence(id)) } }
        tasks.save(edit(db, current.id, RecurrenceFrequency.WEEKLY, count = 3))
        for ((id, value) in protected) assertEquals(value, db.dao().task(id) to db.dao().occurrence(id))
        assertTrue(db.dao().occurrence(occurrenceId("s", 6))!!.excludedByRule)
        assertEquals(6, db.dao().series("s")!!.cursor)
    }

    @Test fun staleFutureEditAndMidTransactionFailureNeverPartiallyChangeTheSeries() = database { db ->
        seed(db)
        val tasks = TaskRepository(db, clock())
        val stale = edit(db, frequency = RecurrenceFrequency.WEEKLY)
        val second = db.dao().task(occurrenceId("s", 2))!!
        tasks.save(TaskDraft(second.id, second.title, "較新備註", second.categoryId, second.dueDate, second.dueTime, second.zoneId,
            second.status, second.revision))
        val before = db.dao().allTasks()
        val series = db.dao().series("s")!!
        rejected { tasks.save(stale) }
        assertEquals(before, db.dao().allTasks()); assertEquals(series, db.dao().series("s"))
        val retry = edit(db, frequency = RecurrenceFrequency.WEEKLY)
        val thirdId = occurrenceId("s", 3)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_replan BEFORE UPDATE ON tasks WHEN OLD.id = '$thirdId' BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        try { tasks.save(retry); fail("Expected DB failure") } catch (_: android.database.SQLException) { }
        assertEquals(before, db.dao().allTasks()); assertEquals(series, db.dao().series("s"))
        assertEquals(1, db.dao().allRules().size)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_replan")
        tasks.save(retry)
        assertEquals(2, db.dao().allRules().size)
        assertEquals(LocalDate.of(2026, 11, 1), db.dao().task(second.id)!!.dueDate)
    }

    @Test fun categoryDeletionCannotCompleteOrActivateCancelledOccurrences() = database { db ->
        seed(db)
        val tasks = TaskRepository(db, clock())
        val org = OrganizationRepository(db, clock())
        org.saveCategory("source", "來源分類", null)
        tasks.moveBatch(db.dao().allTasks().associate { it.id to it.revision }, "source", emptyMap())
        tasks.save(edit(db, count = 1))
        val revisions = db.dao().allTasks().associate { it.id to it.revision }
        val before = db.dao().allTasks()
        rejected { org.deleteCategory("source", db.dao().category("source")!!.revision, ASSIGNMENTS_ID,
            revisions, revisions.mapValues { TaskStatus.COMPLETED }) }
        assertEquals(before, db.dao().allTasks())
        assertNotNull(db.dao().category("source"))
        val choices = revisions.mapValues { (id, _) -> if (id == occurrenceId("s", 1)) TaskStatus.IN_PROGRESS else TaskStatus.NOT_STARTED }
        org.deleteCategory("source", db.dao().category("source")!!.revision, ASSIGNMENTS_ID, revisions, choices)
        assertTrue(db.dao().allOccurrences().filter { it.sequence > 1 }.all { it.excludedByRule })
        assertTrue(db.dao().allTasks().all { it.status != TaskStatus.COMPLETED })
        tasks.save(edit(db, count = 6))
        assertTrue(db.dao().allOccurrences().none { it.excludedByRule })
        assertEquals(TaskStatus.NOT_STARTED, db.dao().task(occurrenceId("s", 2))!!.status)
    }

    @Test fun retiredCompletedAndStoppedSourcesCannotChangeTheTemplate() = database { db ->
        val tasks = TaskRepository(db, clock())
        val rule = RecurrenceRule(LocalDate.of(2026, 10, 20), RecurrenceFrequency.DAILY, "UTC", maxCount = 6)
        tasks.save(TaskDraft("s", "舊名稱", dueDate = rule.anchor, zoneId = "UTC", recurrence = rule))
        rejected { tasks.save(edit(db)) }
        val current = db.dao().task(occurrenceId("s", 3))!!
        tasks.setStatus(current.id, current.revision, TaskStatus.COMPLETED)
        rejected { tasks.save(edit(db, current.id)) }
        val nextId = occurrenceId("s", 4)
        RecurringRepository(db, clock()).stop("s", db.dao().series("s")!!.revision)
        rejected { tasks.save(edit(db, nextId)) }
        assertEquals(1, db.dao().allRules().size)
    }

    @Test fun replanAcrossDstKeepsSeriesZoneAndResolvesFutureGapAutomatically() = database { db ->
        val time = clock("2026-03-06")
        seed(db, date = "2026-03-07", frequency = RecurrenceFrequency.WEEKLY, time = time)
        TaskRepository(db, time).save(edit(db, time = LocalTime.of(2, 30), zone = "America/New_York"))
        val second = db.dao().task(occurrenceId("s", 2))!!
        assertEquals(LocalDate.of(2026, 3, 8), second.dueDate)
        assertEquals("America/New_York", second.zoneId)
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), second.dueInstant())
        assertEquals(Instant.parse("2026-03-09T06:30:00Z"), db.dao().task(occurrenceId("s", 3))!!.dueInstant())
    }

    @Test fun nonEmptyVersionThreeUpgradePreservesRecurringRowsAndDefaultsToActiveSlots() = runBlocking {
        val name = "test-v3-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name); file.parentFile!!.mkdirs()
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.chimafok.todo.data.TodoDatabase/3.json").bufferedReader().use { it.readText() }).getJSONObject("database")
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
            old.execSQL("INSERT INTO tasks VALUES ('legacy','舊任務','原備註',?,'2026-10-25',NULL,'UTC','OPEN',NULL,NULL,1,2,3)", arrayOf(UNCATEGORIZED_ID))
            old.execSQL("INSERT INTO recurring_series VALUES ('s','舊任務','原備註',?,'ACTIVE',1,NULL,NULL,1,2,3)", arrayOf(UNCATEGORIZED_ID))
            old.execSQL("INSERT INTO series_rules VALUES ('v1','s',1,1,'2026-10-25','DAILY',1,0,NULL,'UTC',NULL,5,1)")
            old.execSQL("INSERT INTO occurrences VALUES ('legacy','s',1,'v1',NULL,NULL)")
            old.version = 3
        }
        val db = TodoDatabase.create(context, name)
        try {
            val task = db.dao().task("legacy")!!
            assertEquals("原備註", task.notes); assertEquals(3L, task.revision)
            assertFalse(db.dao().occurrence("legacy")!!.excludedByRule)
            assertEquals(3L, db.dao().series("s")!!.revision)
            assertEquals(7, db.openHelper.readableDatabase.version)
            TaskRepository(db, clock()).save(edit(db, "legacy", RecurrenceFrequency.WEEKLY))
            assertEquals("legacy", db.dao().task("legacy")!!.id)
            assertEquals(1, db.dao().series("s")!!.cursor)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
