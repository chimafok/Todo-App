package com.chimafok.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@com.chimafok.todo.Regression101
@RunWith(AndroidJUnit4::class)
class SeriesReminderUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val owned=mutableListOf<String>()
    private fun <T> data(block:suspend(TodoDatabase)->T):T=runBlocking{block(ReminderRuntime.database)}
    private fun ready(){compose.waitUntil(20_000){compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty()}}
    private fun closed(){compose.waitUntil(10_000){compose.onAllNodesWithTag("task_title").fetchSemanticsNodes().isEmpty()}}
    private fun keyboard(){androidx.test.espresso.Espresso.closeSoftKeyboard();compose.waitForIdle()}
    private fun edit(id:String){
        compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_$id"))
        val edit=hasText("編輯") and hasAnyAncestor(hasTestTag("task_$id"))
        if(compose.onAllNodes(edit).fetchSemanticsNodes().isEmpty())compose.onNodeWithTag("task_$id").performClick()
        compose.onNode(edit).performClick()
    }
    private fun seed():String {
        val id=UUID.randomUUID().toString().also{owned+=it};val day=LocalDate.now().plusDays(4);val zone=ZoneId.systemDefault().id
        data{db->val repo=TaskRepository(db);repo.save(TaskDraft(id,"系列UI-${id.take(6)}",dueDate=day,zoneId=zone,
            recurrence=RecurrenceRule(day,RecurrenceFrequency.WEEKLY,zone,maxCount=6),
            seriesReminders=listOf(SeriesReminderSpec("template-$id",SeriesReminderKind.RELATIVE_DUE,offsetMinutes=60))))
            for(seq in 1..2){val t=db.dao().task(occurrenceId(id,seq))!!;repo.trash(t.id,t.revision);repo.restore(t.id)}}
        compose.waitUntil(10_000){data{it.dao().seriesOccurrences(id).size==3}};compose.waitForIdle();return id
    }
    @After fun cleanup(){data{db->db.withTransaction{owned.forEach{id->
        val tasks=db.dao().seriesOccurrences(id).map{it.taskId};val gateway=AndroidReminderGateway(compose.activity)
        tasks.forEach{task->db.reminders().forTask(task).forEach{r->r.alarmKey?.let{gateway.cancel(r,it)};gateway.dismiss(r.id)}}
        db.openHelper.writableDatabase.execSQL("DELETE FROM occurrences WHERE seriesId=?",arrayOf(id))
        tasks.forEach{db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id=?",arrayOf(it))}
        db.openHelper.writableDatabase.execSQL("DELETE FROM recurring_series WHERE id=?",arrayOf(id))
    }}}}
    @Test fun createSeriesWithRelativeAndDayOffsetBlankTimeRotationAndNextOccurrence() {
        ready();val title="新增系列提醒-${UUID.randomUUID()}";val date=LocalDate.now().plusDays(4)
        compose.onNodeWithTag("add_task").performClick();compose.onNodeWithTag("task_title").performTextInput(title)
        compose.onNodeWithTag("task_date").performTextInput(date.toString());keyboard()
        compose.onNodeWithTag("repeat_WEEKLY").performScrollTo().performClick()
        compose.onNodeWithTag("add_series_reminder").performScrollTo().performClick()
        compose.onNodeWithTag("series_reminder_quantity_0").performScrollTo().performTextReplacement("2");keyboard()
        compose.onNodeWithTag("series_reminder_unit_0").performScrollTo().performClick();compose.onNodeWithTag("series_reminder_unit_0_DAYS").performClick()
        compose.onNodeWithTag("add_series_reminder").performScrollTo().performClick()
        compose.onNodeWithTag("series_reminder_fixed_1").performScrollTo().performClick()
        compose.onNodeWithTag("series_reminder_direction_1_BEFORE").performScrollTo().performClick()
        compose.onNodeWithTag("series_reminder_days_1").performScrollTo().performTextReplacement("1")
        compose.onNodeWithTag("series_reminder_base_1").performScrollTo().performTextReplacement("1815");keyboard()
        compose.activityRule.scenario.recreate();keyboard()
        compose.onNodeWithTag("series_reminder_days_1").performScrollTo().assertTextContains("1")
        compose.onNodeWithTag("series_reminder_preview_1").performScrollTo().assertTextContains("18:15",substring=true)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /data/local/tmp/todo-052-series-reminders-qa.png").use{fd->java.io.FileInputStream(fd.fileDescriptor).use{it.readBytes()}}
        compose.onNodeWithTag("save_task").performClick();closed()
        val series=data{it.dao().allSeries().first{r->r.title==title}};owned+=series.id
        assertEquals(2,data{it.reminders().templatesFor(series.id).count{it.enabled}})
        val first=data{it.dao().task(occurrenceId(series.id,1))!!};assertNull(first.dueTime)
        val firstRows=data{it.reminders().forTask(first.id)};assertEquals(2,firstRows.size)
        assertEquals(2880,firstRows.first{it.kind=="RELATIVE_DUE"}.offsetMinutes)
        assertEquals(date.minusDays(1).atTime(18,15).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),firstRows.first{it.kind=="FIXED"}.fixedAt)
        data{TaskRepository(it).setStatus(first.id,first.revision,TaskStatus.COMPLETED)}
        val nextId=occurrenceId(series.id,2);val next=data{it.reminders().forTask(nextId)}
        assertEquals(2,next.size);assertTrue(next.none{r->firstRows.any{it.id==r.id}})
    }
    @Test fun futureAndSingleDraftsStaySeparateAndFutureConfirmationSavesTemplates() {
        ready();val id=seed();edit(occurrenceId(id,1))
        compose.onNodeWithTag("series_reminder_quantity_0").performScrollTo().performTextReplacement("2");keyboard()
        compose.onNodeWithTag("series_reminder_unit_0").performScrollTo().performClick();compose.onNodeWithTag("series_reminder_unit_0_DAYS").performClick()
        compose.onNodeWithTag("repeat_scope_single").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_offset_0").performScrollTo().performTextReplacement("90");keyboard()
        compose.activityRule.scenario.recreate();keyboard()
        compose.onNodeWithTag("repeat_scope_future").performScrollTo().performClick()
        compose.onNodeWithTag("series_reminder_quantity_0").performScrollTo().assertTextContains("2")
        compose.onNodeWithTag("series_reminder_unit_0").assertTextContains("天 ▾")
        compose.onNodeWithTag("save_task").performClick();compose.onNodeWithTag("confirm_series_edit").performClick();closed()
        assertEquals(2880,data{it.reminders().templatesFor(id).single{r->r.enabled}.offsetMinutes})
        assertFalse(data{it.reminders().overrides().any{r->r.taskId==occurrenceId(id,1)}})
        assertTrue(data{it.reminders().all().filter{r->r.enabled&&r.taskId in it.dao().seriesOccurrences(id).map{ref->ref.taskId}}.all{r->r.offsetMinutes==2880}})
    }
    @Test fun singleCustomReminderAndResetToSeriesBothSurviveRotation() {
        ready();val id=seed();val first=occurrenceId(id,1);edit(first)
        compose.onNodeWithTag("repeat_scope_single").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_offset_0").performScrollTo().performTextReplacement("90");keyboard()
        compose.onNodeWithTag("reminder_unit_0").performScrollTo().performClick();compose.onNodeWithTag("reminder_unit_0_MINUTES").performClick()
        compose.activityRule.scenario.recreate();keyboard();compose.onNodeWithTag("save_task").performClick();closed()
        assertTrue(data{it.reminders().overridden(first)});assertEquals(90,data{it.reminders().forTask(first).single{r->r.enabled}.offsetMinutes})
        assertEquals(60,data{it.reminders().templatesFor(id).single{r->r.enabled}.offsetMinutes})
        edit(first);compose.onNodeWithTag("repeat_scope_single").performScrollTo().performClick()
        compose.onNodeWithTag("reset_series_reminders").performScrollTo().performClick()
        compose.activityRule.scenario.recreate();keyboard();compose.onNodeWithTag("save_task").performClick();closed()
        assertFalse(data{it.reminders().overridden(first)});assertEquals(60,data{it.reminders().forTask(first).single{r->r.enabled}.offsetMinutes})
    }
    @Test fun addingRecurrenceNeverSilentlyDropsAnExistingSingleReminderDraft() {
        ready();val title="保留單筆草稿-${UUID.randomUUID()}"
        compose.onNodeWithTag("add_task").performClick();compose.onNodeWithTag("task_title").performTextInput(title)
        compose.onNodeWithTag("task_date").performTextInput(LocalDate.now().plusDays(4).toString());keyboard()
        compose.onNodeWithTag("add_reminder").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_time_0").performScrollTo().performTextReplacement("1430");keyboard()
        compose.onNodeWithTag("repeat_WEEKLY").performScrollTo().performClick();compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("editor_error").performScrollTo().assertTextContains("單筆提醒",substring=true)
        assertFalse(data{it.dao().allTasks().any{r->r.title==title}})
        compose.onNodeWithTag("repeat_NONE").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_time_0").performScrollTo().assertTextContains("1430")
        compose.onNodeWithText("取消",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("draft_exit_dialog").assertIsDisplayed()
        compose.onNodeWithText("捨棄",substring=false).performClick();closed()
        assertFalse(data{it.dao().allTasks().any{r->r.title==title}})
    }

}
