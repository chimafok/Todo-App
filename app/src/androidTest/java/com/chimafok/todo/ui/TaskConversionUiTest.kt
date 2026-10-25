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
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TaskConversionUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val owned = mutableListOf<String>()
    private fun <T> data(block: suspend (TodoDatabase) -> T): T = runBlocking {
        val db = TodoDatabase.create(compose.activity)
        try { block(db) } finally { db.close() }
    }
    private fun ready() { compose.waitUntil(20_000) { compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty() } }
    private fun closed() { compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_title").fetchSemanticsNodes().isEmpty() } }
    private fun reloadFixture() {
        lateinit var model: TodoViewModel
        compose.runOnIdle { model = androidx.lifecycle.ViewModelProvider(compose.activity)[TodoViewModel::class.java]; model.reload() }
        compose.waitUntil(10_000) { !model.state.value.loading }
    }
    private fun seed(date: LocalDate? = LocalDate.now().plusDays(2), assignment: Boolean = false): TaskEntity {
        val id = UUID.randomUUID().toString().also(owned::add)
        val task = data { db ->
            TaskRepository(db).save(TaskDraft(id, "轉換測試-${id.take(8)}", "保留已有備註",
                if (assignment) ASSIGNMENTS_ID else UNCATEGORIZED_ID, date, LocalTime.of(20, 0).takeIf { date != null },
                "Asia/Hong_Kong".takeIf { date != null }, if (assignment) TaskStatus.IN_PROGRESS else TaskStatus.OPEN))
            db.dao().task(id)!!
        }
        reloadFixture(); return task
    }
    private fun edit(id: String) {
        compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_$id"))
        compose.onNodeWithTag("task_$id").performClick()
        compose.onNode(hasText("編輯") and hasAnyAncestor(hasTestTag("task_$id"))).performClick()
    }
    private fun hideKeyboard() { androidx.test.espresso.Espresso.closeSoftKeyboard(); compose.waitForIdle() }
    private fun capture(label: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/todo-042-$label.png").use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }
    @After fun cleanupOnlyOwnedRows() { data { db -> db.withTransaction {
        owned.forEach { id ->
            db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id IN (SELECT taskId FROM occurrences WHERE seriesId = ?)", arrayOf(id))
            db.openHelper.writableDatabase.execSQL("DELETE FROM recurring_series WHERE id = ?", arrayOf(id))
            db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(id))
        }
    } } }

    @Test fun previewCancelRotationAndConfirmationKeepTheOriginalTaskAndCapturedZone() {
        ready(); val original = seed(); edit(original.id)
        compose.onNodeWithTag("repeat_WEEKLY").performScrollTo().performClick(); hideKeyboard()
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("convert_series_preview").assertExists()
        compose.onNodeWithText("截止時區：Asia/Hong_Kong").assertExists()
        capture("conversion-preview")
        compose.onNodeWithTag("cancel_convert_series").performClick()
        assertNull(data { it.dao().occurrence(original.id) })
        assertEquals(original, data { it.dao().task(original.id) })
        compose.onNodeWithTag("save_task").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("convert_series_preview").assertExists()
        compose.onNodeWithTag("confirm_convert_series").performClick(); closed()
        compose.waitUntil(10_000) { data { it.dao().occurrence(original.id) != null } }
        val saved = data { it.dao().task(original.id)!! }
        assertEquals(original.createdAt, saved.createdAt); assertEquals(original.status, saved.status)
        assertEquals(original.zoneId, saved.zoneId); assertEquals(original.dueInstant(), saved.dueInstant())
        assertEquals(1, data { it.dao().seriesOccurrences(original.id).size })
        compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_${original.id}"))
        compose.onNodeWithTag("complete_${original.id}").performClick()
        compose.waitUntil(10_000) { data { it.dao().task(original.id)!!.status == TaskStatus.COMPLETED } }
        compose.onNodeWithTag("open_menu").performClick(); compose.onNodeWithTag("nav_COMPLETED").performClick()
        compose.onNodeWithTag("task_${original.id}").assertDoesNotExist()
        compose.onNodeWithText("重複紀錄").performClick()
        compose.onNodeWithTag("recurring_list").performScrollToNode(hasTestTag("recurring_series_${original.id}"))
        compose.onNodeWithTag("recurring_series_${original.id}").performClick()
        compose.onNodeWithTag("recurring_list").performScrollToNode(hasTestTag("task_${original.id}"))
        compose.onNodeWithTag("task_${original.id}").assertExists()
        capture("converted-history")
    }

    @Test fun aNewerOrdinaryEditBlocksConversionAndKeepsTheForm() {
        ready(); val original = seed(); edit(original.id)
        compose.onNodeWithTag("repeat_DAILY").performScrollTo().performClick(); hideKeyboard()
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("convert_series_preview").assertExists()
        data { db -> TaskRepository(db).save(TaskDraft(original.id, original.title, "另一處最新內容", original.categoryId,
            original.dueDate, original.dueTime, original.zoneId, original.status, original.revision)) }
        compose.onNodeWithTag("confirm_convert_series").performClick()
        compose.onNodeWithTag("editor_error").performScrollTo().assertTextContains("任務已更新", substring = true)
        compose.onNodeWithTag("task_title").assertTextContains(original.title)
        assertEquals("另一處最新內容", data { it.dao().task(original.id)!!.notes })
        assertNull(data { it.dao().series(original.id) })
    }

    @Test fun anUndatedAssignmentDefaultsStartToTodayAndRetainsItsProgress() {
        ready(); val original = seed(date = null, assignment = true); edit(original.id)
        compose.onNodeWithTag("repeat_DAILY").performScrollTo().performClick(); hideKeyboard()
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("convert_series_preview").assertExists()
        assertNull(data { it.dao().occurrence(original.id) })
        assertEquals(original, data { it.dao().task(original.id) })
        val start = LocalDate.now()
        compose.onNodeWithTag("confirm_convert_series").performClick(); closed()
        compose.waitUntil(10_000) { data { it.dao().occurrence(original.id) != null } }
        val saved = data { it.dao().task(original.id)!! }
        assertEquals(TaskStatus.IN_PROGRESS, saved.status); assertEquals(start, saved.dueDate)
        assertEquals(original.createdAt, saved.createdAt); assertNull(saved.dueTime)
        assertEquals(ZoneId.systemDefault().id, saved.zoneId)
    }

    @Test fun pastWarningAndConversionPreviewCanBothBeCancelledWithoutWrites() {
        ready(); val original = seed(date = LocalDate.now().minusDays(1)); edit(original.id)
        compose.onNodeWithTag("repeat_DAILY").performScrollTo().performClick(); hideKeyboard()
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("past_deadline_warning").assertExists()
        compose.onNodeWithTag("cancel_past_deadline").performClick()
        assertEquals(original, data { it.dao().task(original.id) })
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("confirm_past_deadline").performClick()
        compose.onNodeWithTag("convert_series_preview").assertExists()
        compose.onNodeWithTag("cancel_convert_series").performClick()
        assertNull(data { it.dao().series(original.id) })
        assertEquals(original, data { it.dao().task(original.id) })
        compose.onNodeWithTag("repeat_NONE").performScrollTo().performClick()
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("confirm_past_deadline").performClick(); closed()
        assertNull(data { it.dao().occurrence(original.id) })
    }
}
