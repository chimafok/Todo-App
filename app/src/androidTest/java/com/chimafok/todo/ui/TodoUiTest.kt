package com.chimafok.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimafok.todo.MainActivity
import com.chimafok.todo.data.TodoDatabase
import com.chimafok.todo.domain.TaskStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.TimeZone
import java.time.ZoneId
import com.chimafok.todo.domain.dueInstant
import com.chimafok.todo.domain.UNCATEGORIZED_ID
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class TodoUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val names = mutableListOf<String>()
    private val organizationIds = mutableListOf<Pair<String, Boolean>>()
    private fun name(label: String = "QA") = "$label-${UUID.randomUUID().toString().take(8)}".also(names::add)

    private fun capture(file: String) {
        compose.waitForIdle()
        // Shell-owned temporary evidence survives QA's automatic uninstall. No
        // screenshots or sample tasks are written into the user's app/database.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/todo-motion-$file.png").use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
    }

    private fun ready() {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun navigate(destination: String) {
        compose.onNodeWithTag("open_menu").performClick()
        compose.onNodeWithTag("nav_$destination").performClick()
        compose.waitForIdle()
    }
    private fun editable(tag: String, expected: String) {
        compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)))
    }
    private fun saveEditor() {
        // Compose idleness does not include the system IME's moving inset animation.
        // The editor lives in a Dialog; targeting the Activity can pick a root
        // without focus after dismissing the category popup.
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
        compose.waitForIdle()
        compose.onNodeWithTag("save_task").performClick()
    }
    private fun editorClosed() {
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_title").fetchSemanticsNodes().isEmpty() }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Editor did not close: " + compose.onRoot(useUnmergedTree=true).printToString(), e)
        }
    }
    private fun taskVisible(title: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun reveal(title: String, action: String) {
        if (compose.onAllNodesWithText(action).fetchSemanticsNodes().isEmpty()) compose.onNodeWithText(title).performClick()
    }

    @After fun cleanupOnlyTestTasks() {
        if (names.isEmpty() && organizationIds.isEmpty()) return
        val db = TodoDatabase.create(compose.activity)
        try {
            names.forEach { title ->
                db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE title = ?", arrayOf(title))
            }
            organizationIds.filter { it.second }.forEach { (id, _) -> db.openHelper.writableDatabase.execSQL("DELETE FROM tags WHERE id = ?", arrayOf(id)) }
            organizationIds.filterNot { it.second }.forEach { (id, _) -> db.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE id = ?", arrayOf(id)) }
        } finally { db.close() }
    }

    @Test fun drawerHasAllSixDestinationsAndForegroundReturnsHome() {
        ready()
        for (destination in listOf("UNSCHEDULED", "COMPLETED", "ORGANIZE", "TRASH", "SETTINGS")) {
            navigate(destination)
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        ready()
        compose.onNodeWithTag("page_title").assertTextEquals("日程")
        navigate("COMPLETED")
        compose.onNodeWithText("重複紀錄").performClick()
        compose.onNodeWithText("還沒有重複紀錄").assertIsDisplayed()
        compose.onNodeWithText("已完成任務").performClick()
        compose.onNodeWithText("還沒有重複紀錄").assertDoesNotExist()
    }

    @Test fun quickAddValidationNextAndSavedFormSurvivesRotation() {
        ready()
        val title = name()
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithText("展開詳細設定").assertDoesNotExist()
        compose.onNodeWithText("收起詳細設定").assertDoesNotExist()
        compose.onNodeWithTag("task_date").assertExists()
        compose.onNodeWithTag("task_notes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("task_title").performScrollTo()
        saveEditor()
        compose.onNodeWithTag("editor_error").assertExists()
        compose.onNodeWithTag("task_title").performTextInput(title)
        compose.activityRule.scenario.recreate()
        editable("task_title", title)
        compose.onNodeWithTag("save_next").performClick()
        compose.waitUntil(10_000) { compose.onNodeWithTag("task_title").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty() }
        editable("task_title", "")
        compose.onNodeWithText("取消").performClick()
        val db = TodoDatabase.create(compose.activity)
        try {
            val tasks = runBlocking { db.dao().tasks().first().filter { it.title == title } }
            assertEquals(1, tasks.size)
            assertNull(tasks.single().dueDate)
        } finally { db.close() }
    }

    @Test fun dateContextDetailsAndCrudUsePersistentTasks() {
        ready()
        val title = name()
        val renamed = name()
        val date = LocalDate.now().toString()
        compose.onNodeWithTag("add_$date").performClick()
        compose.onNodeWithTag("task_title").performTextInput(title)
        editable("task_date", date)
        compose.onNodeWithTag("task_time").performTextInput("23:59")
        compose.onNodeWithTag("task_notes").performScrollTo().performTextInput("第一行\nhttps://example.com")
        saveEditor()
        editorClosed()
        taskVisible(title)
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText("編輯").performClick()
        compose.onNodeWithTag("task_title").performTextReplacement(renamed)
        saveEditor()
        editorClosed()
        taskVisible(renamed)
        val db = TodoDatabase.create(compose.activity)
        try {
            val task = runBlocking { db.dao().tasks().first().single { it.title == renamed } }
            assertEquals(LocalDate.parse(date), task.dueDate)
            assertEquals("23:59", task.dueTime.toString())
            compose.onNodeWithTag("complete_${task.id}").performClick()
            navigate("COMPLETED")
            taskVisible(renamed)
            reveal(renamed, "重新開啟")
            compose.onNodeWithText("重新開啟").performClick()
            navigate("AGENDA")
            taskVisible(renamed)
            reveal(renamed, "刪除")
            compose.onNodeWithText("刪除").performClick()
            compose.onNodeWithText("移至垃圾桶").performClick()
            navigate("TRASH")
            taskVisible(renamed)
            reveal(renamed, "復原")
            compose.onNodeWithText("復原").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(renamed).fetchSemanticsNodes().isEmpty() }
            val restored = runBlocking { db.dao().task(task.id)!! }
            assertNull(restored.deletedAt)
            assertEquals(TaskStatus.OPEN, restored.status)
        } finally { db.close() }
    }

    @Test fun datePickerUsesSundayAsFirstDay() {
        ready()
        compose.onNodeWithContentDescription("跳至日期").performClick()
        compose.onNodeWithTag("date_picker").assertExists()
        val sunday = compose.onNodeWithText("日", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val monday = compose.onNodeWithText("一", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Sunday must precede Monday", sunday.left < monday.left)
        val today = LocalDate.now()
        val lastDayLabel = today.withDayOfMonth(today.lengthOfMonth())
            .format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.TAIWAN))
        // Identify the full calendar date; ICU weekday wording/spacing varies by Android version.
        val lastDayBounds = compose.onNodeWithText(lastDayLabel, substring = true, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val confirmBounds = compose.onNodeWithTag("confirm_date").getUnclippedBoundsInRoot()
        assertTrue("Calendar's final row must fit above dialog actions", lastDayBounds.bottom <= confirmBounds.top)
        compose.onNodeWithTag("confirm_date").performClick()
        compose.onNodeWithTag("date_picker").assertDoesNotExist()
    }

    @Test fun timePickerUses24HoursAndCancelDoesNotChangeTaskTime() {
        ready()
        compose.onNodeWithTag("add_${LocalDate.now()}").performClick()
        compose.onNodeWithTag("task_time").performTextInput("23:59")
        compose.onNodeWithTag("open_time").performClick()
        compose.onNodeWithText("24 小時制").assertIsDisplayed()
        fun inputs() = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("time_picker")))
        inputs().assertCountEquals(2)
        inputs()[0].performTextReplacement("06")
        inputs()[1].performTextReplacement("05")
        compose.onNodeWithTag("cancel_time").performClick()
        editable("task_time", "23:59")
        compose.onNodeWithTag("open_time").performClick()
        inputs()[0].performTextReplacement("06")
        inputs()[1].performTextReplacement("05")
        compose.onNodeWithTag("confirm_time").performClick()
        editable("task_time", "06:05")
        compose.onNodeWithText("取消").performClick()
    }

    @Test fun assignmentUsesExplicitStatusAndUnsubmittedIsStillPending() {
        ready()
        val general = name("整理本週待辦")
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput(general)
        saveEditor()
        editorClosed()
        taskVisible(general)
        val title = name("完成報告並提交")
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput(title)
        compose.onNodeWithText("分類：未分類").performScrollTo().performClick()
        compose.onNodeWithText("作業", substring = false).performClick()
        saveEditor()
        editorClosed()
        taskVisible(title)
        val db = TodoDatabase.create(compose.activity)
        try {
            val task = runBlocking { db.dao().tasks().first().single { it.title == title } }
            val normalTask = runBlocking { db.dao().tasks().first().single { it.title == general } }
            val assignmentTitle = compose.onNodeWithTag("task_title_${task.id}", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val normalTitle = compose.onNodeWithTag("task_title_${normalTask.id}", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals("Both task types must share a title column", normalTitle.left.value, assignmentTitle.left.value, 0.5f)
            val statusControl = compose.onNodeWithTag("status_${task.id}")
            statusControl.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "未開始"))
            statusControl.performClick()
            TaskStatus.entries.filter { it != TaskStatus.OPEN }.forEach {
                compose.onNodeWithTag("choose_status_${it.name}").assertIsDisplayed()
            }
            capture("status-picker")
            compose.onNodeWithText("取消").performClick()
            assertEquals(TaskStatus.NOT_STARTED, runBlocking { db.dao().task(task.id)!!.status })
            for (status in listOf(TaskStatus.IN_PROGRESS, TaskStatus.DONE_UNSUBMITTED)) {
                statusControl.performClick()
                compose.onNodeWithTag("choose_status_${status.name}").performClick()
                compose.waitUntil(10_000) { runBlocking { db.dao().task(task.id)!!.status == status } }
                statusControl.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, statusLabel(status)))
            }
            assertNull(runBlocking { db.dao().task(task.id)!!.completedAt })
            capture("aligned-tasks")
            compose.onNodeWithTag("complete_${task.id}").assertDoesNotExist()
            reveal(title, "推進至完成")
            compose.onNodeWithText("推進至完成").performClick()
            navigate("COMPLETED")
            taskVisible(title)
            compose.onNodeWithTag("status_${task.id}").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "完成"))
            reveal(title, "重新開啟")
            compose.onNodeWithText("重新開啟").performClick()
            compose.onNodeWithText("進行中", substring = false).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() }
            val reopened = runBlocking { db.dao().task(task.id)!! }
            assertEquals(TaskStatus.IN_PROGRESS, reopened.status)
            assertNull(reopened.completedAt)
        } finally { db.close() }
    }

    @Test fun completionFeedbackFollowsCommitAndThenRemovesTheRow() {
        ready()
        val title = name()
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput(title)
        saveEditor()
        editorClosed()
        taskVisible(title)
        val db = TodoDatabase.create(compose.activity)
        try {
            val task = runBlocking { db.dao().tasks().first().single { it.title == title } }
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("complete_${task.id}").performClick()
            compose.waitUntil(10_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithTag("complete_${task.id}").fetchSemanticsNodes().any {
                    it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On
                }
            }
            val committed = runBlocking { db.dao().task(task.id)!! }
            assertEquals(TaskStatus.COMPLETED, committed.status)
            assertNotNull(committed.completedAt)
            compose.onNodeWithTag("complete_${task.id}").assertIsOn().assertIsNotEnabled()
            compose.mainClock.advanceTimeBy(160)
            capture("completion-feedback")
            compose.mainClock.advanceTimeBy(1_000)
            compose.mainClock.autoAdvance = true
            compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() }
            navigate("COMPLETED")
            taskVisible(title)
        } finally {
            compose.mainClock.autoAdvance = true
            db.close()
        }
    }

    @Test fun failedCompletionKeepsPendingTaskAndDoesNotShowSuccess() {
        ready()
        val title = name()
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput(title)
        saveEditor()
        editorClosed()
        taskVisible(title)
        val db = TodoDatabase.create(compose.activity)
        try {
            val task = runBlocking { db.dao().tasks().first().single { it.title == title } }
            // Simulate a newer writer while the displayed row still has its old
            // revision. This is deliberately outside Room's observed transaction.
            db.openHelper.writableDatabase.execSQL("UPDATE tasks SET revision = revision + 1 WHERE id = ?", arrayOf(task.id))
            compose.onNodeWithTag("complete_${task.id}").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("資料已更新，請重試").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("資料已更新，請重試").assertIsDisplayed()
            compose.onNodeWithTag("complete_${task.id}").assertIsOff()
            compose.onNodeWithText(title).assertIsDisplayed()
            val unchanged = runBlocking { db.dao().task(task.id)!! }
            assertEquals(TaskStatus.OPEN, unchanged.status)
            assertNull(unchanged.completedAt)
        } finally { db.close() }
    }

    @Test fun compactInputCapturesEntryZoneAndNotesEditDoesNotReinterpretDeadline() {
        val originalZone = TimeZone.getDefault()
        val title = name("跨時區截止")
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Hong_Kong"))
            ready()
            val date = LocalDate.now(ZoneId.of("Asia/Hong_Kong")).plusDays(3)
            compose.onNodeWithTag("add_task").performClick()
            compose.onNodeWithTag("task_title").performTextInput(title)
            compose.onNodeWithTag("task_date").performTextInput("230229")
            saveEditor()
            compose.onNodeWithTag("editor_error").assertExists()
            val compactDate = date.format(DateTimeFormatter.ofPattern("yyMMdd"))
            compose.onNodeWithTag("task_date").performTextReplacement(compactDate)
            compose.onNodeWithTag("task_time").performTextInput("2400")
            saveEditor()
            compose.onNodeWithTag("editor_error").assertExists()
            compose.onNodeWithTag("task_time").performTextReplacement("0935")
            compose.activityRule.scenario.recreate()
            editable("task_date", compactDate)
            editable("task_time", "0935")
            compose.onNodeWithTag("deadline_zone").assertTextContains("Asia/Hong_Kong", substring = true)
            saveEditor()
            editorClosed()
            val db = TodoDatabase.create(compose.activity)
            try {
                val saved = runBlocking { db.dao().tasks().first().single { it.title == title } }
                assertEquals(date, saved.dueDate)
                assertEquals("09:35", saved.dueTime.toString())
                assertEquals("Asia/Hong_Kong", saved.zoneId)
                val instant = saved.dueInstant()
                TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
                compose.activityRule.scenario.recreate()
                ready()
                compose.onNodeWithTag("task_list").performScrollToNode(hasText(title))
                compose.onNodeWithText("原設定", substring = true).assertIsDisplayed()
                reveal(title, "編輯")
                compose.onNodeWithText("編輯").performClick()
                compose.onNodeWithTag("deadline_zone").assertTextContains("Asia/Hong_Kong", substring = true)
                compose.onNodeWithTag("task_notes").performScrollTo().performTextInput("跨區只改備註")
                saveEditor()
                editorClosed()
                val edited = runBlocking { db.dao().task(saved.id)!! }
                assertEquals(instant, edited.dueInstant())
                assertEquals("Asia/Hong_Kong", edited.zoneId)
                compose.onNodeWithTag("task_list").performScrollToNode(hasText(title))
                reveal(title, "編輯")
                compose.onNodeWithText("編輯").performClick()
                compose.onNodeWithTag("task_time").performTextReplacement("1010")
                compose.onNodeWithTag("deadline_zone").assertTextContains("America/Los_Angeles", substring = true)
                saveEditor()
                editorClosed()
                val reentered = runBlocking { db.dao().task(saved.id)!! }
                assertEquals("America/Los_Angeles", reentered.zoneId)
                assertEquals("10:10", reentered.dueTime.toString())
                assertNotEquals(instant, reentered.dueInstant())
            } finally { db.close() }
        } finally { TimeZone.setDefault(originalZone) }
    }

    @Test fun pastDeadlineWarningCancelsWithoutWritingAndConfirmNextSurvivesRotation() {
        ready()
        val title = name("過去截止確認")
        val today = LocalDate.now()
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_date").performClick()
        compose.onNodeWithText("YYYY-MM-DD").assertExists()
        compose.onNodeWithTag("task_title").performTextInput(title)
        compose.onNodeWithTag("task_date").performTextInput(today.toString())
        compose.onNodeWithTag("task_time").performClick()
        compose.onNodeWithText("HH:mm").assertExists()
        compose.onNodeWithTag("task_time").performTextInput("0000")
        saveEditor()
        compose.onNodeWithTag("past_deadline_warning").assertExists()
        val db = TodoDatabase.create(compose.activity)
        try {
            assertTrue(runBlocking { db.dao().tasks().first().none { it.title == title } })
            capture("past-warning")
            compose.onNodeWithTag("cancel_past_deadline").performClick()
            editable("task_title", title)
            editable("task_time", "0000")
            compose.onNodeWithTag("save_next").performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("past_deadline_warning").assertExists()
            compose.onNodeWithTag("confirm_past_deadline").performClick()
            compose.waitUntil(10_000) { compose.onNodeWithTag("task_title").fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty() }
            editable("task_title", "")
            compose.onNodeWithText("取消", substring = false).performClick()
            val saved = runBlocking { db.dao().tasks().first().filter { it.title == title } }
            assertEquals(1, saved.size)
            assertEquals(today, saved.single().dueDate)
            assertEquals(java.time.LocalTime.MIDNIGHT, saved.single().dueTime)
        } finally { db.close() }
    }

    @Test fun sameDayMonthDayAndPastTimeRollTogetherAndSurviveRotation() {
        ready()
        val today = LocalDate.now()
        val title = name("同日過去時間跨年")
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput(title)
        compose.onNodeWithTag("task_date").performTextInput(today.format(DateTimeFormatter.ofPattern("MMdd")))
        compose.onNodeWithTag("task_time").performTextInput("0000")
        val nextYear = java.time.LocalDate.of(today.year + 1, today.month, today.dayOfMonth)
        compose.onNodeWithText(nextYear.toString()).assertExists()
        capture("same-day-year")
        compose.onNodeWithText("00:00").assertExists()
        compose.onNodeWithTag("task_time").performTextReplacement("2359")
        compose.onNodeWithText(today.toString()).assertExists()
        compose.onNodeWithTag("task_time").performTextReplacement("0000")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(nextYear.toString()).assertExists()
        saveEditor()
        editorClosed()
        val db = TodoDatabase.create(compose.activity)
        try {
            val saved = runBlocking { db.dao().tasks().first().single { it.title == title } }
            assertEquals(nextYear, saved.dueDate)
            assertEquals(java.time.LocalTime.MIDNIGHT, saved.dueTime)
        } finally { db.close() }
    }

    @Test fun monthDayRollsForwardButExplicitPastDateNeedsConfirmation() {
        ready()
        val title = name("簡寫跨年")
        val pastTitle = name("指定過去日期")
        val today = LocalDate.now()
        compose.onNodeWithTag("add_task").performClick()
        compose.onNodeWithTag("task_title").performTextInput(title)
        compose.onNodeWithTag("task_date").performTextInput("0101")
        val resolved = LocalDate.of(today.year + if (today.dayOfYear > 1) 1 else 0, 1, 1)
        compose.onNodeWithText(resolved.toString(), substring = false).assertExists()
        saveEditor()
        editorClosed()
        val db = TodoDatabase.create(compose.activity)
        try {
            val saved = runBlocking { db.dao().tasks().first().single { it.title == title } }
            assertEquals(resolved, saved.dueDate)
            assertNull(saved.dueTime)
            compose.onNodeWithTag("add_task").performClick()
            compose.onNodeWithTag("task_title").performTextInput(pastTitle)
            compose.onNodeWithTag("task_date").performTextInput(today.minusDays(1).toString())
            androidx.test.espresso.Espresso.closeSoftKeyboard(); compose.waitForIdle()
            saveEditor()
            compose.onNodeWithTag("past_deadline_warning").assertExists()
            compose.onNodeWithTag("cancel_past_deadline").performClick()
            assertTrue(runBlocking { db.dao().tasks().first().none { it.title == pastTitle } })
            // Compose owns the modal; avoid selecting the unfocused base window
            // through Espresso while Android hands focus back to the editor.
            compose.waitForIdle()
            compose.onNodeWithTag("task_date").assertTextContains(today.minusDays(1).toString())
            saveEditor()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("confirm_past_deadline").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("confirm_past_deadline").performClick()
            editorClosed()
            val past = runBlocking { db.dao().tasks().first().single { it.title == pastTitle } }
            assertEquals(today.minusDays(1), past.dueDate)
            assertNull(past.dueTime)
        } finally { db.close() }
    }

    @Test fun manageCategoriesAndMultipleTagsWithoutDeletingTasks() {
        ready()
        val categoryName = name("專題")
        val tagName = name("優先")
        val secondTag = name("閱讀")
        val title = name("分類與標籤")
        navigate("ORGANIZE")
        fun add(label: String) {
            compose.onNodeWithTag("org_add").performClick()
            compose.onNodeWithTag("org_name").performTextInput(label)
            compose.onNodeWithTag("org_save").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("org_name").fetchSemanticsNodes().isEmpty() }
        }
        add(categoryName)
        val db = TodoDatabase.create(compose.activity)
        try {
            val category = runBlocking { db.dao().allCategories().single { it.name == categoryName } }
            organizationIds += category.id to false
            compose.onNodeWithText("標籤", substring = false).performClick()
            add(tagName)
            add(secondTag)
            val tags = runBlocking { db.dao().allTags() }
            val first = tags.single { it.name == tagName }
            val second = tags.single { it.name == secondTag }
            organizationIds += listOf(first.id to true, second.id to true)
            compose.onNodeWithTag("org_menu_${second.id}").performClick()
            compose.onNodeWithText("上移").performClick()
            compose.waitUntil(10_000) { runBlocking { db.dao().allTags().first().id == second.id } }
            navigate("AGENDA")
            compose.onNodeWithTag("add_task").performClick()
            compose.onNodeWithTag("task_title").performTextInput(title)
            compose.onNodeWithText("分類：未分類").performScrollTo().performClick()
            compose.onNodeWithText(categoryName).performClick()
            compose.onNodeWithTag("editor_tag_${first.id}").performScrollTo().performClick()
            compose.onNodeWithTag("editor_tag_${second.id}").performScrollTo().performClick()
            saveEditor()
            editorClosed()
            taskVisible(title)
            val task = runBlocking { db.dao().tasks().first().single { it.title == title } }
            assertEquals(category.id, task.categoryId)
            assertEquals(setOf(first.id, second.id), runBlocking { db.dao().taskTagIds(task.id).toSet() })
            navigate("ORGANIZE")
            compose.onNodeWithText("標籤", substring = false).performClick()
            compose.onNodeWithTag("org_menu_${first.id}").performClick()
            compose.onNodeWithText("刪除", substring = false).performClick()
            compose.onNodeWithTag("org_delete_confirm").performClick()
            compose.waitUntil(10_000) { runBlocking { db.dao().tag(first.id) == null } }
            assertEquals(listOf(second.id), runBlocking { db.dao().taskTagIds(task.id) })
            compose.onNodeWithText("分類", substring = false).performClick()
            compose.onNodeWithTag("org_menu_${category.id}").performScrollTo().performClick()
            compose.onNodeWithText("刪除", substring = false).performClick()
            compose.onNodeWithText("取消", substring = false).performClick()
            assertEquals(category.id, runBlocking { db.dao().task(task.id)!!.categoryId })
            compose.onNodeWithTag("org_menu_${category.id}").performClick()
            compose.onNodeWithText("刪除", substring = false).performClick()
            compose.onNodeWithTag("org_delete_confirm").performClick()
            compose.waitUntil(10_000) { runBlocking { db.dao().category(category.id) == null } }
            assertEquals(UNCATEGORIZED_ID, runBlocking { db.dao().task(task.id)!!.categoryId })
            assertEquals(listOf(second.id), runBlocking { db.dao().taskTagIds(task.id) })
            navigate("AGENDA")
            taskVisible(title)
            capture("organization-task")
        } finally { db.close() }
    }
}
