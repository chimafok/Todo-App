package com.chimafok.todo.ui

import com.chimafok.todo.i18n.tr
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

fun statusLabel(status: TaskStatus, categoryId: String? = null): String = when (status) {
    TaskStatus.OPEN -> if (categoryId == STUDY_ID) tr(R.string.text_not_started_e94431) else tr(R.string.text_incomplete_6707de)
    TaskStatus.NOT_STARTED -> tr(R.string.text_not_started_e94431)
    TaskStatus.IN_PROGRESS -> tr(R.string.text_in_progress_e7e586)
    TaskStatus.DONE_UNSUBMITTED -> tr(R.string.text_done_not_submitted_c13e37)
    TaskStatus.COMPLETED -> tr(R.string.text_completed_c0b3fb)
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
    val searchIndex = remember(state.categories, state.tags, state.taskTags, com.chimafok.todo.i18n.I18n.locale) { TaskSearchIndex(state.categories, state.tags, state.taskTags) }
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
            TextButton(onClick = onClearJump) { Text(tr(R.string.text_back_to_today_e09528)) }
        }
        if (destination == Destination.TRASH) item {
            Text(tr(R.string.text_kept_for_days_after_deletion_1dae66), Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        groups.forEach { group ->
            val groupKey = group.date?.toString() ?: "undated"
            if(group.date==null && moreDated) item(key="more_dated") {
                OutlinedButton(enabled=actionsEnabled,onClick=onMoreDated,modifier=Modifier.fillMaxWidth().testTag("load_more_tasks")) { Text(tr(R.string.text_load_more_tasks_bddce6)) }
            }
            item(key = "header_$groupKey") {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(
                        when {
                            destination == Destination.TRASH -> tr(R.string.text_deleted_tasks_f841a8)
                            group.date == null -> if (destination == Destination.COMPLETED) tr(R.string.text_no_due_date_6ae390) else tr(R.string.text_unscheduled_7c525e)
                            group.date == today && destination == Destination.AGENDA -> tr(R.string.text_today_d5f5a7)
                            else -> displayDate(group.date, today)
                        }, style = if (group.date == today && destination == Destination.AGENDA) MaterialTheme.typography.headlineSmall
                            else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    )
                    val count=if(counts.isEmpty()) null else counts[group.date] ?: 0
                    Surface(shape=RoundedCornerShape(8.dp),color=if(count != null && count>0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier=Modifier.align(Alignment.CenterVertically)) {
                        Text(count?.let { tr(R.string.text_s_items_cfad0b, it) } ?: "…",Modifier.padding(horizontal=8.dp,vertical=4.dp).testTag("count_$groupKey"),
                            style=MaterialTheme.typography.labelMedium,color=if(count != null && count>0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    }
                    if (group.date != null) Text(
                        if (group.date == today && destination == Destination.AGENDA) displayDate(today)
                        else group.date.format(DateTimeFormatter.ofPattern("EEEE", com.chimafok.todo.i18n.I18n.locale)),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (destination == Destination.AGENDA) {
                        TextButton(onClick = { onAdd(group.date) }, modifier = Modifier.testTag("add_$groupKey")) { Text(tr(R.string.text_add_07a04d)) }
                    }
                }
            }
            if (group.tasks.isEmpty()) item(key = "empty_$groupKey") {
                if(counts.isEmpty()) Spacer(Modifier.height(8.dp)) else EmptyTasks(title = when {
                    filtered -> tr(R.string.text_no_matching_tasks_bd0ce7)
                    destination == Destination.TRASH -> tr(R.string.text_trash_is_empty_510eda)
                    destination == Destination.COMPLETED -> tr(R.string.text_no_completed_records_yet_362cff)
                    group.date == today -> tr(R.string.text_no_tasks_today_6162f3)
                    group.date == null -> tr(R.string.text_no_unscheduled_tasks_2ba8f2)
                    else -> tr(R.string.text_no_tasks_on_this_day_01c8a9)
                })
            }
            items(group.tasks, key = { it.id }) { task ->
                TaskCard(task, searchIndex.categoryNames[task.categoryId] ?: tr(R.string.text_uncategorized_607089), clock,
                    destination == Destination.TRASH, actionsEnabled && task.id !in finishingTasks,
                    onEdit, onComplete, onReopen, onStatus, onAdvance, onDelete, onRestore,
                    completion = if (pendingView) completionProgress[task.id] else null,
                    selectionMode = selectionMode, selected = task.id in selectedIds, onToggleSelection = onToggleSelection,
                    onLongSelect = onLongSelect,
                    recurringLabel = occurrencesByTask[task.id]?.let { ref ->
                        rulesById[ref.ruleId]?.frequency?.let { tr(R.string.text_recurring_s_dbfe22, recurrenceLabel(it)) }
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
                Text(if(destination==Destination.COMPLETED) tr(R.string.text_load_more_records_without_due_dates_ddc45b) else tr(R.string.text_load_more_unscheduled_tasks_296d8f))
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
    val study = task.categoryId == STUDY_ID
    val hasProgress = assignment || study
    val progressName = if (study) tr(R.string.text_study_dcb987) else tr(R.string.text_assignments_d87d04)
    val overdue = !readOnly && task.status != TaskStatus.COMPLETED && isOverdue(task.dueDate, task.dueTime, task.zoneId, clock)
    val completed = task.status == TaskStatus.COMPLETED || completion != null
    val feedback = completion ?: 0f
    Card(modifier = modifier.fillMaxWidth().testTag("task_${task.id}")
        .graphicsLayer { scaleX = 1f - feedback * 0.015f; scaleY = 1f - feedback * 0.015f }
        .animateContentSize(tween(220)).clip(MaterialTheme.shapes.large)
        .combinedClickable(enabled=enabled,role=Role.Button,
            onLongClickLabel=if(onLongSelect!=null && !readOnly) tr(R.string.text_select_task_6a0154) else null,
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
                                contentDescription = tr(R.string.text_select_s_a90c94, task.title)
                            })
                    } else if (hasProgress) {
                        val shownStatus = if (completion != null) TaskStatus.COMPLETED else task.status
                        if (inTrash) {
                            AssignmentStatusIcon(shownStatus, Modifier.semantics {
                                contentDescription = tr(R.string.text_s_status_s_9fdb3d, progressName, statusLabel(shownStatus, task.categoryId))
                            })
                        } else {
                            IconButton(onClick = { onStatus(task) }, enabled = enabled && !readOnly,
                                modifier = Modifier.size(48.dp).testTag("status_${task.id}").semantics {
                                    contentDescription = tr(R.string.text_choose_s_status_s_e515b0, progressName, task.title)
                                    stateDescription = statusLabel(shownStatus, task.categoryId)
                                }) {
                                AnimatedContent(shownStatus, label = "assignment status") { AssignmentStatusIcon(it) }
                            }
                        }
                    } else if (completion != null) {
                        Box(Modifier.size(48.dp).testTag("complete_${task.id}").semantics(mergeDescendants = true) {
                            contentDescription = tr(R.string.text_completed_s_960607, task.title)
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
                                    contentDescription = tr(R.string.text_complete_s_3d6fec, task.title)
                                }) {
                            Icon(painterResource(R.drawable.ic_status_not_started), null, Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Icon(painterResource(if (completed) R.drawable.ic_status_completed else R.drawable.ic_status_not_started),
                            contentDescription = if (completed) tr(R.string.text_completed_f28461) else tr(R.string.text_incomplete_6707de),
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
                    if (overdue) MetadataPill(tr(R.string.text_overdue_da7ece), MaterialTheme.colorScheme.error)
                    if (hasProgress && task.status !in listOf(TaskStatus.OPEN, TaskStatus.NOT_STARTED, TaskStatus.COMPLETED)) {
                        MetadataPill(statusLabel(task.status), if (task.status == TaskStatus.DONE_UNSUBMITTED) assignmentStatusColor(task.status)
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    }
                    task.startDate?.takeIf { task.categoryId == STUDY_ID }?.let { date ->
                        val localStart = task.startTime?.let { date.atTime(it).atZone(java.time.ZoneId.of(requireNotNull(task.startZoneId))).withZoneSameInstant(clock.zone) }
                        Text(tr(R.string.text_start_s_s_6ebb4c, displayDate(localStart?.toLocalDate() ?: date, LocalDate.now(clock)), localStart?.format(DateTimeFormatter.ofPattern(" HH:mm")) ?: ""),
                            modifier = Modifier.testTag("task_start_${task.id}"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    task.dueDate?.let { date ->
                        val localDeadline = task.dueInstant()?.atZone(clock.zone)
                        val prefix = if (localDeadline != null && task.zoneId != clock.zone.id) tr(R.string.text_due_locally_34b749) else tr(R.string.text_due_fb37c6)
                        Text("$prefix ${displayDate(localDeadline?.toLocalDate() ?: date, LocalDate.now(clock))}${localDeadline?.let { " ${it.format(DateTimeFormatter.ofPattern("HH:mm"))}" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (task.dueTime != null && task.zoneId != clock.zone.id) Text(
                            tr(R.string.text_original_s_s_s_f7d66b, displayDate(date, LocalDate.now(clock)), task.dueTime.format(DateTimeFormatter.ofPattern("HH:mm")), task.zoneId),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (expanded && !selectionMode) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (task.notes.isNotBlank()) LinkifiedNotes(task.notes)
                NoteImageGallery(task.imageIds)
                reminders.orderedFor(task).filter { it.enabled }.forEach { reminder ->
                    val trigger = runCatching { com.chimafok.todo.domain.resolveReminder(reminder.spec(), task.dueDate, task.dueTime, task.zoneId) }.getOrNull()
                    Text(tr(R.string.text_reminder_s_s_d5f9ee, trigger?.let { com.chimafok.todo.reminders.reminderInstantLabel(it) }.orEmpty(), com.chimafok.todo.reminders.reminderStateLabel(reminder.state)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                historyDescription?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (task.zoneId != null && task.zoneId != clock.zone.id) Text(tr(R.string.text_time_zone_s_4c666b, task.zoneId), style = MaterialTheme.typography.bodySmall)
                if (inTrash) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onRestore(task) }, enabled = enabled) { Text(tr(R.string.text_restore_2abdcb)) }
                        onPurge?.let { purge -> TextButton(onClick = { purge(task) }, enabled = enabled,
                            modifier = Modifier.testTag("purge_${task.id}"), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(tr(R.string.text_delete_permanently_15a9d2)) } }
                    }
                } else if (!readOnly) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onEdit(task) }, enabled = enabled) { Text(tr(R.string.text_edit_e0d448)) }
                        if (task.status == TaskStatus.COMPLETED) {
                            TextButton(onClick = { onReopen(task) }, enabled = enabled) { Text(tr(R.string.text_reopen_d82e4e)) }
                        } else if (hasProgress) {
                            TextButton(onClick = { onAdvance(task) }, enabled = enabled) { Text(tr(R.string.text_advance_to_s_01798d, statusLabel(nextProgressStatus(task.status, study)))) }
                        }
                        TextButton(onClick = { onDelete(task) }, enabled = enabled,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(tr(R.string.text_delete_3c8f5b)) }
                        onDeleteFuture?.let { delete ->
                            TextButton(onClick = delete, enabled = enabled,
                                modifier = Modifier.testTag("delete_future_${task.id}"),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(tr(R.string.text_delete_future_tasks_2d7630)) }
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
