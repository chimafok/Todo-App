package com.chimafok.todo.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.chimafok.todo.R
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun TaskBrowser(
    state: TodoState, destination: Destination, homeEpoch: Int, clock: Clock, jumpDate: LocalDate?,
    onClearJump: () -> Unit, onAdd: (LocalDate?) -> Unit, onEdit: (TaskEntity) -> Unit,
    onComplete: (TaskEntity) -> Unit, onReopen: (TaskEntity) -> Unit, onStatus: (TaskEntity) -> Unit,
    onAdvance: (TaskEntity) -> Unit, onDelete: (TaskEntity) -> Unit, onRestore: (TaskEntity) -> Unit,
    actionsEnabled: Boolean, finishingTasks: Map<String, TaskEntity>, completionProgress: Map<String, Float>,
    viewModel: TodoViewModel, onSelectionMode: (Boolean) -> Unit,
) {
    var deleteFutureSeriesId by rememberSaveable { mutableStateOf<String?>(null) }
    deleteFutureSeriesId?.let { id ->
        DeleteFutureSeriesDialog(id, state, viewModel, clock) { deleteFutureSeriesId = null }
    }
    var showPurge by rememberSaveable { mutableStateOf(false) }
    var purgeTicket by rememberSaveable { mutableStateOf("") }
    fun confirmPurge(task: TaskEntity? = null) {
        viewModel.purgePreview = PurgePreview(
            if (task != null) mapOf(task.id to task.revision) else state.tasks.filter { it.deletedAt != null }.associate { it.id to it.revision },
            if (task != null) emptyMap() else state.series.filter { it.deletedAt != null }.associate { it.id to it.revision }, task == null)
        purgeTicket = java.util.UUID.randomUUID().toString(); viewModel.purgeTicket = purgeTicket
        showPurge = true
    }
    if (showPurge) {
        val frozen = viewModel.purgePreview.takeIf { purgeTicket == viewModel.purgeTicket }
        if (frozen != null) PurgeDialog(frozen, viewModel) { showPurge = false; viewModel.purgePreview = null }
        else AlertDialog(onDismissRequest = { showPurge = false }, title = { Text("請重新預覽") },
            text = { Text("程式已重新啟動，舊刪除預覽已失效。請關閉後重新選取範圍。") },
            confirmButton = { TextButton(onClick = { showPurge = false }) { Text("關閉") } })
    }
    var query by rememberSaveable(destination, homeEpoch) { mutableStateOf("") }
    var categoryIds by rememberSaveable(destination, homeEpoch) { mutableStateOf(emptyList<String>()) }
    var tagIds by rememberSaveable(destination, homeEpoch) { mutableStateOf(emptyList<String>()) }
    var allTags by rememberSaveable(destination, homeEpoch) { mutableStateOf(true) }
    var showFilters by rememberSaveable(destination, homeEpoch) { mutableStateOf(false) }
    var selecting by rememberSaveable(destination, homeEpoch) { mutableStateOf(false) }
    var selected by viewModel.taskSelection
    var selectedTrashSeries by viewModel.trashSeriesSelection
    var selectionGeneration by remember {mutableIntStateOf(0)}
    var selectionBusy by remember {mutableStateOf(false)}
    var selectionError by remember {mutableStateOf<String?>(null)}
    var trashSelectionAction by rememberSaveable(destination,homeEpoch) {mutableStateOf<String?>(null)}
    var trashSelectionTicket by rememberSaveable(destination,homeEpoch) {mutableStateOf("")}
    val selectionScope=rememberCoroutineScope()
    val selectedSet=remember(selected){selected.toSet()}
    var batch by rememberSaveable(destination, homeEpoch) { mutableStateOf<String?>(null) }
    val pendingRevisions = viewModel.batchRevisions
    val filter = TaskFilter(query, categoryIds.toSet(), tagIds.toSet(), allTags)
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = when(destination) { Destination.AGENDA->TaskScope.AGENDA;Destination.UNSCHEDULED->TaskScope.UNSCHEDULED;Destination.COMPLETED->TaskScope.COMPLETED;else->TaskScope.TRASH }
    val paged = destination != Destination.TRASH
    var datedLimit by rememberSaveable(destination,homeEpoch,query,categoryIds,tagIds,allTags) { mutableIntStateOf(TaskPageRepository.PAGE_SIZE) }
    var undatedLimit by rememberSaveable(destination,homeEpoch,query,categoryIds,tagIds,allTags) { mutableIntStateOf(TaskPageRepository.PAGE_SIZE) }
    val initialHome = remember(destination,homeEpoch) {
        if(destination == Destination.AGENDA && !filter.active && jumpDate == null) viewModel.cachedHomePage(clock) else null
    }
    var window by remember(destination,homeEpoch) { mutableStateOf(initialHome ?: TaskPageWindow()) }
    var committedFilter by remember(destination,homeEpoch) { mutableStateOf<TaskFilter?>(if(initialHome != null) TaskFilter() else null) }
    var retry by remember { mutableIntStateOf(0) }
    val pageRequest=listOf(destination,homeEpoch,filter,clock,jumpDate,datedLimit,undatedLimit,state.dataEpoch,retry)
    var completedRequest by remember(destination,homeEpoch) { mutableStateOf<List<Any?>?>(null) }
    val pageLoading=paged && completedRequest!=pageRequest
    var pageError by remember(destination,homeEpoch) { mutableStateOf<String?>(null) }
    val latestFinishing by rememberUpdatedState(finishingTasks)
    LaunchedEffect(destination,homeEpoch) { viewModel.resetSelectionIfChanged(destination,homeEpoch) }
    DisposableEffect(destination,homeEpoch) { onDispose { viewModel.clearPage() } }
    LaunchedEffect(paged,destination,filter,clock,jumpDate,datedLimit,undatedLimit,state.dataEpoch,retry) {
        if (!paged) return@LaunchedEffect
        pageError=null
        val expectedEpoch=state.dataEpoch
        try {
            val loaded=viewModel.taskWindow(scope,filter,clock,jumpDate,datedLimit,undatedLimit)
            if(viewModel.state.value.dataEpoch==expectedEpoch) {
                val retained=latestFinishing.keys
                val old=window.rows
                val rows=loaded.rows.copy(
                    links=loaded.rows.links+old.links.filter { it.taskId in retained && it !in loaded.rows.links },
                    occurrences=loaded.rows.occurrences+old.occurrences.filter { it.taskId in retained && it !in loaded.rows.occurrences },
                    reminders=loaded.rows.reminders+old.reminders.filter { it.taskId in retained && it !in loaded.rows.reminders })
                window=loaded.copy(rows=rows)
                committedFilter=filter
                if(destination == Destination.AGENDA && !filter.active && jumpDate == null &&
                    datedLimit == TaskPageRepository.PAGE_SIZE && undatedLimit == TaskPageRepository.PAGE_SIZE)
                    viewModel.retainHomePage(window,clock)
                viewModel.presentPage(rows)
                completedRequest=pageRequest
            }
        } catch(e:Exception) {
            if(e is kotlinx.coroutines.CancellationException) throw e
            pageError="無法讀取任務，請重試。"
            completedRequest=pageRequest
        }
    }
    val finishingIds=finishingTasks.keys.toSet()
    val sourceLinks=if(paged) window.rows.links else state.taskTags
    val searchIndex=remember(state.categories,state.tags,sourceLinks) { TaskSearchIndex(state.categories,state.tags,sourceLinks) }
    val matching=if(paged) {
        window.rows.tasks+searchIndex.filter(finishingTasks.values.filter { saved->window.rows.tasks.none { it.id==saved.id } },filter)
    } else remember(state.tasks,searchIndex,filter) { searchIndex.filter(state.tasks,filter) }
    val resultIds=if(paged) window.revisions.keys else scopedTasks(matching,scope,clock).map { it.id }.toSet()
    val ready=actionsEnabled && !pageLoading && pageError==null && !selectionBusy
    val latestPageRequest by rememberUpdatedState(pageRequest)
    val selectionColors=ButtonDefaults.textButtonColors()
    // A pending query still blocks selection, but should not flash its label grey
    // on every keystroke. Genuine loading/error/blocked states keep normal styling.
    val stableSelectionColors=if(actionsEnabled && pageLoading && pageError==null && committedFilter!=null)
        selectionColors.copy(disabledContentColor=selectionColors.contentColor) else selectionColors
    // Include the first data page and its UI draw in Android's full-startup metric.
    ReportDrawnWhen { ready }
    LaunchedEffect(resultIds,pageLoading) { if(!pageLoading && pageError==null) selected=selected.filter { it in resultIds } }
    LaunchedEffect(batch) { if(batch==null) viewModel.clearBatch() else if(pendingRevisions.isEmpty()) batch=null }
    LaunchedEffect(state.categories.map { it.id }, state.tags.map { it.id }) {
        val validCategories = categoryIds.filter { id -> state.categories.any { it.id == id } }
        val validTags = tagIds.filter { id -> state.tags.any { it.id == id } }
        if (validCategories != categoryIds || validTags != tagIds) {
            categoryIds = validCategories; tagIds = validTags; selected = emptyList()
        }
    }
    LaunchedEffect(selecting) { onSelectionMode(selecting) }
    DisposableEffect(Unit) { onDispose { onSelectionMode(false) } }
    BackHandler(selecting) { selecting = false; selected = emptyList();selectedTrashSeries=emptyList();selectionGeneration++ }
    LaunchedEffect(state.series) { val valid=state.series.filter {it.deletedAt!=null}.map {it.id}.toSet();selectedTrashSeries=selectedTrashSeries.filter {it in valid} }
    fun longSelect(id:String,series:Boolean=false) {
        if(!ready || (!series && id !in resultIds))return
        if(!selecting){selected=emptyList();selectedTrashSeries=emptyList();selectionGeneration++}
        selecting=true
        if(series) selectedTrashSeries=(selectedTrashSeries+id).distinct() else selected=(selected+id).distinct()
    }
    fun previewTrash(restore:Boolean) {
        if(!ready)return
        val revisions=matching.filter {it.id in selectedSet}.associate {it.id to it.revision}
        val series=state.series.filter {it.id in selectedTrashSeries && it.deletedAt!=null}.associate {it.id to it.revision}
        if(revisions.size!=selected.size || series.size!=selectedTrashSeries.size){selectionError="選取範圍已更新，請重新選取。";return}
        val request=pageRequest;val generation=selectionGeneration
        selectionBusy=true;selectionError=null
        selectionScope.launch {try {
            viewModel.prepareTrashSelection(revisions,series)
            if(latestPageRequest==request && generation==selectionGeneration && selecting) {
                trashSelectionTicket=java.util.UUID.randomUUID().toString();viewModel.trashSelectionTicket=trashSelectionTicket
                trashSelectionAction=if(restore)"RESTORE" else "PURGE"
            } else {viewModel.trashSelectionPreview=null;if(selecting)selectionError="查詢已變更，請重新選取。"}
        } catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;selectionError=e.message}
          finally {selectionBusy=false}}
    }
    fun beginBatch(action: String) {
        val revisions=window.revisions.filterKeys { it in selectedSet }
        if(revisions.isNotEmpty()) { viewModel.prepareBatch(revisions);batch=action }
    }
    Column(Modifier.fillMaxSize()) {
        if (destination == Destination.TRASH) TrashToolbar(state,viewModel,clock,ready,{confirmPurge()},
            selectionMode=selecting,selectedSeries=selectedTrashSeries.toSet(),
            onToggleSeries={id->if(ready)selectedTrashSeries=if(id in selectedTrashSeries)selectedTrashSeries-id else selectedTrashSeries+id},
            onLongSeries={longSelect(it,true)},onSelectAllSeries={if(ready)selectedTrashSeries=state.series.filter {it.deletedAt!=null}.map {it.id}})
        OutlinedTextField(query, {
            if (query != it) { query = it; selected = emptyList();selectedTrashSeries=emptyList();selectionGeneration++ }
        }, placeholder = { Text("搜尋任務") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = ""; selected = emptyList();selectedTrashSeries=emptyList();selectionGeneration++ }, modifier = Modifier.testTag("clear_search")) {
                Icon(painterResource(R.drawable.ic_close), "清除搜尋")
            } }, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).testTag("task_search"))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showFilters = true }, modifier = Modifier.testTag("open_filters")) {
                Icon(painterResource(R.drawable.ic_filter), null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text(if (categoryIds.isEmpty() && tagIds.isEmpty()) "篩選" else "篩選 ${categoryIds.size + tagIds.size}")
            }
            Text("${resultIds.size} 項", Modifier.weight(1f).testTag("result_count"), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(enabled = ready, onClick = { selecting = !selecting; selected = emptyList();selectedTrashSeries=emptyList();selectionGeneration++ },
                colors = stableSelectionColors, modifier = Modifier.testTag("toggle_selection")) { Text(if (selecting) "結束選取" else "多選") }
        }
        if (selecting) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("已選 ${selected.size} 項" + if(scope==TaskScope.TRASH) "任務 · ${selectedTrashSeries.size} 個系列" else "", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelLarge)
                TextButton(enabled = ready && resultIds.isNotEmpty(), onClick = { selected = resultIds.toList() },
                    modifier = Modifier.testTag("select_results")) { Text(if(scope==TaskScope.TRASH)"全選任務結果" else "全選結果") }
                if(scope==TaskScope.TRASH) {
                    TextButton(enabled=ready && (selected.isNotEmpty() || selectedTrashSeries.isNotEmpty()),onClick={previewTrash(true)},modifier=Modifier.testTag("batch_restore")) {Text("復原所選")}
                    TextButton(enabled=ready && (selected.isNotEmpty() || selectedTrashSeries.isNotEmpty()),onClick={previewTrash(false)},modifier=Modifier.testTag("batch_purge"),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) {Text("永久刪除所選…")}
                } else {
                TextButton(enabled = ready && selected.isNotEmpty(), onClick = { beginBatch("MOVE") }, modifier = Modifier.testTag("batch_move")) { Text("移分類") }
                TextButton(enabled = ready && selected.isNotEmpty(), onClick = { beginBatch("TAGS") }, modifier = Modifier.testTag("batch_tags")) { Text("加標籤") }
                TextButton(enabled = ready && selected.isNotEmpty(), onClick = { beginBatch("TRASH") }, modifier = Modifier.testTag("batch_trash"),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("移至垃圾桶") }
                }
            }
        }
        selectionError?.let {Text(it,Modifier.padding(horizontal=20.dp),color=MaterialTheme.colorScheme.error)}
        DelayedLoadingIndicator(pageLoading, Modifier.testTag("task_loading"), pageRequest)
        pageError?.let { message->Row(Modifier.padding(horizontal=20.dp)) {
            Text(message,Modifier.weight(1f),color=MaterialTheme.colorScheme.error)
            TextButton(onClick={retry++}) { Text("重試") }
        } }
        val displayState=if(paged) state.copy(tasks=matching,taskTags=window.rows.links,occurrences=window.rows.occurrences,
            reminders=window.rows.reminders,reminderOverrides=window.rows.overrides) else state.copy(tasks=matching)
        val hasCompletionFeedback = matching.any { it.id in finishingIds }
        // Render the last committed result kind while the next query runs. An empty
        // search must not briefly become the ordinary agenda on every keystroke.
        val displayFilter = if(paged) committedFilter else filter
        if (displayFilter?.active == true && resultIds.isEmpty() && pageError==null && jumpDate == null && !hasCompletionFeedback) {
            EmptyMessage("找不到符合的任務", "調整搜尋文字或篩選條件再試一次。")
        } else if(paged && committedFilter == null && filter.active) {
            Spacer(Modifier.weight(1f))
        } else TaskList(displayState, destination, clock, jumpDate, onClearJump, onAdd, onEdit,
            onComplete, onReopen, onStatus, onAdvance, onDelete, onRestore, ready,
            finishingTasks, completionProgress, selectionMode = selecting, selectedIds = selectedSet,
            onToggleSelection = { id -> if(ready && id in resultIds)selected = if (id in selected) selected - id else selected + id },
            onLongSelect = {longSelect(it)},groupCounts=if(paged)window.groupCounts else null, filtered = displayFilter?.active == true,
            onPurge = { confirmPurge(it) },
            moreDated=paged && window.moreDated,moreUndated=paged && window.moreUndated,jumpReady=!pageLoading,
            onMoreDated={datedLimit=(datedLimit.toLong()+50).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()},
            onMoreUndated={undatedLimit=(undatedLimit.toLong()+50).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()},
            onDeleteFuture = { deleteFutureSeriesId = it })
    }
    trashSelectionAction?.let { action ->
        val preview=viewModel.trashSelectionPreview.takeIf {trashSelectionTicket==viewModel.trashSelectionTicket}
        fun close(){trashSelectionAction=null;viewModel.trashSelectionPreview=null}
        if(preview==null)LostSelectionDialog(::close) else TrashSelectionDialog(preview,action=="RESTORE",viewModel,::close) {
            close();selected=emptyList();selectedTrashSeries=emptyList();selecting=false
        }
    }
    if (showFilters) TaskFilterDialog(state, categoryIds, tagIds, allTags,
        onDismiss = { showFilters = false }, onApply = { categories, tags, all ->
            if (categoryIds != categories || tagIds != tags || allTags != all) {selected = emptyList();selectedTrashSeries=emptyList();selectionGeneration++}
            categoryIds = categories; tagIds = tags; allTags = all; showFilters = false
        })
    batch?.takeIf { pendingRevisions.isNotEmpty() }?.let { action ->
        val available=state.tasks.map { it.id }.toSet()
        if(state.batchToken==viewModel.batchToken || available.containsAll(pendingRevisions.keys)) TaskBatchDialog(state,action,pendingRevisions,viewModel,
            onDismiss={batch=null;viewModel.clearBatch()},onSaved={batch=null;viewModel.clearBatch();selected=emptyList();selecting=false})
        else AlertDialog(onDismissRequest={batch=null;viewModel.clearBatch()},title={Text("讀取所選任務")},
            text={Column { LinearProgressIndicator(Modifier.fillMaxWidth());Text("正在準備完整操作範圍…") }},
            confirmButton={},dismissButton={TextButton(onClick={batch=null;viewModel.clearBatch()}) {Text("取消")}})
    }
}

