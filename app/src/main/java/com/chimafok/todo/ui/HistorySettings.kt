package com.chimafok.todo.ui

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import kotlinx.coroutines.launch
import java.time.Clock

@Composable
fun HistorySettings(state: TodoState, viewModel: TodoViewModel) {
    val current = state.retentionPolicy.days
    var option by rememberSaveable(current) { mutableStateOf(if (current == null) "FOREVER" else if (current in listOf(30,90,365)) current.toString() else "CUSTOM") }
    var custom by rememberSaveable(current) { mutableStateOf(current?.toString() ?: "30") }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var previewDays by rememberSaveable { mutableStateOf<Int?>(null) }
    var previewRevision by rememberSaveable { mutableLongStateOf(0L) }
    var previewTicket by rememberSaveable { mutableStateOf("") }
    val frozenPreview = viewModel.retainedRetention.takeIf { previewTicket == viewModel.retentionTicket }
    val previewTasks = frozenPreview?.tasks.orEmpty()
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Text(tr(R.string.text_completed_record_retention_973ef2), style = MaterialTheme.typography.titleLarge)
    Text(tr(R.string.text_current_s_d0b9d1, current?.let { tr(R.string.text_s_days_5c0556, it) } ?: tr(R.string.text_keep_forever_14b99e)), Modifier.testTag("retention_current"))
    Text(tr(R.string.text_applies_only_to_completed_ordinary_tasks_cb52c2), style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("FOREVER" to tr(R.string.text_forever_3e71cc), "30" to tr(R.string.text_days_84ad29), "90" to tr(R.string.text_days_cb82f4), "365" to tr(R.string.text_days_ef9523), "CUSTOM" to tr(R.string.text_custom_1fe988)).forEach { (key,label) ->
            FilterChip(option == key, onClick = { option = key; error = null }, enabled = !busy,
                label = { Text(label) }, modifier = Modifier.testTag("retention_$key"))
        }
    }
    if (option == "CUSTOM") OutlinedTextField(custom, { custom = it; error = null }, label = { Text(tr(R.string.text_days_to_keep_ee61d3)) },
        singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("retention_days"))
    error?.let { Text(it, Modifier.testTag("retention_error"), color = MaterialTheme.colorScheme.error) }
    Button(enabled = !busy, modifier = Modifier.testTag("preview_retention"), onClick = {
        busy = true; error = null
        scope.launch {
            try {
                val days = if (option == "FOREVER") null else retentionDays(if (option == "CUSTOM") custom else option)
                val preview = viewModel.previewRetention(days)
                previewDays = preview.days; previewRevision = preview.policyRevision
                viewModel.retainedRetention = preview
                previewTicket = java.util.UUID.randomUUID().toString(); viewModel.retentionTicket = previewTicket
                showPreview = true
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message }
            finally { busy = false }
        }
    }) { Text(tr(R.string.text_preview_and_apply_dcb996)) }
    Text(tr(R.string.text_days_are_counted_from_completion_restored_c25664), style = MaterialTheme.typography.bodySmall)
    if (showPreview) AlertDialog(onDismissRequest = { if (!busy) showPreview = false }, modifier = Modifier.testTag("retention_preview"),
        title = { Text(tr(R.string.text_change_completed_record_retention_cc5447)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (frozenPreview == null) Text(tr(R.string.text_the_app_restarted_and_the_preview_32b6bf), color = MaterialTheme.colorScheme.error)
            Text(tr(R.string.text_retention_period_s_103a41, previewDays?.let { tr(R.string.text_s_days_5c0556, it) } ?: tr(R.string.text_forever_3e71cc)))
            Text(tr(R.string.text_s_completed_tasks_will_move_to_02245e, previewTasks.size))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !busy && frozenPreview != null, modifier = Modifier.testTag("confirm_retention"), onClick = {
            busy = true; error = null
            scope.launch { try { viewModel.applyRetention(requireNotNull(frozenPreview)); showPreview = false; viewModel.retainedRetention = null }
                catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message }
                finally { busy = false } }
        }) { Text(tr(R.string.text_confirm_and_apply_ddf142)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { showPreview = false; error = null }, modifier = Modifier.testTag("cancel_retention")) { Text(tr(R.string.text_back_to_edit_3160fc)) } })
}

