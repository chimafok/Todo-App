package com.chimafok.todo.ui

import com.chimafok.todo.i18n.tr
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chimafok.todo.R
import com.chimafok.todo.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private fun Destination.label() = when (this) {
    Destination.AGENDA -> R.string.agenda
    Destination.UNSCHEDULED -> R.string.unscheduled
    Destination.COMPLETED -> R.string.completed
    Destination.ORGANIZE -> R.string.organize
    Destination.TRASH -> R.string.trash
    Destination.SETTINGS -> R.string.settings
}

private fun Destination.icon() = when (this) {
    Destination.AGENDA -> R.drawable.ic_agenda
    Destination.UNSCHEDULED -> R.drawable.ic_unscheduled
    Destination.COMPLETED -> R.drawable.ic_completed
    Destination.ORGANIZE -> R.drawable.ic_categories
    Destination.TRASH -> R.drawable.ic_trash
    Destination.SETTINGS -> R.drawable.ic_settings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoApp(viewModel: TodoViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val destination = viewModel.destination
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var completedTab by rememberSaveable { mutableIntStateOf(0) }
    var jumpDate by rememberSaveable { mutableStateOf<String?>(null) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorDate by rememberSaveable { mutableStateOf<String?>(null) }
    var editExisting by rememberSaveable { mutableStateOf(false) }
    var offerDraft by rememberSaveable { mutableStateOf(false) }
    var offeredDate by rememberSaveable { mutableStateOf<String?>(null) }
    var checkingDraft by remember { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var statusId by rememberSaveable { mutableStateOf<String?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var listSelectionActive by remember { mutableStateOf(false) }
    var restoreSeriesId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreSeriesRevision by rememberSaveable { mutableLongStateOf(0L) }
    val finishingTasks = remember { mutableStateMapOf<String, com.chimafok.todo.data.TaskEntity>() }
    val completionProgress = remember { mutableStateMapOf<String, Float>() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); viewModel.reconcile(); delay(30_000) }
    }
    LaunchedEffect(viewModel.foregroundEpoch) { now = System.currentTimeMillis() }
    var handledHomeEpoch by rememberSaveable { mutableIntStateOf(viewModel.homeEpoch) }
    LaunchedEffect(viewModel.homeEpoch) {
        if (handledHomeEpoch != viewModel.homeEpoch) {
            completedTab = 0
            jumpDate = null
            handledHomeEpoch = viewModel.homeEpoch
        }
        drawerState.close()
    }
    LaunchedEffect(editorId, editExisting, deleteId, statusId, viewModel.notificationTaskId) {
        viewModel.loadDetail(if (editExisting && editorId != null) editorId else deleteId ?: statusId ?: viewModel.notificationTaskId)
    }
    fun action(block: suspend () -> Unit) {
        if (actionBusy) return
        actionBusy = true
        scope.launch {
            try { block() } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                snackbar.showSnackbar(exception.message ?: tr(R.string.text_operation_failed_please_try_again_6d73ff))
            } finally { actionBusy = false }
        }
    }
    fun createFresh(date: LocalDate?) {
        editorDate = date?.toString()
        editExisting = false
        editorId = UUID.randomUUID().toString()
    }
    fun resumeDraft() {
        viewModel.newTaskDraft?.let { draft ->
            editorDate = null;editExisting = false;editorId = draft.id;offerDraft = false
        }
    }
    fun create(date: LocalDate?) {
        if(checkingDraft) return
        checkingDraft = true
        scope.launch {
            try {
                val draft = viewModel.awaitNewTaskDraft()
                if(draft != null || viewModel.draftLoadError != null) { offeredDate = date?.toString();offerDraft = true }
                else createFresh(date)
            } finally { checkingDraft = false }
        }
    }
    fun updateStatus(task: com.chimafok.todo.data.TaskEntity, status: TaskStatus) {
        action {
            if (status != TaskStatus.COMPLETED || task.status == TaskStatus.COMPLETED) {
                viewModel.status(task, status)
            } else {
                finishingTasks[task.id] = task
                try {
                    viewModel.status(task, status)
                    // Wait for the committed Room snapshot before showing success or
                    // dropping the retained row. No delayed or optimistic DB write.
                    val observed = viewModel.state.first { snapshot -> snapshot.error != null ||
                        snapshot.tasks.none { it.id == task.id && it.status != status } }
                    check(observed.error == null) { tr(R.string.text_completion_saved_but_the_screen_could_413f40) }
                    animate(0f, 1f, animationSpec = tween(320)) { value, _ -> completionProgress[task.id] = value }
                } finally {
                    completionProgress.remove(task.id)
                    finishingTasks.remove(task.id)
                }
            }
        }
    }
    BackHandler(enabled = drawerState.isOpen || destination != Destination.AGENDA) {
        if (drawerState.isOpen) scope.launch { drawerState.close() } else viewModel.goHome()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Row(Modifier.fillMaxWidth().testTag("drawer_home")
                        .clickable(onClickLabel = tr(R.string.text_back_to_agenda_ad3306)) { viewModel.goHome(); scope.launch { drawerState.close() } }
                        .padding(horizontal = 12.dp, vertical = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_completed), null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Todo", style = MaterialTheme.typography.headlineSmall)
                            Text(tr(R.string.text_personal_tasks_ecdf52), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(bottom = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Destination.entries.forEach { item ->
                        if (item == Destination.ORGANIZE) {
                            Text(tr(R.string.text_manage_bb6d99), Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        NavigationDrawerItem(
                            label = { Text(stringResource(item.label())) },
                            icon = { Icon(painterResource(item.icon()), contentDescription = null) },
                            selected = destination == item,
                            shape = MaterialTheme.shapes.medium,
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            onClick = {
                                viewModel.destination = item
                                completedTab = 0
                                jumpDate = null
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(vertical = 3.dp).testTag("nav_${item.name}"),
                        )
                    }
                    Text(tr(R.string.text_data_is_stored_locally_91ff28), Modifier.padding(start = 16.dp, top = 28.dp, bottom = 16.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(destination.label()), Modifier.testTag("page_title"), style = MaterialTheme.typography.titleLarge)
                            if (destination == Destination.AGENDA) Text(
                                LocalDate.now(clock).format(DateTimeFormatter.ofPattern(if (com.chimafok.todo.i18n.I18n.locale.language == "en") "EEEE, MMM d" else "M 月 d 日 EEEE", com.chimafok.todo.i18n.I18n.locale)),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.testTag("open_menu")) {
                            Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.open_menu))
                        }
                    },
                    actions = {
                        if (destination == Destination.AGENDA) {
                            DatePickerButton(LocalDate.now(clock), R.string.jump_date) { jumpDate = it.toString() }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            floatingActionButton = {
                if (destination in listOf(Destination.AGENDA, Destination.UNSCHEDULED) && !listSelectionActive && !state.loading && state.error == null) {
                    ExtendedFloatingActionButton(onClick = { create(null) }, modifier = Modifier.testTag("add_task"),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        icon = { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.add_task)) },
                        text = { Text(tr(R.string.text_add_task_54bff0)) })
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if(editorId == null && (viewModel.newTaskDraft != null || viewModel.draftLoadError != null)) {
                    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=6.dp).testTag("new_task_draft")) {
                        FlowRow(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(viewModel.draftLoadError ?: tr(R.string.text_you_have_an_unfinished_draft_42de36),Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.bodyMedium)
                            if(viewModel.newTaskDraft != null) TextButton(enabled=!state.loading && !checkingDraft,onClick=::resumeDraft,
                                modifier=Modifier.testTag("resume_draft")) { Text(tr(R.string.text_keep_editing_24a05e)) }
                            TextButton(enabled=!checkingDraft,onClick={
                                checkingDraft=true
                                scope.launch { try {viewModel.discardNewTaskDraft()}
                                    catch(e:Exception) {if(e is kotlinx.coroutines.CancellationException)throw e;snackbar.showSnackbar(tr(R.string.text_cannot_discard_the_draft_please_try_98042a))}
                                    finally {checkingDraft=false} }
                            },modifier=Modifier.testTag("delete_saved_draft")) {Text(tr(R.string.text_discard_draft_1357dc))}
                        }
                    }
                }
                DelayedLoadingIndicator(viewModel.reconciling && !state.loading, modifier = Modifier.testTag("maintenance_loading"))
                viewModel.recurrenceError?.let { message -> Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.reconcile(force = true) }) { Text(tr(R.string.text_retry_7e59d0)) }
                } }
                if (state.loading) {
                    DelayedLoadingIndicator(true, modifier = Modifier.testTag("startup_loading"))
                } else if (state.error != null) {
                    Text(state.error!!, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::reload, modifier = Modifier.padding(horizontal = 24.dp)) { Text(tr(R.string.text_retry_7e59d0)) }
                } else when (destination) {
                    Destination.AGENDA, Destination.UNSCHEDULED, Destination.COMPLETED, Destination.TRASH -> {
                        if (destination == Destination.COMPLETED) {
                            PrimaryTabRow(selectedTabIndex = completedTab) {
                                listOf(R.string.completed_tasks, R.string.recurring_records).forEachIndexed { index, label ->
                                    Tab(selected = completedTab == index, onClick = { completedTab = index },
                                        text = { Text(stringResource(label)) })
                                }
                            }
                        }
                        if (destination == Destination.COMPLETED && completedTab == 1) {
                            RecurringRecords(state, viewModel, clock, !actionBusy,
                                onEdit = { task -> editExisting = true; editorId = task.id; editorDate = null },
                                onComplete = { task -> updateStatus(task, TaskStatus.COMPLETED) },
                                onReopen = { task -> if (task.categoryId in setOf(ASSIGNMENTS_ID, STUDY_ID)) statusId = task.id else action { viewModel.status(task, TaskStatus.OPEN) } },
                                onStatus = { statusId = it.id }, onAdvance = { task -> updateStatus(task, nextProgressStatus(task.status, task.categoryId == STUDY_ID)) },
                                onDelete = { deleteId = it.id }, onSelectionMode = {listSelectionActive=it})
                        } else {
                            TaskBrowser(
                                state = state, destination = destination, clock = clock,
                                homeEpoch = viewModel.homeEpoch, viewModel = viewModel,
                                onSelectionMode = { listSelectionActive = it },
                                jumpDate = jumpDate?.let(LocalDate::parse), onClearJump = { jumpDate = null },
                                onAdd = ::create,
                                onEdit = { task -> editExisting = true; editorId = task.id; editorDate = null },
                                onComplete = { task -> updateStatus(task, TaskStatus.COMPLETED) },
                                onReopen = { task ->
                                    if (task.categoryId in setOf(ASSIGNMENTS_ID, STUDY_ID)) statusId = task.id
                                    else action { viewModel.status(task, TaskStatus.OPEN) }
                                },
                                onStatus = { statusId = it.id },
                                onAdvance = { task -> updateStatus(task, nextProgressStatus(task.status, task.categoryId == STUDY_ID)) },
                                onDelete = { deleteId = it.id },
                                onRestore = { task ->
                                    val ref = state.occurrences.find { it.taskId == task.id }
                                    val series = state.series.find { it.id == ref?.seriesId }
                                    if (series?.deletedAt != null) { restoreSeriesId = series.id; restoreSeriesRevision = series.revision }
                                    else action { viewModel.restore(task) }
                                },
                                actionsEnabled = !actionBusy,
                                finishingTasks = finishingTasks, completionProgress = completionProgress,
                            )
                        }
                    }
                    Destination.ORGANIZE -> OrganizationScreen(state, viewModel)
                    Destination.SETTINGS -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(tr(R.string.text_reminders_and_notifications_b758c2), style = MaterialTheme.typography.titleLarge)
                        ReminderCapabilities()
                        Text(tr(R.string.text_future_reminders_resume_after_a_phone_a0c246), style = MaterialTheme.typography.bodyMedium)
                        Text(tr(R.string.text_if_background_activity_is_restricted_check_efbb4e), style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                        Text(tr(R.string.text_local_data_0a1460), style = MaterialTheme.typography.titleMedium)
                        LanguageSettings(viewModel)
                        HorizontalDivider()
                        HistorySettings(state, viewModel)
                        HorizontalDivider()
                        BackupSettings(viewModel)
                    }
                }
            }
        }
    }
    state.tasks.find { it.id == viewModel.notificationTaskId }?.let { task ->
        AlertDialog(onDismissRequest = viewModel::dismissReminderDetail, title = { Text(task.title) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr(R.string.text_current_status_s_e84da1, statusLabel(task.status, task.categoryId)))
                task.dueDate?.let { Text(tr(R.string.text_due_s_s_s_56b289, it, task.dueTime?.toString().orEmpty(), task.zoneId.orEmpty())) }
                if (task.notes.isNotBlank()) LinkifiedNotes(task.notes)
                NoteImageGallery(task.imageIds)
                if (editorId != null) Text(tr(R.string.text_you_have_unsaved_edits_finish_or_2bbdeb))
            }
        }, confirmButton = { TextButton(enabled = editorId == null, onClick = { editExisting = true; editorId = task.id; editorDate = null; viewModel.dismissReminderDetail() }) { Text(tr(R.string.text_edit_e0d448)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissReminderDetail) { Text(tr(R.string.text_close_c7fddd)) } })
    }
    val tasksById = remember(state.tasks) { state.tasks.associateBy { it.id } }
    val existing = if (editExisting) tasksById[editorId] else null
    editorId?.let { id ->
        if (!state.loading && state.error == null && (!editExisting || state.detailTaskId == id)) key(id) {
            TaskEditor(
                id = id, existing = existing, expectedExisting = editExisting,
                initialDate = editorDate?.let(LocalDate::parse), categories = state.categories,
                tags = state.tags, initialTagIds = state.taskTags.filter { it.taskId == id }.map { it.tagId }.toSet(),
                onDismiss = { scope.launch { viewModel.cleanupNoteImages(); editorId = null } }, onSave = { viewModel.save(it) },
                onSaved = { next -> scope.launch { viewModel.cleanupNoteImages(); if (next) create(editorDate?.let(LocalDate::parse)) else editorId = null } },
                onImagePickerActive = { viewModel.externalImageFlow = it },
                initialReminders = state.reminders.filter { it.taskId == id },
                retainedVersions = state.occurrences.find { it.taskId == id }?.let { ref ->
                    viewModel.editorVersions(id, state.occurrences.filter { it.seriesId == ref.seriesId }.mapNotNull { occurrence ->
                        tasksById[occurrence.taskId]?.let { SeriesEditRecord(occurrence, it) }
                    })
                },
                initialDraft = if(editExisting) null else viewModel.newTaskDraft?.takeIf { it.id == id },
                onKeepDraft = { viewModel.keepNewTaskDraft(it) },
                onDiscardDraft = { viewModel.discardNewTaskDraft(id) },
                recurringContext = state.occurrences.find { it.taskId == id }?.let { ref ->
                    val series = state.series.find { it.id == ref.seriesId }
                    val rule = state.rules.filter { it.seriesId == ref.seriesId }.maxByOrNull { it.version }
                    if (series != null && rule != null) RecurringEditorContext(series, rule, ref,
                        state.occurrences.filter { it.seriesId == series.id }.mapNotNull { occurrence ->
                            tasksById[occurrence.taskId]?.let { SeriesEditRecord(occurrence, it) }
                        }, state.seriesReminders.filter { it.seriesId == series.id }, state.reminderOverrides.any { it.taskId == id }) else null
                },
            )
        }
    }
    if(offerDraft) AlertDialog(onDismissRequest={if(!checkingDraft)offerDraft=false},title={Text(tr(R.string.text_continue_the_previous_draft_662498))},
        text={Text(viewModel.draftLoadError ?: tr(R.string.text_continue_editing_or_discard_the_draft_21c177))},
        confirmButton={if(viewModel.newTaskDraft != null) TextButton(enabled=!checkingDraft,onClick=::resumeDraft,
            modifier=Modifier.testTag("open_saved_draft")) {Text(tr(R.string.text_continue_draft_1c51f1))}},
        dismissButton={Row {
            TextButton(enabled=!checkingDraft,modifier=Modifier.testTag("discard_and_create"),onClick={
                checkingDraft=true
                scope.launch {try { viewModel.discardNewTaskDraft();offerDraft=false;createFresh(offeredDate?.let(LocalDate::parse)) }
                    catch(e:Exception) {if(e is kotlinx.coroutines.CancellationException)throw e;snackbar.showSnackbar(tr(R.string.text_cannot_discard_the_draft_please_try_98042a))}
                    finally {checkingDraft=false} }
            }) {Text(tr(R.string.text_discard_and_add_new_7277d1))}
            TextButton(enabled=!checkingDraft,onClick={offerDraft=false}) {Text(tr(R.string.text_cancel_2cd0f3))}
        }})
    state.tasks.find { it.id == deleteId }?.let { task ->
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text(tr(R.string.text_move_to_trash_1b2fd4)) },
            text = { Text(tr(R.string.text_s_will_be_kept_for_days_5df61f, task.title) +
                if (state.occurrences.any { it.taskId == task.id }) tr(R.string.text_delete_only_this_occurrence_keep_other_771d5a) else "") },
            confirmButton = { TextButton(onClick = { deleteId = null; action { viewModel.trash(task) } }) { Text(tr(R.string.text_move_to_trash_f86355)) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(tr(R.string.text_cancel_2cd0f3)) } })
    }
    state.tasks.find { it.id == statusId }?.let { task ->
        AlertDialog(onDismissRequest = { statusId = null }, title = { Text(if (task.categoryId == STUDY_ID) tr(R.string.text_choose_study_status_982bfe) else tr(R.string.text_choose_assignment_status_a4e8ba)) }, text = {
            Column { statusOptions(task.categoryId == ASSIGNMENTS_ID, task.categoryId == STUDY_ID).forEach { status ->
                TextButton(onClick = { statusId = null; updateStatus(task, status) },
                    modifier = Modifier.fillMaxWidth().testTag("choose_status_${status.name}"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                    AssignmentStatusIcon(status)
                    Spacer(Modifier.width(16.dp))
                    Text(statusLabel(status, task.categoryId), Modifier.weight(1f))
                    if (status == task.status) Icon(painterResource(R.drawable.ic_status_completed), tr(R.string.text_current_status_a27329),
                        Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            } }
        }, confirmButton = { TextButton(onClick = { statusId = null }) { Text(tr(R.string.text_cancel_2cd0f3)) } })
    }
    restoreSeriesId?.let { id -> state.series.find { it.id == id }?.let { series ->
        val count = state.occurrences.count { it.seriesId == id && it.deletedWithSeriesAt == series.deletedAt }
        AlertDialog(onDismissRequest = { restoreSeriesId = null }, title = { Text(tr(R.string.text_restore_recurring_series_b13107)) },
            text = { Text(tr(R.string.text_restore_s_and_s_remaining_records_9a29ed, series.title, count)) },
            confirmButton = { TextButton(enabled = !actionBusy, modifier = Modifier.testTag("restore_series_confirm"), onClick = {
                restoreSeriesId = null; action { viewModel.restoreSeries(id, restoreSeriesRevision) }
            }) { Text(tr(R.string.text_restore_series_9fbd02)) } }, dismissButton = { TextButton(onClick = { restoreSeriesId = null }) { Text(tr(R.string.text_cancel_2cd0f3)) } })
    } }
}
