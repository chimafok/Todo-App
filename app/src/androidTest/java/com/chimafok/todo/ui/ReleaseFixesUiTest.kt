package com.chimafok.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.chimafok.todo.MainActivity
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReleaseFixesUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val prefix = "QA100-${UUID.randomUUID().toString().take(8)}"
    private lateinit var db: TodoDatabase
    @Before fun ready() {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("com.chimafok.todo.qa", compose.activity.packageName)
        db = TodoDatabase.create(compose.activity)
    }
    @After fun cleanupOwnedFixtures() {
        if (!::db.isInitialized) return
        try { runBlocking {
            val owned = db.dao().allSeries().filter { it.title.startsWith(prefix) }
            owned.forEach { s ->
                db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id IN (SELECT taskId FROM occurrences WHERE seriesId=?)", arrayOf(s.id))
                db.openHelper.writableDatabase.execSQL("DELETE FROM recurring_series WHERE id=?", arrayOf(s.id))
            }
            db.dao().allTasks().filter { it.title.startsWith(prefix) }.forEach {
                db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id=?", arrayOf(it.id))
            }
        } } finally { db.close() }
    }
    private fun navigate(value: String) {
        compose.onNodeWithTag("open_menu").performClick()
        compose.onNodeWithTag("nav_$value").performClick()
    }
    private fun closeEditor() {
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
        compose.waitForIdle()
        compose.onNodeWithTag("save_task").performClick()
        val errors = compose.onAllNodesWithTag("editor_error").fetchSemanticsNodes()
        assertTrue("Fixture form must validate before waiting for save: ${errors.map { it.config[SemanticsProperties.Text] }}",errors.isEmpty())
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_title").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun blankStartUsesTodayAndWeekdayOrderIsMondayThroughSunday() {
        val today = LocalDate.now()
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput("$prefix-weekdays")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("repeat_WEEKDAYS").performScrollTo().performClick()
        // Unclipped bounds check the rendered order, including rows below the viewport.
        val positions = (1..7).map { day ->
            compose.onNodeWithTag("repeat_weekday_$day").getUnclippedBoundsInRoot()
        }
        positions.zipWithNext().forEach { (a,b) -> assertTrue("Weekday bounds: $positions",b.top > a.top || (b.top == a.top && b.left > a.left)) }
        compose.onNodeWithTag("repeat_weekday_1").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("repeat_weekday_7").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("repeat_end_COUNT").performScrollTo().performClick()
        compose.onNodeWithTag("repeat_count").performScrollTo().performTextReplacement("3")
        compose.onNodeWithTag("task_date").performScrollTo().assertContentDescriptionContains("留空為今天", substring=true)
        compose.onNodeWithTag("task_time").performTextInput("2359")
        closeEditor()
        val series = runBlocking { db.dao().allSeries().single { it.title == "$prefix-weekdays" } }
        val rule = runBlocking { db.dao().latestRule(series.id)!! }
        assertEquals(today, rule.rule().anchor)
        assertEquals(setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.SUNDAY), rule.rule().weekdays)
        assertEquals(java.time.LocalTime.of(23,59), rule.dueTime)
        assertEquals(3, rule.maxCount)
    }

    @Test fun deadlineTimeStaysEditableWhileTypingPartialInput() {
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput("$prefix-partial-time")
        compose.onNodeWithTag("task_date").performScrollTo().performTextInput(LocalDate.now().plusDays(1).toString())
        for (digit in "2130") compose.onNodeWithTag("task_time").assertIsEnabled().performTextInput(digit.toString())
        compose.onNodeWithTag("task_time").assertIsEnabled()
        closeEditor()
        val task = runBlocking { db.dao().allTasks().single { it.title == "$prefix-partial-time" } }
        assertEquals(java.time.LocalTime.of(21,30),task.dueTime)
    }

    @Test fun drawerBrandReturnsHomeAndClearsExistingSearch() {
        navigate("SETTINGS")
        compose.onNodeWithTag("open_menu").performClick()
        compose.onNodeWithTag("drawer_home").performClick()
        compose.onNodeWithTag("page_title").assertTextEquals("日程")
        compose.onNodeWithTag("task_search").performTextReplacement("$prefix-no-match")
        compose.onNodeWithTag("task_search").performImeAction()
        compose.onNodeWithTag("open_menu").performClick()
        compose.onNodeWithTag("drawer_home").performClick()
        compose.onNodeWithTag("task_search").assertTextContains("")
        compose.onNodeWithTag("task_search").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithTag("add_task").assertExists()
    }

    @Test fun circularCompletionKeepsCheckboxSemanticsAndCanReopen() {
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput("$prefix-circle")
        closeEditor()
        val task = runBlocking { db.dao().allTasks().single { it.title == "$prefix-circle" } }
        compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_${task.id}"))
        compose.onNodeWithTag("complete_${task.id}").assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).performClick()
        compose.waitUntil(10_000) { runBlocking { db.dao().task(task.id)?.status == TaskStatus.COMPLETED } }
        navigate("COMPLETED")
        compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_${task.id}"))
        compose.onNodeWithTag("task_${task.id}").performClick()
        compose.onNodeWithText("重新開啟", substring=false).performClick()
        compose.waitUntil(10_000) { runBlocking { db.dao().task(task.id)?.status == TaskStatus.OPEN } }
    }

    @Test fun agendaFutureDeletionCancelsRejectsStaleAndPreservesTodayAndHistory() {
        val id = UUID.randomUUID().toString()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault().id
        runBlocking {
            TaskRepository(db).save(TaskDraft(id, "$prefix-series", dueDate=today.minusDays(1), zoneId=zone,
                recurrence=RecurrenceRule(today.minusDays(1),RecurrenceFrequency.DAILY,zone,maxCount=5)))
            val rule = db.dao().latestRule(id)!!
            val now = System.currentTimeMillis()
            for (n in 1..5) {
                val taskId = occurrenceId(id,n)
                if (db.dao().task(taskId) == null) {
                    db.dao().insert(TaskEntity(taskId,"$prefix-series","",UNCATEGORIZED_ID,today.plusDays(n-2L),zoneId=zone,createdAt=now,updatedAt=now))
                    db.dao().insertOccurrence(OccurrenceEntity(taskId,id,n,rule.id))
                }
                if (n == 1 || n == 5) db.dao().task(taskId)!!.let {
                    db.dao().update(it.copy(status=TaskStatus.COMPLETED,completedAt=now,revision=it.revision+1))
                }
            }
            // All five finite slots are materialized; keep the fixture's lifecycle
            // consistent so background reconciliation does not change its revision.
            db.dao().series(id)!!.let { db.dao().updateSeries(it.copy(cursor=5,lifecycle=SeriesLifecycle.ENDED,revision=it.revision+1)) }
        }
        compose.runOnUiThread { ViewModelProvider(compose.activity)[TodoViewModel::class.java].reload() }
        val current = occurrenceId(id,2)
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("task_$current").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task_$current"))
        compose.onNodeWithTag("task_$current").performClick()
        compose.onNodeWithTag("delete_future_$current").performScrollTo().performClick()
        compose.onNodeWithTag("cancel_delete_future").performClick()
        assertTrue(runBlocking { db.dao().seriesOccurrences(id).all { db.dao().task(it.taskId)!!.deletedAt == null } })
        compose.onNodeWithTag("delete_future_$current").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("confirm_delete_future").fetchSemanticsNodes().singleOrNull()?.config?.contains(SemanticsProperties.Disabled) == false }
        val frozenTaskRevision = ViewModelProvider(compose.activity)[TodoViewModel::class.java].seriesActionPreview!!.tasks.getValue(current)
        runBlocking { db.dao().task(current)!!.let { db.dao().update(it.copy(notes="concurrent fixture update",revision=it.revision+1)) } }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("confirm_delete_future").fetchSemanticsNodes().singleOrNull()?.config?.contains(SemanticsProperties.Disabled) == false }
        assertEquals(frozenTaskRevision,ViewModelProvider(compose.activity)[TodoViewModel::class.java].seriesActionPreview!!.tasks.getValue(current))
        compose.onNodeWithTag("confirm_delete_future").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("已更新", substring=true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { db.dao().seriesOccurrences(id).all { db.dao().task(it.taskId)!!.deletedAt == null } })
        compose.onNodeWithTag("cancel_delete_future").performClick()
        compose.onNodeWithTag("delete_future_$current").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("之後的 2 項", substring=true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("confirm_delete_future").performClick()
        compose.waitUntil(10_000) { runBlocking { db.dao().series(id)!!.lifecycle == SeriesLifecycle.STOPPED } }
        runBlocking {
            for(n in listOf(1,2,5)) assertNull(db.dao().task(occurrenceId(id,n))!!.deletedAt)
            for(n in listOf(3,4)) assertNotNull(db.dao().task(occurrenceId(id,n))!!.deletedAt)
            assertEquals(TaskStatus.COMPLETED,db.dao().task(occurrenceId(id,1))!!.status)
            assertEquals(TaskStatus.COMPLETED,db.dao().task(occurrenceId(id,5))!!.status)
            RecurringRepository(db).reconcileSeries(id)
            assertEquals(5,db.dao().seriesOccurrences(id).size)
        }
    }
}
