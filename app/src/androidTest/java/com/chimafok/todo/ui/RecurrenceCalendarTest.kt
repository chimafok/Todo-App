package com.chimafok.todo.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.R
import com.chimafok.todo.domain.occurrenceId
import com.chimafok.todo.i18n.tr
import com.chimafok.todo.reminders.ReminderRuntime
import com.chimafok.todo.ui.theme.TodoTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecurrenceCalendarTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val reference = LocalDate.of(2030, 11, 13)
    private val input = mutableStateOf("")
    private val owned = mutableListOf<String>()
    private fun fixture(value: String, enabled: Boolean = true) {
        assertEquals("com.chimafok.todo.qa", compose.activity.packageName)
        input.value = value
        compose.runOnUiThread { compose.activity.setContent { TodoTheme { Column {
            RecurrenceFields("WEEKLY", "1", emptyList(), "DATE", input.value, "", false, enabled,
                {}, {}, {}, {}, { input.value = it }, {}, endReference = reference)
        } } } }
    }
    private fun calendar() = compose.onNode(hasContentDescription(compose.activity.getString(R.string.choose_date)) and
        hasAnyAncestor(hasTestTag("repeat_end_date")))
    private fun headline(date: String) = compose.onNode(hasText(date) and hasAnyAncestor(hasTestTag("date_picker")))
    @Test fun compactYearlessDateUsesItsSavedReferenceAndConfirmSelectsCalendarDay() {
        fixture("1221")
        calendar().performClick(); headline("2030-12-21").assertExists()
        compose.onNode((hasText("22", substring = true) or hasContentDescription("22", substring = true)) and hasAnyAncestor(hasTestTag("date_picker"))).performClick()
        compose.onNodeWithTag("confirm_date").performClick()
        compose.onNodeWithTag("repeat_end_date").assertTextContains("2030-12-22")
        assertEquals("2030-12-22", input.value)
        calendar().performClick(); headline("2030-12-22").assertExists()
        compose.onNodeWithTag("cancel_date").performClick()
        assertEquals("2030-12-22", input.value)
    }
    @Test fun typedFullAndShortYearsAreRetainedWhenCalendarIsCancelled() {
        fixture("301221")
        calendar().performClick(); headline("2030-12-21").assertExists()
        compose.onNodeWithTag("cancel_date").performClick(); assertEquals("301221", input.value)
        compose.onNodeWithTag("repeat_end_date").performTextReplacement("20310115")
        calendar().performClick(); headline("2031-01-15").assertExists()
        compose.onNodeWithTag("cancel_date").performClick(); assertEquals("20310115", input.value)
    }
    @Test fun emptyAndInvalidTextOpenAtReferenceWithoutChangingInputOnCancel() {
        fixture("")
        calendar().performClick(); headline(reference.toString()).assertExists()
        compose.onNodeWithTag("cancel_date").performClick(); assertEquals("", input.value)
        compose.onNodeWithTag("repeat_end_date").performTextReplacement("20301340")
        calendar().performClick(); headline(reference.toString()).assertExists()
        compose.onNodeWithTag("cancel_date").performClick(); assertEquals("20301340", input.value)
    }
    @Test fun disabledRecurrenceDisablesTheCalendar() {
        fixture("2030-12-21", false)
        calendar().assertIsNotEnabled()
        compose.onNodeWithTag("date_picker").assertDoesNotExist()
    }
    @Test fun calendarDatePersistsWhenCreatingAndEditingThisAndFutureOccurrences() {
        assertEquals("com.chimafok.todo.qa", compose.activity.packageName)
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty() }
        val title = "QA150-calendar-${UUID.randomUUID().toString().take(8)}"
        val first = LocalDate.now().plusDays(1); val end = first.plusDays(20)
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput(title)
        compose.onNodeWithTag("task_date").performTextInput(first.toString())
        compose.onNodeWithTag("repeat_WEEKLY").performScrollTo().performClick()
        compose.onNodeWithTag("repeat_end_DATE").performScrollTo().performClick()
        compose.onNodeWithTag("repeat_end_date").performScrollTo().performTextInput(end.toString())
        calendar().performClick(); headline(end.toString()).assertExists()
        compose.onNodeWithTag("confirm_date").performClick()
        compose.onNodeWithTag("save_task").performClick()
        val db = ReminderRuntime.database
        compose.waitUntil(15_000) { runBlocking { db.dao().allSeries().any { it.title == title } } }
        val series = runBlocking { db.dao().allSeries().single { it.title == title } }; owned.add(series.id)
        assertEquals(end, runBlocking { db.dao().latestRule(series.id)!!.endDate })
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_title").fetchSemanticsNodes().isEmpty() }
        val taskId = occurrenceId(series.id, 1)
        val task = "task_$taskId"
        // Closing the editor and committing the database do not mean that the
        // paged query has presented its new rows yet, especially on older devices.
        compose.waitUntil(15_000) {
            androidx.lifecycle.ViewModelProvider(compose.activity)[TodoViewModel::class.java].state.value.tasks.any { it.id == taskId } &&
                compose.onAllNodesWithTag("toggle_selection").fetchSemanticsNodes().singleOrNull()?.config?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == false
        }
        compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag(task))
        compose.onNodeWithTag(task).performClick()
        compose.onNode(hasActionLabel(tr(R.string.text_edit_e0d448)) and hasAnyAncestor(hasTestTag(task))).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("repeat_scope_future").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("repeat_scope_future").performScrollTo().performClick()
        val extended = end.plusDays(7)
        compose.onNodeWithTag("repeat_end_date").performScrollTo().performTextReplacement(extended.toString().replace("-", ""))
        compose.onNodeWithTag("repeat_end_date").assertTextContains(extended.toString().replace("-", ""))
        calendar().performClick()
        headline(extended.toString()).assertExists(compose.onNodeWithTag("date_picker").printToString())
        compose.onNodeWithTag("confirm_date").performClick()
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("confirm_series_edit").performClick()
        compose.waitUntil(15_000) { runBlocking { db.dao().latestRule(series.id)!!.endDate == extended } }
    }
    @After fun cleanupOwnedFixture() {
        if (owned.isEmpty()) return
        val db = ReminderRuntime.database
        runBlocking { db.withTransaction { owned.forEach { id ->
            db.openHelper.writableDatabase.execSQL("UPDATE recurring_series SET lifecycle = 'STOPPED' WHERE id = ?", arrayOf(id))
            db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id IN (SELECT taskId FROM occurrences WHERE seriesId = ?)", arrayOf(id))
            db.openHelper.writableDatabase.execSQL("DELETE FROM recurring_series WHERE id = ?", arrayOf(id))
        } } }
    }
}
