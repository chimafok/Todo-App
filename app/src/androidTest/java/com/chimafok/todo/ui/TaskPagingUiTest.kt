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
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TaskPagingUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val prefix="分批-${UUID.randomUUID().toString().take(8)}"
    private val model get()=ViewModelProvider(compose.activity)[TodoViewModel::class.java]
    private fun <T> data(block:suspend(TodoDatabase)->T):T=runBlocking{block(ReminderRuntime.database)}
    private fun awaitTag(tag:String){compose.waitUntil(20_000){compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
    private fun loaded(){compose.waitForIdle();compose.waitUntil(20_000){compose.onAllNodesWithTag("task_loading").fetchSemanticsNodes().isEmpty()};compose.waitForIdle()}
    private fun search(text:String){compose.onNodeWithTag("task_search").performTextReplacement(text);compose.onNodeWithTag("task_search").performImeAction();loaded()}
    private fun selectAll(){compose.onNodeWithTag("toggle_selection").performClick();compose.onNodeWithTag("select_results").performClick()}
    @Before fun seed(){
        awaitTag("add_task")
        data {db->db.withTransaction {
            (1..130).forEach{n->db.dao().insert(TaskEntity("$prefix-$n","$prefix-$n",if(n==1)"$prefix-頁外唯一" else "完整備註$n",UNCATEGORIZED_ID,createdAt=n.toLong(),updatedAt=1))}
            db.dao().insert(TaskEntity("$prefix-outside","保留項目","",UNCATEGORIZED_ID,createdAt=1,updatedAt=1))
            db.dao().insertTag(TagEntity("$prefix-tag","分批測試標籤","分批測試標籤",0,1,1))
        }}
        compose.onNodeWithTag("open_menu").performClick();compose.onNodeWithTag("nav_UNSCHEDULED").performClick();search(prefix)
        compose.onNodeWithTag("result_count").assertTextEquals("130 項")
    }
    @After fun cleanup(){data{db->db.withTransaction{
        db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id LIKE ?",arrayOf("$prefix-%"))
        db.openHelper.writableDatabase.execSQL("DELETE FROM tags WHERE id=?",arrayOf("$prefix-tag"))
        db.openHelper.writableDatabase.execSQL("DELETE FROM schedule_outbox WHERE taskId LIKE ?",arrayOf("$prefix-%"))
    }}}
    @Test fun pageLimitsRotationOffPageSearchEditorAndAllResultTagsKeepFullScope(){
        compose.waitUntil(20_000){model.state.value.tasks.count{it.id.startsWith("$prefix-")}==50}
        compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("load_more_undated"))
        compose.onNodeWithTag("load_more_undated").performClick();loaded()
        compose.waitUntil(20_000){model.state.value.tasks.count{it.id.startsWith("$prefix-")}==100}
        compose.activityRule.scenario.recreate();awaitTag("task_search");loaded()
        compose.waitUntil(20_000){model.state.value.tasks.count{it.id.startsWith("$prefix-")}==100}
        search("$prefix-頁外唯一")
        compose.onNodeWithTag("result_count").assertTextEquals("1 項")
        compose.onNodeWithTag("task_$prefix-1").performClick()
        compose.onNode(hasText("編輯") and hasAnyAncestor(hasTestTag("task_$prefix-1"))).performClick()
        awaitTag("task_title");compose.onNodeWithTag("task_title").assertTextContains("$prefix-1")
        compose.onNodeWithText("取消",substring=false).performClick();search(prefix)
        selectAll();compose.onNodeWithText("已選 130 項").assertExists()
        compose.activityRule.scenario.recreate();awaitTag("task_search");loaded()
        compose.onNodeWithText("已選 130 項").assertExists()
        compose.onNodeWithTag("batch_tags").performClick();awaitTag("confirm_batch")
        compose.onNodeWithTag("batch_tag_$prefix-tag").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_batch").performClick()
        compose.waitUntil(20_000){data{db->(1..130).all{db.dao().taskTagIds("$prefix-$it").contains("$prefix-tag")}}}
        assertTrue(data{it.dao().taskTagIds("$prefix-outside").isEmpty()})
    }
    @Test fun unloadedRevisionChangesDisableBatchAndFullDeletePreservesOtherResults(){
        selectAll();compose.onNodeWithTag("batch_trash").performClick();awaitTag("confirm_batch")
        compose.onNodeWithText("取消",substring=false).performClick()
        assertTrue(data{db->(1..130).all{db.dao().task("$prefix-$it")!!.deletedAt==null}})
        compose.onNodeWithTag("batch_trash").performClick();awaitTag("confirm_batch")
        data{db->val old=db.dao().task("$prefix-1")!!;db.dao().update(old.copy(notes="更新備註",revision=2))}
        compose.waitUntil(20_000){model.state.value.tasks.any{it.id=="$prefix-1"&&it.revision==2L}}
        compose.onNodeWithTag("confirm_batch").assertIsNotEnabled()
        compose.onNodeWithText("取消",substring=false).performClick();loaded()
        compose.onNodeWithTag("batch_trash").performClick();awaitTag("confirm_batch")
        compose.activityRule.scenario.recreate();awaitTag("confirm_batch")
        compose.onNodeWithTag("confirm_batch").performClick()
        compose.waitUntil(20_000){data{db->(1..130).all{db.dao().task("$prefix-$it")!!.deletedAt!=null}}}
        assertNull(data{it.dao().task("$prefix-outside")!!.deletedAt})
    }
}
