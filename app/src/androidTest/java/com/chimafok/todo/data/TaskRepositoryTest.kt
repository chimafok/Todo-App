package com.chimafok.todo.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TaskRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneId.of("Asia/Hong_Kong"))

    @Test fun persistedCrudAndIdempotenceSurviveDatabaseReopen() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        var db = TodoDatabase.create(context, name)
        try {
            var repository = TaskRepository(db, clock)
            assertEquals(listOf("學習", "生活", "作業", "未分類"), repository.categories.first().map { it.name })
            val draft = TaskDraft(UUID.randomUUID().toString(), "持久任務", "第一行\nhttps://example.com", dueDate = LocalDate.of(2026, 10, 21))
            repository.save(draft)
            repository.save(draft)
            assertEquals(1, repository.tasks.first().size)
            val first = db.dao().task(draft.id)!!
            repository.setStatus(first.id, first.revision, TaskStatus.COMPLETED)
            repository.setStatus(first.id, first.revision, TaskStatus.COMPLETED)
            db.close()
            db = TodoDatabase.create(context, name)
            repository = TaskRepository(db, clock)
            val complete = db.dao().task(first.id)!!
            assertEquals(TaskStatus.COMPLETED, complete.status)
            assertEquals(draft.dueDate, complete.dueDate)
            assertEquals(clock.millis(), complete.completedAt)
            repository.setStatus(complete.id, complete.revision, TaskStatus.OPEN)
            val reopened = db.dao().task(complete.id)!!
            assertNull(reopened.completedAt)
            repository.save(draft.copy(title = "已編輯", expectedRevision = reopened.revision))
            val edited = db.dao().task(first.id)!!
            assertEquals("已編輯", edited.title)
            repository.trash(edited.id, edited.revision)
            assertNotNull(db.dao().task(first.id)!!.deletedAt)
            repository.restore(first.id)
            assertNull(db.dao().task(first.id)!!.deletedAt)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun invalidAndStaleUpdatesLeaveOriginalDataIntact() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        val db = TodoDatabase.create(context, name)
        try {
            val repository = TaskRepository(db, clock)
            val draft = TaskDraft(UUID.randomUUID().toString(), "原名")
            repository.save(draft)
            val task = db.dao().task(draft.id)!!
            try { repository.save(draft.copy(title = "錯誤", categoryId = "missing", expectedRevision = task.revision)); fail() }
            catch (_: IllegalArgumentException) { }
            try { repository.save(draft.copy(title = "過期", expectedRevision = task.revision + 1)); fail() }
            catch (_: IllegalArgumentException) { }
            assertEquals(task, db.dao().task(draft.id))
            try { repository.save(draft.copy(id = UUID.randomUUID().toString(), status = TaskStatus.DONE_UNSUBMITTED)); fail() }
            catch (_: IllegalArgumentException) { }
            assertEquals(1, repository.tasks.first().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun assignmentUnsubmittedRemainsOpenAndExpiredTrashCannotRestore() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        val db = TodoDatabase.create(context, name)
        try {
            val repository = TaskRepository(db, clock)
            val draft = TaskDraft(UUID.randomUUID().toString(), "作業", categoryId = ASSIGNMENTS_ID, status = TaskStatus.DONE_UNSUBMITTED)
            repository.save(draft)
            val task = db.dao().task(draft.id)!!
            assertNull(task.completedAt)
            repository.trash(task.id, task.revision)
            val later = TaskRepository(db, Clock.offset(clock, Duration.ofDays(30)))
            try { later.restore(task.id); fail("Expired trash must not restore") }
            catch (_: IllegalArgumentException) { }
            assertNotNull(db.dao().task(task.id)!!.deletedAt)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
