package com.chimafok.todo.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.ui.theme.TodoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@com.chimafok.todo.Regression104
@RunWith(AndroidJUnit4::class)
class LoadingUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun quickSearchNeverFlashesAndChangedRequestRestartsDelay() {
        val busy = mutableStateOf(true)
        val request = mutableStateOf(1)
        compose.mainClock.autoAdvance=false
        try {
            compose.runOnUiThread { compose.activity.setContent { TodoTheme {
                DelayedLoadingIndicator(busy.value,Modifier.testTag("slow_progress"),request.value)
            } } }
            compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
            compose.onNodeWithTag("slow_progress").assertDoesNotExist()
            compose.mainClock.advanceTimeBy(700)
            compose.runOnUiThread { busy.value=false };compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("slow_progress").assertDoesNotExist()
            compose.runOnUiThread { busy.value=true;request.value=2 };compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(700)
            compose.onNodeWithTag("slow_progress").assertDoesNotExist()
            compose.runOnUiThread { request.value=3 };compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(1_900)
            compose.onNodeWithTag("slow_progress").assertDoesNotExist()
            compose.mainClock.advanceTimeBy(150);compose.waitForIdle()
            compose.onNodeWithTag("slow_progress").assertExists()
            compose.runOnUiThread { busy.value=false };compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("slow_progress").assertDoesNotExist()
        } finally {compose.mainClock.autoAdvance=true}
    }
}
