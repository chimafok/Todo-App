package com.chimafok.todo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chimafok.todo.data.SeriesActionPreview
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Agenda access to the existing transactional, revision-checked series deletion. */
@Composable
internal fun DeleteFutureSeriesDialog(id: String, state: TodoState, viewModel: TodoViewModel,
    clock: Clock, onDismiss: () -> Unit) {
    var preview by remember(id) { mutableStateOf<SeriesActionPreview?>(null) }
    var boundary by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var zone by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var expectedRevision by rememberSaveable(id) { mutableLongStateOf(-1L) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val series = state.series.find { it.id == id }
    LaunchedEffect(id) {
        try {
            if (expectedRevision >= 0) {
                // Rotation keeps the frozen map in the ViewModel. A new process cannot
                // silently substitute a larger/newer scope for the old confirmation.
                preview = viewModel.seriesActionPreview?.takeIf { it.id == id && it.revision == expectedRevision }
                check(preview != null) { "請關閉後重新預覽" }
            } else {
                val rule = requireNotNull(state.rules.filter { it.seriesId == id }.maxByOrNull { it.version })
                zone = rule.zoneId
                boundary = LocalDate.now(clock.withZone(ZoneId.of(requireNotNull(zone)))).toString()
                val frozen = viewModel.prepareSeriesAction(id, clock)
                expectedRevision = frozen.revision
                preview = frozen
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = "無法預覽系列，請關閉後重試。"
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        modifier = Modifier.testTag("delete_future_dialog"),
        title = { Text("刪除未來任務？") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(series?.title ?: "重複系列")
            if (preview == null && error == null) Text("正在核對系列紀錄…")
            preview?.let {
                Text("停止此系列，不再產生新任務；將 ${boundary} 之後的 ${it.futureCount} 項未完成任務移至垃圾桶。")
                Text("保留今天、過去及全部已完成紀錄。垃圾桶保留 30 天；本次操作不會自動重新啟用系列。")
                if (it.futureCount == 0) Text("目前沒有已產生的未來未完成項目；確認後仍會停止後續重複。")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !busy && preview != null && error == null,
            modifier = Modifier.testTag("confirm_delete_future"), onClick = {
                busy = true
                scope.launch {
                    try {
                        check(boundary == LocalDate.now(Clock.system(ZoneId.of(requireNotNull(zone)))).toString()) { "日期已改變，請關閉後重新檢查。" }
                        val frozen = requireNotNull(preview)
                        viewModel.deleteSeries(id, frozen.revision, frozen.tasks, false)
                        onDismiss()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        error = e.message ?: "系列已變更，請關閉後重新檢查。"
                    } finally { busy = false }
                }
            }) { Text(if (busy) "儲存中…" else "刪除未來任務") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss,
            modifier = Modifier.testTag("cancel_delete_future")) { Text("取消") } })
}
