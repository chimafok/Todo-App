package com.chimafok.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.room.withTransaction
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
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SeriesEditUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val owned = mutableListOf<String>()
    private val ownedCategories = mutableListOf<String>()
    private fun <T> data(block: suspend (TodoDatabase) -> T): T = runBlocking {
        val db = TodoDatabase.create(compose.activity)
        try { block(db) } finally { db.close() }
    }
    private fun ready() { compose.waitUntil(20_000) { compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty() } }
    private fun closed() { compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_title").fetchSemanticsNodes().isEmpty() } }
    private fun navigate(destination: String) {
        compose.onNodeWithTag("open_menu").performClick(); compose.onNodeWithTag("nav_$destination").performClick()
    }
    private fun seed(past: Boolean = false): String {
        val id = UUID.randomUUID().toString().also(owned::add)
        data { db ->
            val start = LocalDate.now().plusDays(if (past) -1 else 1)
            val rule = RecurrenceRule(start, RecurrenceFrequency.DAILY, java.time.ZoneId.systemDefault().id, maxCount = 6)
            val tasks = TaskRepository(db)
            tasks.save(TaskDraft(id, "系列編輯-${id.take(8)}", dueDate = start, zoneId = rule.zoneId, recurrence = rule))
            if (!past) for (sequence in 1..2) {
                val task = db.dao().task(occurrenceId(id, sequence))!!
                tasks.trash(task.id, task.revision); tasks.restore(task.id)
            }
        }
        reloadFixture()
        return id
    }
    private fun reloadFixture() {
        // The fixture uses its own Room instance; explicitly load it before UI actions.
        lateinit var model: TodoViewModel
        compose.runOnIdle {
            model = androidx.lifecycle.ViewModelProvider(compose.activity)[TodoViewModel::class.java]
            model.reload()
        }
        compose.waitUntil(10_000) { !model.state.value.loading }
    }
    private fun edit(id: String, list: String = "task_list") {
        compose.onNodeWithTag(list).performScrollToNode(hasTestTag("task_$id"))
        val button = hasText("編輯") and hasAnyAncestor(hasTestTag("task_$id"))
        if (compose.onAllNodes(button).fetchSemanticsNodes().isEmpty()) compose.onNodeWithTag("task_$id").performClick()
        compose.onNode(button).performClick()
    }
    private fun history(series: String) {
        navigate("COMPLETED"); compose.onNodeWithText("重複紀錄").performClick()
        compose.onNodeWithTag("recurring_list").performScrollToNode(hasTestTag("recurring_series_$series"))
        compose.onNodeWithTag("recurring_series_$series").performClick()
    }
    private fun hideKeyboard() { androidx.test.espresso.Espresso.closeSoftKeyboard(); compose.waitForIdle() }
    private fun capture(label: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/todo-041-$label.png").use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }
    @After fun cleanupOnlyOwnedRows() { data { db -> db.withTransaction {
        owned.forEach { id ->
            db.openHelper.writableDatabase.execSQL("UPDATE recurring_series SET lifecycle = 'STOPPED' WHERE id = ?", arrayOf(id))
            db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id IN (SELECT taskId FROM occurrences WHERE seriesId = ?)", arrayOf(id))
            db.openHelper.writableDatabase.execSQL("DELETE FROM recurring_series WHERE id = ?", arrayOf(id))
        }
        ownedCategories.forEach { id -> db.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE id = ?", arrayOf(id)) }
    } } }

    @Test fun previewCancelRotationShortenAndExtendPreserveFutureIds() {
        ready(); val series = seed()
        val first = occurrenceId(series, 1); val second = occurrenceId(series, 2)
        val originalIds = data { it.dao().seriesOccurrences(series).map { ref -> ref.taskId }.toSet() }
        edit(first)
        compose.onNodeWithTag("repeat_scope_future").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("repeat_WEEKLY").performScrollTo().performClick()
        compose.onNodeWithTag("repeat_count").performScrollTo().performTextReplacement("1"); hideKeyboard()
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("series_edit_preview").assertExists()
        compose.onNodeWithText("取消 2 項", substring = true).assertExists()
        capture("edit-preview")
        compose.onNodeWithTag("cancel_series_edit").performClick()
        assertEquals(1, data { it.dao().allRules().count { rule -> rule.seriesId == series } })
        assertFalse(data { it.dao().seriesOccurrences(series).any { ref -> ref.excludedByRule } })
        compose.onNodeWithTag("save_task").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("series_edit_preview").assertExists()
        compose.onNodeWithTag("confirm_series_edit").performClick(); closed()
        compose.waitUntil(10_000) { data { it.dao().seriesOccurrences(series).count { ref -> ref.excludedByRule } == 2 } }
        compose.onNodeWithTag("task_$second").assertDoesNotExist()
        history(series)
        compose.onNodeWithTag("recurring_list").performScrollToNode(hasTestTag("task_$second"))
        compose.onNodeWithTag("occurrence_caption_$second").assertTextContains("規則取消", substring = true)
        compose.onNodeWithTag("complete_$second").assertIsNotEnabled()
        capture("cancelled-history")
        navigate("AGENDA"); edit(first)
        compose.onNodeWithTag("repeat_count").performScrollTo().performTextReplacement("4"); hideKeyboard()
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithText("重新啟用 2 項", substring = true).assertExists()
        compose.onNodeWithTag("confirm_series_edit").performClick(); closed()
        compose.waitUntil(10_000) { data { it.dao().seriesOccurrences(series).none { ref -> ref.excludedByRule } } }
        assertEquals(originalIds, data { it.dao().seriesOccurrences(series).map { ref -> ref.taskId }.toSet() })
        val start = data { it.dao().task(first)!!.dueDate!! }
        assertEquals(start.plusWeeks(1), data { it.dao().task(second)!!.dueDate })
        assertEquals(3, data { it.dao().series(series)!!.cursor })
    }

    @Test fun singleScopeAndHistoricalEditNeverChangeTheFutureTemplate() {
        ready(); val series = seed(); val first = occurrenceId(series, 1)
        val before = data { it.dao().allTasks().filter { task -> task.id != first } }
        edit(first)
        compose.onNodeWithTag("repeat_scope_single").performScrollTo().performClick()
        compose.onNodeWithTag("task_notes").performScrollTo().performTextReplacement("只改本次")
        compose.onNodeWithTag("save_task").performClick(); closed()
        assertEquals(before, data { it.dao().allTasks().filter { task -> task.id != first } })
        assertEquals("", data { it.dao().series(series)!!.notes })
        val oldSeries = seed(past = true); val oldId = occurrenceId(oldSeries, 1)
        history(oldSeries); edit(oldId, "recurring_list")
        compose.onNodeWithTag("repeat_scope_future").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("repeat_scope_single").assertIsSelected()
        compose.onNodeWithTag("task_notes").performScrollTo().performTextReplacement("歷史備註")
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("confirm_past_deadline").performClick(); closed()
        assertEquals("歷史備註", data { it.dao().task(oldId)!!.notes })
        assertEquals("", data { it.dao().series(oldSeries)!!.notes })
        assertEquals(1, data { it.dao().allRules().count { rule -> rule.seriesId == oldSeries } })
    }

    @Test fun movingCancelledRowsIntoAssignmentsNeverOffersCompletion() {
        ready(); val series = seed(); val first = occurrenceId(series, 1)
        val category = UUID.randomUUID().toString().also(ownedCategories::add)
        data { db ->
            OrganizationRepository(db).saveCategory(category, "取消搬移-${category.take(8)}", null)
            val revisions = db.dao().seriesOccurrences(series).associate { it.taskId to db.dao().task(it.taskId)!!.revision }
            TaskRepository(db).moveBatch(revisions, category, emptyMap())
        }
        reloadFixture(); edit(first)
        compose.onNodeWithTag("repeat_count").performScrollTo().performTextReplacement("1"); hideKeyboard()
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("confirm_series_edit").performClick(); closed()
        navigate("ORGANIZE")
        compose.onNodeWithTag("org_menu_$category").performScrollTo().performClick()
        compose.onNodeWithText("刪除", substring = false).performClick()
        compose.onNodeWithTag("move_target_$ASSIGNMENTS_ID").performScrollTo().performClick()
        compose.onNodeWithTag("org_assignment_$first").performScrollTo().performClick()
        compose.onNodeWithText("進行中", substring = false).performClick()
        for (sequence in 2..3) {
            val id = occurrenceId(series, sequence)
            compose.onNodeWithTag("org_assignment_$id").performScrollTo().performClick()
            compose.onNodeWithText("完成", substring = false).assertDoesNotExist()
            compose.onNode(hasText("未開始") and hasAnyAncestor(isPopup())).performClick()
        }
        compose.onNodeWithTag("org_delete_confirm").performClick()
        compose.waitUntil(10_000) { data { it.dao().category(category) == null } }
        assertEquals(TaskStatus.IN_PROGRESS, data { it.dao().task(first)!!.status })
        for (sequence in 2..3) {
            val id = occurrenceId(series, sequence)
            assertEquals(TaskStatus.NOT_STARTED, data { it.dao().task(id)!!.status })
            assertTrue(data { it.dao().occurrence(id)!!.excludedByRule })
        }
    }

    @Test fun aNewerSingleOccurrenceEditBlocksTheWholeSeriesCommit() {
        ready(); val series = seed(); val first = occurrenceId(series, 1); val second = occurrenceId(series, 2)
        val original = data { it.dao().task(first)!! }
        edit(first)
        compose.onNodeWithTag("repeat_WEEKLY").performScrollTo().performClick()
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("series_edit_preview").assertExists()
        data { db -> val task = db.dao().task(second)!!
            TaskRepository(db).save(TaskDraft(task.id, task.title, "另一處剛修改", task.categoryId, task.dueDate, task.dueTime,
                task.zoneId, task.status, task.revision))
        }
        compose.onNodeWithTag("confirm_series_edit").performClick()
        compose.onNodeWithTag("editor_error").performScrollTo().assertTextContains("系列內的紀錄已更新", substring = true)
        assertEquals(original, data { it.dao().task(first) })
        assertEquals(1, data { it.dao().allRules().count { rule -> rule.seriesId == series } })
        assertEquals("另一處剛修改", data { it.dao().task(second)!!.notes })
    }
}
