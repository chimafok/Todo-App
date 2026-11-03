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
import com.chimafok.todo.domain.*
import kotlinx.coroutines.launch

@Composable
fun TaskBatchDialog(state: TodoState, action: String, revisions: Map<String, Long>, viewModel: TodoViewModel,
    onDismiss: () -> Unit, onSaved: () -> Unit) {
    var targetId by rememberSaveable { mutableStateOf(UNCATEGORIZED_ID) }
    var tags by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var choices by viewModel.batchChoices
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val tasks = state.tasks.filter { it.id in revisions }
    val stale = tasks.associate { it.id to it.revision } != revisions || tasks.any { it.deletedAt != null }
    val target = state.categories.find { it.id == targetId }
    val entering = if (target?.systemKind == SystemCategory.ASSIGNMENTS) tasks.filter { it.categoryId != ASSIGNMENTS_ID } else emptyList()
    val canSave = !busy && !stale && when (action) {
        "MOVE" -> target != null && entering.all { it.id in choices }
        "TAGS" -> tags.isNotEmpty() && tags.all { id -> state.tags.any { it.id == id } }
        else -> true
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(when (action) { "MOVE" -> tr(R.string.text_move_selected_tasks_958200); "TAGS" -> tr(R.string.text_add_tags_to_selected_tasks_7ae66f); else -> tr(R.string.text_move_to_trash_1b2fd4) })
            Text(tr(R.string.text_s_tasks_selected_b9d830, revisions.size), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (action) {
                "MOVE" -> {
                    Text(tr(R.string.text_choose_the_destination_category_choose_a_45b27a))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { state.categories.forEach { category ->
                        FilterChip(category.id == targetId, onClick = { targetId = category.id; choices = emptyMap() }, enabled = !busy,
                            label = { Text(com.chimafok.todo.i18n.categoryName(category)) }, modifier = Modifier.testTag("batch_target_${category.id}"))
                    } }
                    entering.forEach { task ->
                        Text(task.title)
                        var menu by remember(task.id) { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.testTag("batch_status_${task.id}")) {
                                Text(choices[task.id]?.let { statusLabel(TaskStatus.valueOf(it)) } ?: tr(R.string.text_choose_an_assignment_status_3fcfdf))
                            }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                TaskStatus.entries.filter { it != TaskStatus.OPEN }.forEach { status ->
                                    DropdownMenuItem(text = { Text(statusLabel(status)) }, leadingIcon = { AssignmentStatusIcon(status) },
                                        onClick = { choices = choices + (task.id to status.name); menu = false })
                                }
                            }
                        }
                    }
                }
                "TAGS" -> {
                    Text(tr(R.string.text_add_selected_tags_keeping_each_task_7e1f55))
                    if (state.tags.isEmpty()) Text(tr(R.string.text_create_tags_in_categories_tags_first_daa9ba))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { state.tags.forEach { tag ->
                        FilterChip(tag.id in tags, onClick = { tags = if (tag.id in tags) tags - tag.id else tags + tag.id }, enabled = !busy,
                            label = { Text(tag.name) }, modifier = Modifier.testTag("batch_tag_${tag.id}"))
                    } }
                }
                else -> Text(tr(R.string.text_these_tasks_will_move_to_trash_7f00c4))
            }
            if (action != "TRASH" && state.occurrences.any { it.taskId in revisions }) Text(
                tr(R.string.text_recurring_tasks_also_update_future_occurrence_60d8b4), style = MaterialTheme.typography.bodySmall)
            if (stale) Text(tr(R.string.text_selected_tasks_changed_cancel_and_select_b3f235), color = MaterialTheme.colorScheme.error)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = canSave, modifier = Modifier.testTag("confirm_batch"), onClick = {
            busy = true; error = null
            scope.launch {
                try {
                    when (action) {
                        "MOVE" -> viewModel.moveBatch(revisions, targetId, if (entering.isEmpty()) emptyMap() else choices.mapValues { TaskStatus.valueOf(it.value) })
                        "TAGS" -> viewModel.addTagsBatch(revisions, tags.toSet())
                        else -> viewModel.trashBatch(revisions)
                    }
                    onSaved()
                } catch (exception: Exception) {
                    if (exception is kotlinx.coroutines.CancellationException) throw exception
                    error = exception.message ?: tr(R.string.text_operation_failed_please_try_again_6d73ff)
                } finally { busy = false }
            }
        }) { Text(if (busy) tr(R.string.text_saving_00190a) else tr(R.string.text_confirm_dce52f)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr(R.string.text_cancel_2cd0f3)) } })
}
