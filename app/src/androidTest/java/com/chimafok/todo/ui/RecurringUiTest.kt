package com.chimafok.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
class RecurringUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val titles = mutableListOf<String>()
    private fun name(label: String) = "$label-${UUID.randomUUID().toString().take(8)}".also(titles::add)
    private fun ready() { compose.waitUntil(20_000) { compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty() } }
    private fun navigate(destination: String) {
        compose.onNodeWithTag("open_menu").performClick(); compose.onNodeWithTag("nav_$destination").performClick()
    }
    private fun closed() { compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_title").fetchSemanticsNodes().isEmpty() } }
    private fun capture(label: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/todo-040-$label.png").use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }
    @After fun cleanupOnlyOwnedSeries() {
        val db = TodoDatabase.create(compose.activity)
        try { runBlocking {
            db.dao().allSeries().filter { it.title in titles }.forEach { series ->
                db.openHelper.writableDatabase.execSQL("UPDATE recurring_series SET lifecycle = 'STOPPED' WHERE id = ?", arrayOf(series.id))
                db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id IN (SELECT taskId FROM occurrences WHERE seriesId = ?)", arrayOf(series.id))
                db.openHelper.writableDatabase.execSQL("DELETE FROM recurring_series WHERE id = ?", arrayOf(series.id))
            }
        } } finally { db.close() }
    }

    @Test fun createAdvanceAndStopKeepHistoryOutOfCompletedMainPage() {
        ready()
        val title = name("每日閱讀")
        val date = LocalDate.now().plusDays(1)
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput(title)
        compose.onNodeWithTag("task_date").performTextInput(date.toString())
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithTag("repeat_DAILY").performScrollTo().performClick()
        compose.onNodeWithTag("repeat_end_COUNT").performScrollTo().performClick()
        compose.onNodeWithTag("repeat_count").performScrollTo().performTextReplacement("3")
        compose.onNodeWithTag("repeat_count").assertTextContains("3")
        capture("repeat-form")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("repeat_DAILY").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("save_task").performClick(); closed()
        val db = TodoDatabase.create(compose.activity)
        try {
            val series = runBlocking { db.dao().allSeries().single { it.title == title } }
            assertEquals(3, runBlocking { db.dao().latestRule(series.id)!!.maxCount })
            val first = occurrenceId(series.id, 1)
            compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_$first"))
            compose.onNodeWithTag("complete_$first").performClick()
            compose.waitUntil(10_000) { runBlocking { db.dao().seriesOccurrences(series.id).size == 2 } }
            val second = occurrenceId(series.id, 2)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_$first").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_$second"))
            navigate("COMPLETED")
            compose.onNodeWithText(title).assertDoesNotExist()
            compose.onNodeWithText("重複紀錄").performClick()
            compose.onNodeWithTag("recurring_series_${series.id}").performClick()
            compose.onNodeWithTag("recurring_list").performScrollToNode(hasTestTag("task_$first"))
            compose.onNodeWithTag("task_$first").assertExists()
            capture("history")
            compose.onNodeWithTag("back_to_series").performClick()
            compose.onNodeWithTag("stop_series_${series.id}").performClick()
            compose.onNodeWithText("取消", substring = false).performClick()
            assertEquals(SeriesLifecycle.ACTIVE, runBlocking { db.dao().series(series.id)!!.lifecycle })
            compose.onNodeWithTag("stop_series_${series.id}").performClick()
            compose.onNodeWithTag("confirm_series_action").performClick()
            compose.waitUntil(10_000) { runBlocking { db.dao().series(series.id)!!.lifecycle == SeriesLifecycle.STOPPED } }
            navigate("AGENDA")
            compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_$second"))
            compose.onNodeWithTag("complete_$second").performClick()
            compose.waitUntil(10_000) { runBlocking { db.dao().task(second)!!.status == TaskStatus.COMPLETED } }
            assertEquals(2, runBlocking { db.dao().seriesOccurrences(series.id).size })
        } finally { db.close() }
    }

    @Test fun assignmentResetsAndWholeSeriesRestoreRequiresConfirmation() {
        ready()
        val title = name("每月作業")
        val date = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput(title)
        compose.onNodeWithTag("task_date").performTextInput(date.toString())
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("分類：未分類").performScrollTo().performClick()
        compose.onNodeWithText("作業", substring = false).performClick()
        compose.onNodeWithTag("repeat_MONTHLY").performScrollTo().performClick()
        compose.onNodeWithTag("repeat_end_COUNT").performScrollTo().performClick()
        compose.onNodeWithTag("repeat_count").performScrollTo().performTextReplacement("2")
        compose.onNodeWithTag("repeat_count").assertTextContains("2")
        compose.onNodeWithTag("save_task").performClick(); closed()
        val db = TodoDatabase.create(compose.activity)
        try {
            val series = runBlocking { db.dao().allSeries().single { it.title == title } }
            assertEquals(2, runBlocking { db.dao().latestRule(series.id)!!.maxCount })
            val first = occurrenceId(series.id, 1)
            compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_$first"))
            for (status in listOf(TaskStatus.IN_PROGRESS, TaskStatus.DONE_UNSUBMITTED)) {
                compose.onNodeWithTag("status_$first").performClick()
                compose.onNodeWithTag("choose_status_${status.name}").performClick()
                compose.waitUntil(10_000) { runBlocking { db.dao().task(first)!!.status == status } }
                assertEquals(1, runBlocking { db.dao().seriesOccurrences(series.id).size })
            }
            compose.onNodeWithTag("status_$first").performClick()
            compose.onNodeWithTag("choose_status_COMPLETED").performClick()
            compose.waitUntil(10_000) { runBlocking { db.dao().seriesOccurrences(series.id).size == 2 } }
            val second = occurrenceId(series.id, 2)
            assertEquals(date.plusMonths(1), runBlocking { db.dao().task(second)!!.dueDate })
            assertEquals(TaskStatus.NOT_STARTED, runBlocking { db.dao().task(second)!!.status })
            navigate("COMPLETED"); compose.onNodeWithText("重複紀錄").performClick()
            compose.onNodeWithTag("delete_series_${series.id}").performClick()
            compose.onNodeWithTag("include_series_history").performClick()
            compose.onNodeWithTag("confirm_series_action").performClick()
            compose.waitUntil(10_000) { runBlocking { db.dao().series(series.id)!!.lifecycle == SeriesLifecycle.TRASHED } }
            navigate("TRASH")
            compose.onNodeWithTag("task_search").performTextReplacement(title)
            compose.onNodeWithTag("task_search").performImeAction()
            compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_$second"))
            compose.onNodeWithTag("task_$second").performClick()
            compose.onNodeWithText("復原", substring = false).performClick()
            compose.onNodeWithTag("restore_series_confirm").assertExists()
            compose.onNodeWithText("取消", substring = false).performClick()
            assertNotNull(runBlocking { db.dao().task(second)!!.deletedAt })
            compose.onNodeWithText("復原", substring = false).performClick()
            compose.onNodeWithTag("restore_series_confirm").performClick()
            compose.waitUntil(10_000) { runBlocking { db.dao().series(series.id)!!.deletedAt == null } }
            assertNull(runBlocking { db.dao().task(first)!!.deletedAt })
            assertNull(runBlocking { db.dao().task(second)!!.deletedAt })
            assertEquals(TaskStatus.COMPLETED, runBlocking { db.dao().task(first)!!.status })
            assertEquals(TaskStatus.NOT_STARTED, runBlocking { db.dao().task(second)!!.status })
        } finally { db.close() }
    }
}
