package com.chimafok.todo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.chimafok.todo.data.OrganizationDeletePreview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.chimafok.todo.R
import com.chimafok.todo.domain.*
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun OrganizationScreen(state: TodoState, viewModel: TodoViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingTag by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf("") }
    var editRevision by rememberSaveable { mutableLongStateOf(-1) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingTag by rememberSaveable { mutableStateOf(false) }
    var targetId by rememberSaveable { mutableStateOf(UNCATEGORIZED_ID) }
    var choices by rememberSaveable { mutableStateOf(emptyMap<String, String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by viewModel.organizationSelection
    val deletePreview = viewModel.organizationDeletePreview
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val ids = if (tab == 0) state.categories.map { it.id } else state.tags.map { it.id }
    val revisions = if (tab == 0) state.categories.associate { it.id to it.revision } else state.tags.associate { it.id to it.revision }
    val deletable = if (tab == 0) state.categories.filter { canDeleteCategory(it.id, it.systemKind) }.map { it.id } else ids
    var localOrder by remember(tab) { mutableStateOf<List<String>?>(null) }
    var dragged by remember(tab) { mutableStateOf<String?>(null) }
    var dragTop by remember { mutableFloatStateOf(0f) }
    var dragHeight by remember { mutableFloatStateOf(0f) }
    var beforeDrag by remember { mutableStateOf(emptyList<String>()) }
    var dragRevisions by remember { mutableStateOf(emptyMap<String, Long>()) }
    var edgeScroll by remember { mutableFloatStateOf(0f) }
    val order = localOrder ?: ids
    val liveOrder by rememberUpdatedState(order)
    fun updateDragOrder() {
        val id = dragged ?: return
        val middle = dragTop + dragHeight / 2
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key != id && item.key in liveOrder && middle >= item.offset && middle <= item.offset + item.size
        }?.key as? String ?: return
        val copy = liveOrder.toMutableList(); val from = copy.indexOf(id); val to = copy.indexOf(target)
        if (from >= 0 && to >= 0 && from != to) { copy.removeAt(from); copy.add(to, id); localOrder = copy }
    }
    LaunchedEffect(dragged, edgeScroll) {
        while (dragged != null && edgeScroll != 0f) {
            withFrameNanos { }
            listState.scrollBy(edgeScroll)
            updateDragOrder()
        }
    }
    LaunchedEffect(ids, revisions) { if (dragged == null) localOrder = null; selected = selected.filter { it in deletable } }
    BackHandler(selecting && deletePreview == null && !busy) { selecting = false; selected = emptyList() }
    fun toggle(id: String) { if (!busy && id in deletable) selected = if (id in selected) selected - id else selected + id }
    fun rowModifier(id: String): Modifier {
        val offset = listState.layoutInfo.visibleItemsInfo.find { it.key == id }?.offset ?: 0
        return Modifier.zIndex(if (dragged == id) 1f else 0f).graphicsLayer {
            if (dragged == id) { translationY = dragTop - offset; shadowElevation = 12.dp.toPx() }
        }.then(if (selecting && id in deletable) Modifier.clickable(enabled = !busy) { toggle(id) } else Modifier)
    }

    fun runOperation(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { block(); onSuccess() }
            catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                error = exception.message ?: "操作失敗，請重試。"
            } finally { busy = false }
        }
    }
    fun edit(id: String, name: String, revision: Long?, tag: Boolean) {
        editingTag = tag; editName = name; editRevision = revision ?: -1
        error = null; editingId = id
    }
    fun remove(id: String, tag: Boolean) {
        deletingTag = tag; targetId = UNCATEGORIZED_ID; choices = emptyMap()
        error = null; deletingId = id
    }

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(tab) {
            listOf("分類", "標籤").forEachIndexed { index, label ->
                Tab(tab == index, onClick = { if (!busy && dragged == null) { tab = index; error = null; selecting = false; selected = emptyList() } }, text = { Text(label) })
            }
        }
        LazyColumn(state = listState, userScrollEnabled = dragged == null, modifier = Modifier.weight(1f).testTag("org_list").semantics { stateDescription = if (dragged == null) "可長按拖拽排序" else "正在排序" }.pointerInput(tab, selecting, busy, ids) {
            if (!selecting && !busy) detectDragGesturesAfterLongPress(
                onDragStart = { position ->
                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key in ids && position.y >= it.offset && position.y < it.offset + it.size }
                    if (item != null) {
                        beforeDrag = ids; dragRevisions = revisions; localOrder = ids; dragged = item.key as String
                        dragTop = item.offset.toFloat(); dragHeight = item.size.toFloat()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }, onDrag = { change, delta ->
                    if (dragged != null) {
                        change.consume(); dragTop += delta.y; updateDragOrder()
                        edgeScroll = when { change.position.y < 96.dp.toPx() -> -14.dp.toPx()
                            change.position.y > listState.layoutInfo.viewportEndOffset - 72.dp.toPx() -> 14.dp.toPx(); else -> 0f }
                    }
                }, onDragCancel = { if (dragged != null) { dragged = null; localOrder = null }; edgeScroll = 0f },
                onDragEnd = {
                    val result = localOrder; dragged = null; edgeScroll = 0f
                    if (result != null && result != beforeDrag) runOperation {
                        try { viewModel.reorderOrganization(tab == 1, beforeDrag, result, dragRevisions) }
                        catch (e: Exception) { localOrder = null; throw e }
                    } else localOrder = null
                })
        }, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "actions") {
                if (selecting) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("已選 ${selected.size} 項", Modifier.weight(1f))
                        TextButton(enabled = !busy, onClick = { selected = if (selected.size == deletable.size) emptyList() else deletable }, modifier = Modifier.testTag("org_select_all")) { Text("全選") }
                        TextButton(enabled = !busy, onClick = { selecting = false; selected = emptyList() }) { Text("取消") }
                    }
                    Button(enabled = !busy && selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("org_batch_confirm"), onClick = {
                        runOperation { viewModel.organizationDeleteChoices = emptyMap(); viewModel.organizationDeletePreview = viewModel.previewOrganizationDelete(selected.toSet(), tab == 1) }
                    }) { Text("刪除所選 ${selected.size} 項") }
                } else {
                FilledTonalButton(onClick = { edit(UUID.randomUUID().toString(), "", null, tab == 1) },
                    enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("org_add")) {
                    Icon(painterResource(R.drawable.ic_add), null)
                    Spacer(Modifier.width(8.dp)); Text(if (tab == 0) "新增分類" else "新增標籤")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("長按拖拽可排序", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(enabled = !busy && deletable.isNotEmpty(), onClick = { selecting = true; selected = emptyList() }, modifier = Modifier.testTag("org_batch_delete")) { Text("批量刪除") }
                }
                }
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (tab == 0) itemsIndexed(order.mapNotNull { id -> state.categories.find { it.id == id } }, key = { _, item -> item.id }) { index, category ->
                OrganizationRow(category.id, category.name, when (category.systemKind) {
                    SystemCategory.UNCATEGORIZED -> "預設分類・不可改名或刪除"
                    SystemCategory.ASSIGNMENTS -> "作業四態・可改名，不可刪除"
                    null -> if (category.id == STUDY_ID) "學習三態・可改名，不可刪除" else "一般分類"
                }, false, busy || selecting, index > 0, index < state.categories.lastIndex,
                    modifier = rowModifier(category.id).animateItem(), selecting = selecting, selected = category.id in selected,
                    selectable = category.id in deletable, onSelect = { toggle(category.id) },
                    onRename = if (category.systemKind == SystemCategory.UNCATEGORIZED) null else {
                        { edit(category.id, category.name, category.revision, false) }
                    },
                    onDelete = if (!canDeleteCategory(category.id, category.systemKind)) null else { { remove(category.id, false) } },
                    onMove = { delta -> runOperation { viewModel.moveOrganization(category.id, false, delta, state.categories.map { it.id }) } })
            }
            else {
                if (state.tags.isEmpty()) item { EmptyMessage("還沒有標籤", "建立標籤後，可在任務表單勾選多個標籤。") }
                itemsIndexed(order.mapNotNull { id -> state.tags.find { it.id == id } }, key = { _, item -> item.id }) { index, tag ->
                    OrganizationRow(tag.id, tag.name, "可套用到多個任務", true, busy || selecting,
                        index > 0, index < state.tags.lastIndex,
                        modifier = rowModifier(tag.id).animateItem(), selecting = selecting, selected = tag.id in selected,
                        selectable = true, onSelect = { toggle(tag.id) },
                        onRename = { edit(tag.id, tag.name, tag.revision, true) },
                        onDelete = { remove(tag.id, true) },
                        onMove = { delta -> runOperation { viewModel.moveOrganization(tag.id, true, delta, state.tags.map { it.id }) } })
                }
            }
        }
    }

    deletePreview?.let { preview ->
        OrganizationBatchDeleteDialog(preview, busy, error, viewModel.organizationDeleteChoices, { viewModel.organizationDeleteChoices = it },
            onDismiss = { if (!busy) { viewModel.organizationDeletePreview = null; error = null } },
            onConfirm = { target, statuses -> runOperation(onSuccess = {
                viewModel.organizationDeletePreview = null; selecting = false; selected = emptyList()
            }) { viewModel.deleteOrganizationBatch(preview, target, statuses) } })
    }
    editingId?.let { id ->
        AlertDialog(onDismissRequest = { if (!busy) { editingId = null; error = null } },
            title = { Text((if (editRevision < 0) "新增" else "重新命名") + if (editingTag) "標籤" else "分類") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(editName, { editName = it }, label = { Text("名稱") }, singleLine = true,
                    enabled = !busy, isError = error != null, modifier = Modifier.testTag("org_name"))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("org_save"), onClick = {
                runOperation(onSuccess = { editingId = null }) {
                    if (editingTag) viewModel.saveTag(id, editName, editRevision.takeIf { it >= 0 })
                    else viewModel.saveCategory(id, editName, editRevision.takeIf { it >= 0 })
                }
            }) { Text(if (busy) "儲存中…" else "儲存") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { editingId = null; error = null }) { Text("取消") } })
    }
    if (deletingTag) state.tags.find { it.id == deletingId }?.let { tag ->
        AlertDialog(onDismissRequest = { if (!busy) deletingId = null }, title = { Text("刪除標籤？") },
            text = { Column { Text("移除「${tag.name}」及其連結，任務會保留。"); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
            confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("org_delete_confirm"), onClick = {
                runOperation(onSuccess = { deletingId = null }) { viewModel.deleteTag(tag) }
            }) { Text("刪除標籤") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deletingId = null; error = null }) { Text("取消") } })
    }
    else state.categories.find { it.id == deletingId }?.let { category ->
        val affected = state.tasks.filter { it.categoryId == category.id }
        val targets = state.categories.filter { it.id != category.id }
        val target = targets.find { it.id == targetId }
        val assignment = target?.systemKind == SystemCategory.ASSIGNMENTS
        AlertDialog(onDismissRequest = { if (!busy) deletingId = null }, title = { Text("刪除分類並搬移任務") },
            text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("「${category.name}」的 ${affected.size} 項任務（含已完成及垃圾桶）將移到選定分類，任務不會刪除。")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    targets.forEach { item -> FilterChip(item.id == targetId,
                        onClick = { targetId = item.id; choices = emptyMap() }, enabled = !busy,
                        label = { Text(item.name) }, modifier = Modifier.testTag("move_target_${item.id}")) }
                }
                if (assignment) affected.forEach { task ->
                    val cancelled = state.occurrences.any { it.taskId == task.id && it.excludedByRule }
                    Text(task.title + if (cancelled) "（規則取消，僅對應為未開始）" else "", style = MaterialTheme.typography.bodyMedium)
                    var expanded by remember(task.id) { mutableStateOf(false) }
                    Box {
                        OutlinedButton(enabled = !busy, onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag("org_assignment_${task.id}")) {
                            Text(choices[task.id]?.let { statusLabel(TaskStatus.valueOf(it)) } ?: "請選擇作業狀態")
                        }
                        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                            TaskStatus.entries.filter { it != TaskStatus.OPEN && (!cancelled || it == TaskStatus.NOT_STARTED) }.forEach { status ->
                                DropdownMenuItem(text = { Text(statusLabel(status)) }, leadingIcon = { AssignmentStatusIcon(status) },
                                    onClick = { choices = choices + (task.id to status.name); expanded = false })
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !busy && target != null && (!assignment || affected.all { it.id in choices }),
                modifier = Modifier.testTag("org_delete_confirm"), onClick = {
                    runOperation(onSuccess = { deletingId = null }) {
                        viewModel.deleteCategory(category, targetId, affected.associate { it.id to it.revision }, choices.mapValues { TaskStatus.valueOf(it.value) })
                    }
                }) { Text("搬移並刪除分類") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deletingId = null; error = null }) { Text("取消") } })
    }
}

