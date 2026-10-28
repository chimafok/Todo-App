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
class OrganizationBatchTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val clock = Clock.fixed(Instant.parse("2030-03-02T09:00:00Z"), ZoneOffset.UTC)
    private fun database(block: suspend (TodoDatabase, OrganizationRepository) -> Unit) = runBlocking {
        assertEquals("com.chimafok.todo.qa", context.packageName)
        val name = "organization130-${UUID.randomUUID()}.db"; val db = TodoDatabase.create(context, name)
        try { block(db, OrganizationRepository(db, clock)) } finally { db.close(); context.deleteDatabase(name) }
    }
    private suspend fun rejected(block: suspend () -> Unit) { var no = false; try { block() } catch (_: IllegalArgumentException) { no = true }; assertTrue(no) }
    @Test fun arbitraryCategoryAndTagOrderPersistsAndRejectsStaleOrInvalidOrder() = database { db, org ->
        val before = db.dao().allCategories(); val order = before.map { it.id }.reversed()
        org.reorder(false, before.map { it.id }, order, before.associate { it.id to it.revision })
        assertEquals(order, db.dao().allCategories().map { it.id })
        rejected { org.reorder(false, before.map { it.id }, order, before.associate { it.id to it.revision }) }
        listOf("a", "b", "c").forEach { org.saveTag(it, it, null) }
        val tags = db.dao().allTags(); val ids = tags.map { it.id }; val revisions = tags.associate { it.id to it.revision }
        rejected { org.reorder(true, ids, listOf("a", "a", "b"), revisions) }
        org.saveTag("a", "renamed", 1)
        rejected { org.reorder(true, ids, ids.reversed(), revisions) }
        val fresh = db.dao().allTags(); org.reorder(true, ids, listOf("c", "a", "b"), fresh.associate { it.id to it.revision })
        assertEquals(listOf("c", "a", "b"), db.dao().allTags().map { it.id })
    }
    @Test fun categoryBatchMovesActiveTrashAndFutureTemplatesWithoutDeletingTasks() = database { db, org ->
        org.saveCategory("a", "甲", null); org.saveCategory("b", "乙", null)
        val r = TaskRepository(db, clock)
        r.save(TaskDraft("active", "任務", categoryId = "a"))
        r.save(TaskDraft("trash", "垃圾桶", categoryId = "b")); r.trash("trash", db.dao().task("trash")!!.revision)
        val day = LocalDate.now(clock)
        r.save(TaskDraft("series", "重複", categoryId = "b", dueDate = day, zoneId = "UTC", recurrence = RecurrenceRule(day, RecurrenceFrequency.DAILY, "UTC", maxCount = 3)))
        val oldIds = db.dao().allTasks().map { it.id }.toSet(); val deleted = db.dao().task("trash")!!.deletedAt
        val p = org.previewDelete(linkedSetOf("b", "a"), false)
        org.deleteBatch(p, STUDY_ID, emptyMap())
        assertNull(db.dao().category("a")); assertNull(db.dao().category("b"))
        assertEquals(oldIds, db.dao().allTasks().map { it.id }.toSet())
        assertTrue(db.dao().allTasks().all { it.categoryId == STUDY_ID })
        assertEquals(deleted, db.dao().task("trash")!!.deletedAt); assertEquals(STUDY_ID, db.dao().series("series")!!.categoryId)
    }
    @Test fun systemCategoriesCannotBeSelectedAndChosenTargetsCannotBeDeleted() = database { db, org ->
        rejected { org.previewDelete(setOf(ASSIGNMENTS_ID), false) }; rejected { org.previewDelete(setOf(UNCATEGORIZED_ID), false) }
        org.saveCategory("a", "甲", null); org.saveCategory("b", "乙", null)
        val p = org.previewDelete(linkedSetOf("b", "a"), false)
        rejected { org.deleteBatch(p, "a", emptyMap()) }; assertNotNull(db.dao().category("a"))
    }
    @Test fun staleTaskOrSeriesInvalidatesWholeBatch() = database { db, org ->
        org.saveCategory("a", "甲", null); org.saveCategory("b", "乙", null)
        val r = TaskRepository(db, clock); r.save(TaskDraft("task", "原任務", categoryId = "b"))
        val p = org.previewDelete(linkedSetOf("b", "a"), false)
        r.save(TaskDraft("task", "較新任務", categoryId = "b", expectedRevision = db.dao().task("task")!!.revision))
        rejected { org.deleteBatch(p, UNCATEGORIZED_ID, emptyMap()) }
        assertNotNull(db.dao().category("a")); assertNotNull(db.dao().category("b")); assertEquals("較新任務", db.dao().task("task")!!.title)
    }
    @Test fun assignmentChoiceAndSecondWriteFailureRollBackWholeBatch() = database { db, org ->
        org.saveCategory("a", "甲", null); org.saveCategory("b", "乙", null)
        TaskRepository(db, clock).save(TaskDraft("task", "任務", categoryId = "a"))
        val p = org.previewDelete(linkedSetOf("b", "a"), false)
        rejected { org.deleteBatch(p, ASSIGNMENTS_ID, emptyMap()) }; assertNotNull(db.dao().category("a"))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER qa_abort_category BEFORE DELETE ON categories WHEN OLD.id='b' BEGIN SELECT RAISE(ABORT,'QA rollback'); END")
        var failed = false; try { org.deleteBatch(p, UNCATEGORIZED_ID, emptyMap()) } catch (_: Exception) { failed = true }
        assertTrue(failed); assertNotNull(db.dao().category("a")); assertEquals("a", db.dao().task("task")!!.categoryId)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER qa_abort_category")
        org.deleteBatch(p, ASSIGNMENTS_ID, mapOf("task" to TaskStatus.IN_PROGRESS))
        assertEquals(TaskStatus.IN_PROGRESS, db.dao().task("task")!!.status)
    }
    @Test fun tagBatchRemovesLinksButPreservesTasksAndRejectsStalePreview() = database { db, org ->
        listOf("a", "b", "keep").forEach { org.saveTag(it, it, null) }
        val r = TaskRepository(db, clock); r.save(TaskDraft("task", "任務", tagIds = setOf("a", "b", "keep")))
        val stale = org.previewDelete(linkedSetOf("b", "a"), true)
        r.save(TaskDraft("task", "更新", expectedRevision = db.dao().task("task")!!.revision, tagIds = setOf("a", "b", "keep")))
        rejected { org.deleteBatch(stale, UNCATEGORIZED_ID, emptyMap()) }
        org.deleteBatch(org.previewDelete(linkedSetOf("b", "a"), true), UNCATEGORIZED_ID, emptyMap())
        assertNotNull(db.dao().task("task")); assertEquals(listOf("keep"), db.dao().taskTagIds("task"))
        assertNull(db.dao().tag("a")); assertNull(db.dao().tag("b"))
    }
}
