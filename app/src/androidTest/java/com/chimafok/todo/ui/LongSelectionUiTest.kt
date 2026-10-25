package com.chimafok.todo.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimafok.todo.MainActivity
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.ReminderRuntime
import com.chimafok.todo.ui.theme.TodoTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.time.*
import java.util.UUID

@com.chimafok.todo.Regression104
@RunWith(AndroidJUnit4::class)
class LongSelectionUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val prefix="QA104-${UUID.randomUUID().toString().take(8)}"
    private val db get()=ReminderRuntime.database
    private val model get()=ViewModelProvider(compose.activity)[TodoViewModel::class.java]
    private fun <T> data(block:suspend()->T):T=runBlocking {block()}
    @Before fun setup(){await("add_task");assertEquals("com.chimafok.todo.qa",compose.activity.packageName)}
    @After fun cleanup(){data {db.withTransaction {
        db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id LIKE ?",arrayOf("$prefix%"))
        db.openHelper.writableDatabase.execSQL("DELETE FROM recurring_series WHERE id LIKE ?",arrayOf("$prefix%"))
        db.openHelper.writableDatabase.execSQL("DELETE FROM schedule_outbox WHERE taskId LIKE ?",arrayOf("$prefix%"))
    }}}
    private fun await(tag:String){compose.waitUntil(20_000){compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
    private fun ready(history:Boolean=false){compose.waitUntil(20_000){compose.onAllNodesWithTag(if(history)"history_toggle_selection" else "toggle_selection").fetchSemanticsNodes().singleOrNull()?.config?.contains(SemanticsProperties.Disabled)==false}}
    private fun reload(){compose.runOnUiThread {model.reload()};ready()}
    private fun search(text:String,history:Boolean=false){compose.onNodeWithTag(if(history)"recurring_search" else "task_search").performTextReplacement(text);compose.onNodeWithTag(if(history)"recurring_search" else "task_search").performImeAction();ready(history)}
    private fun nav(page:String){compose.onNodeWithTag("open_menu").performClick();compose.onNodeWithTag("nav_$page").performClick();ready()}
    private fun history(){nav("COMPLETED");compose.onNodeWithText("重複紀錄",substring=false).performClick();ready(true)}
    private fun long(tag:String,list:String="task_list") {
        compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performTouchInput {longClick(Offset(width-20f,height/2f))}
    }
    private suspend fun task(id:String,date:LocalDate?=null,done:Boolean=false,deleted:Boolean=false) {
        db.dao().insert(TaskEntity(id,id,"",UNCATEGORIZED_ID,dueDate=date,zoneId=if(date!=null)ZoneId.systemDefault().id else null,
            status=if(done)TaskStatus.COMPLETED else TaskStatus.OPEN,completedAt=if(done)System.currentTimeMillis() else null,
            deletedAt=if(deleted)System.currentTimeMillis() else null,createdAt=1,updatedAt=1))
    }
    private suspend fun series(id:String,count:Int=2,ended:Boolean=false) {
        val anchor=if(ended)LocalDate.now().minusDays(count+2L) else LocalDate.now().plusDays(2)
        db.dao().insertSeries(RecurringSeriesEntity(id,id,"",UNCATEGORIZED_ID,lifecycle=if(ended)SeriesLifecycle.ENDED else SeriesLifecycle.ACTIVE,
            cursor=count,createdAt=1,updatedAt=1))
        db.dao().insertRule(SeriesRuleEntity("$id-rule",id,1,1,anchor,RecurrenceFrequency.DAILY,1,0,null,ZoneId.systemDefault().id,null,if(ended)count else count+2,1))
        (1..count).forEach {n->task("$id-$n",anchor.plusDays(n-1L),ended);db.dao().insertOccurrence(OccurrenceEntity("$id-$n",id,n,"$id-rule"))}
    }
    private fun closed(tag:String){compose.waitUntil(20_000){compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()}}
    private fun capture(label:String){InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/todo-104-$label.png").use {FileInputStream(it.fileDescriptor).use {input->input.readBytes()}}}

    @Test fun compactEmptySectionsShowZeroAndRealCardsWithCountsAtNormalAndDoubleFont() {
        val day=LocalDate.of(2026,10,25);val future=day.plusDays(5)
        val row=TaskEntity("$prefix-layout","真正待辦","",UNCATEGORIZED_ID,dueDate=future,zoneId="UTC",createdAt=1,updatedAt=1)
        val large=mutableStateOf(false)
        compose.runOnUiThread {compose.activity.setContent {TodoTheme {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,if(large.value)2f else 1f)) {
                Surface {Box(Modifier.systemBarsPadding()) {TaskList(TodoState(tasks=listOf(row)),Destination.AGENDA,
                    Clock.fixed(day.atStartOfDay(ZoneOffset.UTC).toInstant(),ZoneOffset.UTC),null,{}, {}, {}, {}, {}, {}, {}, {}, {},true)}}
            }
        }}}
        compose.onNodeWithTag("count_$day").assertTextEquals("0 項")
        compose.onNodeWithTag("count_$future").assertTextEquals("1 項")
        compose.onNodeWithText("今天沒有待辦").assertHasNoClickAction()
        val hintBounds=compose.onNodeWithText("今天沒有待辦").getUnclippedBoundsInRoot()
        assertTrue((hintBounds.bottom-hintBounds.top).value<52f)
        compose.onNodeWithText("使用「新增」安排這一天的任務。").assertDoesNotExist()
        compose.onNodeWithTag("task_${row.id}").assertIsDisplayed()
        capture("compact")
        compose.runOnUiThread {large.value=true}
        compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("count_undated"))
        compose.onNodeWithTag("count_undated").assertTextEquals("0 項").assertIsDisplayed()
        capture("large-font")
    }
    @Test fun longPressSelectsWithoutCompletingAndWorksInPendingAndCompletedPages() {
        val a="$prefix-a";val b="$prefix-b";val done="$prefix-done"
        data {db.withTransaction {task(a);task(b);task(done,done=true)}};reload();search(prefix)
        long("task_$a");compose.onNodeWithTag("select_$a").assertIsOn()
        long("task_$b");compose.onNodeWithTag("select_$b").assertIsOn()
        compose.onNodeWithTag("task_$a").performClick();compose.onNodeWithTag("select_$a").assertIsOff()
        assertEquals(TaskStatus.OPEN,data {db.dao().task(a)!!.status})
        compose.onNodeWithTag("toggle_selection").performClick()
        nav("UNSCHEDULED");search(prefix);long("task_$a");compose.onNodeWithTag("select_$a").assertIsOn()
        nav("COMPLETED");search(prefix);long("task_$done");compose.onNodeWithTag("select_$done").assertIsOn()
        assertEquals(TaskStatus.COMPLETED,data {db.dao().task(done)!!.status})
        compose.onNodeWithTag("toggle_selection").performClick();compose.onNodeWithTag("task_$done").performClick()
        compose.onNodeWithText("重新開啟",substring=false).assertExists()
    }
    @Test fun trashLongSelectCanCancelThenRestoreAndPurgeOnlyTheChosenTasks() {
        val a="$prefix-a";val b="$prefix-b";val keep="$prefix-keep"
        data {db.withTransaction {task(a,deleted=true);task(b,deleted=true);task(keep,deleted=true)}};reload();nav("TRASH");search(prefix)
        long("task_$a");long("task_$b");compose.onNodeWithTag("batch_restore").performClick();await("trash_selection_dialog")
        compose.onNodeWithTag("cancel_trash_selection").performClick();assertNotNull(data {db.dao().task(a)!!.deletedAt})
        compose.onNodeWithTag("batch_restore").performClick();await("confirm_trash_selection");compose.onNodeWithTag("confirm_trash_selection").performClick();closed("trash_selection_dialog");ready()
        assertNull(data {db.dao().task(a)!!.deletedAt});assertNotNull(data {db.dao().task(keep)!!.deletedAt})
        long("task_$keep");compose.onNodeWithTag("batch_purge").performClick();await("trash_selection_dialog")
        compose.onNodeWithTag("cancel_trash_selection").performClick();assertNotNull(data {db.dao().task(keep)})
        compose.onNodeWithTag("batch_purge").performClick();await("confirm_trash_selection");compose.onNodeWithTag("confirm_trash_selection").performClick();closed("trash_selection_dialog")
        assertNull(data {db.dao().task(keep)});assertNotNull(data {db.dao().task(a)})
    }
    @Test fun deletedSeriesLongSelectionRestoresItsOwnDeletionGroupAndCanPurgeAGroup() {
        val id="$prefix-series";data {db.withTransaction {series(id,3,true)
            val first=db.dao().task("$id-1")!!;db.dao().update(first.copy(deletedAt=System.currentTimeMillis()-1000,revision=2))
            val p=HistoryBrowserRepository(db).actionPreview(id,DeviceClock);RecurringRepository(db).delete(id,p.revision,p.tasks,true)
        }};reload();nav("TRASH")
        compose.onNodeWithTag("trash_series_$id").performTouchInput {longClick(Offset(width*0.4f,height/2f))}
        compose.onNodeWithTag("select_trash_series_$id").assertIsOn()
        compose.onNodeWithTag("batch_restore").performClick();await("confirm_trash_selection");compose.onNodeWithTag("confirm_trash_selection").performClick();closed("trash_selection_dialog")
        assertNull(data {db.dao().series(id)!!.deletedAt});assertNotNull(data {db.dao().task("$id-1")!!.deletedAt});assertNull(data {db.dao().task("$id-2")!!.deletedAt})
        data {val p=HistoryBrowserRepository(db).actionPreview(id,DeviceClock);RecurringRepository(db).delete(id,p.revision,p.tasks,true)}
        ready();await("trash_series_$id")
        compose.onNodeWithTag("trash_series_$id").performTouchInput {longClick(Offset(width*0.4f,height/2f))}
        compose.onNodeWithTag("batch_purge").performClick();await("confirm_trash_selection")
        compose.onNodeWithTag("cancel_trash_selection").performClick();assertNotNull(data {db.dao().series(id)})
        compose.onNodeWithTag("batch_purge").performClick();await("confirm_trash_selection");compose.onNodeWithTag("confirm_trash_selection").performClick();closed("trash_selection_dialog")
        assertNull(data {db.dao().series(id)});assertNull(data {db.dao().task("$id-1")})
    }
    @Test fun seriesLongSelectionKeepsFrozenPreviewAcrossRotationAndRejectsStaleChildren() {
        val a="$prefix-a";val b="$prefix-b";data {db.withTransaction {series(a);series(b)}};reload();history();search(prefix,true)
        long("recurring_series_$a","recurring_list");long("recurring_series_$b","recurring_list")
        compose.onNodeWithTag("history_batch_stop").performClick();await("series_selection_dialog")
        val frozen=model.seriesSelectionPreview
        compose.activityRule.scenario.recreate();await("series_selection_dialog");assertEquals(frozen,model.seriesSelectionPreview)
        data {val task=db.dao().task("$b-1")!!;db.dao().update(task.copy(notes="changed",revision=task.revision+1))}
        compose.onNodeWithTag("confirm_series_selection").performClick();await("selection_error")
        assertEquals(SeriesLifecycle.ACTIVE,data {db.dao().series(a)!!.lifecycle})
        compose.onNodeWithTag("cancel_series_selection").performClick()
    }
    @Test fun historyRecordLongSelectionSelectsAllMatchingPagesAndKeepsCancelledRowsReadOnly() {
        val id="$prefix-series";data {db.withTransaction {series(id,55,true);db.dao().updateOccurrence(db.dao().occurrence("$id-1")!!.copy(excludedByRule=true))}}
        reload();history();search(prefix,true);compose.onNodeWithTag("recurring_series_$id").performClick();ready(true)
        long("task_$id-55","recurring_list");compose.onNodeWithTag("select_$id-55").assertIsOn()
        compose.onNodeWithTag("history_select_results").performClick();ready(true)
        compose.waitUntil(20_000){model.historyRecordSelection.value.size==54}
        assertTrue("$id-2" in model.historyRecordSelection.value);assertFalse("$id-1" in model.historyRecordSelection.value)
        compose.onNodeWithTag("history_batch_trash").performClick();await("confirm_batch");compose.onNodeWithText("取消",substring=false).performClick();ready(true)
        assertNull(data {db.dao().task("$id-2")!!.deletedAt})
        compose.onNodeWithTag("history_batch_trash").performClick();await("confirm_batch");compose.onNodeWithTag("confirm_batch").performClick();closed("confirm_batch")
        assertNotNull(data {db.dao().task("$id-2")!!.deletedAt});assertNull(data {db.dao().task("$id-1")!!.deletedAt})
    }

    @Test fun historyQueryPendingBlocksLongPressAndCancellingAllSelectionIgnoresLateResults() {
        val id="$prefix-series";data {db.withTransaction {series(id,3,true)}};reload();history();search(prefix,true)
        compose.onNodeWithTag("recurring_series_$id").performClick();ready(true)
        long("task_$id-3","recurring_list")
        fun locked(block:()->Unit) {
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            val job=scope.launch {db.withTransaction {entered.complete(Unit);release.await()}}
            runBlocking {withTimeout(10_000){entered.await()}}
            try {block()} finally {release.complete(Unit);runBlocking {withTimeout(10_000){job.join()}};scope.cancel()}
        }
        locked {
            compose.onNodeWithTag("recurring_search").performTextReplacement("$prefix-no-match")
            compose.onNodeWithTag("task_$id-3").assertIsNotEnabled()
            compose.onNodeWithTag("task_$id-3").performTouchInput {longClick(Offset(width-20f,height/2f))}
            assertTrue(model.historyRecordSelection.value.isEmpty())
        }
        ready(true);compose.onNodeWithText("沒有符合的紀錄",substring=false).assertExists()
        search("",true);ready(true)
        locked {
            compose.onNodeWithTag("history_select_results").performClick()
            androidx.test.espresso.Espresso.pressBack()
        }
        ready(true)
        compose.onNodeWithTag("history_toggle_selection").assertTextContains("多選")
        assertTrue(model.historyRecordSelection.value.isEmpty())
    }
}
