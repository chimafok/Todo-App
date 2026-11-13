package com.chimafok.todo.ui

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
import com.chimafok.todo.ui.theme.TodoTheme
import com.chimafok.todo.data.TaskRepository
import com.chimafok.todo.domain.TaskDraft
import com.chimafok.todo.reminders.ReminderRuntime
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import java.util.UUID
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ActionIconsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun qaOnly() { assertEquals("com.chimafok.todo.qa", compose.activity.packageName) }
    @Test fun localizedIconsHaveLargeTargetsSelectedStateAndLongPressHelpWithoutInvokingTheAction() {
        var clicks = 0
        val label = mutableStateOf("")
        compose.runOnUiThread { compose.activity.setContent { TodoTheme {
            ActionIconButton(R.drawable.ic_filter, label.value, Modifier.testTag("action"), selected = true, badgeCount = 2, onClick = { clicks++ })
        } } }
        for (language in listOf("zh-Hant", "zh-Hans", "en")) {
            val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val translated = compose.activity.createConfigurationContext(config).getString(R.string.text_filter_s_6613a2, 2)
            compose.runOnIdle { label.value = translated }
            val action = compose.onNodeWithTag("action")
            action.assertContentDescriptionEquals(translated).assertIsSelected()
            val bounds = action.getUnclippedBoundsInRoot()
            assertTrue((bounds.right - bounds.left).value >= 48f && (bounds.bottom - bounds.top).value >= 48f)
            action.performTouchInput { longClick() }
            compose.onNodeWithText(translated).assertIsDisplayed()
            assertEquals(0, clicks)
            compose.waitUntil(5_000) { compose.onAllNodesWithText(translated).fetchSemanticsNodes().isEmpty() }
        }
        compose.onNodeWithTag("action").performClick(); assertEquals(1, clicks)
    }
    @Test fun toolbarIconsAndTextActionsEditTrashAndRestoreOnlyTheChosenTask() {
        val id = "QA150-icons-${UUID.randomUUID()}"
        val db = ReminderRuntime.database
        fun await(tag: String) { compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
        fun ready() { compose.waitUntil(20_000) { compose.onAllNodesWithTag("toggle_selection").fetchSemanticsNodes().singleOrNull()?.config?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == false } }
        fun reveal(action: String) {
            compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_$id"))
            if (compose.onAllNodesWithTag(action).fetchSemanticsNodes().isEmpty()) compose.onNodeWithTag("task_$id").performClick()
            compose.onNodeWithTag(action).performScrollTo()
        }
        await("add_task")
        try {
            runBlocking { TaskRepository(db).save(TaskDraft(id, id, dueDate = LocalDate.now())) }
            compose.runOnUiThread { ViewModelProvider(compose.activity)[TodoViewModel::class.java].reload() }
            ready(); compose.onNodeWithTag("task_search").performTextReplacement(id); ready()
            compose.onNodeWithTag("open_filters").assertContentDescriptionEquals(tr(R.string.text_filter_9bd5d8)).performClick()
            compose.onNodeWithTag("apply_filters").performClick(); ready()
            fun assertToolbarSize(tag: String, label: String) {
                val glyph = compose.onNode(hasContentDescription(label) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).getUnclippedBoundsInRoot()
                assertEquals(18f, (glyph.right - glyph.left).value, .1f)
                assertEquals(18f, (glyph.bottom - glyph.top).value, .1f)
            }
            assertToolbarSize("open_filters", tr(R.string.text_filter_9bd5d8))
            assertToolbarSize("toggle_selection", tr(R.string.text_select_1dc26d))
            val target = compose.onNodeWithTag("open_filters").getUnclippedBoundsInRoot()
            assertTrue((target.right - target.left).value >= 48f && (target.bottom - target.top).value >= 48f)
            val normalImage = compose.onNodeWithTag("toggle_selection").captureToImage()
            val normal = normalImage.toPixelMap()
            compose.onNodeWithTag("toggle_selection").performClick().assertIsSelected()
            assertToolbarSize("toggle_selection", tr(R.string.text_done_selecting_d8c47c))
            // Android's native ripple uses real render-thread time, not Compose's virtual clock.
            compose.waitUntil(5_000) {
                val pixels = compose.onNodeWithTag("toggle_selection").captureToImage().toPixelMap()
                val actual = pixels[pixels.width / 2, pixels.height / 6]
                val expected = normal[normal.width / 2, normal.height / 6]
                maxOf(kotlin.math.abs(actual.red - expected.red), kotlin.math.abs(actual.green - expected.green), kotlin.math.abs(actual.blue - expected.blue)) < .02f
            }
            compose.onNodeWithTag("select_results").assertTextContains(tr(R.string.text_select_all_results_9951b1)).performClick(); ready()
            compose.onNodeWithTag("select_$id").assertIsOn()
            compose.onNodeWithTag("toggle_selection").performClick().assertIsNotSelected()
            reveal("edit_$id"); compose.onNodeWithTag("edit_$id").assertTextContains(tr(R.string.text_edit_e0d448)).performClick(); await("task_title")
            compose.onNodeWithTag("task_title").assertTextContains(id)
            compose.onNodeWithText(tr(R.string.text_cancel_2cd0f3), substring = false).performClick(); ready()
            reveal("trash_$id"); compose.onNodeWithTag("trash_$id").assertTextContains(tr(R.string.text_delete_3c8f5b)).performClick()
            compose.onNodeWithText(tr(R.string.text_move_to_trash_f86355), substring = false).performClick()
            compose.waitUntil(15_000) { runBlocking { db.dao().task(id)?.deletedAt != null } }
            compose.onNodeWithTag("open_menu").performClick(); compose.onNodeWithTag("nav_TRASH").performClick(); ready()
            reveal("restore_$id"); compose.onNodeWithTag("restore_$id").assertTextContains(tr(R.string.text_restore_2abdcb)).performClick()
            compose.waitUntil(15_000) { runBlocking { db.dao().task(id)?.deletedAt == null } }
        } finally { runBlocking { db.history().purgeTask(id) } }
    }
    @Test fun singleReminderClockAndRemoveTextKeepTheirOriginalActions() {
        val encoded = mutableStateOf(encodeReminderInputs(listOf(ReminderInput(kind = "FIXED", date = "2030-01-01", time = "10:00"))))
        compose.runOnUiThread { compose.activity.setContent { TodoTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ReminderFields(encoded.value, { encoded.value = it }, LocalDate.of(2030,1,2), LocalTime.of(10,0), "UTC", emptyList(), true, true)
            }
        } } }
        compose.onNodeWithTag("open_reminder_time_0").performScrollTo().assertContentDescriptionEquals(tr(R.string.text_choose_time_9f8b0c)).performClick()
        compose.onNodeWithTag("time_input_mode").assertContentDescriptionEquals(tr(R.string.text_enter_time_341b45)).assertIsSelected()
        compose.onNodeWithTag("time_dial_mode").assertContentDescriptionEquals(tr(R.string.text_clock_119cdf)).performClick().assertIsSelected()
        compose.onNodeWithTag("time_input_mode").performClick(); compose.onNodeWithTag("confirm_time").performClick()
        compose.onNodeWithTag("remove_reminder_0").performScrollTo().assertTextContains(tr(R.string.text_remove_6135d4)).performClick()
        compose.runOnIdle { assertTrue(decodeReminderInputs(encoded.value).isEmpty()) }
    }
    @Test fun seriesReminderClockAndRemoveTextKeepTheirOriginalActions() {
        val encoded = mutableStateOf(encodeSeriesReminderInputs(listOf(SeriesReminderInput(kind = "OCCURRENCE_TIME", time = "10:00"))))
        compose.runOnUiThread { compose.activity.setContent { TodoTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SeriesReminderFields(encoded.value, { encoded.value = it }, LocalDate.of(2030,1,2), LocalTime.of(10,0), "UTC", true)
            }
        } } }
        compose.onNodeWithTag("open_series_reminder_time_0").performScrollTo().assertContentDescriptionEquals(tr(R.string.text_choose_time_9f8b0c)).performClick()
        compose.onNodeWithTag("confirm_time").performClick()
        compose.onNodeWithTag("remove_series_reminder_0").performScrollTo().assertTextContains(tr(R.string.text_remove_6135d4)).performClick()
        compose.runOnIdle { assertTrue(decodeSeriesReminderInputs(encoded.value).isEmpty()) }
    }
}
