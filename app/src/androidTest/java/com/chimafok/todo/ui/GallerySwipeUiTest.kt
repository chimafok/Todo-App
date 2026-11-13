package com.chimafok.todo.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.R
import com.chimafok.todo.data.NewTaskDraft
import com.chimafok.todo.data.NoteImages
import com.chimafok.todo.domain.*
import com.chimafok.todo.i18n.tr
import com.chimafok.todo.reminders.ReminderRuntime
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GallerySwipeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val id = "QA150-gallery-${UUID.randomUUID()}"
    private val model get() = ViewModelProvider(compose.activity)[TodoViewModel::class.java]
    private fun await(tag: String) { compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun keyboard() {
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
        compose.waitForIdle()
    }
    private fun open(count: Int = 3, initial: Int = 0) {
        await("add_task"); assertEquals("com.chimafok.todo.qa", compose.activity.packageName)
        val uris = (0 until count).map { index ->
            val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(listOf(Color.RED, Color.GREEN, Color.BLUE)[index])
            File(compose.activity.cacheDir, "qa-image-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            Uri.parse("content://${compose.activity.packageName}.backup-test/image-$index.png")
        }
        runBlocking {
            val images = NoteImages(ReminderRuntime.database).import(compose.activity, uris, emptyList())
            model.keepNewTaskDraft(NewTaskDraft(id, title = id, notes = "Synthetic gallery notes", imageIds = encodeImageIds(images)))
        }
        await("resume_draft"); compose.onNodeWithTag("resume_draft").performClick(); await("task_title"); keyboard()
        compose.onNodeWithTag("note_add_images").performScrollTo()
        compose.onNodeWithTag("note_images").performScrollToIndex(initial)
        compose.onNodeWithTag("note_image_$initial").performClick(); page(initial + 1, count)
    }
    private fun page(number: Int, total: Int = 3) {
        val label = tr(R.string.text_image_s_s_cae5c2, number, total)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("note_image_counter").fetchSemanticsNodes().any {
            it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.any { text -> text.text == label }
        } }
        compose.waitForIdle(); await("note_image_preview")
    }
    private fun preview() = compose.onNodeWithTag("note_image_preview")
    @After fun cleanup() {
        runBlocking { model.discardNewTaskDraft(id); model.cleanupNoteImages() }
        (0..2).forEach { File(compose.activity.cacheDir, "qa-image-$it.png").delete() }
    }
    @Test fun swipeBothWaysStopsAtEndsAndArrowAndCloseIconsRemainAccessible() {
        open(); compose.onNodeWithTag("note_image_previous").assertIsNotEnabled()
        preview().performTouchInput { swipeLeft() }; page(2)
        preview().performTouchInput { swipeLeft() }; page(3)
        compose.onNodeWithTag("note_image_next").assertIsNotEnabled()
        preview().performTouchInput { swipeLeft() }; page(3)
        preview().performTouchInput { swipeRight() }; page(2)
        compose.onNodeWithTag("note_image_previous").assertContentDescriptionEquals(tr(R.string.text_previous_image_a3b6d8)).performClick(); page(1)
        compose.onNodeWithTag("note_image_next").assertContentDescriptionEquals(tr(R.string.text_next_image_c3b16d)).performClick(); page(2)
        compose.onNodeWithTag("note_image_close").assertContentDescriptionEquals(tr(R.string.text_close_c7fddd)).performClick()
        compose.onNodeWithTag("note_image_close").assertDoesNotExist()
        compose.onNodeWithTag("task_notes").assertTextContains("Synthetic gallery notes")
    }
    @Test fun zoomedDragPansAndChangingPageResetsZoomBeforeTheNextSwipe() {
        open()
        preview().performTouchInput {
            pinch(Offset(width * .4f, centerY), Offset(width * .6f, centerY),
                Offset(width * .1f, centerY), Offset(width * .9f, centerY), 600)
        }
        preview().performTouchInput { swipeLeft() }; page(1)
        compose.onNodeWithTag("note_image_next").performClick(); page(2)
        compose.onNodeWithTag("note_image_previous").performClick(); page(1)
        preview().performTouchInput { swipeLeft() }; page(2)
        preview().performTouchInput { click(Offset(centerX, 5f)) }
        compose.onNodeWithTag("note_image_close").assertDoesNotExist()
    }
    @Test fun chosenThumbnailAndSettledSwipeSurviveActivityRecreation() {
        open(initial = 1); preview().performTouchInput { swipeLeft() }; page(3)
        compose.activityRule.scenario.recreate(); page(3)
        compose.onNodeWithTag("note_image_close").performClick()
        compose.onNodeWithTag("task_notes").assertTextContains("Synthetic gallery notes")
    }
    @Test fun arrowsAcceptRapidRepeatsAndReversalBeforeThePreviousAnimationSettles() {
        open()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("note_image_next").performClick()
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("note_image_next").assertIsEnabled().performClick()
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("note_image_next").assertIsNotEnabled()
            compose.onNodeWithTag("note_image_previous").assertIsEnabled().performClick()
            compose.mainClock.advanceTimeBy(240)
            compose.onNodeWithTag("note_image_counter").assertTextEquals(tr(R.string.text_image_s_s_cae5c2, 2, 3))
            compose.onNodeWithTag("note_image_next").assertIsEnabled().performClick()
            compose.mainClock.advanceTimeBy(240)
            compose.onNodeWithTag("note_image_counter").assertTextEquals(tr(R.string.text_image_s_s_cae5c2, 3, 3))
            compose.onNodeWithTag("note_image_next").assertIsNotEnabled()
        } finally { compose.mainClock.autoAdvance = true }
    }
    @Test fun singleImageKeepsBothArrowsDisabledAndBlankTapDismisses() {
        open(1); compose.onNodeWithTag("note_image_previous").assertIsNotEnabled(); compose.onNodeWithTag("note_image_next").assertIsNotEnabled()
        preview().performTouchInput { swipeLeft() }; page(1, 1)
        preview().performTouchInput { click(center) }; compose.onNodeWithTag("note_image_close").assertExists()
        preview().performTouchInput { click(Offset(centerX, height - 5f)) }
        compose.onNodeWithTag("note_image_close").assertDoesNotExist()
    }
}
