package com.chimafok.todo.ui

import androidx.compose.ui.test.*
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
class ReminderUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val owned=mutableListOf<String>()
    private fun <T> data(block:suspend(TodoDatabase)->T):T=runBlocking { val db=TodoDatabase.create(compose.activity);try{block(db)}finally{db.close()} }
    private fun ready() { compose.waitUntil(20_000){compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty()} }
    private fun keyboard() { androidx.test.espresso.Espresso.closeSoftKeyboard();compose.waitForIdle() }
    private fun reload() { compose.runOnIdle{androidx.lifecycle.ViewModelProvider(compose.activity)[TodoViewModel::class.java].reload()};ready() }
    @After fun cleanupOwned() { data { db -> owned.forEach { id ->
        val gateway=com.chimafok.todo.reminders.AndroidReminderGateway(compose.activity)
        db.reminders().forTask(id).forEach { row -> row.alarmKey?.let { gateway.cancel(row,it) };gateway.dismiss(row.id) }
        db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id=?",arrayOf(id))
    } } }
    @Test fun directMultipleRemindersRotationAndDateOnlyBaseSaveWithoutAddingDeadlineTime() {
        ready();val title="提醒UI-${UUID.randomUUID()}"
        compose.onNodeWithTag("add_task").performClick();compose.onNodeWithTag("task_title").performTextInput(title)
        val date=LocalDate.now().plusDays(2)
        compose.onNodeWithTag("task_date").performTextInput(date.toString());keyboard()
        compose.onNodeWithTag("add_reminder").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_relative_0").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_offset_0").performScrollTo().performTextReplacement("1");keyboard()
        compose.onNodeWithTag("reminder_unit_0").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_unit_0_DAYS").performClick()
        compose.onNodeWithTag("reminder_base_0").performScrollTo().performTextReplacement("1830");keyboard()
        compose.onNodeWithTag("add_reminder").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_time_1").performScrollTo().performTextReplacement("1900");keyboard()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("reminder_base_0").performScrollTo().assertTextContains("1830")
        compose.onNodeWithTag("reminder_time_1").performScrollTo().assertTextContains("1900")
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /data/local/tmp/todo-051-reminders-qa.png").use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
        compose.onNodeWithTag("save_task").performClick()
        compose.waitUntil(10_000){data{it.dao().allTasks().any{task->task.title==title}}}
        val task=data{it.dao().allTasks().first{task->task.title==title}};owned.add(task.id)
        assertNull(task.dueTime);assertEquals(date,task.dueDate)
        val reminders=data{it.reminders().forTask(task.id).filter{row->row.enabled}}
        assertEquals(2,reminders.size);assertEquals(LocalTime.of(18,30),reminders.first{it.kind=="RELATIVE_DUE"}.baseTime)
        assertEquals(1440,reminders.first{it.kind=="RELATIVE_DUE"}.offsetMinutes)
    }
    @Test fun missingDateRelativeAndCancelDoNotWriteAnyTask() {
        ready();val title="取消提醒-${UUID.randomUUID()}"
        compose.onNodeWithTag("add_task").performClick();compose.onNodeWithTag("task_title").performTextInput(title);keyboard()
        compose.onNodeWithTag("add_reminder").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_relative_0").performScrollTo().performClick()
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("editor_error").performScrollTo().assertTextContains("相對提醒需要截止日期",substring=true)
        assertFalse(data{it.dao().allTasks().any{task->task.title==title}})
        compose.onNodeWithText("取消",useUnmergedTree=true).performClick()
        assertFalse(data{it.dao().allTasks().any{task->task.title==title}})
    }
    @Test fun editingDeadlinePreservesFixedInstantAndRelativeIsRecalculated() {
        ready();val id=UUID.randomUUID().toString();owned.add(id);val date=LocalDate.now().plusDays(3)
        val fixed=Instant.now().plusSeconds(86400).toEpochMilli()
        data{TaskRepository(it).save(TaskDraft(id,"已有提醒-${id.take(6)}",dueDate=date,zoneId="Asia/Hong_Kong",reminders=listOf(
            ReminderSpec("fixed-$id",ReminderKind.FIXED,"Asia/Hong_Kong",fixedAt=fixed),
            ReminderSpec("relative-$id",ReminderKind.RELATIVE_DUE,"Asia/Hong_Kong",offsetMinutes=60))))};reload()
        compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_$id"));compose.onNodeWithTag("task_$id").performClick()
        compose.onNode(hasText("編輯") and hasAnyAncestor(hasTestTag("task_$id"))).performClick()
        compose.onNodeWithTag("task_date").performTextReplacement(date.plusDays(2).toString());keyboard()
        compose.onNodeWithTag("save_task").performClick()
        compose.waitUntil(10_000){data{it.dao().task(id)!!.dueDate==date.plusDays(2)}}
        assertEquals(fixed,data{it.reminders().get("fixed-$id")!!.fixedAt})
        assertNull(data{it.dao().task(id)!!.dueTime})
    }
    @Test fun unitMenuRotationAndOverflowRejectBeforeSavingThenValidHoursPersist() {
        ready();val title="單位選單-${UUID.randomUUID()}"
        compose.onNodeWithTag("add_task").performClick();compose.onNodeWithTag("task_title").performTextInput(title)
        compose.onNodeWithTag("task_date").performTextInput(LocalDate.now().plusDays(4).toString());keyboard()
        compose.onNodeWithTag("add_reminder").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_relative_0").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_unit_0").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_unit_0_DAYS").performClick()
        compose.onNodeWithTag("reminder_offset_0").performTextReplacement("999999999999999999999");keyboard()
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("editor_error").performScrollTo().assertTextContains("超出範圍",substring=true)
        assertFalse(data{it.dao().allTasks().any{task->task.title==title}})
        compose.onNodeWithTag("reminder_offset_0").performScrollTo().performTextReplacement("2");keyboard()
        compose.onNodeWithTag("reminder_unit_0").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_unit_0_HOURS").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("reminder_offset_0").performScrollTo().assertTextContains("2")
        compose.onNodeWithTag("reminder_unit_0").assertTextContains("小時 ▾")
        compose.onNodeWithTag("reminder_base_0").performScrollTo()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /data/local/tmp/todo-051-unit-menu-qa.png").use { d -> java.io.FileInputStream(d.fileDescriptor).use { it.readBytes() } }
        compose.onNodeWithTag("save_task").performClick()
        compose.waitUntil(10_000){data{it.dao().allTasks().any{task->task.title==title}}}
        val task=data{it.dao().allTasks().first{task->task.title==title}};owned.add(task.id)
        assertEquals(120,data{it.reminders().forTask(task.id).single().offsetMinutes})
    }
    @Test fun blankFixedTimeUsesDeadlineOrEditableBaseWithAndWithoutTaskDate() {
        ready()
        listOf("timed", "date-only", "undated").forEach { mode ->
            val title="留空-$mode-${UUID.randomUUID()}";val day=LocalDate.now().plusDays(3)
            compose.onNodeWithTag("add_task").performClick();compose.onNodeWithTag("task_title").performTextInput(title)
            if(mode!="undated") compose.onNodeWithTag("task_date").performTextInput(day.toString())
            if(mode=="timed") compose.onNodeWithTag("task_time").performTextInput("1830")
            keyboard();compose.onNodeWithTag("add_reminder").performScrollTo().performClick()
            compose.onNodeWithTag("reminder_time_0").performScrollTo().assertTextContains("提醒時間（選填）")
            compose.onNodeWithTag("reminder_date_0").performScrollTo().performTextReplacement(day.toString());keyboard()
            if(mode!="timed") { compose.onNodeWithTag("reminder_base_0").performScrollTo().performTextReplacement("1715");keyboard() }
            compose.activityRule.scenario.recreate();keyboard()
            compose.onNodeWithTag("reminder_preview_0").performScrollTo().assertTextContains(if(mode=="timed") "18:30" else "17:15",substring=true)
            compose.onNodeWithTag("save_task").performClick()
            try {
                compose.waitUntil(10_000){data{it.dao().allTasks().any{task->task.title==title}}}
            } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
                throw AssertionError("Fixed reminder not saved for $mode: " + compose.onRoot(useUnmergedTree=true).printToString(), e)
            }
            val task=data{it.dao().allTasks().first{task->task.title==title}};owned.add(task.id)
            val reminder=data{it.reminders().forTask(task.id).single()}
            val expected=day.atTime(if(mode=="timed") LocalTime.of(18,30) else LocalTime.of(17,15)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            assertEquals(expected,reminder.fixedAt);assertEquals(expected,reminder.resolvedAt)
            if(mode!="timed") assertNull(task.dueTime)
            if(mode=="undated") assertNull(task.dueDate)
        }
    }

}
