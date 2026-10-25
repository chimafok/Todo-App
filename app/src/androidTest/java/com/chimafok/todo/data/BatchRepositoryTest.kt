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
class BatchRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC)
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }
    private fun database(block: suspend (TodoDatabase, TaskRepository, OrganizationRepository) -> Unit) = runBlocking {
        val name = "test-batch-${UUID.randomUUID()}.db"
        val db = TodoDatabase.create(context, name)
        try { block(db, TaskRepository(db, clock), OrganizationRepository(db, clock)) }
        finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun addTagsIsUnionAndRetryDoesNotRewriteAnUnchangedTask() = database { db, repo, org ->
        org.saveTag("a", "A", null); org.saveTag("b", "B", null)
        repo.save(TaskDraft("one", "一", tagIds = setOf("a")))
        repo.save(TaskDraft("two", "二"))
        val revisions = listOf("one", "two").associateWith { db.dao().task(it)!!.revision }
        repo.addTagsBatch(revisions, setOf("b"))
        assertEquals(setOf("a", "b"), db.dao().taskTagIds("one").toSet())
        assertEquals(setOf("b"), db.dao().taskTagIds("two").toSet())
        val after = listOf("one", "two").associateWith { db.dao().task(it)!!.revision }
        repo.addTagsBatch(after, setOf("b"))
        assertEquals(after, listOf("one", "two").associateWith { db.dao().task(it)!!.revision })
        rejected { repo.addTagsBatch(after, setOf("a", "missing")) }
        assertEquals(setOf("b"), db.dao().taskTagIds("two").toSet())
    }

    @Test fun assignmentMoveRequiresEveryChoiceAndPreservesDeadlineTagsAndExistingState() = database { db, repo, org ->
        org.saveTag("a", "A", null)
        repo.save(TaskDraft("one", "一", dueDate = LocalDate.of(2026, 12, 1), dueTime = LocalTime.NOON,
            zoneId = "Asia/Hong_Kong", tagIds = setOf("a")))
        repo.save(TaskDraft("two", "二", status = TaskStatus.COMPLETED))
        repo.save(TaskDraft("three", "三", categoryId = ASSIGNMENTS_ID, status = TaskStatus.DONE_UNSUBMITTED))
        val before = listOf("one", "two", "three").associateWith { db.dao().task(it)!! }
        val revisions = before.mapValues { it.value.revision }
        rejected { repo.moveBatch(revisions, ASSIGNMENTS_ID, mapOf("one" to TaskStatus.IN_PROGRESS)) }
        assertEquals(before, before.keys.associateWith { db.dao().task(it)!! })
        repo.moveBatch(revisions, ASSIGNMENTS_ID, mapOf("one" to TaskStatus.IN_PROGRESS, "two" to TaskStatus.COMPLETED))
        val one = db.dao().task("one")!!
        assertEquals(TaskStatus.IN_PROGRESS, one.status)
        assertEquals(before.getValue("one").dueInstant(), one.dueInstant())
        assertEquals("Asia/Hong_Kong", one.zoneId)
        assertEquals(setOf("a"), db.dao().taskTagIds("one").toSet())
        assertEquals(before.getValue("two").completedAt, db.dao().task("two")!!.completedAt)
        assertEquals(before.getValue("three"), db.dao().task("three"))
        repo.moveBatch(before.keys.associateWith { db.dao().task(it)!!.revision }, UNCATEGORIZED_ID, emptyMap())
        assertEquals(TaskStatus.OPEN, db.dao().task("one")!!.status)
        assertEquals(TaskStatus.COMPLETED, db.dao().task("two")!!.status)
        assertEquals(TaskStatus.OPEN, db.dao().task("three")!!.status)
    }

    @Test fun staleMemberRejectsWholeBatchAndTrashUsesOneTimestamp() = database { db, repo, _ ->
        repo.save(TaskDraft("one", "一")); repo.save(TaskDraft("two", "二"))
        val revisions = listOf("one", "two").associateWith { db.dao().task(it)!!.revision }
        repo.setStatus("two", revisions.getValue("two"), TaskStatus.COMPLETED)
        rejected { repo.trashBatch(revisions) }
        assertNull(db.dao().task("one")!!.deletedAt); assertNull(db.dao().task("two")!!.deletedAt)
        repo.trashBatch(revisions.keys.associateWith { db.dao().task(it)!!.revision })
        assertEquals(clock.millis(), db.dao().task("one")!!.deletedAt)
        assertEquals(clock.millis(), db.dao().task("two")!!.deletedAt)
        assertEquals(TaskStatus.COMPLETED, db.dao().task("two")!!.status)
    }

    @Test fun failureOnSecondWriteRollsBackFirstWriteAndRetrySucceeds() = database { db, repo, _ ->
        repo.save(TaskDraft("one", "一")); repo.save(TaskDraft("two", "二"))
        val before = listOf("one", "two").associateWith { db.dao().task(it)!! }
        val revisions = before.mapValues { it.value.revision }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_second BEFORE UPDATE ON tasks WHEN NEW.id = 'two' BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        try { repo.trashBatch(revisions); fail("Expected SQLite failure") } catch (_: android.database.SQLException) { }
        assertEquals(before, before.keys.associateWith { db.dao().task(it)!! })
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_second")
        repo.trashBatch(revisions)
        assertTrue(before.keys.all { db.dao().task(it)!!.deletedAt != null })
    }
}
