package com.chimafok.todo.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimafok.todo.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OrganizationRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneId.of("Asia/Hong_Kong"))
    private suspend fun expectRejected(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }

    @Test fun protectedNamesUniqueTagsOrderingAndDeletionAreTransactional() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        var db = TodoDatabase.create(context, name)
        try {
            var org = OrganizationRepository(db, clock)
            val tasks = TaskRepository(db, clock)
            val categories = db.dao().allCategories()
            expectRejected { org.saveCategory(UNCATEGORIZED_ID, "其他", categories.single { it.id == UNCATEGORIZED_ID }.revision) }
            expectRejected { org.deleteCategory(ASSIGNMENTS_ID, 1, UNCATEGORIZED_ID, emptyMap(), emptyMap()) }
            expectRejected { org.saveCategory("dupe", " 學習 ", null) }
            org.saveTag("tag-a", "Important", null)
            expectRejected { org.saveTag("dupe", " important ", null) }
            org.saveTag("tag-b", "其他", null)
            org.move("tag-b", true, -1, listOf("tag-a", "tag-b"))
            tasks.save(TaskDraft("task", "保留任務", tagIds = setOf("tag-a", "tag-b")))
            val before = db.dao().task("task")!!
            org.deleteTag("tag-a", db.dao().tag("tag-a")!!.revision)
            assertEquals(setOf("tag-b"), db.dao().taskTagIds("task").toSet())
            assertTrue(db.dao().task("task")!!.revision > before.revision)
            db.close()
            db = TodoDatabase.create(context, name)
            org = OrganizationRepository(db, clock)
            assertEquals(listOf("tag-b"), db.dao().allTags().map { it.id })
            org.saveCategory(ASSIGNMENTS_ID, "課業", db.dao().category(ASSIGNMENTS_ID)!!.revision)
            assertEquals(SystemCategory.ASSIGNMENTS, db.dao().category(ASSIGNMENTS_ID)!!.systemKind)
            assertNotNull(db.dao().task("task"))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun deletingCategoryRequiresAllAssignmentChoicesAndPreservesTrashAndTags() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        val db = TodoDatabase.create(context, name)
        try {
            val org = OrganizationRepository(db, clock)
            val tasks = TaskRepository(db, clock)
            org.saveCategory("source", "來源", null)
            org.saveTag("tag", "保留", null)
            tasks.save(TaskDraft("active", "未完成", categoryId = "source", tagIds = setOf("tag")))
            tasks.save(TaskDraft("trash", "已刪除", categoryId = "source", status = TaskStatus.COMPLETED))
            val trash = db.dao().task("trash")!!
            tasks.trash(trash.id, trash.revision)
            val snapshot = db.dao().categoryTasks("source")
            val revisions = snapshot.associate { it.id to it.revision }
            expectRejected { org.deleteCategory("source", 1, ASSIGNMENTS_ID, revisions, mapOf("active" to TaskStatus.IN_PROGRESS)) }
            assertEquals(snapshot, db.dao().categoryTasks("source"))
            expectRejected { org.deleteCategory("source", 1, UNCATEGORIZED_ID, emptyMap(), emptyMap()) }
            org.deleteCategory("source", 1, ASSIGNMENTS_ID, revisions,
                mapOf("active" to TaskStatus.DONE_UNSUBMITTED, "trash" to TaskStatus.COMPLETED))
            assertNull(db.dao().category("source"))
            assertEquals(TaskStatus.DONE_UNSUBMITTED, db.dao().task("active")!!.status)
            assertNull(db.dao().task("active")!!.completedAt)
            assertNotNull(db.dao().task("trash")!!.deletedAt)
            assertEquals(setOf("tag"), db.dao().taskTagIds("active").toSet())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun timedTaskReopenKeepsZoneAndRetryWithAnotherZoneIsRejected() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        var db = TodoDatabase.create(context, name)
        try {
            val draft = TaskDraft("deadline", "香港截止", dueDate = LocalDate.of(2026, 10, 22), dueTime = LocalTime.of(20, 0), zoneId = "Asia/Hong_Kong")
            TaskRepository(db, clock).save(draft)
            expectRejected { TaskRepository(db, clock).save(draft.copy(id = "gap", dueDate = LocalDate.of(2026, 3, 8),
                dueTime = LocalTime.of(2, 30), zoneId = "America/New_York")) }
            expectRejected { TaskRepository(db, clock).save(draft.copy(zoneId = "America/Los_Angeles")) }
            db.close(); db = TodoDatabase.create(context, name)
            val task = db.dao().task(draft.id)!!
            assertEquals(draft.zoneId, task.zoneId)
            assertEquals(Instant.parse("2026-10-22T12:00:00Z"), task.dueInstant())
            TaskRepository(db, clock).save(draft.copy(title = "只改名稱", expectedRevision = task.revision))
            assertEquals(draft.zoneId, db.dao().task(draft.id)!!.zoneId)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun actualVersionOneSchemaMigratesWithoutLosingDeadlineOrTaskIdentity() = runBlocking {
        val name = "test-migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.chimafok.todo.data.TodoDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
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
            old.execSQL("INSERT INTO categories VALUES (?, ?, ?, 0, 1, 1, 1)", arrayOf(UNCATEGORIZED_ID, "未分類", "UNCATEGORIZED"))
            old.execSQL("INSERT INTO tasks VALUES ('legacy','舊任務','保留備註',?,'2026-10-22','20:00','Asia/Hong_Kong','OPEN',NULL,NULL,1,2,3)", arrayOf(UNCATEGORIZED_ID))
            old.version = 1
        }
        val db = TodoDatabase.create(context, name)
        try {
            val task = db.dao().task("legacy")!!
            assertEquals("保留備註", task.notes)
            assertEquals(3, task.revision.toInt())
            assertEquals("Asia/Hong_Kong", task.zoneId)
            assertEquals(Instant.parse("2026-10-22T12:00:00Z"), task.dueInstant())
            assertTrue(db.dao().allTags().isEmpty())
            OrganizationRepository(db, clock).saveTag("new-tag", "新標籤", null)
            TaskRepository(db, clock).save(TaskDraft(task.id, task.title, task.notes, task.categoryId, task.dueDate, task.dueTime,
                task.zoneId, task.status, task.revision, setOf("new-tag")))
            assertEquals(listOf("new-tag"), db.dao().taskTagIds("legacy"))
            assertEquals(9, db.openHelper.readableDatabase.version)
            assertNull(task.startDate); assertNull(task.startTime); assertNull(task.startZoneId)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
