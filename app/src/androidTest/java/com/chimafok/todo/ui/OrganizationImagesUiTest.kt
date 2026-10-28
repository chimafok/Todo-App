package com.chimafok.todo.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.ReminderRuntime
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OrganizationImagesUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val prefix = "QA130-${UUID.randomUUID().toString().take(8)}"
    private val db get() = ReminderRuntime.database
    private val model get() = ViewModelProvider(compose.activity)[TodoViewModel::class.java]
    private fun <T> data(block: suspend () -> T) = runBlocking { block() }
    private fun await(tag: String) { compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    @Before fun setup() { await("add_task"); assertEquals("com.chimafok.todo.qa", compose.activity.packageName) }
    @After fun cleanup() { data { model.discardNewTaskDraft(); db.withTransaction {
        db.dao().allTasks().filter { it.title.startsWith(prefix) }.forEach { db.history().purgeTask(it.id) }
        db.dao().allTags().filter { it.name.startsWith(prefix) }.forEach { db.dao().deleteTag(it.id) }
        db.dao().allCategories().filter { it.name.startsWith(prefix) }.forEach { db.dao().deleteCategory(it.id) }
    } }; File(compose.activity.cacheDir, "qa-image.png").delete(); (0..11).forEach { File(compose.activity.cacheDir, "qa-image-$it.png").delete() } }
    private fun organize(tags: Boolean = false) {
        compose.onNodeWithTag("open_menu").performClick(); compose.onNodeWithTag("nav_ORGANIZE").performClick(); await("org_add")
        if (tags) compose.onNodeWithText("標籤", substring = false).performClick()
    }
    private fun show(tag: String) { compose.onNodeWithTag("org_list").performScrollToNode(hasTestTag(tag)) }
    private fun capture(label: String) { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/todo130-$label.png").use { java.io.FileInputStream(it.fileDescriptor).use { input -> input.readBytes() } } }
    private fun keyboard() { androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot()).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.closeSoftKeyboard()); compose.waitForIdle() }
    private fun drag(first: String, second: String) {
        show("org_row_$second")
        val a = compose.onNodeWithTag("org_row_$first").getUnclippedBoundsInRoot()
        val b = compose.onNodeWithTag("org_row_$second").getUnclippedBoundsInRoot()
        val distance = with(compose.density) { ((b.top + b.bottom - a.top - a.bottom) / 2).toPx() }
        compose.onNodeWithTag("org_row_$first").performTouchInput { down(Offset(25f, height/2f)) }
        compose.mainClock.advanceTimeBy(800); Thread.sleep(800)
        capture("drag-hold")
        compose.onNodeWithTag("org_list").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"正在排序"))
        compose.onNodeWithTag("org_row_$first").performTouchInput { moveTo(Offset(25f, height/2f + distance), delayMillis = 300); up() }
        compose.waitForIdle(); capture("drag-end")
    }
    @Test fun longPressReordersCategoriesAndTagsAndPersists() {
        data { val org = OrganizationRepository(db); listOf("a", "b").forEach { org.saveCategory("$prefix-$it", "$prefix-$it", null); org.saveTag("$prefix-$it", "$prefix-$it", null) } }
        organize(); drag("$prefix-a", "$prefix-b")
        compose.waitUntil(15_000) { data { val ids = db.dao().allCategories().map { it.id }; ids.indexOf("$prefix-a") > ids.indexOf("$prefix-b") } }
        compose.onNodeWithText("標籤", substring = false).performClick(); drag("$prefix-a", "$prefix-b")
        compose.waitUntil(15_000) { data { db.dao().allTags().map { it.id }.takeLast(2) == listOf("$prefix-b", "$prefix-a") } }
        compose.activityRule.scenario.recreate(); await("org_add")
        assertTrue(data { val ids = db.dao().allCategories().map { it.id }; ids.indexOf("$prefix-a") > ids.indexOf("$prefix-b") })
    }
    @Test fun heldDragAtBottomScrollsAndMovesAcrossOffscreenTags() {
        data { val org = OrganizationRepository(db); (0..11).forEach { val id = "$prefix-${it.toString().padStart(2,'0')}"; org.saveTag(id, id, null) } }
        organize(true); show("org_row_$prefix-00")
        val row = compose.onNodeWithTag("org_row_$prefix-00").getUnclippedBoundsInRoot()
        val list = compose.onNodeWithTag("org_list").getUnclippedBoundsInRoot()
        val bottom = with(compose.density) { (list.bottom - row.top).toPx() } - 24f
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("org_row_$prefix-00").performTouchInput { down(Offset(25f, height/2f)) }
            compose.mainClock.advanceTimeBy(800); Thread.sleep(700)
            compose.onNodeWithTag("org_row_$prefix-00").performTouchInput { advanceEventTime(800); moveTo(Offset(25f, bottom), delayMillis = 300) }
            compose.mainClock.advanceTimeBy(2000)
            compose.onNodeWithTag("org_list").performTouchInput { up() }
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitUntil(15_000) { data { db.dao().allTags().map { it.id }.indexOf("$prefix-00") >= 6 } }
        capture("drag-autoscroll")
    }
    @Test fun categoryBatchSelectionProtectsSystemCategoriesAndKeepsTask() {
        data { val org = OrganizationRepository(db); listOf("a", "b").forEach { org.saveCategory("$prefix-$it", "$prefix-$it", null) }; TaskRepository(db).save(TaskDraft("$prefix-task", prefix, categoryId = "$prefix-a")) }
        organize(); compose.onNodeWithTag("org_batch_delete").performClick()
        show("org_select_$UNCATEGORIZED_ID"); compose.onNodeWithTag("org_select_$UNCATEGORIZED_ID").assertIsNotEnabled()
        listOf("a", "b").forEach { show("org_select_$prefix-$it"); compose.onNodeWithTag("org_select_$prefix-$it").performClick() }
        show("org_batch_confirm"); compose.onNodeWithTag("org_batch_confirm").performClick(); await("org_batch_delete_confirm")
        compose.onNodeWithTag("org_batch_delete_confirm").performClick()
        compose.waitUntil(15_000) { data { db.dao().category("$prefix-a") == null && db.dao().category("$prefix-b") == null } }
        assertEquals(UNCATEGORIZED_ID, data { db.dao().task("$prefix-task")!!.categoryId })
    }
    @Test fun tagBatchCancelDoesNotDeleteAndConfirmOnlyRemovesSelection() {
        data { val org = OrganizationRepository(db); listOf("a", "b", "keep").forEach { org.saveTag("$prefix-$it", "$prefix-$it", null) } }
        organize(true); compose.onNodeWithTag("org_batch_delete").performClick()
        listOf("a", "b").forEach { show("org_select_$prefix-$it"); compose.onNodeWithTag("org_select_$prefix-$it").performClick() }
        show("org_batch_confirm"); compose.onNodeWithTag("org_batch_confirm").performClick(); await("org_batch_delete_confirm")
        compose.onNodeWithTag("org_batch_delete_cancel").performClick(); assertNotNull(data { db.dao().tag("$prefix-a") })
        compose.onNodeWithTag("org_batch_confirm").performClick(); await("org_batch_delete_confirm"); compose.onNodeWithTag("org_batch_delete_confirm").performClick()
        compose.waitUntil(15_000) { data { db.dao().tag("$prefix-a") == null } }; assertNotNull(data { db.dao().tag("$prefix-keep") })
    }
    private fun draftImage(): String = data {
        val bitmap = android.graphics.Bitmap.createBitmap(160, 100, android.graphics.Bitmap.Config.ARGB_8888); bitmap.eraseColor(android.graphics.Color.CYAN)
        File(compose.activity.cacheDir, "qa-image.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val id = NoteImages(db).import(compose.activity, listOf(android.net.Uri.parse("content://${compose.activity.packageName}.backup-test/image.png")), emptyList()).single()
        model.keepNewTaskDraft(NewTaskDraft("$prefix-task", title = prefix, notes = "原有文字", imageIds = id)); id
    }
    @Test fun imageDraftThumbnailViewerRotationRemoveAndTextRemain() {
        val image = draftImage(); await("resume_draft"); compose.onNodeWithTag("resume_draft").performClick(); await("task_title"); keyboard()
        compose.onNodeWithTag("note_add_images").performScrollTo(); capture("image-before"); compose.onNodeWithTag("note_image_0").performClick(); capture("image-after"); await("note_image_close"); compose.onNodeWithTag("note_image_close").performClick()
        compose.activityRule.scenario.recreate(); await("task_title"); keyboard(); compose.onNodeWithTag("note_add_images").performScrollTo(); compose.onNodeWithTag("note_image_0").assertIsDisplayed()
        compose.onNodeWithTag("task_notes").assertTextContains("原有文字")
        val thumb = compose.onNodeWithTag("note_image_0").getUnclippedBoundsInRoot()
        val remove = compose.onNodeWithTag("note_image_remove_0").getUnclippedBoundsInRoot()
        assertEquals(thumb.top.value, remove.top.value, 1f)
        assertEquals(thumb.right.value, remove.right.value, 1f)
        compose.onNodeWithContentDescription("移除圖片 1").assertExists()
        compose.onNodeWithTag("note_image_remove_0").performClick(); compose.onNodeWithTag("note_image_0").assertDoesNotExist()
        compose.onNodeWithTag("note_image_close").assertDoesNotExist()
        compose.onNodeWithTag("save_task").performClick()
        compose.waitUntil(15_000) { data { db.dao().task("$prefix-task") != null } }
        assertEquals("", data { db.dao().task("$prefix-task")!!.imageIds }); assertEquals("原有文字", data { db.dao().task("$prefix-task")!!.notes })
        compose.waitUntil(15_000) { data { db.noteImages().image(image) == null } }
    }
    @Test fun selectedImageResultAddsCopyAndKeepsText() {
        val context = compose.activity
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val uri = android.net.Uri.parse("content://${context.packageName}.backup-test/image.png")
        val bitmap = android.graphics.Bitmap.createBitmap(140, 90, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        File(context.cacheDir, "qa-image.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val monitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: android.content.Intent): android.app.Instrumentation.ActivityResult? {
                if (intent.type != "image/*") return null
                return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, android.content.Intent().setData(uri))
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.onNodeWithTag("add_task").performClick(); compose.onNodeWithTag("task_title").performTextInput(prefix); keyboard()
            compose.onNodeWithTag("task_notes").performScrollTo().performTextInput("選圖保留文字"); keyboard()
            compose.onNodeWithTag("note_add_images").performScrollTo().performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("note_image_0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("task_notes").assertTextContains("選圖保留文字")
            File(context.cacheDir, "qa-image.png").delete()
            compose.onNodeWithTag("save_task").performClick()
            compose.waitUntil(15_000) { data { db.dao().allTasks().any { it.title == prefix } } }
            val task = data { db.dao().allTasks().single { it.title == prefix } }
            assertEquals(1, decodeImageIds(task.imageIds).size); assertNotNull(data { NoteImages(db).bitmap(task.imageIds, true) })
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun twelveImagesAcrossBatchesCanScrollRemoveAndSave() {
        val context = compose.activity
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val uris = (0..11).map { n ->
            val bitmap = android.graphics.Bitmap.createBitmap(140, 90, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(10 + n * 19, 40, 220 - n * 10))
            File(context.cacheDir, "qa-image-$n.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            android.net.Uri.parse("content://${context.packageName}.backup-test/image-$n.png")
        }
        var batches = 0
        val monitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: android.content.Intent): android.app.Instrumentation.ActivityResult? {
                if (intent.type != "image/*") return null
                val batch = if (batches++ == 0) uris.take(9) else uris.drop(9)
                val clip = android.content.ClipData.newRawUri("QA images", batch.first())
                batch.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
                return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, android.content.Intent().apply { clipData = clip })
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.onNodeWithTag("add_task").performClick(); compose.onNodeWithTag("task_title").performTextInput(prefix); keyboard()
            compose.onNodeWithTag("task_notes").performScrollTo().performTextInput("多張圖片保留文字"); keyboard()
            compose.onNodeWithTag("note_add_images").performScrollTo().performClick(); await("note_image_0")
            compose.onNodeWithTag("note_images").performScrollToIndex(8)
            compose.onNodeWithTag("note_image_8").assertIsDisplayed()
            compose.onNodeWithTag("note_add_images").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("note_add_images") and isEnabled()).fetchSemanticsNodes().isNotEmpty() && batches == 2 }
            compose.onNodeWithTag("note_images").performScrollToIndex(11)
            compose.onNodeWithTag("note_image_11").assertIsDisplayed(); capture("unlimited-images")
            compose.onNodeWithTag("note_image_remove_11").performClick()
            compose.onNodeWithTag("note_image_close").assertDoesNotExist()
            compose.onNodeWithTag("task_notes").assertTextContains("多張圖片保留文字")
            compose.onNodeWithTag("save_task").performClick()
            compose.waitUntil(15_000) { data { db.dao().allTasks().any { it.title == prefix } } }
            val task = data { db.dao().allTasks().single { it.title == prefix } }
            assertEquals(11, decodeImageIds(task.imageIds).size); assertEquals("多張圖片保留文字", task.notes)
            assertEquals(2, batches)
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun cancelSystemPhotoPickerReturnsToSameDraft() {
        val qaPackage = compose.activity.packageName
        val currentModel = model
        compose.onNodeWithTag("add_task").performClick(); compose.onNodeWithTag("task_title").performTextInput(prefix); keyboard()
        compose.onNodeWithTag("note_add_images").performScrollTo().performClick()
        // Wait for this app's explicit picker flow; press Back only on the owned QA emulator.
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
        while (android.os.SystemClock.uptimeMillis() < deadline && automation.rootInActiveWindow?.packageName == qaPackage) Thread.sleep(100)
        // The platform picker may show a keyboard/intro layer before the picker itself.
        // Back only while this owned emulator is outside QA; never dismiss the draft.
        Thread.sleep(1000)
        repeat(3) {
            if (currentModel.externalImageFlow && automation.rootInActiveWindow?.packageName != qaPackage) {
                automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                val returned = android.os.SystemClock.uptimeMillis() + 3000
                while (currentModel.externalImageFlow && android.os.SystemClock.uptimeMillis() < returned) Thread.sleep(100)
            }
        }
        compose.waitUntil(15_000) { !currentModel.externalImageFlow }; await("task_title"); compose.onNodeWithTag("task_title").assertTextContains(prefix)
    }
}
