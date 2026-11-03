package com.chimafok.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.R
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import com.chimafok.todo.i18n.*
import com.chimafok.todo.reminders.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LanguageUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val db get()=ReminderRuntime.database
    private val model get()=ViewModelProvider(compose.activity)[TodoViewModel::class.java]
    private val prefix="QA140-${UUID.randomUUID()}"
    private fun <T> data(block:suspend()->T)=runBlocking { block() }
    private fun await(tag:String) { compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    @Before fun setup() {
        assertEquals("com.chimafok.todo.qa",compose.activity.packageName)
        await("add_task");data { model.setLanguage(LanguageChoice.ENGLISH) };compose.waitForIdle()
    }
    @After fun cleanup() {
        data { model.discardNewTaskDraft();db.withTransaction {
            db.dao().allTasks().filter { it.title.startsWith(prefix) }.forEach { db.history().purgeTask(it.id) }
            db.dao().allCategories().filter { it.id==prefix }.forEach { db.dao().deleteCategory(it.id) }
        };model.setLanguage(LanguageChoice.TRADITIONAL) }
    }
    private fun awaitText(text:String) { compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun capture(label:String) { compose.mainClock.advanceTimeBy(600);compose.waitForIdle();Thread.sleep(600);compose.waitForIdle();androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/todo140-"+android.os.Build.VERSION.SDK_INT+"-"+label+".png").use { java.io.FileInputStream(it.fileDescriptor).use { input -> input.readBytes() } } }
    private fun settings() { compose.onNodeWithTag("open_menu").performClick();compose.onNodeWithTag("nav_SETTINGS").performClick();await("language_ENGLISH") }
    @Test fun switchAllLanguagesAndRecreateRetainsChoice() {
        settings();compose.onNodeWithText("App language").assertExists();capture("english")
        compose.onNodeWithTag("language_TRADITIONAL").performClick();awaitText("介面語言")
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("介面語言").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().layoutInput.style.localeList.toString().contains("zh"))
        capture("traditional")
        compose.onNodeWithTag("language_SIMPLIFIED").performClick();awaitText("介面语言");capture("simplified")
        compose.onNodeWithTag("language_ENGLISH").performClick();awaitText("App language")
        compose.activityRule.scenario.recreate();await("language_ENGLISH");compose.onNodeWithTag("language_ENGLISH").assertIsSelected()
        assertEquals("ENGLISH",data { db.history().policy()!!.language })
        compose.onNodeWithTag("language_SYSTEM").performClick();compose.waitForIdle()
        assertEquals(resolveLanguage(LanguageChoice.SYSTEM,(0 until android.content.res.Resources.getSystem().configuration.locales.size()).map { android.content.res.Resources.getSystem().configuration.locales[it] }),I18n.locale)
    }
    @Test fun liveLanguageSwitchPreservesEditorAndDraft() {
        compose.onNodeWithTag("add_task").performClick();await("task_title")
        compose.onNodeWithTag("task_title").performTextInput("$prefix 原文 Title")
        data { model.setLanguage(LanguageChoice.SIMPLIFIED) };compose.waitForIdle()
        compose.onNodeWithTag("task_title").assertTextContains("$prefix 原文 Title")
        assertEquals("zh-Hans",I18n.locale.toLanguageTag())
        assertEquals("新增任务",tr(R.string.text_add_task_54bff0))
        awaitText("新增任务")
        compose.activityRule.scenario.recreate();await("task_title")
        compose.onNodeWithTag("task_title").assertTextContains("$prefix 原文 Title")
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot()).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
        androidx.test.espresso.Espresso.pressBack();await("draft_exit_dialog")
        compose.onNodeWithTag("keep_draft").performClick()
        compose.waitUntil(10_000) { model.newTaskDraft!=null }
        assertEquals("$prefix 原文 Title",model.newTaskDraft!!.title)
    }
    @Test fun customNamesTaskTimesAndReminderLabelsSurviveSwitch() {
        val date=java.time.LocalDate.of(2030,12,31);val time=java.time.LocalTime.of(10,0)
        data { OrganizationRepository(db).saveCategory(prefix,"自訂 Custom",null)
            TaskRepository(db).save(TaskDraft(prefix,"$prefix 原文",notes="原備註 Notes",categoryId=prefix,dueDate=date,dueTime=time,zoneId="Asia/Hong_Kong")) }
        val before=data { db.dao().task(prefix)!! }
        listOf(LanguageChoice.TRADITIONAL,LanguageChoice.SIMPLIFIED,LanguageChoice.ENGLISH).forEach { choice ->
            data { model.setLanguage(choice) };compose.waitForIdle()
            assertEquals(before,data { db.dao().task(prefix) })
            assertEquals("自訂 Custom",categoryName(data { db.dao().allCategories().single { it.id==prefix } }))
        }
        assertEquals("Hours",ReminderOffsetUnit.HOURS.label)
        assertEquals("Waiting for reminder",reminderStateLabel("PENDING"))
        val gateway=AndroidReminderGateway(compose.activity);gateway.ensureChannel()
        val manager=compose.activity.getSystemService(android.app.NotificationManager::class.java)
        assertEquals("Task reminders",manager.getNotificationChannel(AndroidReminderGateway.CHANNEL).name.toString())
        assertEquals("Study",categoryName(data { db.dao().allCategories().single { it.id==STUDY_ID } }.copy(name="學習")))
        assertEquals("自訂 Study",categoryName(data { db.dao().allCategories().single { it.id==STUDY_ID } }.copy(name="自訂 Study")))
    }
}
