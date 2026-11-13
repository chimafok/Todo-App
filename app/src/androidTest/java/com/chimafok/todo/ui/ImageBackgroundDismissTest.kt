package com.chimafok.todo.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.ui.theme.TodoTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageBackgroundDismissTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun open(width: Int = 400, height: Int = 200) {
        assertEquals("com.chimafok.todo.qa", compose.activity.packageName)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.CYAN) }.asImageBitmap()
        val visible = mutableStateOf(true)
        compose.runOnUiThread { compose.activity.setContent { TodoTheme {
            if (visible.value) NoteImagePreview(bitmap, "Synthetic image", Modifier.size(300.dp, 420.dp)) { visible.value = false }
        } } }
        preview().assertExists()
    }
    private fun preview() = compose.onNodeWithTag("note_image_preview")
    @Test fun tappingImageKeepsItOpenAndTopOrBottomLetterboxingDismisses() {
        open(); preview().performTouchInput { click(center) }; preview().assertExists()
        preview().performTouchInput { click(Offset(centerX, 5f)) }; preview().assertDoesNotExist()
        open(); preview().performTouchInput { click(Offset(centerX, height - 5f)) }; preview().assertDoesNotExist()
    }
    @Test fun portraitImageSideLetterboxingAlsoDismisses() {
        open(100, 800); preview().performTouchInput { click(center) }; preview().assertExists()
        preview().performTouchInput { click(Offset(5f, centerY)) }; preview().assertDoesNotExist()
        open(100, 800); preview().performTouchInput { click(Offset(width - 5f, centerY)) }; preview().assertDoesNotExist()
    }
    @Test fun pinchAndPanRemainAvailableAndHitTestingFollowsTheTransformedImage() {
        open()
        preview().performTouchInput {
            pinch(Offset(width * .4f, centerY), Offset(width * .6f, centerY),
                Offset(width * .1f, centerY), Offset(width * .9f, centerY), 600)
        }
        // This point was blank before zoom, and is now painted image content.
        preview().performTouchInput { click(Offset(centerX, height * .2f)) }; preview().assertExists()
        preview().performTouchInput { swipe(Offset(centerX, height * .8f), Offset(centerX, height * .15f), 600) }
        preview().assertExists()
        // Panning upward exposes blank space below the image.
        preview().performTouchInput { click(Offset(centerX, height - 5f)) }; preview().assertDoesNotExist()
    }
}
