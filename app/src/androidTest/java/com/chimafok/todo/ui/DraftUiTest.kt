package com.chimafok.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.AndroidReminderGateway
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.TimeZone
import java.util.UUID

@com.chimafok.todo.Regression101
@RunWith(AndroidJUnit4::class)
class DraftUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val prefix="QA101-draft-${UUID.randomUUID().toString().take(8)}"
    private lateinit var db:TodoDatabase
    private val vm get()=ViewModelProvider(compose.activity)[TodoViewModel::class.java]
    @Before fun ready() {
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty()}
        assertEquals("com.chimafok.todo.qa",compose.activity.packageName)
        db=TodoDatabase.create(compose.activity)
        runBlocking {vm.awaitNewTaskDraft()}
    }
    @After fun cleanupOwned() {
        if(!::db.isInitialized)return
        try {runBlocking {
            if(vm.newTaskDraft?.title?.startsWith(prefix)==true)vm.discardNewTaskDraft()
            val gateway=AndroidReminderGateway(compose.activity)
            db.dao().allTasks().filter {it.title.startsWith(prefix)}.forEach {task ->
                db.reminders().forTask(task.id).forEach {r->r.alarmKey?.let{gateway.cancel(r,it)};gateway.dismiss(r.id)}
                db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id=?",arrayOf(task.id))
            }
        }} finally {db.close()}
    }
    private fun keyboard() {
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
        compose.waitForIdle()
    }
    private fun closed() {compose.waitUntil(10_000){compose.onAllNodesWithTag("task_title").fetchSemanticsNodes().isEmpty()}}
    private fun value(tag:String,text:String) {
        compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(text)))
    }
    @Test fun cancelAndBackAskAndContinueOrDiscardNeverCreateATask() {
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput("$prefix-exit")
        keyboard();compose.onNodeWithText("取消",substring=false).performClick()
        compose.onNodeWithTag("draft_exit_dialog").assertExists()
        compose.onNodeWithTag("continue_editing").performClick()
        value("task_title","$prefix-exit")
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("draft_exit_dialog").assertExists()
        compose.onNodeWithTag("discard_draft").performClick();closed()
        assertNull(NewTaskDraftStore(compose.activity.filesDir).read())
        assertTrue(runBlocking {db.dao().allTasks().none{it.title.startsWith(prefix)}})
    }
    @Test fun keptRawInputsReloadWithFreshViewModelAndOnlySavingCreatesTaskAndRemovesDraft() {
        val oldZone=TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Hong_Kong"))
            compose.onNodeWithTag("add_task").performClick()
            compose.onNodeWithTag("task_title").performTextInput("$prefix-keep")
            compose.onNodeWithTag("task_date").performScrollTo().performTextInput("1332")
            compose.onNodeWithTag("task_time").performTextInput("2")
            keyboard();compose.onNodeWithTag("task_notes").performScrollTo().performTextInput("第一行\n保留尚未完成的輸入")
            keyboard();compose.onNodeWithText("取消",substring=false).performClick()
            compose.onNodeWithTag("keep_draft").performClick();closed()
            compose.onNodeWithTag("resume_draft").assertExists()
            assertTrue(runBlocking {db.dao().allTasks().none{it.title.startsWith(prefix)}})
            val stored=NewTaskDraftStore(compose.activity.filesDir).read()!!
            assertEquals("1332",stored.date);assertEquals("2",stored.time);assertEquals("Asia/Hong_Kong",stored.zoneId)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            compose.runOnUiThread {compose.activity.viewModelStore.clear()}
            compose.activityRule.scenario.recreate()
            compose.waitUntil(20_000){compose.onAllNodesWithTag("resume_draft").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("resume_draft").performClick()
            value("task_title","$prefix-keep");value("task_date","1332");value("task_time","2")
            compose.onNodeWithTag("deadline_zone").assertTextContains("Asia/Hong_Kong",substring=true)
            compose.onNodeWithTag("task_date").performScrollTo().performTextReplacement(LocalDate.now().plusDays(2).toString())
            compose.onNodeWithTag("task_time").performTextReplacement("2359")
            keyboard();compose.onNodeWithTag("save_task").performClick();closed()
            assertNull(NewTaskDraftStore(compose.activity.filesDir).read())
            val task=runBlocking {db.dao().allTasks().single{it.title=="$prefix-keep"}}
            assertEquals(stored.id,task.id)
            assertEquals("第一行\n保留尚未完成的輸入",task.notes)
            assertEquals("America/New_York",task.zoneId)
            compose.onNodeWithTag("new_task_draft").assertDoesNotExist()
        } finally {TimeZone.setDefault(oldZone)}
    }
    @Test fun existingDraftRequiresAnExplicitChoiceBeforeStartingAnother() {
        val draft=NewTaskDraft(UUID.randomUUID().toString(),"$prefix-offer",zoneId=ZoneId.systemDefault().id)
        runBlocking {vm.keepNewTaskDraft(draft)}
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("open_saved_draft").assertExists()
        compose.onNodeWithText("取消",substring=false).performClick()
        assertEquals(draft,NewTaskDraftStore(compose.activity.filesDir).read())
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("discard_and_create").performClick()
        value("task_title","")
        assertNull(NewTaskDraftStore(compose.activity.filesDir).read())
        compose.onNodeWithText("取消",substring=false).performClick();closed()
        compose.onNodeWithTag("draft_exit_dialog").assertDoesNotExist()
    }
    @Test fun deletedCategoryAndTagsInADraftAreVisibleAndCanBeCorrectedBeforeSaving() {
        val draft=NewTaskDraft(UUID.randomUUID().toString(),"$prefix-stale",tagIds=listOf("gone-tag"),categoryId="gone-category",zoneId=ZoneId.systemDefault().id)
        runBlocking {vm.keepNewTaskDraft(draft)}
        compose.onNodeWithTag("resume_draft").performClick()
        compose.onNodeWithText("草稿中的部分分類或標籤已不存在",substring=true).assertExists()
        keyboard();compose.onNodeWithTag("save_task").performClick();closed()
        val task=runBlocking {db.dao().task(draft.id)!!}
        assertEquals(UNCATEGORIZED_ID,task.categoryId)
        assertTrue(runBlocking {db.dao().taskTagIds(task.id).isEmpty()})
        assertNull(NewTaskDraftStore(compose.activity.filesDir).read())
    }
    @Test fun aDraftLeftAfterAnAlreadyCommittedTaskIsRemovedOnFreshViewModelLoad() {
        val id=UUID.randomUUID().toString()
        val title="$prefix-already-committed"
        runBlocking {TaskRepository(db).save(TaskDraft(id,title))}
        NewTaskDraftStore(compose.activity.filesDir).save(NewTaskDraft(id,title,zoneId=ZoneId.systemDefault().id))
        compose.runOnUiThread {compose.activity.viewModelStore.clear()}
        compose.activityRule.scenario.recreate()
        runBlocking {vm.awaitNewTaskDraft()}
        assertNull(NewTaskDraftStore(compose.activity.filesDir).read())
        assertNull(vm.newTaskDraft)
        assertEquals(1,runBlocking {db.dao().allTasks().count {it.id==id}})
    }
}