@Composable
fun PurgeDialog(preview: PurgePreview, viewModel: TodoViewModel, onDismiss: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, modifier = Modifier.testTag("purge_confirmation"),
        title = { Text(if (preview.allTrash) tr(R.string.text_empty_all_trash_913dd9) else tr(R.string.text_permanently_delete_this_task_2c7f72)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr(R.string.text_permanently_delete_s_tasks_339d35, preview.tasks.size) + if (preview.series.isNotEmpty()) tr(R.string.text_and_s_deleted_series_4a42de, preview.series.size) else "。")
            if (preview.allTrash) Text(tr(R.string.text_includes_all_trash_data_including_items_6f9eae))
            Text(tr(R.string.text_this_cannot_be_undone_related_tag_6d0589))
            error?.let { Text(it, Modifier.testTag("purge_error"), color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("confirm_purge"), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), onClick = {
            busy = true; error = null
            scope.launch { try { viewModel.purge(preview); onDismiss() }
                catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message }
                finally { busy = false } }
        }) { Text(tr(R.string.text_delete_permanently_667198)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.testTag("cancel_purge")) { Text(tr(R.string.text_cancel_2cd0f3)) } })
}

@Composable
fun TrashToolbar(state: TodoState, viewModel: TodoViewModel, clock: Clock, enabled: Boolean, onEmpty: () -> Unit,
    selectionMode:Boolean=false,selectedSeries:Set<String> = emptySet(),onToggleSeries:(String)->Unit={},
    onLongSeries:((String)->Unit)?=null,onSelectAllSeries:()->Unit={}) {
    val groups = state.series.filter { it.deletedAt != null }
    val remainingCounts=remember(state.series,state.tasks,state.occurrences) {
        val byTask=state.tasks.associateBy {it.id};val bySeries=state.occurrences.groupBy {it.seriesId}
        groups.associate { series->series.id to bySeries[series.id].orEmpty().count {ref->ref.deletedWithSeriesAt==series.deletedAt && byTask[ref.taskId]?.deletedAt==series.deletedAt} }
    }
    val tasks = state.tasks.count { it.deletedAt != null }
    var restoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var revision by rememberSaveable { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(tr(R.string.text_automatically_deleted_permanently_after_days_ec45fb), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        TextButton(enabled = enabled && !selectionMode && (tasks > 0 || groups.isNotEmpty()), onClick = onEmpty, modifier = Modifier.testTag("empty_trash")) { Text(tr(R.string.text_empty_all_ee87e0)) }
    }
    if (groups.isNotEmpty()) Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        if(selectionMode)ActionIconButton(R.drawable.ic_select_all, tr(R.string.text_select_all_deleted_series_7aa243), enabled=enabled,onClick=onSelectAllSeries,modifier=Modifier.testTag("select_all_trash_series"))
        groups.forEach { series -> Row(Modifier.fillMaxWidth().testTag("trash_series_${series.id}").combinedClickable(
            enabled=enabled && onLongSeries!=null,onClick={if(selectionMode)onToggleSeries(series.id) else onLongSeries?.invoke(series.id)},
            onLongClick={onLongSeries?.invoke(series.id)},onLongClickLabel=tr(R.string.text_select_series_af0ee8)),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(selectionMode)Checkbox(series.id in selectedSeries,onCheckedChange={onToggleSeries(series.id)},enabled=enabled,modifier=Modifier.testTag("select_trash_series_${series.id}"))
            val remaining=remainingCounts[series.id] ?: 0
            Text(tr(R.string.text_s_s_remaining_58241c, series.title, remaining), Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = enabled && !selectionMode && !retentionExpired(requireNotNull(series.deletedAt),TRASH_DAYS,clock.millis()), modifier = Modifier.testTag("trash_restore_series_${series.id}"), onClick = {
                restoreId = series.id; revision = series.revision; error = null
            }) { Text(tr(R.string.text_restore_series_9fbd02)) }
        } }
    }
    restoreId?.let { id -> state.series.find { it.id == id }?.let { series ->
        val remaining=remainingCounts[id] ?: 0
        AlertDialog(onDismissRequest = { if (!busy) restoreId = null }, title = { Text(tr(R.string.text_restore_recurring_series_b13107)) },
            text = { Column { Text(tr(R.string.text_restore_s_and_its_s_remaining_aa8a27, series.title, remaining)); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
            confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("confirm_trash_restore_series"), onClick = {
                busy = true; scope.launch { try { viewModel.restoreSeries(id, revision); restoreId = null }
                    catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message }
                    finally { busy = false } }
            }) { Text(tr(R.string.text_restore_remaining_data_feb5e4)) } }, dismissButton = { TextButton(enabled = !busy, onClick = { restoreId = null }) { Text(tr(R.string.text_cancel_2cd0f3)) } })
    } }
}
