package com.chimafok.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.ReminderRuntime
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoryPagingUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val series="paging-${UUID.randomUUID()}"
    private val needle="歷史尾端-${UUID.randomUUID()}"
    private val model get()=ViewModelProvider(compose.activity)[TodoViewModel::class.java]
    private fun <T> data(block:suspend(TodoDatabase)->T):T=runBlocking{block(ReminderRuntime.database)}
    private fun awaitTag(tag:String) { compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun loaded() { compose.waitUntil(20_000) { compose.onAllNodesWithTag("history_loading").fetchSemanticsNodes().isEmpty() };compose.waitForIdle() }
    private fun nav(destination:String) { compose.onNodeWithTag("open_menu").performClick();compose.onNodeWithTag("nav_$destination").performClick() }
    private fun history() {
        nav("COMPLETED");compose.onNodeWithText("重複紀錄").performClick();loaded()
        compose.onNodeWithTag("recurring_series_$series").performClick();awaitTag("history_page_summary");loaded()
    }
    @Before fun seed() {
        awaitTag("add_task")
        data { db -> db.withTransaction {
            val date=LocalDate.now().minusDays(150)
            db.dao().insertSeries(RecurringSeriesEntity(series,"分頁系列","",UNCATEGORIZED_ID,SeriesLifecycle.ENDED,123,createdAt=1,updatedAt=1))
            db.dao().insertRule(SeriesRuleEntity("$series-rule",series,1,1,date,RecurrenceFrequency.DAILY,1,0,null,"UTC",null,123,1))
            (1..123).forEach { n ->
                val id="$series-$n"
                db.dao().insert(TaskEntity(id,"entry$n","",UNCATEGORIZED_ID,date.plusDays(n.toLong()),zoneId="UTC",status=TaskStatus.COMPLETED,completedAt=2,createdAt=1,updatedAt=1))
                db.dao().insertOccurrence(OccurrenceEntity(id,series,n,"$series-rule",OccurrenceSnapshot(if(n==1)needle else "entry$n","","未分類",emptyList(),"COMPLETED",false).json()))
            }
        } }
        compose.waitUntil(20_000) { model.state.value.series.any { it.id==series } }
        assertTrue(model.state.value.tasks.none { it.id.startsWith(series) })
    }
    @After fun clean() { data { db -> db.withTransaction {
        db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id IN (SELECT taskId FROM occurrences WHERE seriesId=?)",arrayOf(series))
        db.openHelper.writableDatabase.execSQL("DELETE FROM recurring_series WHERE id=?",arrayOf(series))
    } } }
    @Test fun loadMoreRotationFullSearchAndCompletedEditorUseTheCorrectScope() {
        history();compose.onNodeWithTag("history_page_summary").assertTextContains("50 / 123",substring=true)
        compose.onNodeWithTag("task_$series-1").assertDoesNotExist()
        compose.onNodeWithTag("recurring_list").performScrollToNode(hasTestTag("load_more_history"))
        compose.onNodeWithTag("load_more_history").performClick();loaded()
        compose.activityRule.scenario.recreate();awaitTag("history_page_summary");loaded()
        compose.onNodeWithTag("recurring_list").performScrollToNode(hasTestTag("history_page_summary"))
        compose.onNodeWithTag("history_page_summary").assertTextContains("100 / 123",substring=true)
        compose.onNodeWithTag("recurring_search").performTextReplacement(needle)
        compose.onNodeWithTag("recurring_search").performImeAction();loaded()
        compose.onNodeWithTag("history_page_summary").assertTextContains("1 / 1",substring=true)
        compose.onNodeWithTag("task_$series-1").performClick()
        compose.onNode(hasText("編輯") and hasAnyAncestor(hasTestTag("task_$series-1"))).performClick()
        awaitTag("task_title");compose.onNodeWithTag("task_title").assertTextContains("entry1")
        assertEquals(123,model.state.value.occurrences.count { it.seriesId==series })
        compose.onNodeWithText("取消",substring=false).performClick()
        compose.waitUntil(20_000) { model.state.value.detailTaskId==null && model.state.value.tasks.none { it.id.startsWith(series) } };loaded()
        compose.onNode(hasText("重新開啟") and hasAnyAncestor(hasTestTag("task_$series-1"))).performClick()
        compose.waitUntil(20_000) { data { it.dao().task("$series-1")!!.status==TaskStatus.OPEN } }
        compose.waitUntil(20_000) { model.state.value.tasks.any { it.id=="$series-1" && it.status==TaskStatus.OPEN } }
    }
    @Test fun deleteConfirmationCountsAllPagesAndKeepsItsScopeAcrossRotation() {
        nav("COMPLETED");compose.onNodeWithText("重複紀錄").performClick();loaded()
        compose.onNodeWithTag("delete_series_$series").performClick();awaitTag("confirm_series_action")
        compose.onNodeWithText("包含全部 123 項紀錄（含已完成）").assertExists()
        compose.onNodeWithText("取消",substring=false).performClick()
        assertEquals(123,data { it.dao().seriesOccurrences(series).size })
        compose.onNodeWithTag("delete_series_$series").performClick();awaitTag("confirm_series_action")
        compose.onNodeWithTag("include_series_history").performClick()
        compose.activityRule.scenario.recreate();awaitTag("include_series_history")
        compose.onNodeWithTag("include_series_history").assertIsOn()
        compose.onNodeWithTag("confirm_series_action").performClick()
        compose.waitUntil(20_000) { data { it.dao().series(series)!!.deletedAt!=null } }
        assertEquals(123,data { db->db.dao().seriesOccurrences(series).count { db.dao().task(it.taskId)!!.deletedAt!=null } })
        nav("TRASH")
        compose.waitUntil(20_000) { model.state.value.tasks.count { it.id.startsWith(series) }==123 }
    }
}
