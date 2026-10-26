package com.chimafok.todo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chimafok.todo.data.TaskEntity
import com.chimafok.todo.data.orderedFor
import com.chimafok.todo.R
import com.chimafok.todo.domain.*
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun statusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.OPEN -> "未完成"
    TaskStatus.NOT_STARTED -> "未開始"
    TaskStatus.IN_PROGRESS -> "進行中"
    TaskStatus.DONE_UNSUBMITTED -> "完成未繳交"
    TaskStatus.COMPLETED -> "完成"
}

fun displayDate(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "M/d" else "yyyy/M/d"))

@Composable
fun EmptyMessage(title: String, message: String = "") {
    Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun EmptyTasks(title: String) {
    Text(title,Modifier.fillMaxWidth().padding(start=4.dp,top=4.dp,bottom=8.dp),
        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MetadataPill(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(8.dp)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun TaskList(
    state: TodoState, destination: Destination, clock: Clock, jumpDate: LocalDate?,
    onClearJump: () -> Unit, onAdd: (LocalDate?) -> Unit, onEdit: (TaskEntity) -> Unit,
    onComplete: (TaskEntity) -> Unit, onReopen: (TaskEntity) -> Unit,
    onStatus: (TaskEntity) -> Unit, onAdvance: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit, onRestore: (TaskEntity) -> Unit, actionsEnabled: Boolean,
    finishingTasks: Map<String, TaskEntity> = emptyMap(), completionProgress: Map<String, Float> = emptyMap(),
    selectionMode: Boolean = false, selectedIds: Set<String> = emptySet(),
    onToggleSelection: (String) -> Unit = {}, filtered: Boolean = false,
    onPurge: ((TaskEntity) -> Unit)? = null,
    moreDated: Boolean = false, moreUndated: Boolean = false, jumpReady: Boolean = true,
    onMoreDated: () -> Unit = {}, onMoreUndated: () -> Unit = {},
    onDeleteFuture: ((String) -> Unit)? = null,
    groupCounts: Map<LocalDate?,Int>? = null, onLongSelect: ((String) -> Unit)? = null,
) {
    val today = LocalDate.now(clock)
    // Keep the original row in its date group only during a successful completion's
    // feedback. Room remains the source of truth; completed/trash views use real data.
    val pendingView = destination == Destination.AGENDA || destination == Destination.UNSCHEDULED
    val finishingSnapshot = finishingTasks.toMap()
    val displayedTasks = remember(state.tasks, pendingView, finishingSnapshot) {
        if (pendingView && finishingSnapshot.isNotEmpty()) state.tasks.map { finishingSnapshot[it.id] ?: it } else state.tasks
    }
    val searchIndex = remember(state.categories, state.tags, state.taskTags) { TaskSearchIndex(state.categories, state.tags, state.taskTags) }
    val occurrencesByTask = remember(state.occurrences) { state.occurrences.associateBy { it.taskId } }
    val rulesById = remember(state.rules) { state.rules.associateBy { it.id } }
    val remindersByTask = remember(state.reminders) { state.reminders.groupBy { it.taskId } }
    val groups = remember(displayedTasks, destination, clock, jumpDate) { when (destination) {
        Destination.AGENDA -> agendaGroups(displayedTasks, clock, jumpDate)
        Destination.UNSCHEDULED -> listOf(TaskGroup(null, displayedTasks.filter {
            it.deletedAt == null && it.status != TaskStatus.COMPLETED && it.dueDate == null
        }.sortedWith(unscheduledTaskOrder)))
        Destination.COMPLETED -> completedGroups(state.tasks)
        else -> listOf(TaskGroup(null, state.tasks.filter {
            it.deletedAt != null && !retentionExpired(it.deletedAt, TRASH_DAYS, clock.millis())
        }.sortedByDescending { it.deletedAt }))
    } }
    val counts = groupCounts ?: groups.associate { it.date to it.tasks.size }
    val listState = rememberLazyListState()
    var scrolledJump by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(jumpDate, destination, jumpReady) {
        if(jumpDate==null) scrolledJump=null
        val target="${destination.name}/$jumpDate"
        if (destination == Destination.AGENDA && jumpDate != null && jumpReady && scrolledJump!=target) {
            val groupIndex = groups.indexOfFirst { it.date == jumpDate }
            if (groupIndex >= 0) {
                listState.animateScrollToItem(1 + groups.take(groupIndex).sumOf { 1 + maxOf(1, it.tasks.size) })
                scrolledJump=target
            }
        }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("task_list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (jumpDate != null && destination == Destination.AGENDA) item(key = "jump") {
            TextButton(onClick = onClearJump) { Text("返回今天") }
        }
        if (destination == Destination.TRASH) item {
            Text("刪除後保留 30 天", Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        groups.forEach { group ->
            val groupKey = group.date?.toString() ?: "undated"
            if(group.date==null && moreDated) item(key="more_dated") {
                OutlinedButton(enabled=actionsEnabled,onClick=onMoreDated,modifier=Modifier.fillMaxWidth().testTag("load_more_tasks")) { Text("載入更多任務") }
            }
            item(key = "header_$groupKey") {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(
                        when {
                            destination == Destination.TRASH -> "已刪除任務"
                            group.date == null -> if (destination == Destination.COMPLETED) "無截止日期" else "未安排"
                            group.date == today && destination == Destination.AGENDA -> "今天"
                            else -> displayDate(group.date, today)
                        }, style = if (group.date == today && destination == Destination.AGENDA) MaterialTheme.typography.headlineSmall
                            else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    )
                    val count=if(counts.isEmpty()) null else counts[group.date] ?: 0
                    Surface(shape=RoundedCornerShape(8.dp),color=if(count != null && count>0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier=Modifier.align(Alignment.CenterVertically)) {
                        Text(count?.let { "$it 項" } ?: "…",Modifier.padding(horizontal=8.dp,vertical=4.dp).testTag("count_$groupKey"),
                            style=MaterialTheme.typography.labelMedium,color=if(count != null && count>0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    }
                    if (group.date != null) Text(
                        if (group.date == today && destination == Destination.AGENDA) displayDate(today)
                        else group.date.format(DateTimeFormatter.ofPattern("EEEE", Locale.TAIWAN)),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (destination == Destination.AGENDA) {
                        TextButton(onClick = { onAdd(group.date) }, modifier = Modifier.testTag("add_$groupKey")) { Text("＋ 新增") }
                    }
                }
            }
            if (group.tasks.isEmpty()) item(key = "empty_$groupKey") {
                if(counts.isEmpty()) Spacer(Modifier.height(8.dp)) else EmptyTasks(title = when {
                    filtered -> "沒有符合的任務"
                    destination == Destination.TRASH -> "垃圾桶是空的"
                    destination == Destination.COMPLETED -> "還沒有完成紀錄"
                    group.date == today -> "今天沒有待辦"
                    group.date == null -> "沒有未安排的待辦"
                    else -> "當天沒有待辦"
                })
            }
            items(group.tasks, key = { it.id }) { task ->
                TaskCard(task, searchIndex.categoryNames[task.categoryId] ?: "未分類", clock,
                    destination == Destination.TRASH, actionsEnabled && task.id !in finishingTasks,
                    onEdit, onComplete, onReopen, onStatus, onAdvance, onDelete, onRestore,
                    completion = if (pendingView) completionProgress[task.id] else null,
                    selectionMode = selectionMode, selected = task.id in selectedIds, onToggleSelection = onToggleSelection,
                    onLongSelect = onLongSelect,
                    recurringLabel = occurrencesByTask[task.id]?.let { ref ->
                        rulesById[ref.ruleId]?.frequency?.let { "重複 · ${recurrenceLabel(it)}" }
                    },
                    tags = searchIndex.tagNames(task.id),
                    reminders = remindersByTask[task.id].orEmpty(), onPurge = onPurge,
                    onDeleteFuture = if (destination == Destination.AGENDA && onDeleteFuture != null)
                        occurrencesByTask[task.id]?.let { ref -> { onDeleteFuture(ref.seriesId) } } else null,
                    modifier = Modifier.animateItem(fadeInSpec = tween(180), fadeOutSpec = tween(180),
                        placementSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)))
            }
        }
        if(moreUndated) item(key="more_undated") {
            OutlinedButton(enabled=actionsEnabled,onClick=onMoreUndated,modifier=Modifier.fillMaxWidth().testTag("load_more_undated")) {
                Text(if(destination==Destination.COMPLETED) "載入更多無截止日期紀錄" else "載入更多未安排任務")
            }
        }
    }
}

@Composable
internal fun TaskCard(
    task: TaskEntity, category: String, clock: Clock, inTrash: Boolean, enabled: Boolean,
    onEdit: (TaskEntity) -> Unit, onComplete: (TaskEntity) -> Unit, onReopen: (TaskEntity) -> Unit,
    onStatus: (TaskEntity) -> Unit, onAdvance: (TaskEntity) -> Unit, onDelete: (TaskEntity) -> Unit,
    onRestore: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier, completion: Float? = null, tags: List<String> = emptyList(),
    selectionMode: Boolean = false, selected: Boolean = false, onToggleSelection: (String) -> Unit = {},
    recurringLabel: String? = null, historyDescription: String? = null, readOnly: Boolean = false,
    reminders: List<com.chimafok.todo.data.ReminderEntity> = emptyList(),
    onPurge: ((TaskEntity) -> Unit)? = null,
    onDeleteFuture: (() -> Unit)? = null,
    onLongSelect: ((String) -> Unit)? = null,
) {
    var expanded by rememberSaveable(task.id) { mutableStateOf(false) }
    val assignment = task.categoryId == ASSIGNMENTS_ID
    val overdue = !readOnly && task.status != TaskStatus.COMPLETED && isOverdue(task.dueDate, task.dueTime, task.zoneId, clock)
    val completed = task.status == TaskStatus.COMPLETED || completion != null
    val feedback = completion ?: 0f
    Card(modifier = modifier.fillMaxWidth().testTag("task_${task.id}")
        .graphicsLayer { scaleX = 1f - feedback * 0.015f; scaleY = 1f - feedback * 0.015f }
        .animateContentSize(tween(220)).clip(MaterialTheme.shapes.large)
        .combinedClickable(enabled=enabled,role=Role.Button,
            onLongClickLabel=if(onLongSelect!=null && !readOnly) "選取任務" else null,
            onLongClick=if(onLongSelect!=null && !readOnly && completion==null) ({onLongSelect(task.id)}) else null,
            onClick={if(selectionMode) {if(!readOnly)onToggleSelection(task.id)} else expanded = !expanded}),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = lerp(MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.primaryContainer, feedback * 0.65f)),
        border = BorderStroke(1.dp, when {
            selectionMode && selected -> MaterialTheme.colorScheme.primary
            completion != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            overdue -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.outlineVariant
        })) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (selectionMode) {
                        Checkbox(selected, onCheckedChange = { onToggleSelection(task.id) }, enabled = enabled && !readOnly,
                            modifier = Modifier.size(48.dp).testTag("select_${task.id}").semantics {
                                contentDescription = "選取：${task.title}"
                            })
                    } else if (assignment) {
                        val shownStatus = if (completion != null) TaskStatus.COMPLETED else task.status
                        if (inTrash) {
                            AssignmentStatusIcon(shownStatus, Modifier.semantics {
                                contentDescription = "作業狀態：${statusLabel(shownStatus)}"
                            })
                        } else {
                            IconButton(onClick = { onStatus(task) }, enabled = enabled && !readOnly,
                                modifier = Modifier.size(48.dp).testTag("status_${task.id}").semantics {
                                    contentDescription = "選擇作業狀態：${task.title}"
                                    stateDescription = statusLabel(shownStatus)
                                }) {
                                AnimatedContent(shownStatus, label = "assignment status") { AssignmentStatusIcon(it) }
                            }
                        }
                    } else if (completion != null) {
                        Box(Modifier.size(48.dp).testTag("complete_${task.id}").semantics(mergeDescendants = true) {
                            contentDescription = "已完成：${task.title}"
                            toggleableState = ToggleableState.On
                            role = Role.Checkbox
                            disabled()
                        }, contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_status_completed), null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp).graphicsLayer {
                                    scaleX = 0.85f + feedback * 0.15f
                                    scaleY = scaleX
                                })
                        }
                    } else if (!completed && !inTrash) {
                        IconToggleButton(checked = false, enabled = enabled && !readOnly,
                            onCheckedChange = { onComplete(task) }, modifier = Modifier.size(48.dp)
                                .testTag("complete_${task.id}").semantics {
                                    role = Role.Checkbox
                                    contentDescription = "完成：${task.title}"
                                }) {
                            Icon(painterResource(R.drawable.ic_status_not_started), null, Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Icon(painterResource(if (completed) R.drawable.ic_status_completed else R.drawable.ic_status_not_started),
                            contentDescription = if (completed) "已完成" else "未完成",
                            tint = if (completed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(task.title, modifier = Modifier.testTag("task_title_${task.id}"),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        textDecoration = if (completion != null) TextDecoration.LineThrough else null)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetadataPill(category, if (assignment) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
                    recurringLabel?.let { MetadataPill(it, MaterialTheme.colorScheme.tertiary) }
                    tags.forEach { MetadataPill("# $it") }
                    if (overdue) MetadataPill("逾期", MaterialTheme.colorScheme.error)
                    if (assignment && task.status !in listOf(TaskStatus.NOT_STARTED, TaskStatus.COMPLETED)) {
                        MetadataPill(statusLabel(task.status), if (task.status == TaskStatus.DONE_UNSUBMITTED) assignmentStatusColor(task.status)
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    }
                    task.startDate?.takeIf { task.categoryId == STUDY_ID }?.let { date ->
                        val localStart = task.startTime?.let { date.atTime(it).atZone(java.time.ZoneId.of(requireNotNull(task.startZoneId))).withZoneSameInstant(clock.zone) }
                        Text("開始 ${displayDate(localStart?.toLocalDate() ?: date, LocalDate.now(clock))}${localStart?.format(DateTimeFormatter.ofPattern(" HH:mm")) ?: ""}",
                            modifier = Modifier.testTag("task_start_${task.id}"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    task.dueDate?.let { date ->
                        val localDeadline = task.dueInstant()?.atZone(clock.zone)
                        val prefix = if (localDeadline != null && task.zoneId != clock.zone.id) "當地截止" else "截止"
                        Text("$prefix ${displayDate(localDeadline?.toLocalDate() ?: date, LocalDate.now(clock))}${localDeadline?.let { " ${it.format(DateTimeFormatter.ofPattern("HH:mm"))}" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (task.dueTime != null && task.zoneId != clock.zone.id) Text(
                            "原設定 ${displayDate(date, LocalDate.now(clock))} ${task.dueTime.format(DateTimeFormatter.ofPattern("HH:mm"))} · ${task.zoneId}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (expanded && !selectionMode) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (task.notes.isNotBlank()) LinkifiedNotes(task.notes)
                reminders.orderedFor(task).filter { it.enabled }.forEach { reminder ->
                    val trigger = runCatching { com.chimafok.todo.domain.resolveReminder(reminder.spec(), task.dueDate, task.dueTime, task.zoneId) }.getOrNull()
                    Text("提醒：${trigger?.let { com.chimafok.todo.reminders.reminderInstantLabel(it) }.orEmpty()} · ${com.chimafok.todo.reminders.reminderStateLabel(reminder.state)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                historyDescription?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (task.zoneId != null && task.zoneId != clock.zone.id) Text("時區：${task.zoneId}", style = MaterialTheme.typography.bodySmall)
                if (inTrash) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onRestore(task) }, enabled = enabled) { Text("復原") }
                        onPurge?.let { purge -> TextButton(onClick = { purge(task) }, enabled = enabled,
                            modifier = Modifier.testTag("purge_${task.id}"), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("永久刪除…") } }
                    }
                } else if (!readOnly) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onEdit(task) }, enabled = enabled) { Text("編輯") }
                        if (task.status == TaskStatus.COMPLETED) {
                            TextButton(onClick = { onReopen(task) }, enabled = enabled) { Text("重新開啟") }
                        } else if (assignment) {
                            TextButton(onClick = { onAdvance(task) }, enabled = enabled) { Text("推進至${statusLabel(nextAssignmentStatus(task.status))}") }
                        }
                        TextButton(onClick = { onDelete(task) }, enabled = enabled,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("刪除") }
                        onDeleteFuture?.let { delete ->
                            TextButton(onClick = delete, enabled = enabled,
                                modifier = Modifier.testTag("delete_future_${task.id}"),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("刪除未來任務…") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LinkifiedNotes(notes: String) {
    val color = MaterialTheme.colorScheme.primary
    Text(buildAnnotatedString {
        var offset = 0
        Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).findAll(notes).forEach { match ->
            append(notes.substring(offset, match.range.first))
            pushLink(LinkAnnotation.Url(match.value, TextLinkStyles(style = SpanStyle(color = color))))
            append(match.value)
            pop()
            offset = match.range.last + 1
        }
        append(notes.substring(offset))
    })
}
