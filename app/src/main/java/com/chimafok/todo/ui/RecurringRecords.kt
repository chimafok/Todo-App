package com.chimafok.todo.ui

import com.chimafok.todo.i18n.tr
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.chimafok.todo.data.*
import com.chimafok.todo.R
import com.chimafok.todo.domain.*
import kotlinx.coroutines.launch
import java.time.*

@Composable
fun RecurringRecords(state: TodoState, viewModel: TodoViewModel, clock: Clock, enabled: Boolean,
    onEdit: (TaskEntity) -> Unit, onComplete: (TaskEntity) -> Unit, onReopen: (TaskEntity) -> Unit,
    onStatus: (TaskEntity) -> Unit, onAdvance: (TaskEntity) -> Unit, onDelete: (TaskEntity) -> Unit,
    onSelectionMode: (Boolean)->Unit = {}) {
    var selectedSeries by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable(selectedSeries) { mutableStateOf("") }
    var actionId by rememberSaveable { mutableStateOf<String?>(null) }
    var action by rememberSaveable { mutableStateOf("") }
    var expectedRevision by rememberSaveable { mutableLongStateOf(0L) }
    var history by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val selectionSession=rememberSaveable {java.util.UUID.randomUUID().toString()}
    var selectionGeneration by remember {mutableIntStateOf(0)}
    var selectingHistory by rememberSaveable(selectedSeries) {mutableStateOf(false)}
    var recordsSelected by viewModel.historyRecordSelection
    var seriesSelected by viewModel.historySeriesSelection
    var recordBatch by rememberSaveable(selectedSeries) {mutableStateOf(false)}
    var seriesBatchAction by rememberSaveable(selectedSeries) {mutableStateOf<String?>(null)}
    var seriesBatchTicket by rememberSaveable(selectedSeries) {mutableStateOf("")}
    LaunchedEffect(selectionSession,selectedSeries,query){viewModel.resetHistorySelection("$selectionSession/$selectedSeries/$query")}
    LaunchedEffect(selectingHistory){onSelectionMode(selectingHistory)}
    DisposableEffect(Unit){onDispose {onSelectionMode(false)}}
    val keyboard = LocalSoftwareKeyboardController.current
    val series = remember(state.series) { state.series.filter { it.deletedAt == null } }
    val latestRules = remember(state.rules) { state.rules.groupBy { it.seriesId }.mapValues { (_, rules) -> rules.maxBy { it.version } } }
    val categories = remember(state.categories, com.chimafok.todo.i18n.I18n.locale) { state.categories.associate { it.id to com.chimafok.todo.i18n.categoryName(it) } }
    var limit by rememberSaveable(selectedSeries, query) { mutableIntStateOf(HistoryBrowserRepository.PAGE_SIZE) }
    var window by remember { mutableStateOf(HistoryWindow()) }
    var completedWindowRequest by remember {mutableStateOf<List<Any?>?>(null)}
    var windowError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val windowRequest=listOf(selectedSeries,query,limit,state.dataEpoch,retry)
    val latestWindowRequest by rememberUpdatedState(windowRequest)
    val windowLoading=completedWindowRequest!=windowRequest
    LaunchedEffect(selectedSeries,query,limit,state.dataEpoch,retry) {
        windowError=null
        val request=windowRequest
        try {
            val loaded=viewModel.historyWindow(selectedSeries,query,limit)
            if(latestWindowRequest==request){window=loaded;completedWindowRequest=request}
        } catch(e:Exception) {
            if(e is kotlinx.coroutines.CancellationException)throw e
            if(latestWindowRequest==request){windowError=tr(R.string.text_cannot_read_recurring_records_please_try_5248bd);completedWindowRequest=request}
        }
    }
    val canAct = enabled && !busy && !windowLoading && windowError == null
    val visibleSeries=series.filter {item->query.isBlank() || item.title.contains(query.trim(),ignoreCase=true) || item.id in window.matchingSeries}
    fun clearSelection(){recordsSelected=emptyMap();seriesSelected=emptyMap();selectingHistory=false;selectionGeneration++}
    fun selectRecord(task:TaskEntity,long:Boolean) {
        if(!canAct || window.rows.none {it.task.id==task.id && !it.occurrence.excludedByRule})return
        if(!selectingHistory){recordsSelected=emptyMap();seriesSelected=emptyMap();selectionGeneration++}
        selectingHistory=true
        recordsSelected=if(!long && task.id in recordsSelected)recordsSelected-task.id else recordsSelected+(task.id to task.revision)
    }
    fun selectSeries(item:RecurringSeriesEntity,long:Boolean) {
        if(!canAct || item.id !in visibleSeries.map {it.id})return
        if(!selectingHistory){recordsSelected=emptyMap();seriesSelected=emptyMap();selectionGeneration++}
        selectingHistory=true
        seriesSelected=if(!long && item.id in seriesSelected)seriesSelected-item.id else seriesSelected+(item.id to item.revision)
    }
    fun selectAllRecords() {
        val id=selectedSeries ?: return
        val request=windowRequest;val generation=selectionGeneration
        busy=true;error=null
        scope.launch {try {
            val loaded=viewModel.historySelection(id,query)
            if(latestWindowRequest==request && generation==selectionGeneration && selectingHistory)recordsSelected=loaded
        } catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message}
          finally {busy=false}}
    }
    fun previewSeriesBatch(stop:Boolean) {
        if(!canAct || seriesSelected.isEmpty())return
        val revisions=seriesSelected.toMap();val request=windowRequest;val generation=selectionGeneration
        busy=true;error=null
        scope.launch {try {
            viewModel.prepareSeriesSelection(revisions)
            if(latestWindowRequest==request && generation==selectionGeneration && selectingHistory) {
                seriesBatchTicket=java.util.UUID.randomUUID().toString();viewModel.seriesSelectionTicket=seriesBatchTicket
                seriesBatchAction=if(stop)"STOP" else "DELETE"
            } else {viewModel.seriesSelectionPreview=null;if(selectingHistory)error=tr(R.string.text_the_query_changed_please_select_again_b1accc)}
        } catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message}
          finally {busy=false}}
    }
    BackHandler(selectedSeries != null) { selectedSeries = null }
    BackHandler(selectingHistory) {clearSelection()}
    LaunchedEffect(series.map { it.id }) { if (selectedSeries != null && series.none { it.id == selectedSeries }) selectedSeries = null }
    fun openAction(item: RecurringSeriesEntity, mode: String) {
        busy = true; error = null
        scope.launch {
            try {
                val preview = viewModel.prepareSeriesAction(item.id, clock)
                actionId = item.id; action = mode; expectedRevision = preview.revision; history = false
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                error = exception.message ?: tr(R.string.text_cannot_preview_the_series_please_try_18b662)
            } finally { busy = false }
        }
    }
    // Large revision maps belong to the ViewModel, never the saved-state Bundle.
    // A new process must preview again and reset destructive scope before enabling confirmation.
    LaunchedEffect(actionId) {
        val id = actionId ?: run { viewModel.clearSeriesAction(); return@LaunchedEffect }
        if (viewModel.seriesActionPreview?.id != id) {
            busy = true; history = false
            try { expectedRevision = viewModel.prepareSeriesAction(id, clock).revision }
            catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                error = exception.message ?: tr(R.string.text_cannot_preview_the_series_close_and_ddd8e0)
            } finally { busy = false }
        }
    }
    if (series.isEmpty()) {
        EmptyMessage(stringResource(R.string.recurring_empty_title), stringResource(R.string.recurring_empty_message))
    } else Column(Modifier.fillMaxSize()) {
        if (selectedSeries != null) TextButton(onClick = { selectedSeries = null }, modifier = Modifier.padding(horizontal = 12.dp).testTag("back_to_series")) { Text(tr(R.string.text_all_series_2da337)) }
        OutlinedTextField(query, { query = it;recordsSelected=emptyMap();seriesSelected=emptyMap();selectionGeneration++ }, singleLine = true, placeholder = { Text(tr(R.string.text_search_recurring_records_70c9b4)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("recurring_search"), shape = MaterialTheme.shapes.medium)
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
            Text(if(selectedSeries==null) tr(R.string.text_s_series_84f50c, visibleSeries.size) else tr(R.string.text_s_records_e3339e, window.total),style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(vertical=12.dp))
            val normal=ButtonDefaults.textButtonColors()
            ActionIconButton(if(selectingHistory) R.drawable.ic_check else R.drawable.ic_multi_select,
                if(selectingHistory)tr(R.string.text_done_selecting_d8c47c) else tr(R.string.text_select_1dc26d), selected=selectingHistory,
                enabled=canAct, contentColor=normal.contentColor,
                disabledContentColor=if(enabled && !busy && windowLoading && windowError==null && completedWindowRequest!=null)normal.contentColor else normal.disabledContentColor,
                onClick={if(selectingHistory)clearSelection() else {clearSelection();selectingHistory=true}},modifier=Modifier.testTag("history_toggle_selection"))
        }
        if(selectingHistory)FlowRow(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(selectedSeries==null)tr(R.string.text_s_series_selected_70f058, seriesSelected.size) else tr(R.string.text_s_records_selected_86fe42, recordsSelected.size),Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.labelLarge)
            ActionIconButton(R.drawable.ic_select_all, if(selectedSeries==null)tr(R.string.text_select_all_results_9951b1) else tr(R.string.text_select_all_actionable_records_52b2d0),
                enabled=canAct,onClick={if(selectedSeries==null)seriesSelected=visibleSeries.associate {it.id to it.revision} else selectAllRecords()},modifier=Modifier.testTag("history_select_results"))
            if(selectedSeries==null) {
                val allActive=seriesSelected.isNotEmpty() && seriesSelected.all {(id,rev)->series.any {it.id==id && it.revision==rev && it.lifecycle==SeriesLifecycle.ACTIVE}}
                TextButton(enabled=canAct && allActive,onClick={previewSeriesBatch(true)},modifier=Modifier.testTag("history_batch_stop")){Text(tr(R.string.text_stop_repeating_5efad6))}
                TextButton(enabled=canAct && seriesSelected.isNotEmpty(),onClick={previewSeriesBatch(false)},modifier=Modifier.testTag("history_batch_delete")){Text(tr(R.string.text_delete_selected_series_881f8d))}
            } else TextButton(enabled=canAct && recordsSelected.isNotEmpty(),onClick={viewModel.prepareBatch(recordsSelected.toMap());recordBatch=true},modifier=Modifier.testTag("history_batch_trash")){Text(tr(R.string.text_move_to_trash_f86355))}
        }
        DelayedLoadingIndicator(windowLoading, Modifier.testTag("history_loading"), listOf(selectedSeries, query, limit, state.dataEpoch, retry))
        windowError?.let { message -> Row(Modifier.padding(horizontal = 20.dp)) {
            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { retry++ }) { Text(tr(R.string.text_retry_7e59d0)) }
        } }
        if (actionId == null) error?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
        val selected = series.find { it.id == selectedSeries }
        LazyColumn(Modifier.fillMaxSize().testTag("recurring_list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected == null) {
                val visible = visibleSeries
                if (visible.isEmpty() && !windowLoading && windowError == null) item { EmptyTasks(tr(R.string.text_no_matching_series_dfa66a)) }
                items(visible, key = { it.id }) { item ->
                    val counts = window.counts[item.id] ?: SeriesCounts(item.id, 0, 0, 0)
                    val rule = latestRules[item.id]
                    Card(modifier=Modifier.fillMaxWidth().testTag("recurring_series_${item.id}").clip(MaterialTheme.shapes.large).combinedClickable(
                        enabled=canAct,role=Role.Button,onLongClickLabel=tr(R.string.text_select_series_af0ee8),onLongClick={selectSeries(item,true)},
                        onClick={if(selectingHistory)selectSeries(item,false) else selectedSeries=item.id}),
                        border=if(selectingHistory && item.id in seriesSelected)BorderStroke(1.dp,MaterialTheme.colorScheme.primary) else null) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row {
                                if(selectingHistory)Checkbox(item.id in seriesSelected,onCheckedChange={selectSeries(item,false)},enabled=canAct,modifier=Modifier.testTag("select_series_${item.id}"))
                                Text(item.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f).padding(vertical=8.dp))
                            }
                            Text("${rule?.frequency?.let(::recurrenceLabel).orEmpty()} · ${when (item.lifecycle) {
                                SeriesLifecycle.ACTIVE -> tr(R.string.text_repeating_4fd07f); SeriesLifecycle.ENDED -> tr(R.string.text_ended_eea860); else -> tr(R.string.text_stopped_f00645)
                            }}", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
                            Text(tr(R.string.text_s_completed_s_incomplete_s_rule_ee49d2, counts.completed, counts.pending, counts.cancelled),
                                style = MaterialTheme.typography.bodySmall)
                            if(!selectingHistory)Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (item.lifecycle == SeriesLifecycle.ACTIVE) TextButton(enabled = canAct, onClick = { openAction(item, "STOP") },
                                    modifier = Modifier.testTag("stop_series_${item.id}")) { Text(tr(R.string.text_stop_future_recurrence_ded71b)) }
                                TextButton(enabled = canAct, onClick = { openAction(item, "DELETE") }, modifier = Modifier.testTag("delete_series_${item.id}")) { Text(tr(R.string.text_delete_series_83b9c7)) }
                            }
                        }
                    }
                }
            } else {
                item { Text(selected.title, style = MaterialTheme.typography.titleLarge) }
                item { Text(tr(R.string.text_loaded_s_s_records_50c890, window.rows.size, window.total), modifier = Modifier.testTag("history_page_summary"), style = MaterialTheme.typography.bodySmall) }
                if (window.rows.isEmpty() && !windowLoading && windowError == null) item { EmptyTasks(tr(R.string.text_no_matching_records_b5f943)) }
                items(window.rows, key = { it.task.id }) { row ->
                    val task = row.task
                    val ref = row.occurrence
                    val snapshot = OccurrenceSnapshot.read(ref.snapshot)
                    val historical = ref.excludedByRule || task.status == TaskStatus.COMPLETED || task.dueDate!! < LocalDate.now(clock.withZone(ZoneId.of(task.zoneId ?: clock.zone.id)))
                    val shown = if (historical && snapshot != null) task.copy(title = snapshot.title, notes = snapshot.notes, imageIds = snapshot.imageIds) else task
                    val tagNames = if (historical && snapshot != null) snapshot.tags else window.tags[task.id].orEmpty()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr(R.string.text_occurrence_s_s_f7d0c4, ref.sequence, task.dueDate) + if (ref.excludedByRule) tr(R.string.text_rule_canceled_021b70) else if (snapshot?.late == true && task.status == TaskStatus.COMPLETED) tr(R.string.text_completed_late_98b79d) else "",
                            modifier = Modifier.testTag("occurrence_caption_${task.id}"),
                            style = MaterialTheme.typography.labelLarge, color = if (snapshot?.late == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                        TaskCard(shown, if (historical && snapshot != null) snapshot.category else categories[task.categoryId].orEmpty(),
                            clock, false, canAct, onEdit, onComplete, onReopen, onStatus, onAdvance, onDelete, {}, tags = tagNames,
                            readOnly = ref.excludedByRule,
                            selectionMode=selectingHistory,selected=task.id in recordsSelected,
                            onToggleSelection={selectRecord(task,false)},onLongSelect={selectRecord(task,true)},
                            historyDescription = if (ref.excludedByRule) tr(R.string.text_canceled_because_the_series_rule_was_9f9b83) else
                                snapshot?.status?.takeIf { it != task.status.name }?.let { tr(R.string.text_status_at_the_time_s_5b608a, statusLabel(TaskStatus.valueOf(it), task.categoryId)) })
                    }
                }
                if (window.rows.size < window.total) item {
                    OutlinedButton(enabled = !windowLoading && windowError == null, onClick = { limit = (limit.toLong() + HistoryBrowserRepository.PAGE_SIZE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() },
                        modifier = Modifier.fillMaxWidth().testTag("load_more_history")) { Text(tr(R.string.text_load_more_records_91a3c8)) }
                }
            }
        }
    }
    if(recordBatch) {
        val frozen=viewModel.batchRevisions
        fun close(){recordBatch=false;viewModel.clearBatch()}
        if(frozen.isEmpty())LostSelectionDialog(::close)
        else if(state.batchToken==viewModel.batchToken)TaskBatchDialog(state,"TRASH",frozen,viewModel,::close){close();clearSelection()}
        else AlertDialog(onDismissRequest=::close,title={Text(tr(R.string.text_reading_selected_records_420837))},text={Text(tr(R.string.text_checking_the_complete_selection_a09215))},confirmButton={},dismissButton={TextButton(onClick=::close){Text(tr(R.string.text_cancel_2cd0f3))}})
    }
    seriesBatchAction?.let {action ->
        val preview=viewModel.seriesSelectionPreview.takeIf {seriesBatchTicket==viewModel.seriesSelectionTicket}
        fun close(){seriesBatchAction=null;viewModel.seriesSelectionPreview=null}
        if(preview==null)LostSelectionDialog(::close) else SeriesSelectionDialog(preview,action=="STOP",viewModel,::close){close();clearSelection()}
    }
    actionId?.let { id -> state.series.find { it.id == id }?.let { item ->
        val preview = viewModel.seriesActionPreview?.takeIf { it.id == id && it.revision == expectedRevision }
        val count = preview?.activeCount ?: 0
        val future = preview?.futureCount ?: 0
        AlertDialog(onDismissRequest = { if (!busy) actionId = null }, title = { Text(if (action == "STOP") tr(R.string.text_stop_future_recurrence_01edf7) else tr(R.string.text_delete_series_cd87fd)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.title)
                if (action == "STOP") Text(tr(R.string.text_no_new_items_will_be_generated_8f5b2b, count)) else {
                    Text(tr(R.string.text_by_default_stop_recurrence_and_move_62ad18, future))
                    Row { Checkbox(history, onCheckedChange = { history = it }, enabled = !busy, modifier = Modifier.testTag("include_series_history")); Text(tr(R.string.text_include_all_s_records_including_completed_fccf15, count), Modifier.padding(top = 12.dp)) }
                    if (history) Text(tr(R.string.text_the_entire_series_and_its_records_f6edbe))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !busy && preview != null, modifier = Modifier.testTag("confirm_series_action"), onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        if (action == "STOP") viewModel.stopSeries(id, expectedRevision)
                        else viewModel.deleteSeries(id, expectedRevision, requireNotNull(preview).tasks, history)
                        actionId = null
                    } catch (exception: Exception) {
                        if (exception is kotlinx.coroutines.CancellationException) throw exception
                        error = exception.message ?: tr(R.string.text_operation_failed_please_try_again_6d73ff)
                    } finally { busy = false }
                }
            }) { Text(if (busy) tr(R.string.text_saving_00190a) else tr(R.string.text_confirm_dce52f)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { actionId = null }) { Text(tr(R.string.text_cancel_2cd0f3)) } })
    } }
}
