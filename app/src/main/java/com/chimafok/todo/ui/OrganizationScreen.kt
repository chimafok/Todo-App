package com.chimafok.todo.ui

import com.chimafok.todo.i18n.tr
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
        return Modifier.zIndex(if (dragged == id) 1f else 0f).graphicsLayer {
            if (dragged == id) {
                val offset = listState.layoutInfo.visibleItemsInfo.find { it.key == id }?.offset ?: 0
                translationY = dragTop - offset; shadowElevation = 12.dp.toPx()
            }
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
                error = exception.message ?: tr(R.string.text_operation_failed_please_try_again_6d73ff)
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
            listOf(tr(R.string.text_categories_4100e9), tr(R.string.text_tags_9b423a)).forEachIndexed { index, label ->
                Tab(tab == index, onClick = { if (!busy && dragged == null) { tab = index; error = null; selecting = false; selected = emptyList() } }, text = { Text(label) })
            }
        }
        LazyColumn(state = listState, userScrollEnabled = dragged == null, modifier = Modifier.weight(1f).testTag("org_list").semantics { stateDescription = if (dragged == null) tr(R.string.text_long_press_and_drag_to_reorder_37c367) else tr(R.string.text_reordering_37e5dd) }.pointerInput(tab, selecting, busy, ids) {
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
                    SelectionToolbar(tr(R.string.text_s_selected_a5726a, selected.size), summaryTag="org_selection_count") {
                        SelectionTextButton(enabled = !busy,
                            onClick = { selected = if (selected.size == deletable.size) emptyList() else deletable }, modifier = Modifier.testTag("org_select_all")) { Text(tr(R.string.text_select_all_1ffe5f)) }
                        SelectionTextButton(enabled = !busy, onClick = { selecting = false; selected = emptyList() }) { Text(tr(R.string.text_cancel_2cd0f3)) }
                    }
                    Button(enabled = !busy && selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("org_batch_confirm"), onClick = {
                        runOperation { viewModel.organizationDeleteChoices = emptyMap(); viewModel.organizationDeletePreview = viewModel.previewOrganizationDelete(selected.toSet(), tab == 1) }
                    }) { Text(tr(R.string.text_delete_s_selected_items_9c41ed, selected.size)) }
                } else {
                FilledTonalButton(onClick = { edit(UUID.randomUUID().toString(), "", null, tab == 1) },
                    enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("org_add")) {
                    Icon(painterResource(R.drawable.ic_add), null)
                    Spacer(Modifier.width(8.dp)); Text(if (tab == 0) tr(R.string.text_add_category_ebc538) else tr(R.string.text_add_tag_bc4915))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(R.string.text_long_press_and_drag_to_reorder_e8d8a9), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(enabled = !busy && deletable.isNotEmpty(), onClick = { selecting = true; selected = emptyList() }, modifier = Modifier.testTag("org_batch_delete")) { Text(tr(R.string.text_bulk_delete_1d0654)) }
                }
                }
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (tab == 0) itemsIndexed(order.mapNotNull { id -> state.categories.find { it.id == id } }, key = { _, item -> item.id }) { index, category ->
                OrganizationRow(category.id, com.chimafok.todo.i18n.categoryName(category), when (category.systemKind) {
                    SystemCategory.UNCATEGORIZED -> tr(R.string.text_default_category_cannot_rename_or_delete_7233e2)
                    SystemCategory.ASSIGNMENTS -> tr(R.string.text_four_assignment_states_can_rename_cannot_bfc523)
                    null -> if (category.id == STUDY_ID) tr(R.string.text_three_study_states_can_rename_cannot_29e987) else tr(R.string.text_general_category_1d11fe)
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
                if (state.tags.isEmpty()) item { EmptyMessage(tr(R.string.text_no_tags_yet_4bf18d), tr(R.string.text_create_tags_to_select_multiple_tags_1759ae)) }
                itemsIndexed(order.mapNotNull { id -> state.tags.find { it.id == id } }, key = { _, item -> item.id }) { index, tag ->
                    OrganizationRow(tag.id, tag.name, tr(R.string.text_can_apply_to_multiple_tasks_e476e3), true, busy || selecting,
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
            title = { Text((if (editRevision < 0) tr(R.string.text_add_0006d6) else tr(R.string.text_rename_a60560)) + if (editingTag) tr(R.string.text_tags_9b423a) else tr(R.string.text_categories_4100e9)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(editName, { editName = it }, label = { Text(tr(R.string.text_name_a5274f)) }, singleLine = true,
                    enabled = !busy, isError = error != null, modifier = Modifier.testTag("org_name"))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("org_save"), onClick = {
                runOperation(onSuccess = { editingId = null }) {
                    if (editingTag) viewModel.saveTag(id, editName, editRevision.takeIf { it >= 0 })
                    else viewModel.saveCategory(id, editName, editRevision.takeIf { it >= 0 })
                }
            }) { Text(if (busy) tr(R.string.text_saving_00190a) else tr(R.string.text_save_dba44e)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { editingId = null; error = null }) { Text(tr(R.string.text_cancel_2cd0f3)) } })
    }
    if (deletingTag) state.tags.find { it.id == deletingId }?.let { tag ->
        AlertDialog(onDismissRequest = { if (!busy) deletingId = null }, title = { Text(tr(R.string.text_delete_tag_6ffdc8)) },
            text = { Column { Text(tr(R.string.text_remove_s_and_its_links_tasks_c6438e, tag.name)); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
            confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("org_delete_confirm"), onClick = {
                runOperation(onSuccess = { deletingId = null }) { viewModel.deleteTag(tag) }
            }) { Text(tr(R.string.text_delete_tags_bceb1d)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deletingId = null; error = null }) { Text(tr(R.string.text_cancel_2cd0f3)) } })
    }
    else state.categories.find { it.id == deletingId }?.let { category ->
        val affected = state.tasks.filter { it.categoryId == category.id }
        val targets = state.categories.filter { it.id != category.id }
        val target = targets.find { it.id == targetId }
        val assignment = target?.systemKind == SystemCategory.ASSIGNMENTS
        AlertDialog(onDismissRequest = { if (!busy) deletingId = null }, title = { Text(tr(R.string.text_delete_category_and_move_tasks_e0f87b)) },
            text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr(R.string.text_move_s_tasks_in_s_including_a20895, category.name, affected.size))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    targets.forEach { item -> FilterChip(item.id == targetId,
                        onClick = { targetId = item.id; choices = emptyMap() }, enabled = !busy,
                        label = { Text(com.chimafok.todo.i18n.categoryName(item)) }, modifier = Modifier.testTag("move_target_${item.id}")) }
                }
                if (assignment) affected.forEach { task ->
                    val cancelled = state.occurrences.any { it.taskId == task.id && it.excludedByRule }
                    Text(task.title + if (cancelled) tr(R.string.text_rule_canceled_maps_only_to_not_829086) else "", style = MaterialTheme.typography.bodyMedium)
                    var expanded by remember(task.id) { mutableStateOf(false) }
                    Box {
                        OutlinedButton(enabled = !busy, onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag("org_assignment_${task.id}")) {
                            Text(choices[task.id]?.let { statusLabel(TaskStatus.valueOf(it)) } ?: tr(R.string.text_choose_an_assignment_status_3fcfdf))
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
                }) { Text(tr(R.string.text_move_and_delete_categories_1e6482)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deletingId = null; error = null }) { Text(tr(R.string.text_cancel_2cd0f3)) } })
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
                    Icon(painterResource(R.drawable.ic_more), tr(R.string.text_manage_s_9d46c8, name))
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    onRename?.let { DropdownMenuItem(text = { Text(tr(R.string.text_rename_a60560)) }, onClick = { menu = false; it() }) }
                    DropdownMenuItem(text = { Text(tr(R.string.text_move_up_f853a7)) }, enabled = canMoveUp, onClick = { menu = false; onMove(-1) })
                    DropdownMenuItem(text = { Text(tr(R.string.text_move_down_e75e8b)) }, enabled = canMoveDown, onClick = { menu = false; onMove(1) })
                    onDelete?.let { DropdownMenuItem(text = { Text(tr(R.string.text_delete_3c8f5b)) }, onClick = { menu = false; it() }) }
                }
            } })
    }
}
