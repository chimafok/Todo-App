package com.chimafok.todo.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimafok.todo.MainActivity
import com.chimafok.todo.ui.theme.TodoTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class TimeInputLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var result: LocalTime? = null
    private var expectedScale = 1f
    private var originalScale = "1.0"
    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
        java.io.FileInputStream(it.fileDescriptor).use { input -> String(input.readBytes()) }.trim()
    }
    @Before fun rememberFontScale() {
        assertEquals("com.chimafok.todo.qa", compose.activity.packageName)
        originalScale = shell("settings get system font_scale").takeUnless { it == "null" }.orEmpty().ifBlank { "1.0" }
    }
    @After fun restoreFontScale() { shell("settings put system font_scale $originalScale") }
    private fun open(initial: LocalTime = LocalTime.of(10, 0), scale: Float = 1f) {
        assertEquals("com.chimafok.todo.qa", compose.activity.packageName)
        expectedScale = scale
        // Dialogs have their own Android owner; change the owned QA device setting rather
        // than an outer LocalDensity that the dialog may replace.
        shell("settings put system font_scale $scale")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.fontScale - scale) < 0.01f }
        val visible = mutableStateOf(true)
        compose.runOnUiThread { compose.activity.setContent { TodoTheme {
            if (visible.value) TodoTimePicker(initial, { visible.value = false }, { result = it; visible.value = false })
        } } }
        compose.onNodeWithTag("time_hour_input").assertIsFocused()
    }
    private fun field(tag: String) = compose.onNodeWithTag(tag)
    private fun centeredAndUnclipped(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        field(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.last()
        assertEquals("Dialog uses the requested system font scale", expectedScale, layout.layoutInput.density.fontScale, 0.01f)
        assertEquals(2, layout.layoutInput.text.length)
        assertFalse("No horizontal overflow for $tag", layout.didOverflowWidth)
        assertFalse("No vertical overflow for $tag", layout.didOverflowHeight)
        assertTrue(layout.getLineLeft(0) >= 0)
        assertTrue(layout.getLineRight(0) <= layout.size.width)
        assertEquals("Centered text for $tag", layout.size.width / 2f,
            (layout.getLineLeft(0) + layout.getLineRight(0)) / 2f, 1.5f)
        val bounds = field(tag).getUnclippedBoundsInRoot()
        assertTrue("Text fits field height", layout.size.height / compose.activity.resources.displayMetrics.density <= (bounds.bottom - bounds.top).value)
    }
    private fun capture(label: String) {
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle(); Thread.sleep(300)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /data/local/tmp/todo141dev02-$label.png"
        ).use { java.io.FileInputStream(it.fileDescriptor).use { input -> input.readBytes() } }
    }
    @Test fun untouchedDefaultRemainsTenAndCanConfirm() {
        open()
        field("time_hour_input").assertTextEquals("10")
        field("time_minute_input").assertTextEquals("00")
        centeredAndUnclipped("time_hour_input"); centeredAndUnclipped("time_minute_input")
        capture("default")
        compose.onNodeWithTag("confirm_time").performClick()
        assertEquals(LocalTime.of(10, 0), result)
    }
    @Test fun existingTimeAndSelectionRemainCentered() {
        open(LocalTime.of(15, 50))
        field("time_hour_input").assertTextEquals("15"); field("time_minute_input").assertTextEquals("50")
        field("time_minute_input").performClick()
        field("time_minute_input").performTextInputSelection(TextRange(0, 2))
        centeredAndUnclipped("time_hour_input"); centeredAndUnclipped("time_minute_input")
        capture("selection")
    }
    @Test fun enlargedFontAndEndCursorKeepLeadingZeroVisible() {
        open(LocalTime.of(4, 44), 1.4f)
        field("time_minute_input").performClick()
        field("time_minute_input").performTextInputSelection(TextRange(2))
        centeredAndUnclipped("time_hour_input"); centeredAndUnclipped("time_minute_input")
        field("time_hour_input").assertTextEquals("04")
        capture("cursor")
    }
    @Test fun doubleFontStillFitsBothNumericFields() {
        open(LocalTime.of(23, 59), 2f)
        centeredAndUnclipped("time_hour_input"); centeredAndUnclipped("time_minute_input")
        capture("double-font")
    }
}