@Composable
private fun TaskFilterDialog(state: TodoState, initialCategories: List<String>, initialTags: List<String>, initialAll: Boolean,
    onDismiss: () -> Unit, onApply: (List<String>, List<String>, Boolean) -> Unit) {
    var categories by rememberSaveable { mutableStateOf(initialCategories) }
    var tags by rememberSaveable { mutableStateOf(initialTags) }
    var all by rememberSaveable { mutableStateOf(initialAll) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("篩選任務") },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("分類（符合任一）", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { state.categories.forEach { category ->
                FilterChip(category.id in categories, onClick = { categories = if (category.id in categories) categories - category.id else categories + category.id },
                    label = { Text(category.name) }, modifier = Modifier.testTag("filter_category_${category.id}"))
            } }
            HorizontalDivider()
            Text("標籤", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(all, onClick = { all = true }, label = { Text("符合全部") }, modifier = Modifier.testTag("filter_all_tags"))
                FilterChip(!all, onClick = { all = false }, label = { Text("符合任一") }, modifier = Modifier.testTag("filter_any_tags"))
            }
            if (state.tags.isEmpty()) Text("還沒有標籤", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { state.tags.forEach { tag ->
                FilterChip(tag.id in tags, onClick = { tags = if (tag.id in tags) tags - tag.id else tags + tag.id },
                    label = { Text(tag.name) }, modifier = Modifier.testTag("filter_tag_${tag.id}"))
            } }
            TextButton(onClick = { onApply(emptyList(), emptyList(), true) }) { Text("清除篩選") }
        } },
        confirmButton = { TextButton(onClick = { onApply(categories, tags, all) }, modifier = Modifier.testTag("apply_filters")) { Text("套用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
