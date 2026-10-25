package com.chimafok.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimafok.todo.MainActivity
import com.chimafok.todo.data.TaskRepository
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.ReminderRuntime
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Public screenshots use fictional records in the isolated QA app only. */
@RunWith(AndroidJUnit4::class)
class PublicScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val prefix = "public-demo-"
    private val db get() = ReminderRuntime.database
    private fun await(tag: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(600)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/todo-public-$name.png").use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
    }
    @After fun removeOnlyDemonstrationData() = runBlocking {
        db.withTransaction {
            db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id LIKE ?", arrayOf("$prefix%"))
            db.openHelper.writableDatabase.execSQL("DELETE FROM schedule_outbox WHERE taskId LIKE ?", arrayOf("$prefix%"))
        }
    }
    @Test fun fictionalTasksAndEditorRemainUsableForPublicScreenshots() {
        Assert.assertEquals("com.chimafok.todo.qa", compose.activity.packageName)
        await("add_task")
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault().id
        runBlocking {
            val tasks = TaskRepository(db)
            tasks.save(TaskDraft(prefix+"plan", "整理下週讀書計畫", "每天留一點時間給重要的事。", dueDate=today, zoneId=zone))
            tasks.save(TaskDraft(prefix+"practice", "完成 Kotlin 練習", "練習狀態管理與資料保存。", categoryId=ASSIGNMENTS_ID,
                dueDate=today, dueTime=LocalTime.of(20,0), zoneId=zone, status=TaskStatus.IN_PROGRESS))
            tasks.save(TaskDraft(prefix+"reading", "閱讀喜歡的書", "讀完一章，寫下三個重點。", dueDate=today.plusDays(1), zoneId=zone))
            tasks.save(TaskDraft(prefix+"shopping", "週末採買清單", "水果、牛奶、咖啡豆。"))
        }
        compose.runOnUiThread { ViewModelProvider(compose.activity)[TodoViewModel::class.java].reload() }
        await("task_${prefix}plan")
        compose.onNodeWithText("整理下週讀書計畫", substring=false).assertIsDisplayed()
        capture("agenda")
        compose.onNodeWithTag("add_task").performClick()
        await("task_title")
        compose.onNodeWithTag("task_title").performTextInput("準備週末的小旅行")
        compose.onNodeWithTag("task_date").performTextReplacement(today.plusDays(5).toString())
        compose.onNodeWithTag("task_time").performTextReplacement("10:00")
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
        compose.onNodeWithTag("task_title").performScrollTo()
        capture("editor")
        compose.onNodeWithText("取消", substring=false).performClick()
        await("discard_draft")
        compose.onNodeWithTag("discard_draft").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_title").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("open_menu").performClick()
        compose.onNodeWithTag("nav_AGENDA").assertIsDisplayed()
        capture("navigation")
    }
}
