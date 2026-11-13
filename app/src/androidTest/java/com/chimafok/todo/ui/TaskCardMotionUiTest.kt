package com.chimafok.todo.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.data.TaskEntity
import com.chimafok.todo.domain.UNCATEGORIZED_ID
import com.chimafok.todo.ui.theme.TodoTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class TaskCardMotionUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun show() {
        assertEquals("com.chimafok.todo.qa", compose.activity.packageName)
        val task = TaskEntity("motion", "Synthetic motion task", "Details stay visible during collapse", UNCATEGORIZED_ID, createdAt = 1, updatedAt = 1)
        compose.runOnUiThread { compose.activity.setContent { TodoTheme {
            Column(Modifier.fillMaxSize()) {
                TaskCard(task, "Category", Clock.systemUTC(), false, true,
                    {}, {}, {}, {}, {}, {}, {})
            }
        } } }
        compose.waitForIdle()
    }
    private fun height() = compose.onNodeWithTag("task_motion").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
    @Test fun detailsAndCardShrinkProgressivelyUntilTheExitAnimationCompletes() {
        show()
        val collapsed = height()
        compose.onNodeWithTag("task_motion").performClick()
        val expanded = height(); assertTrue(expanded > collapsed + 20f)
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("task_motion").performClick()
            compose.mainClock.advanceTimeBy(96)
            compose.onNodeWithTag("task_details_motion", useUnmergedTree = true).assertExists()
            val midway = height()
            assertTrue("Collapse should be intermediate: $collapsed < $midway < $expanded", midway > collapsed + 1f && midway < expanded - 1f)
            compose.mainClock.advanceTimeBy(320)
            compose.onNodeWithTag("task_details_motion", useUnmergedTree = true).assertDoesNotExist()
            assertEquals(collapsed, height(), 1f)
        } finally { compose.mainClock.autoAdvance = true }
    }
    @Test fun reopeningDuringCollapseReturnsToTheFullDetailHeight() {
        show(); compose.onNodeWithTag("task_motion").performClick()
        val expanded = height()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("task_motion").performClick()
            compose.mainClock.advanceTimeBy(80)
            compose.onNodeWithTag("task_motion").performClick()
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithTag("task_details_motion", useUnmergedTree = true).assertExists()
            assertEquals(expanded, height(), 1f)
        } finally { compose.mainClock.autoAdvance = true }
    }
    @Test fun collapseSpacingDoesNotJumpOnItsFinalFrame() {
        show(); compose.onNodeWithTag("task_motion").performClick()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("task_motion").performClick()
            var previous = height()
            var largestLateStep = 0f
            repeat(24) { frame ->
                compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
                val current = height()
                if (frame >= 14) largestLateStep = maxOf(largestLateStep, kotlin.math.abs(previous - current))
                previous = current
            }
            Log.i("TodoMotionProbe", "collapseLargestLateHeightStepDp=$largestLateStep")
            assertTrue("Final spacing should shrink with the details, not jump: $largestLateStep dp", largestLateStep < 4f)
        } finally { compose.mainClock.autoAdvance = true }
    }
    @Test fun followingCardTracksTheAnimatedCardWithoutATrailingPlacementAnimation() {
        val first = TaskEntity("motion-first", "First synthetic task", "A short detail", UNCATEGORIZED_ID, createdAt = 1, updatedAt = 1)
        val second = first.copy(id = "motion-second", title = "Second synthetic task", notes = "")
        compose.runOnUiThread { compose.activity.setContent { TodoTheme {
            TaskList(TodoState(tasks = listOf(first, second)), Destination.UNSCHEDULED, Clock.systemUTC(), null,
                {}, {}, {}, {}, {}, {}, {}, {}, {}, true)
        } } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        var worstGap = 0f
        try {
            repeat(2) {
                compose.onNodeWithTag("task_motion-first").performClick()
                repeat(26) {
                    compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
                    val a = compose.onNodeWithTag("task_motion-first").getUnclippedBoundsInRoot()
                    val b = compose.onNodeWithTag("task_motion-second").getUnclippedBoundsInRoot()
                    worstGap = maxOf(worstGap, kotlin.math.abs((b.top - a.bottom).value - 12f))
                }
            }
            Log.i("TodoMotionProbe", "largestNeighborGapErrorDp=$worstGap")
            assertTrue("Following card should keep the 12 dp gap during resize: $worstGap dp", worstGap <= 1f)
        } finally { compose.mainClock.autoAdvance = true }
    }

}
