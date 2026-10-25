package com.chimafok.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimafok.todo.MainActivity
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.util.UUID

@com.chimafok.todo.Regression104
@RunWith(AndroidJUnit4::class)
class TaskBrowserUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val prefix = "QAq-${UUID.randomUUID().toString().take(8)}"
    private val category = "$prefix-cat"
    private val tagA = "$prefix-tag-a"
    private val tagB = "$prefix-tag-b"
    private val tagC = "$prefix-tag-c"
    private val ids = listOf("a", "b", "c", "done", "trash", "outside").associateWith { "$prefix-$it" }
    private lateinit var db: TodoDatabase

    @Before fun fixtures() {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty() }
        db = TodoDatabase.create(compose.activity)
        runBlocking {
            val org = OrganizationRepository(db)
            val repo = TaskRepository(db)
            org.saveCategory(category, "QAc分類", null)
            org.saveTag(tagA, "QAt甲", null); org.saveTag(tagB, "QAt乙", null); org.saveTag(tagC, "QAt丙", null)
            repo.save(TaskDraft(ids.getValue("a"), "$prefix-a", categoryId = category, tagIds = setOf(tagA, tagB)))
            repo.save(TaskDraft(ids.getValue("b"), "$prefix-b", tagIds = setOf(tagA)))
            repo.save(TaskDraft(ids.getValue("c"), "$prefix-c", categoryId = category, tagIds = setOf(tagB)))
            repo.save(TaskDraft(ids.getValue("done"), "$prefix-done", status = TaskStatus.COMPLETED))
            repo.save(TaskDraft(ids.getValue("trash"), "$prefix-trash"))
            repo.trash(ids.getValue("trash"), 1)
            repo.save(TaskDraft(ids.getValue("outside"), "QAz範圍外"))
        }
        // A separate fixture connection does not notify the app's Room observer.
        compose.runOnUiThread { ViewModelProvider(compose.activity)[TodoViewModel::class.java].reload() }
        compose.waitUntil(20_000) {
            ViewModelProvider(compose.activity)[TodoViewModel::class.java].state.value.tasks.any { it.id == ids.getValue("a") }
        }
        search(prefix)
    }

    @After fun cleanup() {
        if (!::db.isInitialized) return
        try {
            ids.values.forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(it)) }
            listOf(tagA, tagB, tagC).forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM tags WHERE id = ?", arrayOf(it)) }
            db.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE id = ?", arrayOf(category))
        } finally { db.close() }
    }

    private fun search(query: String) {
        compose.onNodeWithTag("task_search").performTextReplacement(query)
        compose.onNodeWithTag("task_search").performImeAction()
        compose.waitForIdle()
    }
    private fun navigate(destination: String) {
        compose.onNodeWithTag("open_menu").performClick()
        compose.onNodeWithTag("nav_$destination").performClick()
        compose.waitForIdle()
    }
    private fun visible(key: String) {
        compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_${ids.getValue(key)}"))
        compose.onNodeWithTag("task_${ids.getValue(key)}").assertIsDisplayed()
    }
    private fun awaitBatchClosed() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("confirm_batch").fetchSemanticsNodes().isEmpty() }
    }
    private fun capture(label: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/todo-030-$label.png").use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }

    @Test fun cachedSearchRefreshesAfterTagRenameDeletionAndRotation() {
        search("QAt甲")
        visible("a"); visible("b")
        lateinit var viewModel: TodoViewModel
        compose.runOnUiThread { viewModel = ViewModelProvider(compose.activity)[TodoViewModel::class.java] }
        runBlocking { viewModel.saveTag(tagA, "QAt更新名稱", 1) }
        compose.waitUntil(10_000) { viewModel.state.value.tags.any { it.id == tagA && it.name == "QAt更新名稱" } }
        compose.onNodeWithText("找不到符合的任務").assertExists()
        search("QAt更新名稱")
        visible("a"); visible("b")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("task_search").assertTextContains("QAt更新名稱")
        visible("a"); visible("b")
        compose.runOnUiThread { viewModel = ViewModelProvider(compose.activity)[TodoViewModel::class.java] }
        runBlocking { viewModel.deleteTag(viewModel.state.value.tags.single { it.id == tagA }) }
        compose.waitUntil(10_000) { viewModel.state.value.tags.none { it.id == tagA } }
        compose.onNodeWithText("找不到符合的任務").assertExists()
        search(prefix)
        visible("a"); visible("b"); visible("c")
    }

    @Test fun searchFiltersAndSelectionStayInsideTheirPageAndCurrentResults() {
        compose.onNodeWithTag("result_count").assertTextEquals("3 項")
        visible("a"); visible("b"); visible("c")
        compose.onNodeWithText("$prefix-done").assertDoesNotExist()
        compose.onNodeWithText("$prefix-trash").assertDoesNotExist()
        compose.onNodeWithTag("open_filters").performClick()
        compose.onNodeWithTag("filter_tag_$tagA").performScrollTo().performClick()
        compose.onNodeWithTag("filter_tag_$tagB").performScrollTo().performClick()
        compose.onNodeWithTag("apply_filters").performClick()
        compose.onNodeWithTag("result_count").assertTextEquals("1 項")
        visible("a")
        compose.onNodeWithTag("open_filters").performClick()
        compose.onNodeWithTag("filter_category_$category").performScrollTo().performClick()
        compose.onNodeWithTag("filter_any_tags").performScrollTo().performClick()
        compose.onNodeWithTag("apply_filters").performClick()
        compose.onNodeWithTag("result_count").assertTextEquals("2 項")
        visible("a"); visible("c")
        compose.onNodeWithTag("open_filters").performClick()
        compose.onNodeWithTag("filter_category_$category").performScrollTo().performClick()
        compose.onNodeWithText("取消", substring = false).performClick()
        compose.onNodeWithTag("result_count").assertTextEquals("2 項")
        compose.onNodeWithTag("toggle_selection").performClick()
        compose.onNodeWithTag("select_results").performClick()
        compose.onNodeWithText("已選 2 項").assertExists()
        compose.onNodeWithTag("complete_${ids.getValue("a")}").assertDoesNotExist()
        capture("filtered-selection")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("已選 2 項").assertExists()
        search("$prefix-a")
        compose.onNodeWithText("已選 0 項").assertExists()
        compose.onNodeWithTag("batch_trash").assertIsNotEnabled()
        navigate("COMPLETED")
        search(prefix)
        compose.onNodeWithTag("result_count").assertTextEquals("1 項")
        visible("done")
        navigate("TRASH")
        search(prefix)
        compose.onNodeWithTag("result_count").assertTextEquals("1 項")
        visible("trash")
        compose.onNodeWithTag("toggle_selection").assertIsEnabled()
    }

    @Test fun batchAddMoveAndTrashAffectOnlyTheSelectionAndCancelDoesNotWrite() {
        search("$prefix-a")
        compose.onNodeWithTag("toggle_selection").performClick()
        compose.onNodeWithTag("select_results").performClick()
        compose.onNodeWithTag("batch_tags").performClick()
        compose.onNodeWithTag("batch_tag_$tagC").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_batch").performClick()
        awaitBatchClosed()
        assertEquals(setOf(tagA, tagB, tagC), runBlocking { db.dao().taskTagIds(ids.getValue("a")).toSet() })
        assertEquals(setOf(tagA), runBlocking { db.dao().taskTagIds(ids.getValue("b")).toSet() })
        search(prefix)
        compose.onNodeWithTag("toggle_selection").performClick()
        compose.onNodeWithTag("select_results").performClick()
        compose.onNodeWithTag("batch_move").performClick()
        compose.onNodeWithTag("batch_target_$ASSIGNMENTS_ID").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_batch").assertIsNotEnabled()
        compose.onNodeWithText("取消", substring = false).performClick()
        assertEquals(category, runBlocking { db.dao().task(ids.getValue("a"))!!.categoryId })
        compose.onNodeWithTag("batch_move").performClick()
        compose.onNodeWithTag("batch_target_$ASSIGNMENTS_ID").performScrollTo().performClick()
        for ((key, status) in listOf("a" to "進行中", "b" to "完成未繳交", "c" to "未開始")) {
            compose.onNodeWithTag("batch_status_${ids.getValue(key)}").performScrollTo().performClick()
            compose.onNodeWithText(status, substring = false).performClick()
        }
        capture("assignment-batch")
        compose.onNodeWithTag("confirm_batch").performClick()
        awaitBatchClosed()
        runBlocking {
            for (key in listOf("a", "b", "c")) assertEquals(ASSIGNMENTS_ID, db.dao().task(ids.getValue(key))!!.categoryId)
            assertEquals(TaskStatus.DONE_UNSUBMITTED, db.dao().task(ids.getValue("b"))!!.status)
            assertEquals(UNCATEGORIZED_ID, db.dao().task(ids.getValue("outside"))!!.categoryId)
        }
        compose.onNodeWithTag("toggle_selection").performClick()
        compose.onNodeWithTag("select_results").performClick()
        compose.onNodeWithTag("batch_trash").performClick()
        compose.onNodeWithText("取消", substring = false).performClick()
        assertTrue(runBlocking { listOf("a", "b", "c").all { db.dao().task(ids.getValue(it))!!.deletedAt == null } })
        compose.onNodeWithTag("batch_trash").performClick()
        compose.onNodeWithTag("confirm_batch").performClick()
        awaitBatchClosed()
        compose.onNodeWithText("找不到符合的任務").assertExists()
        runBlocking {
            assertTrue(listOf("a", "b", "c").all { db.dao().task(ids.getValue(it))!!.deletedAt != null })
            assertNull(db.dao().task(ids.getValue("outside"))!!.deletedAt)
            assertNull(db.dao().task(ids.getValue("done"))!!.deletedAt)
        }
        navigate("TRASH"); search(prefix)
        compose.onNodeWithTag("result_count").assertTextEquals("4 項")
    }
}
