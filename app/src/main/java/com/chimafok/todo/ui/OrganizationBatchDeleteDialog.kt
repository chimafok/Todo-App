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
import com.chimafok.todo.data.OrganizationDeletePreview
import com.chimafok.todo.domain.*

@Composable
internal fun OrganizationBatchDeleteDialog(preview: OrganizationDeletePreview, busy: Boolean, error: String?,
    choices: Map<String, TaskStatus>, onChoices: (Map<String, TaskStatus>) -> Unit, onDismiss: () -> Unit, onConfirm: (String, Map<String, TaskStatus>) -> Unit) {
    var targetId by rememberSaveable { mutableStateOf(UNCATEGORIZED_ID) }
    val assignment = preview.targets.find { it.id == targetId }?.systemKind == SystemCategory.ASSIGNMENTS
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (preview.tags) "批量刪除標籤？" else "批量刪除分類並搬移任務") },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("已選 ${preview.names.size} 項：${preview.names.joinToString("、")}")
            Text(if (preview.tags) "會移除標籤及其連結，任務會保留。" else "${preview.tasks.size} 項任務（含已完成及垃圾桶）及 ${preview.series.size} 個系列將搬到以下分類；任務不會刪除。")
            if (!preview.tags) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                preview.targets.forEach { category -> FilterChip(category.id == targetId, enabled = !busy,
                    onClick = { targetId = category.id; onChoices(emptyMap()) }, label = { Text(category.name) }, modifier = Modifier.testTag("org_batch_target_${category.id}")) }
            }
            if (assignment) preview.tasks.forEach { task ->
                Text(task.title)
                var expanded by remember(task.id) { mutableStateOf(false) }
                Box {
                    OutlinedButton(enabled = !busy, onClick = { expanded = true }) { Text(choices[task.id]?.let { statusLabel(it) } ?: "請選擇作業狀態") }
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
            modifier = Modifier.testTag("org_batch_delete_confirm"), onClick = { onConfirm(targetId, choices) }) { Text(if (busy) "處理中…" else if (preview.tags) "刪除標籤" else "搬移並刪除分類") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.testTag("org_batch_delete_cancel")) { Text("取消") } })
}
