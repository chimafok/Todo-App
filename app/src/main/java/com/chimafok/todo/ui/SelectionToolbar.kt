package com.chimafok.todo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Keep text baselines aligned per wrapped row, including large text and translated labels. */
@Composable
internal fun SelectionToolbar(summary: String, modifier: Modifier = Modifier,
    summaryTag: String = "selection_count", content: @Composable RowScope.() -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.alignByBaseline().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) { Text(summary, Modifier.testTag(summaryTag)) }
        }
        content()
    }
}

@Composable
internal fun RowScope.SelectionTextButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, colors: ButtonColors = ButtonDefaults.textButtonColors(),
    content: @Composable RowScope.() -> Unit) {
    TextButton(onClick, modifier.heightIn(min = 48.dp).alignByBaseline(), enabled = enabled,
        colors = colors, content = content)
}
