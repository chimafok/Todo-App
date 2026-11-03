package com.chimafok.todo.ui

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
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
                check(preview != null) { tr(R.string.text_close_and_preview_again_2318be) }
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
            error = tr(R.string.text_cannot_preview_the_series_close_and_ddd8e0)
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        modifier = Modifier.testTag("delete_future_dialog"),
        title = { Text(tr(R.string.text_delete_future_tasks_215333)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(series?.title ?: tr(R.string.text_recurring_series_3b72e0))
            if (preview == null && error == null) Text(tr(R.string.text_checking_series_records_518b18))
            preview?.let {
                Text(tr(R.string.text_stop_this_series_from_generating_new_0fcac9, boundary, it.futureCount))
                Text(tr(R.string.text_keep_today_past_and_all_completed_d243c6))
                if (it.futureCount == 0) Text(tr(R.string.text_no_future_incomplete_occurrences_have_been_e80fe0))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !busy && preview != null && error == null,
            modifier = Modifier.testTag("confirm_delete_future"), onClick = {
                busy = true
                scope.launch {
                    try {
                        check(boundary == LocalDate.now(Clock.system(ZoneId.of(requireNotNull(zone)))).toString()) { tr(R.string.text_the_date_changed_close_and_check_adb5ad) }
                        val frozen = requireNotNull(preview)
                        viewModel.deleteSeries(id, frozen.revision, frozen.tasks, false)
                        onDismiss()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        error = e.message ?: tr(R.string.text_the_series_changed_close_and_check_5de146)
                    } finally { busy = false }
                }
            }) { Text(if (busy) tr(R.string.text_saving_00190a) else tr(R.string.text_delete_future_tasks_27e4a5)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss,
            modifier = Modifier.testTag("cancel_delete_future")) { Text(tr(R.string.text_cancel_2cd0f3)) } })
}
