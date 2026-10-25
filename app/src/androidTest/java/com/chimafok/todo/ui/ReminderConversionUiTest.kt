package com.chimafok.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

@RunWith(AndroidJUnit4::class)
class ReminderConversionUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val owned=mutableListOf<String>()
    private fun <T> data(block:suspend(TodoDatabase)->T):T=runBlocking{block(ReminderRuntime.database)}
    private fun ready(){compose.waitUntil(20_000){compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty()}}
    private fun closed(){compose.waitUntil(15_000){compose.onAllNodesWithTag("task_title").fetchSemanticsNodes().isEmpty()}}
    private fun keyboard(){androidx.test.espresso.Espresso.closeSoftKeyboard();compose.waitForIdle()}
    private fun edit(id:String){
        lateinit var model: TodoViewModel
        compose.runOnIdle { model = androidx.lifecycle.ViewModelProvider(compose.activity)[TodoViewModel::class.java] }
        compose.waitUntil(10_000) { model.state.value.tasks.any { it.id == id } }
        compose.waitForIdle()
        compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_$id"))
        val edit = hasText("編輯") and hasAnyAncestor(hasTestTag("task_$id"))
        if (compose.onAllNodes(edit).fetchSemanticsNodes().isEmpty()) compose.onNodeWithTag("task_$id").performClick()
        compose.waitUntil(10_000) { runCatching { compose.onAllNodes(edit).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        compose.onNode(edit).performScrollTo().assertIsDisplayed().performClick()
        // Compose idleness does not wait for the asynchronous Room detail query.
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("task_title").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun seed():Pair<TaskEntity,List<ReminderEntity>> {
        val id=UUID.randomUUID().toString().also{owned+=it};val day=LocalDate.now().plusDays(4)
        val fixed=day.minusDays(1).atTime(18,30).atZone(ZoneId.of("Asia/Hong_Kong")).toInstant().toEpochMilli()
        return data{db->TaskRepository(db).save(TaskDraft(id,"沿用提醒-${id.take(6)}",dueDate=day,dueTime=LocalTime.of(20,0),zoneId="Asia/Hong_Kong",
            reminders=listOf(ReminderSpec("fixed-$id",ReminderKind.FIXED,"UTC",fixedAt=fixed),
                ReminderSpec("relative-$id",ReminderKind.RELATIVE_DUE,"Asia/Hong_Kong",offsetMinutes=90))))
            db.dao().task(id)!! to db.reminders().forTask(id)}
    }
    @After fun cleanup(){data{db->db.withTransaction{owned.forEach{id->
        val ids=(db.dao().seriesOccurrences(id).map{it.taskId}+id).distinct();val gateway=AndroidReminderGateway(compose.activity)
        ids.forEach{task->db.reminders().forTask(task).forEach{r->r.alarmKey?.let{gateway.cancel(r,it)};gateway.dismiss(r.id)}}
        db.openHelper.writableDatabase.execSQL("DELETE FROM occurrences WHERE seriesId=?",arrayOf(id))
        ids.forEach{db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id=?",arrayOf(it))}
        db.openHelper.writableDatabase.execSQL("DELETE FROM recurring_series WHERE id=?",arrayOf(id))
    }}}}
    @Test fun existingReminderConversionPreviewCancelRotationAndNextOccurrencePreserveFirst() {
        ready();val (task,reminders)=seed();edit(task.id)
        compose.onNodeWithTag("repeat_WEEKLY").performScrollTo().performClick();keyboard()
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("editor_error").performScrollTo().assertTextContains("沿用單筆提醒",substring=true)
        assertNull(data{it.dao().series(task.id)})
        compose.onNodeWithTag("inherit_single_reminders").performScrollTo().performClick()
        compose.onNodeWithTag("series_reminder_quantity_0").performScrollTo().assertTextContains("90")
        compose.onNodeWithTag("series_reminder_time_1").performScrollTo().assertTextContains("18:30")
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("convert_series_preview").assertExists()
        compose.onNodeWithTag("cancel_convert_series").performClick()
        assertNull(data{it.dao().series(task.id)});assertEquals(task,data{it.dao().task(task.id)})
        compose.onNodeWithTag("save_task").performClick();compose.activityRule.scenario.recreate();keyboard()
        compose.onNodeWithTag("convert_series_preview").assertExists()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/todo-053-conversion-preview.png").use{fd->java.io.FileInputStream(fd.fileDescriptor).use{it.readBytes()}}
        compose.onNodeWithTag("confirm_convert_series").performClick();closed()
        assertTrue(data{it.reminders().overridden(task.id)})
        assertEquals(reminders.map{it.spec()}.toSet(),data{it.reminders().forTask(task.id).filter{r->r.enabled}.map{it.spec()}.toSet()})
        val saved=data{it.dao().task(task.id)!!};assertEquals(task.createdAt,saved.createdAt)
        data{TaskRepository(it).setStatus(task.id,saved.revision,TaskStatus.COMPLETED)}
        val next=data{it.reminders().forTask(occurrenceId(task.id,2))}
        assertEquals(2,next.count{it.enabled})
        assertEquals(reminders.single{it.kind=="FIXED"}.fixedAt!!+7*86_400_000L,next.single{it.kind=="FIXED"}.fixedAt)
    }
    @Test fun newDraftCanInheritAndSwitchBackWithoutLosingOriginalInput() {
        ready();val title="新增並沿用-${UUID.randomUUID()}"
        compose.onNodeWithTag("add_task").performClick();compose.onNodeWithTag("task_title").performTextInput(title)
        compose.onNodeWithTag("task_date").performTextInput(LocalDate.now().plusDays(4).toString());keyboard()
        compose.onNodeWithTag("add_reminder").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_time_0").performScrollTo().performTextReplacement("1430");keyboard()
        compose.onNodeWithTag("repeat_WEEKLY").performScrollTo().performClick()
        compose.onNodeWithTag("inherit_single_reminders").performScrollTo().performClick()
        compose.onNodeWithTag("repeat_NONE").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_time_0").performScrollTo().assertTextContains("1430")
        compose.onNodeWithTag("repeat_WEEKLY").performScrollTo().performClick()
        compose.activityRule.scenario.recreate();keyboard()
        compose.onNodeWithTag("save_task").performClick();compose.onNodeWithTag("confirm_convert_series").performClick();closed()
        val series=data{it.dao().allSeries().single{r->r.title==title}};owned+=series.id
        val first=data{it.dao().seriesOccurrences(series.id).first()}
        assertEquals(series.id,first.taskId);assertTrue(data{it.reminders().overridden(first.taskId)})
        assertEquals(1,data{it.reminders().templatesFor(series.id).count{r->r.enabled}})
    }
    @Test fun deadlineChangesRequireReviewAndRemappingRetainsManualTemplatesWithoutDuplication() {
        ready();val(task,_)=seed();edit(task.id)
        compose.onNodeWithTag("repeat_WEEKLY").performScrollTo().performClick();keyboard()
        compose.onNodeWithTag("inherit_single_reminders").performScrollTo().performClick()
        compose.onNodeWithTag("add_series_reminder").performScrollTo().performClick()
        compose.onNodeWithTag("task_date").performScrollTo().performTextReplacement(task.dueDate!!.plusDays(1).toString());keyboard()
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("editor_error").performScrollTo().assertTextContains("沿用單筆提醒",substring=true)
        compose.onNodeWithTag("inherit_single_reminders").performScrollTo().performClick()
        compose.onNodeWithTag("inherit_single_reminders").performScrollTo().performClick()
        compose.onNodeWithTag("series_reminder_row_2").performScrollTo().assertExists()
        compose.onNodeWithTag("series_reminder_row_3").assertDoesNotExist()
        compose.onNodeWithTag("save_task").performClick();compose.onNodeWithTag("confirm_convert_series").performClick();closed()
        val rows=data{it.reminders().templatesFor(task.id).filter{r->r.enabled}}
        assertEquals(3,rows.size);assertEquals(-2,rows.single{it.kind=="OCCURRENCE_TIME"}.dayOffset)
        assertEquals(2,data{it.reminders().forTask(task.id).count{r->r.enabled}})
    }
}
