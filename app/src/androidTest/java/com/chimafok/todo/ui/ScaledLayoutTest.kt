package com.chimafok.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.ReminderRuntime
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import java.util.UUID
import java.io.FileInputStream
import androidx.test.platform.app.InstrumentationRegistry

@RunWith(AndroidJUnit4::class)
class ScaledLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val title = "大字操作-${UUID.randomUUID()}"
    private var originalScale = "1.0"
    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).use { input -> String(input.readBytes()) }.trim()
    }
    @Before fun scale() {
        originalScale = shell("settings get system font_scale").takeUnless { it == "null" }.orEmpty().ifBlank { "1.0" }
        shell("settings put system font_scale 2.0")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.activity.resources.configuration.fontScale >= 1.99f }
    }
    @After fun cleanup() {
        try { ReminderRuntime.database.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE title=?", arrayOf(title)) }
        finally { shell("settings put system font_scale $originalScale") }
    }
    @Test fun doubleFontEditorSaveAndDrawerRemainReachable() {
        // The harness also changes the actual system font scale and viewport before this test.
        Assert.assertTrue(compose.activity.resources.configuration.fontScale >= 1.99f)
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput(title)
        compose.onNodeWithTag("task_notes").performScrollTo().performTextInput("長備註：資料保留、提醒與跨時區截止時間。".repeat(30))
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("save_task").assertIsDisplayed().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("task_title").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("open_menu").performClick()
        compose.onNodeWithTag("nav_SETTINGS").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("page_title").assertTextEquals("設定")
        shell("screencap -p /data/local/tmp/todo-200-font.png")
    }
}
