package com.chimafok.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
class HistoryUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val owned=mutableListOf<String>()
    private var originalDays:Int?=null
    private fun <T> data(block:suspend(TodoDatabase)->T):T=runBlocking{block(ReminderRuntime.database)}
    private fun ready(){compose.waitUntil(20_000){compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty()}}
    private fun nav(destination:String){compose.onNodeWithTag("open_menu").performClick();compose.onNodeWithTag("nav_$destination").performClick();compose.waitForIdle()}
    private fun keyboard(){androidx.test.espresso.Espresso.closeSoftKeyboard();compose.waitForIdle()}
    private fun seed(completed:Boolean=false,trash:Boolean=false):TaskEntity {
        val id=UUID.randomUUID().toString().also{owned+=it}
        return data{db->
            TaskRepository(db,if(completed) Clock.offset(DeviceClock,Duration.ofDays(-60)) else DeviceClock)
                .save(TaskDraft(id,"清理測試-${id.take(7)}",status=if(completed)TaskStatus.COMPLETED else TaskStatus.OPEN))
            val t=db.dao().task(id)!!;if(trash)TaskRepository(db).trash(id,t.revision)
            db.dao().task(id)!!
        }
    }
    private fun awaitTag(tag:String){compose.waitUntil(10_000){compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
    private fun expand(id:String){
        compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_$id"))
        if(compose.onAllNodesWithTag("purge_$id").fetchSemanticsNodes().isEmpty())compose.onNodeWithTag("task_$id").performClick()
    }
    @Before fun permanentPolicyForFixture(){data{db->val h=HistoryRepository(db);originalDays=h.policy().days;h.apply(h.preview(null))}}
    @After fun cleanupOwned(){data{db->
        val h=HistoryRepository(db);h.apply(h.preview(null))
        db.withTransaction { owned.forEach{id->
            db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id IN (SELECT taskId FROM occurrences WHERE seriesId=?)",arrayOf(id))
            db.openHelper.writableDatabase.execSQL("DELETE FROM recurring_series WHERE id=?",arrayOf(id))
            db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id=?",arrayOf(id))
        } }
        h.apply(h.preview(originalDays))
    }}
    @Test fun customRetentionPreviewCancelRotationAndRestorePreserveTheOriginalCompletion() {
        ready();val task=seed(completed=true);nav("SETTINGS")
        compose.onNodeWithTag("retention_current").assertTextContains("永久保留",substring=true)
        compose.onNodeWithTag("retention_CUSTOM").performScrollTo().performClick()
        compose.onNodeWithTag("retention_days").performScrollTo().performTextReplacement("0");keyboard()
        compose.onNodeWithTag("preview_retention").performScrollTo().performClick()
        compose.onNodeWithTag("retention_error").performScrollTo().assertTextContains("大於 0",substring=true)
        compose.onNodeWithTag("retention_days").performScrollTo().performTextReplacement("30");keyboard()
        compose.onNodeWithTag("preview_retention").performScrollTo().performClick()
        compose.onNodeWithTag("retention_preview").assertExists()
        compose.onNodeWithTag("cancel_retention").performClick()
        assertNull(data{it.dao().task(task.id)!!.deletedAt});assertNull(data{HistoryRepository(it).policy().days})
        compose.onNodeWithTag("preview_retention").performScrollTo().performClick();awaitTag("retention_preview");compose.activityRule.scenario.recreate()
        awaitTag("retention_preview");compose.onNodeWithTag("retention_preview").assertExists()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/todo-060-retention-qa.png").use{fd->java.io.FileInputStream(fd.fileDescriptor).use{it.readBytes()}}
        compose.onNodeWithTag("confirm_retention").performClick()
        compose.waitUntil(10_000){data{it.dao().task(task.id)!!.deletedAt!=null}}
        assertEquals(30,data{HistoryRepository(it).policy().days});nav("TRASH");expand(task.id)
        compose.onNode(hasText("復原") and hasAnyAncestor(hasTestTag("task_${task.id}"))).performClick()
        compose.waitUntil(10_000){data{it.dao().task(task.id)!!.deletedAt==null}}
        data{HistoryRepository(it).maintain()}
        assertNull(data{it.dao().task(task.id)!!.deletedAt});assertEquals(task.completedAt,data{it.dao().task(task.id)!!.completedAt})
    }
    @Test fun individualPurgeRequiresConfirmationAndStaleRestoredTasksStaySafe() {
        ready();val task=seed(trash=true);nav("TRASH");expand(task.id)
        compose.onNodeWithTag("purge_${task.id}").performClick();compose.onNodeWithTag("cancel_purge").performClick()
        assertNotNull(data{it.dao().task(task.id)})
        compose.onNodeWithTag("purge_${task.id}").performClick()
        data{TaskRepository(it).restore(task.id)}
        compose.onNodeWithTag("confirm_purge").performClick()
        compose.onNodeWithTag("purge_error").assertTextContains("復原或更新",substring=true)
        compose.onNodeWithTag("cancel_purge").performClick()
        val restored=data{it.dao().task(task.id)!!};assertNull(restored.deletedAt)
        data{TaskRepository(it).trash(task.id,restored.revision)}
        expand(task.id);compose.onNodeWithTag("purge_${task.id}").performClick()
        compose.activityRule.scenario.recreate();compose.onNodeWithTag("purge_confirmation").assertExists()
        compose.onNodeWithTag("confirm_purge").performClick()
        compose.waitUntil(10_000){data{it.dao().task(task.id)==null}}
    }
    @Test fun emptyTrashExplicitlyIncludesHiddenSearchResultsAndKeepsActiveTasks() {
        ready();val one=seed(trash=true);val two=seed(trash=true);val active=seed()
        nav("TRASH");compose.onNodeWithTag("task_search").performTextInput(one.title);keyboard()
        compose.onNodeWithTag("task_${two.id}").assertDoesNotExist()
        compose.onNodeWithTag("empty_trash").performClick()
        compose.onNodeWithText("包含搜尋及篩選未顯示的全部垃圾桶資料。").assertExists()
        compose.onNodeWithTag("cancel_purge").performClick()
        assertNotNull(data{it.dao().task(one.id)});assertNotNull(data{it.dao().task(two.id)})
        compose.onNodeWithTag("empty_trash").performClick();compose.onNodeWithTag("confirm_purge").performClick()
        compose.waitUntil(10_000){data{it.dao().task(one.id)==null && it.dao().task(two.id)==null}}
        assertEquals(active,data{it.dao().task(active.id)})
    }
    @Test fun anEmptyPartiallyPurgedSeriesRemainsVisibleAndCanRestoreItsRemainingMetadata() {
        ready();val id=UUID.randomUUID().toString().also{owned+=it};val date=LocalDate.now().plusDays(4);val zone=ZoneId.systemDefault().id
        val first=data{db->val repo=TaskRepository(db);repo.save(TaskDraft(id,"空系列-${id.take(6)}",dueDate=date,zoneId=zone,
            recurrence=RecurrenceRule(date,RecurrenceFrequency.DAILY,zone,maxCount=1)))
            val series=db.dao().series(id)!!;val task=db.dao().seriesOccurrences(id).single().taskId
            RecurringRepository(db).delete(id,series.revision,mapOf(task to db.dao().task(task)!!.revision),true)
            HistoryRepository(db).purge(PurgePreview(mapOf(task to db.dao().task(task)!!.revision)));task}
        nav("TRASH");compose.onNodeWithTag("trash_restore_series_$id").assertExists().performClick()
        compose.onNodeWithTag("confirm_trash_restore_series").performClick()
        compose.waitUntil(10_000){data{it.dao().series(id)!!.deletedAt==null}}
        assertNull(data{it.dao().task(first)});assertEquals(1,data{it.dao().series(id)!!.cursor})
    }
}
