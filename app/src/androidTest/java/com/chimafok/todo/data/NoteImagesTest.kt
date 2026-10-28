package com.chimafok.todo.data

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimafok.todo.domain.*
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NoteImagesTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val clock = Clock.fixed(Instant.parse("2030-03-02T09:00:00Z"), ZoneOffset.UTC)
    private val uri get() = Uri.parse("content://${context.packageName}.backup-test/image.png")
    private fun databases(block: suspend (TodoDatabase, TodoDatabase) -> Unit) = runBlocking {
        assertEquals("com.chimafok.todo.qa", context.packageName)
        val names = List(2) { "images130-${UUID.randomUUID()}.db" }; val a = TodoDatabase.create(context, names[0]); val b = TodoDatabase.create(context, names[1])
        try { block(a, b) } finally { a.close(); b.close(); names.forEach(context::deleteDatabase); File(context.cacheDir, "qa-image.png").delete() }
    }
    private fun source(color: Int = Color.CYAN): ByteArray {
        val image = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888); image.eraseColor(color)
        val out = java.io.ByteArrayOutputStream(); image.compress(Bitmap.CompressFormat.PNG, 100, out); image.recycle()
        return out.toByteArray().also { File(context.cacheDir, "qa-image.png").writeBytes(it) }
    }
    private suspend fun rejected(block: suspend () -> Unit) { var failed = false; try { block() } catch (_: Exception) { failed = true }; assertTrue(failed) }
    @Test fun importsOnlySelectedContentAndKeepsCopyAfterOriginalIsRemoved() = databases { a, _ ->
        val raw = source(); val store = NoteImages(a); val ids = store.import(context, listOf(uri), emptyList())
        assertEquals(1, ids.size); assertTrue(File(context.cacheDir, "qa-image.png").readBytes().contentEquals(raw))
        File(context.cacheDir, "qa-image.png").delete()
        val bitmap = store.bitmap(ids.single(), false)!!; assertEquals(120, bitmap.width); assertEquals(80, bitmap.height); bitmap.recycle()
        val row = a.noteImages().image(ids.single())!!; validateImageData(row.id, row.data)
        rejected { store.import(context, listOf(Uri.parse("file:///invalid")), ids) }
        assertEquals(ids, a.noteImages().ids())
    }
    @Test fun duplicateImagesDeduplicateAndFailedMultiImportDoesNotPartiallyInsert() = databases { a, _ ->
        source(); val store = NoteImages(a); val ids = store.import(context, listOf(uri, uri), emptyList()); assertEquals(1, ids.size)
        source(Color.RED); rejected { store.import(context, listOf(uri, Uri.parse("content://${context.packageName}.backup-test/missing.png")), ids) }
        assertEquals(ids, a.noteImages().ids())
    }
    @Test fun notesImagesSurviveTrashRecurringSnapshotsAndBackupRestore() = databases { a, b ->
        source(); val id = NoteImages(a).import(context, listOf(uri), emptyList()).single(); val encoded = encodeImageIds(listOf(id))
        val r = TaskRepository(a, clock); r.save(TaskDraft("task", "有圖", notes = "保留文字", imageIds = encoded)); r.trash("task", a.dao().task("task")!!.revision)
        val day = LocalDate.now(clock); r.save(TaskDraft("series", "重複圖片", dueDate = day, zoneId = "UTC", recurrence = RecurrenceRule(day, RecurrenceFrequency.DAILY, "UTC", maxCount = 2), imageIds = encoded))
        val first = a.dao().task(occurrenceId("series", 1))!!; r.setStatus(first.id, first.revision, TaskStatus.COMPLETED)
        assertEquals(encoded, OccurrenceSnapshot.read(a.dao().occurrence(first.id)!!.snapshot)!!.imageIds)
        val text = BackupRepository(a, clock).export("1.3.0-dev01"); val envelope = JSONObject(text); assertEquals(4, envelope.getInt("formatVersion")); assertEquals(9, envelope.getInt("schemaVersion"))
        val document = BackupFormat.decode(text); assertEquals(1, document.data.rows("note_images").size)
        val restore = BackupRepository(b, clock); restore.apply(document, restore.preview(document, RestoreMode.REPLACE), emptyMap())
        assertEquals(document.data.content(), BackupFormat.decode(BackupRepository(b, clock).export("1.3.0-dev01")).data.content())
        assertEquals("保留文字", b.dao().task("task")!!.notes); assertNotNull(b.dao().task("task")!!.deletedAt)
        assertTrue(b.dao().allTasks().all { it.imageIds == encoded }); assertNotNull(NoteImages(b).bitmap(id, true))
    }
    @Test fun missingOrCorruptBackupImageIsRejectedBeforeAnyWrite() = databases { a, b ->
        source(); val id = NoteImages(a).import(context, listOf(uri), emptyList()).single()
        TaskRepository(a, clock).save(TaskDraft("task", "有圖", imageIds = id))
        val text = BackupRepository(a, clock).export("test"); val missing = JSONObject(text); missing.getJSONObject("data").put("note_images", JSONArray())
        rejected { BackupFormat.decode(missing.toString()) }; assertTrue(b.dao().allTasks().isEmpty())
        val corrupt = JSONObject(text); corrupt.getJSONObject("data").getJSONArray("note_images").getJSONObject(0).put("data", "aW52YWxpZA==")
        rejected { BackupFormat.decode(corrupt.toString()) }; assertTrue(b.noteImages().ids().isEmpty())
        rejected { TaskRepository(b, clock).save(TaskDraft("missing", "缺圖", imageIds = id)) }; assertNull(b.dao().task("missing"))
    }
    @Test fun savedDraftKeepsImagesAndDiscardPrunesOnlyUnreferencedImages() = databases { a, _ ->
        source(); val id = NoteImages(a).import(context, listOf(uri), emptyList()).single()
        val folder = File(context.cacheDir, "draft130-${UUID.randomUUID()}").also { it.mkdir() }; val store = NewTaskDraftStore(folder)
        try {
            store.save(NewTaskDraft("draft", title = "圖片草稿", imageIds = id)); assertEquals(id, store.read()!!.imageIds)
            NoteImages(a).prune(decodeImageIds(store.read()!!.imageIds)); assertNotNull(a.noteImages().image(id))
            store.clear(); NoteImages(a).prune(emptyList()); assertNull(a.noteImages().image(id))
        } finally { store.clear(); folder.delete() }
    }
    @Test fun oldFormatsOneTwoThreeRemainReadableWithoutImages() = databases { a, b ->
        TaskRepository(a, clock).save(TaskDraft("task", "舊格式"))
        val text = BackupRepository(a, clock).export("test")
        for (version in listOf(1, 2, 3)) {
            val root = JSONObject(text).put("formatVersion", version).put("schemaVersion", if (version == 1) 7 else 8)
            val data = root.getJSONObject("data"); val tables = if (version == 1) legacyBackupTables else previousBackupTables
            data.keys().asSequence().toList().filter { it !in tables }.forEach(data::remove)
            tables.forEach { (table, spec) -> val rows = data.getJSONArray(table); for (n in 0 until rows.length()) {
                val row = rows.getJSONObject(n); row.keys().asSequence().toList().filter { key -> spec.fields.none { it.name == key } }.forEach(row::remove)
            } }
            val doc = BackupFormat.decode(root.toString()); val r = BackupRepository(b, clock); r.apply(doc, r.preview(doc, RestoreMode.REPLACE), emptyMap())
            assertEquals("", b.dao().task("task")!!.imageIds)
        }
    }
    @Test fun schemaEightUpgradesWithoutLosingTasksOrSeries() = runBlocking {
        val name = "migration130-${UUID.randomUUID()}.db"; val file = context.getDatabasePath(name); file.parentFile!!.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("com.chimafok.todo.data.TodoDatabase/8.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { sql ->
            val entities = schema.getJSONArray("entities"); for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i); sql.execSQL(e.getString("createSql").replace("${'$'}{TABLE_NAME}", e.getString("tableName")))
                val indices = e.optJSONArray("indices") ?: JSONArray(); for (n in 0 until indices.length()) sql.execSQL(indices.getJSONObject(n).getString("createSql").replace("${'$'}{TABLE_NAME}", e.getString("tableName")))
            }
            sql.execSQL("INSERT INTO categories VALUES(?, '未分類','UNCATEGORIZED',0,1,1,1)", arrayOf(UNCATEGORIZED_ID))
            sql.execSQL("INSERT INTO tasks(id,title,notes,categoryId,status,createdAt,updatedAt,revision) VALUES('legacy','保留','原內容',?,'OPEN',1,2,3)", arrayOf(UNCATEGORIZED_ID))
            sql.execSQL("INSERT INTO recurring_series(id,title,notes,categoryId,lifecycle,cursor,createdAt,updatedAt,revision) VALUES('series','系列','內容',?,'ACTIVE',0,1,2,4)", arrayOf(UNCATEGORIZED_ID)); sql.version = 8
        }
        val db = TodoDatabase.create(context, name)
        try { assertEquals(9, db.openHelper.readableDatabase.version); val task = db.dao().task("legacy")!!
            assertEquals("原內容", task.notes); assertEquals(3L, task.revision); assertEquals("", task.imageIds)
            assertEquals(4L, db.dao().series("series")!!.revision); assertEquals("", db.dao().series("series")!!.imageIds)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
