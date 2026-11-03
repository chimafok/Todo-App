package com.chimafok.todo.ui

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chimafok.todo.data.OrganizationDeletePreview
import com.chimafok.todo.domain.*

@Composable
internal fun OrganizationBatchDeleteDialog(preview: OrganizationDeletePreview, busy: Boolean, error: String?,
    choices: Map<String, TaskStatus>, onChoices: (Map<String, TaskStatus>) -> Unit, onDismiss: () -> Unit, onConfirm: (String, Map<String, TaskStatus>) -> Unit) {
    var targetId by rememberSaveable { mutableStateOf(UNCATEGORIZED_ID) }
    val assignment = preview.targets.find { it.id == targetId }?.systemKind == SystemCategory.ASSIGNMENTS
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (preview.tags) tr(R.string.text_delete_selected_tags_28ffbd) else tr(R.string.text_delete_categories_and_move_tasks_5edc97)) },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr(R.string.text_s_selected_s_f75f81, preview.names.size, preview.names.joinToString("、")))
            Text(if (preview.tags) tr(R.string.text_tags_and_their_links_will_be_811de4) else tr(R.string.text_s_tasks_including_completed_and_trashed_b5c3dd, preview.tasks.size, preview.series.size))
            if (!preview.tags) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                preview.targets.forEach { category -> FilterChip(category.id == targetId, enabled = !busy,
                    onClick = { targetId = category.id; onChoices(emptyMap()) }, label = { Text(com.chimafok.todo.i18n.categoryName(category)) }, modifier = Modifier.testTag("org_batch_target_${category.id}")) }
            }
            if (assignment) preview.tasks.forEach { task ->
                Text(task.title)
                var expanded by remember(task.id) { mutableStateOf(false) }
                Box {
                    OutlinedButton(enabled = !busy, onClick = { expanded = true }) { Text(choices[task.id]?.let { statusLabel(it) } ?: tr(R.string.text_choose_an_assignment_status_3fcfdf)) }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        statusOptions(true).filter { task.id !in preview.canceled || it == TaskStatus.NOT_STARTED }.forEach { status ->
                            DropdownMenuItem(text = { Text(statusLabel(status)) }, onClick = { onChoices(choices + (task.id to status)); expanded = false })
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !busy && (preview.tags || !assignment || preview.tasks.all { it.id in choices }),
            modifier = Modifier.testTag("org_batch_delete_confirm"), onClick = { onConfirm(targetId, choices) }) { Text(if (busy) tr(R.string.text_processing_d166e7) else if (preview.tags) tr(R.string.text_delete_tags_bceb1d) else tr(R.string.text_move_and_delete_categories_1e6482)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.testTag("org_batch_delete_cancel")) { Text(tr(R.string.text_cancel_2cd0f3)) } })
}
