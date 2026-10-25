package com.chimafok.todo.ui

import android.os.Bundle
import android.os.Parcel
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimafok.todo.MainActivity
import com.chimafok.todo.data.CategoryEntity
import com.chimafok.todo.domain.UNCATEGORIZED_ID
import com.chimafok.todo.ui.theme.TodoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorStateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun fiftyThousandRevisionsStayOutOfBundleAndLostSessionCannotSave() {
        val revisions = (1..50_000).associate { "00000000-0000-4000-8000-${it.toString().padStart(12,'0')}" to 1L }
        val version = mutableStateOf(EditorVersions("fixture", "original-ticket", revisions))
        var saves = 0
        compose.runOnUiThread {
            compose.activity.setContent { TodoTheme {
                TaskEditor("fixture", null, false, null,
                    categories=listOf(CategoryEntity(UNCATEGORIZED_ID,"未分類",sortOrder=0,createdAt=1,updatedAt=1)),
                    tags=emptyList(), initialTagIds=emptySet(), onDismiss={}, onSave={ saves++ }, onSaved={}, retainedVersions=version.value)
            } }
        }
        compose.onNodeWithTag("task_title").performTextInput("大量版本保護")
        compose.runOnIdle { version.value = EditorVersions("fixture", "new-process-ticket", revisions) }
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("save_task").performClick()
        compose.onNodeWithTag("editor_error").assertTextContains("請關閉後重新開啟任務", substring=true)
        assertEquals(0, saves)
        val saved = Bundle()
        compose.runOnUiThread { InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(compose.activity, saved) }
        val parcel = Parcel.obtain()
        val bytes = try { parcel.writeBundle(saved); parcel.dataSize() } finally { parcel.recycle() }
        assertTrue("Saved state must not contain 50,000 revisions: $bytes", bytes < 100_000)
        android.util.Log.i("TodoEditorState", org.json.JSONObject().put("revisionCount",50_000).put("savedStateBytes",bytes).put("lostSessionBlocked",true).toString())
    }
}
