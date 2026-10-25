package com.chimafok.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.ReminderRuntime
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

@com.chimafok.todo.Regression101
@RunWith(AndroidJUnit4::class)
class SearchRefreshUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val id=UUID.randomUUID().toString()
    private val title="QA101-search-${id.take(8)}"
    private lateinit var db:TodoDatabase
    @Before fun setup() {
        compose.waitUntil(20_000){compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty()}
        assertEquals("com.chimafok.todo.qa",compose.activity.packageName)
        db=TodoDatabase.create(compose.activity)
        runBlocking {TaskRepository(db).save(TaskDraft(id,title,dueDate=LocalDate.now()))}
        compose.runOnUiThread {ViewModelProvider(compose.activity)[TodoViewModel::class.java].reload()}
        ready()
    }
    @After fun cleanup() {if(::db.isInitialized){db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id=?",arrayOf(id));db.close()}}
    private fun ready() {compose.waitUntil(20_000){
        compose.onAllNodesWithTag("toggle_selection").fetchSemanticsNodes().singleOrNull()?.config?.contains(SemanticsProperties.Disabled)==false
    }}
    private fun locked(block:()->Unit) {
        val io=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val job=io.launch {ReminderRuntime.database.withTransaction {entered.complete(Unit);release.await()}}
        runBlocking {withTimeout(10_000){entered.await()}}
        try {block()} finally {release.complete(Unit);runBlocking{withTimeout(10_000){job.join()}};io.cancel()}
    }
    private fun assertSelectionAppearance(expectedImage: ImageBitmap) {
        val expected=expectedImage.toPixelMap()
        val actual=compose.onNodeWithTag("toggle_selection").captureToImage().toPixelMap()
        assertEquals(expected.width,actual.width);assertEquals(expected.height,actual.height)
        var changed=0
        for(y in 0 until actual.height) for(x in 0 until actual.width) {
            val a=actual[x,y];val e=expected[x,y]
            if(maxOf(abs(a.red-e.red),abs(a.green-e.green),abs(a.blue-e.blue),abs(a.alpha-e.alpha))>0.03f) changed++
        }
        assertTrue("Selection button changed appearance while a search was pending: $changed pixels",changed<=4)
    }
    @Test fun chineseLettersAndDigitsKeepTheEmptySearchViewWhileNextQueryIsPending() {
        compose.onNodeWithTag("task_search").performTextReplacement("絕無匹配-$id")
        compose.onNodeWithTag("task_search").performImeAction()
        compose.waitUntil(10_000){compose.onAllNodesWithText("找不到符合的任務").fetchSemanticsNodes().isNotEmpty()}
        ready()
        val selectionAppearance=compose.onNodeWithTag("toggle_selection").captureToImage()
        locked {
            for(character in listOf("中","A","1")) {
                compose.onNodeWithTag("task_search").performTextInput(character)
                compose.onNodeWithText("找不到符合的任務").assertIsDisplayed()
                compose.onNodeWithTag("task_list").assertDoesNotExist()
                compose.onNodeWithTag("toggle_selection").assertIsNotEnabled()
                assertSelectionAppearance(selectionAppearance)
            }
        }
        ready()
        assertSelectionAppearance(selectionAppearance)
        compose.onNodeWithTag("task_search").assertTextContains("絕無匹配-${id}中A1")
    }
    @Test fun warmReturnShowsCachedHomeButDisablesStaleActionsUntilTheFreshQueryCompletes() {
        compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_$id"))
        compose.onNodeWithTag("open_menu").performClick();compose.onNodeWithTag("nav_SETTINGS").performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        locked {
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("page_title").assertTextEquals("日程")
            compose.onNodeWithTag("task_$id").assertExists()
            compose.onNodeWithTag("complete_$id").assertIsNotEnabled()
        }
        ready()
        compose.onNodeWithTag("complete_$id").assertIsEnabled()
    }
    @Test fun filterCountShareACentreLineAndDeadlineLabelsKeepAccessibleTextWithoutFloatingNotches() {
        val filter=compose.onNodeWithTag("open_filters").getUnclippedBoundsInRoot()
        val count=compose.onNodeWithTag("result_count").getUnclippedBoundsInRoot()
        assertTrue(abs((filter.top.value+filter.bottom.value)/2-(count.top.value+count.bottom.value)/2)<=1f)
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_date").assertContentDescriptionContains("截止日期（選填）")
        compose.onNodeWithTag("task_time").assertContentDescriptionContains("截止時間（選填）")
        compose.onNodeWithText("截止日期（可不填）").assertDoesNotExist()
        compose.onNodeWithText("取消",substring=false).performClick()
    }
}
