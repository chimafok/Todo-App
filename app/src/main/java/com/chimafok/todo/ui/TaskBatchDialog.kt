package com.chimafok.todo.ui

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
            Text(when (action) { "MOVE" -> "批量移分類"; "TAGS" -> "批量加入標籤"; else -> "移至垃圾桶？" })
            Text("已選 ${revisions.size} 項任務", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (action) {
                "MOVE" -> {
                    Text("選擇目標分類。移入作業的任務需逐項選擇狀態；原本已是作業的項目保留原狀態。")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { state.categories.forEach { category ->
                        FilterChip(category.id == targetId, onClick = { targetId = category.id; choices = emptyMap() }, enabled = !busy,
                            label = { Text(category.name) }, modifier = Modifier.testTag("batch_target_${category.id}"))
                    } }
                    entering.forEach { task ->
                        Text(task.title)
                        var menu by remember(task.id) { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.testTag("batch_status_${task.id}")) {
                                Text(choices[task.id]?.let { statusLabel(TaskStatus.valueOf(it)) } ?: "請選擇作業狀態")
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
                    Text("加入所選標籤，保留每項任務原有標籤。")
                    if (state.tags.isEmpty()) Text("請先到「分類與標籤」建立標籤。")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { state.tags.forEach { tag ->
                        FilterChip(tag.id in tags, onClick = { tags = if (tag.id in tags) tags - tag.id else tags + tag.id }, enabled = !busy,
                            label = { Text(tag.name) }, modifier = Modifier.testTag("batch_tag_${tag.id}"))
                    } }
                }
                else -> Text("這些任務將移至垃圾桶並保留 30 天，期間可以復原。")
            }
            if (action != "TRASH" && state.occurrences.any { it.taskId in revisions }) Text(
                "重複任務也會更新未來新項目的設定；其他已產生紀錄保留。", style = MaterialTheme.typography.bodySmall)
            if (stale) Text("所選任務已更新，請取消後重新選取。", color = MaterialTheme.colorScheme.error)
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
                    error = exception.message ?: "操作失敗，請重試。"
                } finally { busy = false }
            }
        }) { Text(if (busy) "儲存中…" else "確認") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } })
}