@Composable
private fun OrganizationRow(id: String, name: String, description: String, tag: Boolean, busy: Boolean,
    canMoveUp: Boolean, canMoveDown: Boolean, modifier: Modifier = Modifier,
    selecting: Boolean = false, selected: Boolean = false, selectable: Boolean = true, onSelect: () -> Unit = {}, onRename: (() -> Unit)?, onDelete: (() -> Unit)?, onMove: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(modifier = modifier.testTag("org_row_$id"), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        ListItem(colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            headlineContent = { Text(name) }, supportingContent = { Text(description) },
            leadingContent = { if (selecting) Checkbox(checked = selected, enabled = selectable, onCheckedChange = { onSelect() }, modifier = Modifier.testTag("org_select_$id")) else Icon(painterResource(if (tag) R.drawable.ic_tag else R.drawable.ic_categories), null,
                tint = if (tag) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary) },
            trailingContent = { if (!selecting) Box {
                IconButton(enabled = !busy, onClick = { menu = true }, modifier = Modifier.testTag("org_menu_$id")) {
                    Icon(painterResource(R.drawable.ic_more), "管理：$name")
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    onRename?.let { DropdownMenuItem(text = { Text("重新命名") }, onClick = { menu = false; it() }) }
                    DropdownMenuItem(text = { Text("上移") }, enabled = canMoveUp, onClick = { menu = false; onMove(-1) })
                    DropdownMenuItem(text = { Text("下移") }, enabled = canMoveDown, onClick = { menu = false; onMove(1) })
                    onDelete?.let { DropdownMenuItem(text = { Text("刪除") }, onClick = { menu = false; it() }) }
                }
            } })
    }
}
