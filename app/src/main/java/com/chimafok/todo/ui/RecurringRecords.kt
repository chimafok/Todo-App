package com.chimafok.todo.ui

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
    val categories = remember(state.categories) { state.categories.associate { it.id to it.name } }
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
            if(latestWindowRequest==request){windowError="無法讀取重複紀錄，請重試。";completedWindowRequest=request}
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
            } else {viewModel.seriesSelectionPreview=null;if(selectingHistory)error="查詢已變更，請重新選取。"}
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
                error = exception.message ?: "無法預覽系列，請重試。"
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
                error = exception.message ?: "無法預覽系列，請關閉後重試。"
            } finally { busy = false }
        }
    }
    if (series.isEmpty()) {
        EmptyMessage(stringResource(R.string.recurring_empty_title), stringResource(R.string.recurring_empty_message))
    } else Column(Modifier.fillMaxSize()) {
        if (selectedSeries != null) TextButton(onClick = { selectedSeries = null }, modifier = Modifier.padding(horizontal = 12.dp).testTag("back_to_series")) { Text("‹ 所有系列") }
        OutlinedTextField(query, { query = it;recordsSelected=emptyMap();seriesSelected=emptyMap();selectionGeneration++ }, singleLine = true, placeholder = { Text("搜尋重複紀錄") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("recurring_search"), shape = MaterialTheme.shapes.medium)
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(if(selectedSeries==null) "${visibleSeries.size} 個系列" else "${window.total} 項紀錄",style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(vertical=12.dp))
            val normal=ButtonDefaults.textButtonColors()
            TextButton(enabled=canAct,onClick={if(selectingHistory)clearSelection() else {clearSelection();selectingHistory=true}},modifier=Modifier.testTag("history_toggle_selection"),
                colors=if(enabled && !busy && windowLoading && windowError==null && completedWindowRequest!=null)normal.copy(disabledContentColor=normal.contentColor) else normal) {Text(if(selectingHistory)"結束選取" else "多選")}
        }
        if(selectingHistory)FlowRow(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(selectedSeries==null)"已選 ${seriesSelected.size} 個系列" else "已選 ${recordsSelected.size} 項紀錄",Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.labelLarge)
            TextButton(enabled=canAct,onClick={if(selectedSeries==null)seriesSelected=visibleSeries.associate {it.id to it.revision} else selectAllRecords()},modifier=Modifier.testTag("history_select_results")) {Text(if(selectedSeries==null)"全選結果" else "全選可操作紀錄")}
            if(selectedSeries==null) {
                val allActive=seriesSelected.isNotEmpty() && seriesSelected.all {(id,rev)->series.any {it.id==id && it.revision==rev && it.lifecycle==SeriesLifecycle.ACTIVE}}
                TextButton(enabled=canAct && allActive,onClick={previewSeriesBatch(true)},modifier=Modifier.testTag("history_batch_stop")){Text("停止重複")}
                TextButton(enabled=canAct && seriesSelected.isNotEmpty(),onClick={previewSeriesBatch(false)},modifier=Modifier.testTag("history_batch_delete")){Text("刪除所選系列…")}
            } else TextButton(enabled=canAct && recordsSelected.isNotEmpty(),onClick={viewModel.prepareBatch(recordsSelected.toMap());recordBatch=true},modifier=Modifier.testTag("history_batch_trash")){Text("移至垃圾桶")}
        }
        DelayedLoadingIndicator(windowLoading, Modifier.testTag("history_loading"), listOf(selectedSeries, query, limit, state.dataEpoch, retry))
        windowError?.let { message -> Row(Modifier.padding(horizontal = 20.dp)) {
            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { retry++ }) { Text("重試") }
        } }
        if (actionId == null) error?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
        val selected = series.find { it.id == selectedSeries }
        LazyColumn(Modifier.fillMaxSize().testTag("recurring_list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected == null) {
                val visible = visibleSeries
                if (visible.isEmpty() && !windowLoading && windowError == null) item { EmptyTasks("找不到符合的系列") }
                items(visible, key = { it.id }) { item ->
                    val counts = window.counts[item.id] ?: SeriesCounts(item.id, 0, 0, 0)
                    val rule = latestRules[item.id]
                    Card(modifier=Modifier.fillMaxWidth().testTag("recurring_series_${item.id}").clip(MaterialTheme.shapes.large).combinedClickable(
                        enabled=canAct,role=Role.Button,onLongClickLabel="選取系列",onLongClick={selectSeries(item,true)},
                        onClick={if(selectingHistory)selectSeries(item,false) else selectedSeries=item.id}),
                        border=if(selectingHistory && item.id in seriesSelected)BorderStroke(1.dp,MaterialTheme.colorScheme.primary) else null) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row {
                                if(selectingHistory)Checkbox(item.id in seriesSelected,onCheckedChange={selectSeries(item,false)},enabled=canAct,modifier=Modifier.testTag("select_series_${item.id}"))
                                Text(item.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f).padding(vertical=8.dp))
                            }
                            Text("${rule?.frequency?.let(::recurrenceLabel).orEmpty()} · ${when (item.lifecycle) {
                                SeriesLifecycle.ACTIVE -> "重複中"; SeriesLifecycle.ENDED -> "已結束"; else -> "已停止"
                            }}", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
                            Text("${counts.completed} 已完成 · ${counts.pending} 未完成 · ${counts.cancelled} 規則取消",
                                style = MaterialTheme.typography.bodySmall)
                            if(!selectingHistory)Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (item.lifecycle == SeriesLifecycle.ACTIVE) TextButton(enabled = canAct, onClick = { openAction(item, "STOP") },
                                    modifier = Modifier.testTag("stop_series_${item.id}")) { Text("停止未來重複") }
                                TextButton(enabled = canAct, onClick = { openAction(item, "DELETE") }, modifier = Modifier.testTag("delete_series_${item.id}")) { Text("刪除系列…") }
                            }
                        }
                    }
                }
            } else {
                item { Text(selected.title, style = MaterialTheme.typography.titleLarge) }
                item { Text("已載入 ${window.rows.size} / ${window.total} 項紀錄", modifier = Modifier.testTag("history_page_summary"), style = MaterialTheme.typography.bodySmall) }
                if (window.rows.isEmpty() && !windowLoading && windowError == null) item { EmptyTasks("沒有符合的紀錄") }
                items(window.rows, key = { it.task.id }) { row ->
                    val task = row.task
                    val ref = row.occurrence
                    val snapshot = OccurrenceSnapshot.read(ref.snapshot)
                    val historical = ref.excludedByRule || task.status == TaskStatus.COMPLETED || task.dueDate!! < LocalDate.now(clock.withZone(ZoneId.of(task.zoneId ?: clock.zone.id)))
                    val shown = if (historical && snapshot != null) task.copy(title = snapshot.title, notes = snapshot.notes, imageIds = snapshot.imageIds) else task
                    val tagNames = if (historical && snapshot != null) snapshot.tags else window.tags[task.id].orEmpty()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("第 ${ref.sequence} 次 · ${task.dueDate}" + if (ref.excludedByRule) " · 規則取消" else if (snapshot?.late == true && task.status == TaskStatus.COMPLETED) " · 逾期完成" else "",
                            modifier = Modifier.testTag("occurrence_caption_${task.id}"),
                            style = MaterialTheme.typography.labelLarge, color = if (snapshot?.late == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                        TaskCard(shown, if (historical && snapshot != null) snapshot.category else categories[task.categoryId].orEmpty(),
                            clock, false, canAct, onEdit, onComplete, onReopen, onStatus, onAdvance, onDelete, {}, tags = tagNames,
                            readOnly = ref.excludedByRule,
                            selectionMode=selectingHistory,selected=task.id in recordsSelected,
                            onToggleSelection={selectRecord(task,false)},onLongSelect={selectRecord(task,true)},
                            historyDescription = if (ref.excludedByRule) "因系列規則縮短而取消；保留原紀錄，延長規則後可重新啟用。" else
                                snapshot?.status?.takeIf { it != task.status.name }?.let { "當時狀態：${statusLabel(TaskStatus.valueOf(it), task.categoryId)}" })
                    }
                }
                if (window.rows.size < window.total) item {
                    OutlinedButton(enabled = !windowLoading && windowError == null, onClick = { limit = (limit.toLong() + HistoryBrowserRepository.PAGE_SIZE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() },
                        modifier = Modifier.fillMaxWidth().testTag("load_more_history")) { Text("載入更多紀錄") }
                }
            }
        }
    }
    if(recordBatch) {
        val frozen=viewModel.batchRevisions
        fun close(){recordBatch=false;viewModel.clearBatch()}
        if(frozen.isEmpty())LostSelectionDialog(::close)
        else if(state.batchToken==viewModel.batchToken)TaskBatchDialog(state,"TRASH",frozen,viewModel,::close){close();clearSelection()}
        else AlertDialog(onDismissRequest=::close,title={Text("讀取所選紀錄")},text={Text("正在檢查完整選取範圍。")},confirmButton={},dismissButton={TextButton(onClick=::close){Text("取消")}})
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
        AlertDialog(onDismissRequest = { if (!busy) actionId = null }, title = { Text(if (action == "STOP") "停止未來重複？" else "刪除系列？") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.title)
                if (action == "STOP") Text("之後不再產生新項目，已產生的 $count 項紀錄與未完成任務都會保留。") else {
                    Text("預設停止重複，將 $future 項未來未完成項目移至垃圾桶，保留今天及過去紀錄。")
                    Row { Checkbox(history, onCheckedChange = { history = it }, enabled = !busy, modifier = Modifier.testTag("include_series_history")); Text("包含全部 $count 項紀錄（含已完成）", Modifier.padding(top = 12.dp)) }
                    if (history) Text("整個系列與紀錄將一起移至垃圾桶，30 天內可整組復原。")
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
                        error = exception.message ?: "操作失敗，請重試。"
                    } finally { busy = false }
                }
            }) { Text(if (busy) "儲存中…" else "確認") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { actionId = null }) { Text("取消") } })
    } }
}
