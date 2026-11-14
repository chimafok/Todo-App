package com.chimafok.todo.ui

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.MainActivity
import com.chimafok.todo.R
import com.chimafok.todo.ui.theme.TodoTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SelectionToolbarUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private data class Fixture(val summary: String, val labels: List<String>, val width: Int, val scale: Float)
    @Test fun wrappedRowsAlignSummaryAndActionTextInThreeLanguagesAndLargeType() {
        assertEquals("com.chimafok.todo.qa", compose.activity.packageName)
        val fixture = mutableStateOf<Fixture?>(null)
        var clicks = 0
        compose.runOnUiThread { compose.activity.setContent { TodoTheme {
            fixture.value?.let { f ->
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, f.scale)) {
                    Box(Modifier.width(f.width.dp)) {
                        SelectionToolbar(f.summary) {
                            f.labels.forEachIndexed { i, label ->
                                SelectionTextButton(onClick = { clicks++ }, modifier = Modifier.testTag("selection_action_$i")) {
                                    Text(label, Modifier.testTag("selection_label_$i"))
                                }
                            }
                        }
                    }
                }
            }
        } } }
        for (language in listOf("zh-Hant", "zh-Hans", "en")) {
            val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val res = compose.activity.createConfigurationContext(config).resources
            val labels = listOf(R.string.text_select_all_results_9951b1, R.string.text_move_category_64bc58,
                R.string.text_add_tags_cd22f2, R.string.text_move_to_trash_f86355).map(res::getString)
            for (width in listOf(240, 360)) for (scale in listOf(1f, 1.6f, 2f)) {
                compose.runOnIdle { fixture.value = Fixture(res.getString(R.string.text_s_selected_a5726a, 0), labels, width, scale) }
                compose.waitForIdle()
                val nodes = listOf(compose.onNodeWithTag("selection_count", useUnmergedTree = true)) +
                    labels.indices.map { compose.onNodeWithTag("selection_label_$it", useUnmergedTree = true) }
                fun baseline(node: SemanticsNodeInteraction): Float {
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    return (node.fetchSemanticsNode().boundsInRoot.top + layouts.single().firstBaseline) / compose.density.density
                }
                nodes.forEachIndexed { i, a -> nodes.drop(i + 1).forEach { b ->
                    val ar = a.fetchSemanticsNode().boundsInRoot; val br = b.fetchSemanticsNode().boundsInRoot
                    if (minOf(ar.bottom, br.bottom) > maxOf(ar.top, br.top)) {
                        assertEquals("$language width=$width scale=$scale", baseline(a), baseline(b), 1.1f / compose.density.density + .01f)
                    }
                } }
                labels.indices.forEach {
                    val button = compose.onNodeWithTag("selection_action_$it").getUnclippedBoundsInRoot()
                    assertTrue((button.bottom - button.top).value >= 48f)
                }
            }
        }
        compose.onNodeWithTag("selection_action_0").performClick()
        assertEquals(1, clicks)
    }
}
