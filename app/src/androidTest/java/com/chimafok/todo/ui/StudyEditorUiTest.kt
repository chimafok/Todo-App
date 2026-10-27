package com.chimafok.todo.ui

import androidx.compose.ui.test.*
import androidx.room.withTransaction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StudyEditorUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val prefix="QA110-${UUID.randomUUID().toString().take(8)}"
    private lateinit var db:TodoDatabase
    @Before fun ready(){
        compose.waitUntil(20_000){compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty()}
        assertEquals("com.chimafok.todo.qa",compose.activity.packageName);db=TodoDatabase.create(compose.activity)
    }
    @After fun cleanup(){if(::db.isInitialized)try{runBlocking{db.withTransaction {
        val series=db.dao().allSeries().filter{it.title.startsWith(prefix)}
        series.forEach{db.dao().updateSeries(it.copy(lifecycle=SeriesLifecycle.STOPPED))}
        db.dao().allTasks().filter{it.title.startsWith(prefix)}.forEach{db.history().purgeTask(it.id)}
        series.forEach{db.history().purgeSeries(it.id)}
    }}}finally{db.close()}}
    private fun keyboard(){
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
        compose.waitForIdle()
    }
    private fun category(name:String){keyboard();compose.onNodeWithText("分類：未分類").performScrollTo().performClick();compose.onAllNodesWithText(name,substring=false).onLast().performClick()}
    private fun save(){keyboard();compose.onNodeWithTag("save_task").performClick();compose.waitUntil(15_000){compose.onAllNodesWithTag("task_title").fetchSemanticsNodes().isEmpty()}}
    @Test fun newAssignmentMenuExcludesCompletedAndAllowsUnsubmitted(){
        compose.onNodeWithTag("add_task").performClick();compose.onNodeWithTag("task_title").performTextInput(prefix+"-assignment")
        category("作業");compose.onNodeWithText("作業狀態：未開始").performScrollTo().performClick()
        compose.onNodeWithText("完成",substring=false).assertDoesNotExist()
        compose.onNodeWithText("進行中",substring=false).assertExists()
        compose.onNodeWithText("完成未繳交",substring=false).performClick();save()
        assertEquals(TaskStatus.DONE_UNSUBMITTED,runBlocking{db.dao().allTasks().single{it.title==prefix+"-assignment"}}.status)
    }
    @Test fun studyStartTimeRequiresDateAndSavingDoesNotSetDeadline(){
        compose.onNodeWithTag("add_task").performClick();compose.onNodeWithTag("task_title").performTextInput(prefix+"-study")
        category("學習")
        compose.onNodeWithTag("task_start_time").performScrollTo().assertIsNotEnabled()
        val categoryBounds=compose.onNodeWithText("分類：學習").getUnclippedBoundsInRoot()
        val startBounds=compose.onNodeWithTag("task_start_date").getUnclippedBoundsInRoot()
        val statusBounds=compose.onNodeWithTag("study_status").getUnclippedBoundsInRoot()
        assertTrue("學習狀態應在分類與開始日期之間",statusBounds.top>=categoryBounds.bottom && startBounds.top>=statusBounds.bottom)
        val screenshot=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.FileOutputStream(java.io.File(compose.activity.cacheDir,"study120-start-fields.png")).use{
            assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        };screenshot.recycle()
        compose.onNodeWithTag("task_start_date").performTextInput("20600103")
        compose.onNodeWithTag("task_start_time").assertIsEnabled().performTextInput("0930")
        keyboard();compose.onNodeWithText("清除開始日期與時間").performScrollTo().performClick()
        compose.onNodeWithTag("task_start_time").assertIsNotEnabled()
        compose.onNodeWithTag("task_start_date").performScrollTo().performTextInput("20600103")
        compose.onNodeWithTag("task_start_time").performTextInput("0930");save()
        val row=runBlocking{db.dao().allTasks().single{it.title==prefix+"-study"}}
        assertEquals(LocalDate.of(2060,1,3),row.startDate);assertEquals(LocalTime.of(9,30),row.startTime)
        assertNotNull(row.startZoneId);assertNull(row.dueDate);assertNull(row.dueTime)
    }

    @Test fun studyStatusNewMenuExistingMenuCompletionAndReopening(){
        compose.onNodeWithTag("add_task").performClick();compose.onNodeWithTag("task_title").performTextInput(prefix+"-progress")
        category("學習");compose.onNodeWithTag("study_status").performScrollTo().performClick()
        compose.onNodeWithText("未開始",substring=false).assertExists()
        compose.onNodeWithText("完成",substring=false).assertDoesNotExist()
        compose.onNodeWithText("完成未繳交",substring=false).assertDoesNotExist()
        compose.onNodeWithText("進行中",substring=false).performClick();save()
        val task=runBlocking {db.dao().allTasks().single{it.title==prefix+"-progress"}}
        assertEquals(TaskStatus.IN_PROGRESS,task.status)
        compose.onNodeWithTag("status_${task.id}").performScrollTo().performClick()
        compose.onNodeWithTag("choose_status_OPEN").assertExists()
        compose.onNodeWithTag("choose_status_IN_PROGRESS").assertExists()
        compose.onNodeWithTag("choose_status_DONE_UNSUBMITTED").assertDoesNotExist()
        compose.onNodeWithTag("choose_status_COMPLETED").performClick()
        compose.waitUntil(10_000){runBlocking {db.dao().task(task.id)!!.status==TaskStatus.COMPLETED}}
        compose.onNodeWithTag("open_menu").performClick();compose.onNodeWithTag("nav_COMPLETED").performClick()
        compose.onNodeWithTag("task_${task.id}").performScrollTo().performClick()
        compose.onNodeWithText("重新開啟",substring=false).performClick()
        compose.onNodeWithTag("choose_status_IN_PROGRESS").performClick()
        compose.waitUntil(10_000){runBlocking {db.dao().task(task.id)!!.status==TaskStatus.IN_PROGRESS}}
        assertNull(runBlocking {db.dao().task(task.id)!!.completedAt})
    }
    @Test fun recurringFirstDeadlineLabelAndLearningProgressSelection(){
        compose.onNodeWithTag("add_task").performClick();compose.onNodeWithTag("task_title").performTextInput(prefix+"-repeat")
        category("學習");compose.onNodeWithTag("study_status").performScrollTo().performClick()
        compose.onNodeWithText("進行中",substring=false).performClick()
        compose.onNodeWithText("每日",substring=false).performScrollTo().performClick()
        compose.onNodeWithTag("task_date").performScrollTo()
        compose.onNodeWithTag("task_date").assertContentDescriptionEquals("第一次截止日期（留空為今天）")
        compose.onNodeWithTag("study_status").performScrollTo()
        compose.onNodeWithText("學習狀態：進行中",substring=false).assertExists()
        save()
        val first=runBlocking {db.dao().allTasks().single{it.title==prefix+"-repeat" && it.status==TaskStatus.IN_PROGRESS}}
        assertNotNull(first.dueDate)
    }
}
