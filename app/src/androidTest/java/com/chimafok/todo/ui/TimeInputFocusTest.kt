package com.chimafok.todo.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.ui.theme.TodoTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class TimeInputFocusTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var result: LocalTime? = null
    private fun open(initial: LocalTime = LocalTime.of(10, 0)) {
        assertEquals("com.chimafok.todo.qa", compose.activity.packageName)
        val visible = mutableStateOf(true)
        result = null
        compose.runOnUiThread { compose.activity.setContent { TodoTheme {
            if (visible.value) TodoTimePicker(initial, { visible.value = false }, { result = it; visible.value = false })
        } } }
        compose.onNodeWithTag("time_hour_input").assertIsFocused()
    }
    private fun hour() = compose.onNodeWithTag("time_hour_input")
    private fun minute() = compose.onNodeWithTag("time_minute_input")
    private fun confirm() = compose.onNodeWithTag("confirm_time").performClick()

    @Test fun leadingZeroAdvancesAfterSecondDigitAndKeepsTypingMinutes() {
        open()
        hour().performTextInput("0"); hour().assertIsFocused()
        hour().performTextInput("9"); minute().assertIsFocused()
        minute().performTextInput("3"); minute().performTextInput("5")
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        Thread.sleep(300) // Allow the emulator renderer to present the final frame for evidence.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/todo141-focus.png").use {
                java.io.FileInputStream(it.fileDescriptor).use { input -> input.readBytes() }
            }
        confirm(); assertEquals(LocalTime.of(9, 35), result)
    }
    @Test fun midnightAndUnchangedInitialHourAdvance() {
        open(LocalTime.MIDNIGHT)
        hour().performTextInput("0"); hour().assertIsFocused()
        hour().performTextInput("0"); minute().assertIsFocused()
        confirm(); assertEquals(LocalTime.MIDNIGHT, result)
    }
    @Test fun enteringInitialTenStillAdvances() {
        open()
        hour().performTextInput("1"); hour().assertIsFocused()
        hour().performTextInput("0"); minute().assertIsFocused()
        minute().performTextInput("45"); confirm()
        assertEquals(LocalTime.of(10, 45), result)
    }
    @Test fun invalidHourAndMinuteCannotBeAcceptedAndBlankCannotSave() {
        open()
        hour().performTextInput("2"); hour().performTextInput("4")
        hour().assertTextEquals("2"); hour().assertIsFocused()
        hour().performTextInput("3"); minute().assertIsFocused()
        minute().performTextInput("6"); minute().performTextInput("0")
        minute().assertTextEquals("6")
        minute().performTextClearance(); compose.onNodeWithTag("confirm_time").assertIsNotEnabled()
        minute().performTextInput("59"); confirm(); assertEquals(LocalTime.of(23, 59), result)
    }
    @Test fun returningToHourAndDeletingDoesNotJumpBackToMinute() {
        open(LocalTime.of(12, 30))
        hour().performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.Backspace) }
        hour().assertTextEquals("1"); hour().assertIsFocused()
        hour().performTextInput("8"); minute().assertIsFocused()
        hour().performClick(); hour().assertIsFocused()
        confirm(); assertEquals(LocalTime.of(18, 30), result)
    }
    @Test fun clockAndInputShareValuesAndCancelDoesNotCommit() {
        open()
        hour().performTextInput("23"); minute().performTextInput("59")
        compose.onNodeWithTag("time_dial_mode").performClick()
        compose.onNodeWithTag("time_input_mode").performClick()
        hour().assertTextEquals("23"); minute().assertTextEquals("59")
        compose.onNodeWithTag("cancel_time").performClick()
        assertNull(result); compose.onNodeWithTag("time_picker").assertDoesNotExist()
    }
    @Test fun deadlineFormReceivesSelectedTimeAndCancelPreservesIt() {
        assertEquals("com.chimafok.todo.qa", compose.activity.packageName)
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_date").performTextInput("20990101")
        compose.onNodeWithTag("open_time").performScrollTo().performClick()
        hour().performTextInput("2"); hour().assertIsFocused()
        hour().performTextInput("3"); minute().assertIsFocused()
        minute().performTextInput("45"); confirm()
        compose.onNodeWithTag("task_time").assertTextContains("23:45")
        compose.onNodeWithTag("open_time").performSemanticsAction(SemanticsActions.OnClick) { it() }
        hour().performTextInput("05"); minute().assertIsFocused()
        compose.onNodeWithTag("cancel_time").performClick()
        compose.onNodeWithTag("task_time").assertTextContains("23:45")
    }

}
