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
class StudyCategoryProtectionTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val clock = Clock.fixed(Instant.parse("2030-03-02T09:00:00Z"), ZoneOffset.UTC)
    private fun databases(block: suspend (TodoDatabase, TodoDatabase) -> Unit) = runBlocking {
        assertEquals("com.chimafok.todo.qa", context.packageName)
        val names = List(2) { "study-protection-${UUID.randomUUID()}.db" }
        val a = TodoDatabase.create(context, names[0]); val b = TodoDatabase.create(context, names[1])
        try { block(a, b) } finally { a.close(); b.close(); names.forEach(context::deleteDatabase) }
    }
    private suspend fun rejected(block: suspend () -> Unit) {
        var rejected = false; try { block() } catch (_: IllegalArgumentException) { rejected = true }; assertTrue(rejected)
    }
    @Test fun renamedStudyCannotBeDeletedAndKeepsProgressTrashAndRecurringHistory() = databases { a, _ ->
        val org = OrganizationRepository(a, clock); val tasks = TaskRepository(a, clock)
        val day = LocalDate.now(clock)
        tasks.save(TaskDraft("active", "學習進行中", categoryId = STUDY_ID, status = TaskStatus.IN_PROGRESS, startDate = day))
        tasks.save(TaskDraft("trash", "保留垃圾桶", categoryId = STUDY_ID)); tasks.trash("trash", a.dao().task("trash")!!.revision)
        tasks.save(TaskDraft("series", "重複學習", categoryId = STUDY_ID, dueDate = day, zoneId = "UTC",
            recurrence = RecurrenceRule(day, RecurrenceFrequency.DAILY, "UTC", maxCount = 2)))
        val first = a.dao().task(occurrenceId("series", 1))!!; tasks.setStatus(first.id, first.revision, TaskStatus.COMPLETED)
        org.saveCategory(STUDY_ID, "閱讀", a.dao().category(STUDY_ID)!!.revision)
        val before = BackupFormat.decode(BackupRepository(a, clock).export("test")).data.content()
        rejected { org.deleteCategory(STUDY_ID, a.dao().category(STUDY_ID)!!.revision, UNCATEGORIZED_ID,
            a.dao().categoryTasks(STUDY_ID).associate { it.id to it.revision }, emptyMap()) }
        assertEquals(before, BackupFormat.decode(BackupRepository(a, clock).export("test")).data.content())
        assertEquals("閱讀", a.dao().category(STUDY_ID)!!.name)
        assertEquals(TaskStatus.IN_PROGRESS, a.dao().task("active")!!.status)
        assertEquals(day, a.dao().task("active")!!.startDate); assertNotNull(a.dao().task("trash")!!.deletedAt)
        assertNotNull(a.dao().occurrence(first.id)!!.snapshot); assertEquals(STUDY_ID, a.dao().series("series")!!.categoryId)
        // A custom category using Study's old display name is still an ordinary category.
        org.saveCategory("custom", "學習", null); org.deleteCategory("custom", a.dao().category("custom")!!.revision, UNCATEGORIZED_ID, emptyMap(), emptyMap())
        assertNull(a.dao().category("custom")); assertNotNull(a.dao().category(STUDY_ID))
    }
    @Test fun mixedBatchAndOldPreviewCannotDeleteStudyOrPartiallyChangeOtherCategories() = databases { a, _ ->
        val org = OrganizationRepository(a, clock)
        org.saveCategory("custom", "自訂", null)
        TaskRepository(a, clock).save(TaskDraft("task", "保留", categoryId = "custom"))
        val before = BackupFormat.decode(BackupRepository(a, clock).export("test")).data.content()
        rejected { org.previewDelete(linkedSetOf("custom", STUDY_ID), false) }
        val study = a.dao().category(STUDY_ID)!!
        val old = OrganizationDeletePreview(false, mapOf(STUDY_ID to study.revision), listOf(study.name), emptyList(), emptyMap(), listOf(a.dao().category(UNCATEGORIZED_ID)!!))
        rejected { org.deleteBatch(old, UNCATEGORIZED_ID, emptyMap()) }
        assertEquals(before, BackupFormat.decode(BackupRepository(a, clock).export("test")).data.content())
        assertEquals("custom", a.dao().task("task")!!.categoryId)
    }
    @Test fun restoredRenamedStudyRetainsIdentityAndDeletionProtection() = databases { a, b ->
        OrganizationRepository(a, clock).saveCategory(STUDY_ID, "閱讀", a.dao().category(STUDY_ID)!!.revision)
        TaskRepository(a, clock).save(TaskDraft("task", "保留進度", categoryId = STUDY_ID, status = TaskStatus.IN_PROGRESS))
        val document = BackupFormat.decode(BackupRepository(a, clock).export("1.3.1-dev01"))
        val restore = BackupRepository(b, clock); restore.apply(document, restore.preview(document, RestoreMode.REPLACE), emptyMap())
        val org = OrganizationRepository(b, clock); val study = b.dao().category(STUDY_ID)!!
        assertEquals("閱讀", study.name); assertEquals(TaskStatus.IN_PROGRESS, b.dao().task("task")!!.status)
        rejected { org.deleteCategory(STUDY_ID, study.revision, UNCATEGORIZED_ID, mapOf("task" to b.dao().task("task")!!.revision), emptyMap()) }
        rejected { org.previewDelete(setOf(STUDY_ID), false) }
        assertEquals(STUDY_ID, b.dao().task("task")!!.categoryId)
        assertEquals(9, b.openHelper.readableDatabase.version)
    }
}
