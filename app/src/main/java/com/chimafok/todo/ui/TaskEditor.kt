package com.chimafok.todo.ui

import com.chimafok.todo.i18n.tr
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chimafok.todo.R
import com.chimafok.todo.data.CategoryEntity
import com.chimafok.todo.data.TaskEntity
import com.chimafok.todo.data.TagEntity
import com.chimafok.todo.data.orderedFor
import com.chimafok.todo.data.NewTaskDraft
import com.chimafok.todo.domain.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter

data class EditorVersions(val taskId: String, val ticket: String, val revisions: Map<String, Long>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditor(
    id: String, existing: TaskEntity?, expectedExisting: Boolean, initialDate: LocalDate?,
    categories: List<CategoryEntity>, tags: List<TagEntity>, initialTagIds: Set<String>, onDismiss: () -> Unit,
    onSave: suspend (TaskDraft) -> Unit, onSaved: (Boolean) -> Unit,
    recurringContext: RecurringEditorContext? = null,
    initialReminders: List<com.chimafok.todo.data.ReminderEntity> = emptyList(),
    retainedVersions: EditorVersions? = null,
    initialDraft: NewTaskDraft? = null,
    onKeepDraft: suspend (NewTaskDraft) -> Unit = {},
    onDiscardDraft: suspend () -> Unit = {},
    onImagePickerActive: (Boolean) -> Unit = {},
) {
    val restoredDraft = initialDraft?.takeIf { !expectedExisting && it.id == id }
    val knownTags = remember(tags) { tags.map { it.id }.toSet() }
    val restoredCategoryValid = restoredDraft == null || categories.any { it.id == restoredDraft.categoryId }
    val draftReferencesChanged = restoredDraft != null && (!restoredCategoryValid || restoredDraft.tagIds.any { it !in knownTags })
    var reminderInputs by rememberSaveable { mutableStateOf(restoredDraft?.reminderInputs ?: encodeReminderInputs((existing?.let { initialReminders.orderedFor(it) } ?: initialReminders).filter { it.enabled }.map { it.input() })) }
    var seriesReminderInputs by rememberSaveable { mutableStateOf(restoredDraft?.seriesReminderInputs ?: encodeSeriesReminderInputs(recurringContext?.reminders.orEmpty().filter { it.enabled }
        .sortedByDescending { row -> runCatching { resolveReminder(occurrenceReminder(row.spec(), id, requireNotNull(existing?.dueDate), existing.dueTime,
            requireNotNull(existing.zoneId)), existing.dueDate, existing.dueTime, existing.zoneId) }.getOrDefault(Long.MIN_VALUE) }.map { it.spec().input() })) }
    var resetReminders by rememberSaveable { mutableStateOf(false) }
    var inheritedReminderStamp by rememberSaveable { mutableStateOf(restoredDraft?.inheritedReminderStamp) }
    var inheritedTemplateIds by rememberSaveable { mutableStateOf(restoredDraft?.inheritedTemplateIds ?: emptyList()) }
    var title by rememberSaveable { mutableStateOf(restoredDraft?.title ?: existing?.title ?: "") }
    var imageIds by rememberSaveable { mutableStateOf(restoredDraft?.imageIds ?: existing?.imageIds.orEmpty()) }
    var imageBusy by remember { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf(restoredDraft?.notes ?: existing?.notes ?: "") }
    var date by rememberSaveable { mutableStateOf(restoredDraft?.date ?: (existing?.dueDate ?: initialDate)?.toString() ?: "") }
    var time by rememberSaveable { mutableStateOf(restoredDraft?.time ?: existing?.dueTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "") }
    var zone by rememberSaveable { mutableStateOf<String?>(restoredDraft?.zoneId ?: existing?.zoneId ?: if (expectedExisting) null else ZoneId.systemDefault().id) }
    var deadlineReference by rememberSaveable { mutableLongStateOf(restoredDraft?.deadlineReference ?: System.currentTimeMillis()) }
    var startDateInput by rememberSaveable { mutableStateOf(restoredDraft?.startDate ?: existing?.startDate?.toString().orEmpty()) }
    var startTimeInput by rememberSaveable { mutableStateOf(restoredDraft?.startTime ?: existing?.startTime?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty()) }
    var startZone by rememberSaveable { mutableStateOf(restoredDraft?.startZoneId ?: existing?.startZoneId) }
    var startReference by rememberSaveable { mutableLongStateOf(restoredDraft?.startReference?.takeIf { it > 0 } ?: System.currentTimeMillis()) }
    var showStartTimePicker by rememberSaveable { mutableStateOf(false) }
    var selectedTags by rememberSaveable { mutableStateOf(restoredDraft?.tagIds?.filter { it in knownTags } ?: initialTagIds.toList()) }
    var categoryId by rememberSaveable { mutableStateOf(restoredDraft?.categoryId?.takeIf { restoredCategoryValid } ?: existing?.categoryId ?: UNCATEGORIZED_ID) }
    var statusName by rememberSaveable { mutableStateOf(when {
        restoredDraft != null -> if(restoredCategoryValid) {
            if (!expectedExisting && categories.find { it.id == restoredDraft.categoryId }?.systemKind == SystemCategory.ASSIGNMENTS && restoredDraft.statusName == TaskStatus.COMPLETED.name)
                TaskStatus.NOT_STARTED.name else if (!expectedExisting && restoredDraft.categoryId == STUDY_ID && restoredDraft.statusName == TaskStatus.COMPLETED.name) TaskStatus.OPEN.name else restoredDraft.statusName
        } else TaskStatus.OPEN.name
        else -> (existing?.status ?: TaskStatus.OPEN).name
    }) }
    val revision = rememberSaveable { existing?.revision ?: -1L }
    var categoryMenu by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showPastWarning by rememberSaveable { mutableStateOf(false) }
    var pendingNext by rememberSaveable { mutableStateOf(false) }
    var showSeriesPreview by rememberSaveable { mutableStateOf(false) }
    var showConversionPreview by rememberSaveable { mutableStateOf(false) }
    var pendingPastApproved by rememberSaveable { mutableStateOf(false) }
    val seriesRevision = rememberSaveable { recurringContext?.series?.revision ?: -1L }
    // Large frozen revision maps stay in the ViewModel, never in the saved-state Binder transaction.
    val versionTicket = rememberSaveable { retainedVersions?.ticket.orEmpty() }
    val versionsLost = retainedVersions != null && versionTicket != retainedVersions.ticket
    val occurrenceRevisions = retainedVersions?.revisions ?: remember { recurringContext?.records?.associate { it.task.id to it.task.revision } ?: emptyMap() }
    val futureAllowed = recurringContext != null && existing != null &&
        recurringContext.series.lifecycle != com.chimafok.todo.data.SeriesLifecycle.STOPPED &&
        canEditSeriesFrom(SeriesEditRecord(recurringContext.occurrence, existing), DeviceClock)
    var applyFuture by rememberSaveable { mutableStateOf(futureAllowed) }
    var frequency by rememberSaveable { mutableStateOf(restoredDraft?.frequency ?: recurringContext?.rule?.frequency?.name ?: "NONE") }
    var interval by rememberSaveable { mutableStateOf(restoredDraft?.interval ?: recurringContext?.rule?.intervalDays?.toString() ?: "1") }
    var weekdays by rememberSaveable { mutableStateOf(restoredDraft?.weekdays ?: recurringContext?.rule?.rule()?.weekdays?.map { it.value } ?: emptyList()) }
    var endMode by rememberSaveable { mutableStateOf(restoredDraft?.endMode ?: when {
        recurringContext?.rule?.endDate != null -> "DATE"
        recurringContext?.rule?.maxCount != null -> "COUNT"
        else -> "NONE"
    }) }
    var endDate by rememberSaveable { mutableStateOf(restoredDraft?.endDate ?: recurringContext?.rule?.endDate?.toString() ?: "") }
    var endReference by rememberSaveable { mutableStateOf(restoredDraft?.endReference?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()) }
    var count by rememberSaveable { mutableStateOf(restoredDraft?.count ?: recurringContext?.rule?.maxCount?.toString() ?: "10") }
    val scope = rememberCoroutineScope()
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(id) { titleFocus.requestFocus() }
    val study = categoryId == STUDY_ID
    val assignment = categories.find { it.id == categoryId }?.systemKind == SystemCategory.ASSIGNMENTS
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    )
    // Only a new series (including conversion) defaults its blank start to today.
    // Existing occurrences keep their explicit date requirement and history protection.
    fun resolvedDate() = if (date.isBlank() && recurringContext == null && frequency != "NONE")
        LocalDate.now(ZoneId.of(zone ?: ZoneId.systemDefault().id))
    else resolveDeadlineDate(date, parseTimeInput(time), zone, Instant.ofEpochMilli(deadlineReference))
    val conversionAllowed = expectedExisting && recurringContext == null && existing != null &&
        existing.deletedAt == null && existing.status != TaskStatus.COMPLETED
    val convertingExisting = expectedExisting && recurringContext == null && frequency != "NONE"
    val applyRecurrence = if (recurringContext != null) applyFuture else frequency != "NONE"
    // Do not parse a partially typed time to decide whether its own field is enabled.
    val hasDeadlineDate = date.isNotBlank() || (recurringContext == null && frequency != "NONE")
    fun configuredRule(): RecurrenceRule? {
        if (!applyRecurrence) return null
        val start = requireNotNull(resolvedDate()) { tr(R.string.text_enter_the_first_due_date_for_447bec) }
        return RecurrenceRule(start, RecurrenceFrequency.valueOf(frequency), requireNotNull(zone),
            if (frequency == "EVERY_N_DAYS") requireNotNull(interval.toIntOrNull()) { tr(R.string.text_the_interval_must_be_days_878fbc) } else 1,
            if (frequency == "WEEKDAYS") weekdays.map { java.time.DayOfWeek.of(it) }.toSet() else emptySet(),
            if (endMode == "DATE") requireNotNull(parseDateInput(endDate, LocalDate.parse(endReference))) { tr(R.string.text_enter_an_end_date_d5f715) } else null,
            if (endMode == "COUNT") requireNotNull(count.toIntOrNull()) { tr(R.string.text_enter_a_valid_total_occurrence_count_780b98) } else null)
    }
    val hasSingleReminders = recurringContext == null && decodeReminderInputs(reminderInputs).isNotEmpty()
    fun reminderConversionStamp(): String = listOf(reminderInputs, configuredRule()?.dateAt(1), parseTimeInput(time), zone).joinToString("|")
    val inheritedRemindersReady = hasSingleReminders && inheritedReminderStamp != null &&
        inheritedReminderStamp == runCatching { reminderConversionStamp() }.getOrNull()
    val datePreview = runCatching { resolvedDate()?.toString() }.getOrNull()?.takeIf { it != date.trim() }
    val timePreview = runCatching { parseTimeInput(time)?.format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()?.takeIf { it != time.trim() }

    fun resolvedStartDate() = resolveDeadlineDate(startDateInput, parseTimeInput(startTimeInput), startZone, Instant.ofEpochMilli(startReference))
    val startDatePreview = runCatching { resolvedStartDate()?.toString() }.getOrNull()?.takeIf { it != startDateInput.trim() }
    val startTimePreview = runCatching { parseTimeInput(startTimeInput)?.format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()?.takeIf { it != startTimeInput.trim() }

    fun save(next: Boolean, pastConfirmed: Boolean = false, seriesConfirmed: Boolean = false, conversionConfirmed: Boolean = false) {
        if (saving || imageBusy) return
        try {
            require(!versionsLost) { tr(R.string.text_the_editor_state_was_rebuilt_close_5df832) }
            require(!expectedExisting || existing != null) { tr(R.string.text_the_task_no_longer_exists_close_0f63c0) }
            val dueTime = parseTimeInput(time)
            val recurrence = configuredRule()
            val dueDate = recurrence?.dateAt(1) ?: resolvedDate()
            require(recurringContext == null || dueDate != null) { tr(R.string.text_recurring_records_must_keep_their_occurrence_9d5b10) }
            require(!applyRecurrence || !hasSingleReminders || inheritedRemindersReady) { tr(R.string.text_carry_over_single_task_reminders_review_c66bad) }
            val inherit = applyRecurrence && inheritedRemindersReady
            require(expectedExisting || (!assignment && !study) || statusName != TaskStatus.COMPLETED.name) { tr(R.string.text_new_assignment_or_study_tasks_cannot_9b2aba) }
            val beginning = resolvedStartDate()
            val draft = TaskDraft(id, title, notes, categoryId, dueDate, dueTime,
                if (dueDate != null) zone else null,
                requireNotNull(statusName?.let(TaskStatus::valueOf)) { tr(R.string.text_choose_an_assignment_status_first_b6f8c7) }, revision.takeIf { it >= 0 }, selectedTags.toSet(),
                recurrence, seriesRevision.takeIf { it >= 0 }, occurrenceRevisions.takeIf { recurringContext != null },
                if (applyRecurrence && !inherit) null else decodeReminderInputs(reminderInputs).map { it.spec(dueDate, dueTime, zone) },
                if (applyRecurrence) decodeSeriesReminderInputs(seriesReminderInputs).map { it.spec() } else null,
                resetSeriesReminders = !applyRecurrence && resetReminders, inheritSingleReminders = inherit,
                startDate = beginning, startTime = parseTimeInput(startTimeInput), startZoneId = if (beginning != null) startZone else null, imageIds = imageIds)
            validateDraft(draft, assignment)
            draft.seriesReminders.orEmpty().forEach { template ->
                resolveReminder(occurrenceReminder(template, id, requireNotNull(dueDate), dueTime, requireNotNull(zone)), dueDate, dueTime, zone)
            }
            draft.reminders.orEmpty().forEach { resolveReminder(it, dueDate, dueTime, zone) }
            if (convertingExisting) require(conversionAllowed && draft.status != TaskStatus.COMPLETED) {
                tr(R.string.text_reopen_the_completed_task_before_enabling_1ffefa)
            }
            if (dueDate != null && dueTime != null && (existing == null || existing.dueDate != dueDate ||
                    existing.dueTime != dueTime || existing.zoneId != zone)) {
                deadlineInstant(dueDate, dueTime, requireNotNull(zone))
            }
            error = null
            if (!pastConfirmed && isOverdue(dueDate, dueTime, zone, Clock.systemDefaultZone())) {
                pendingNext = next
                showPastWarning = true
                return
            }
            if ((convertingExisting || inherit) && !conversionConfirmed) {
                pendingNext = next; pendingPastApproved = pastConfirmed; showConversionPreview = true
                return
            }
            if (recurringContext != null && applyFuture) {
                planSeriesEdit(draft, SeriesEditRecord(recurringContext.occurrence, requireNotNull(existing)),
                    recurringContext.rule, recurringContext.records, DeviceClock)
                if (!seriesConfirmed) {
                    pendingNext = next; pendingPastApproved = pastConfirmed; showSeriesPreview = true
                    return
                }
            }
            saving = true
            scope.launch {
                try {
                    onSave(draft)
                    onSaved(next)
                } catch (exception: Exception) {
                    if (exception is kotlinx.coroutines.CancellationException) throw exception
                    error = exception.message ?: tr(R.string.text_cannot_save_your_input_is_kept_707f25)
                } finally { saving = false }
            }
        } catch (exception: IllegalArgumentException) { error = exception.message }
    }

    var showDraftExit by rememberSaveable { mutableStateOf(false) }
    fun snapshotDraft() = NewTaskDraft(id,title,notes,date,time,zone,deadlineReference,selectedTags,categoryId,statusName,
        frequency,interval,weekdays,endMode,endDate,endReference,count,reminderInputs,seriesReminderInputs,
        inheritedReminderStamp,inheritedTemplateIds,startDateInput,startTimeInput,startZone,startReference,imageIds)
    fun requestExit() {
        if(saving) return
        if(!expectedExisting && (restoredDraft != null || title.isNotEmpty() || imageIds.isNotEmpty() || notes.isNotEmpty() || date.isNotEmpty() ||
            time.isNotEmpty() || startDateInput.isNotEmpty() || startTimeInput.isNotEmpty() || selectedTags.isNotEmpty() || categoryId != UNCATEGORIZED_ID || frequency != "NONE" ||
            reminderInputs != "[]" || seriesReminderInputs != "[]")) showDraftExit = true else onDismiss()
    }
    Dialog(onDismissRequest = ::requestExit,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(title = { Text(if (expectedExisting) tr(R.string.text_edit_task_89ada5) else tr(R.string.text_add_task_54bff0)) },
                        navigationIcon = { TextButton(onClick = ::requestExit, enabled = !saving) { Text(tr(R.string.text_cancel_2cd0f3)) } })
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!expectedExisting) OutlinedButton(onClick = { save(true) }, enabled = !saving && !imageBusy,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("save_next")) { Text(tr(R.string.text_add_another_89d5d7)) }
                            Button(onClick = { save(false) }, enabled = !saving && !imageBusy, shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("save_task")) {
                                Text(if (saving) tr(R.string.text_saving_00190a) else if (expectedExisting) tr(R.string.text_save_dba44e) else tr(R.string.text_create_da3402))
                            }
                        }
                    }
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text(tr(R.string.text_task_name_b7fea4)) },
                        modifier = Modifier.fillMaxWidth().focusRequester(titleFocus).testTag("task_title"), enabled = !saving && !imageBusy,
                        colors = fieldColors, shape = MaterialTheme.shapes.medium,
                        singleLine = true, isError = error != null && title.isBlank())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("editor_error")) }
                    if(draftReferencesChanged) Text(tr(R.string.text_some_draft_categories_or_tags_no_b3f57d),
                        color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                    Text(tr(R.string.text_date_and_time_40e256), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    DeadlineTextField(date, {
                        date = it; deadlineReference = System.currentTimeMillis(); zone = ZoneId.systemDefault().id
                        if (it.isBlank() && !applyRecurrence) time = ""
                    },
                        label = if (applyRecurrence && recurringContext == null) tr(R.string.text_first_due_date_blank_means_today_c08e76) else if (applyRecurrence) tr(R.string.text_due_date_318024) else tr(R.string.text_due_date_optional_3b1f39), placeholder = "YYYY-MM-DD",
                        supportingText = if (datePreview != null) { { Text(datePreview) } } else null,
                        trailingIcon = { DatePickerButton(runCatching { resolvedDate() }.getOrNull() ?: LocalDate.now(), R.string.choose_date, enabled = !saving) {
                            date = it.toString(); deadlineReference = System.currentTimeMillis(); zone = ZoneId.systemDefault().id
                        } },
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().testTag("task_date"), enabled = !saving)
                    DeadlineTextField(time, { time = it; deadlineReference = System.currentTimeMillis(); zone = ZoneId.systemDefault().id }, label = tr(R.string.text_due_time_optional_434ef0), placeholder = "HH:mm",
                        supportingText = if (timePreview != null) { { Text(timePreview) } } else null,
                        trailingIcon = {
                            ActionIconButton(R.drawable.ic_clock, tr(R.string.text_choose_time_9f8b0c),
                                enabled = hasDeadlineDate && !saving, onClick = { showTimePicker = true }, modifier = Modifier.testTag("open_time"))
                        }, modifier = Modifier.fillMaxWidth().testTag("task_time"),
                        colors = fieldColors,
                        enabled = !saving && hasDeadlineDate)
                    if (hasDeadlineDate) Text(
                        tr(R.string.text_due_time_zone_1624b9) + runCatching {
                            val parsedDate = resolvedDate()
                            val parsedTime = parseTimeInput(time)
                            if (parsedDate != null && parsedTime != null && zone != null) deadlineZoneLabel(parsedDate, parsedTime, requireNotNull(zone)) else zone
                        }.getOrNull().orEmpty().ifBlank { zone.orEmpty().ifBlank { tr(R.string.text_not_recorded_recorded_when_date_or_54236f) } } + if (time.isBlank()) tr(R.string.text_date_only_no_specific_instant_2f9c3c) else "",
                        Modifier.testTag("deadline_zone"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (date.isNotBlank()) TextButton(onClick = { date = ""; time = "" }, enabled = !saving) { Text(tr(R.string.text_clear_due_date_and_time_cb620f)) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(tr(R.string.text_category_and_status_0f92ed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Box {
                        OutlinedButton(onClick = { categoryMenu = true }, enabled = !saving && !imageBusy, shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(tr(R.string.text_category_s_e35224, categories.find { it.id == categoryId }?.let { com.chimafok.todo.i18n.categoryName(it) } ?: tr(R.string.text_uncategorized_607089)))
                        }
                        DropdownMenu(categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            categories.forEach { category -> DropdownMenuItem(text = { Text(com.chimafok.todo.i18n.categoryName(category)) }, onClick = {
                                val wasAssignment = assignment
                                categoryId = category.id
                                if (category.systemKind == SystemCategory.ASSIGNMENTS && !wasAssignment) {
                                    statusName = if (expectedExisting) null else TaskStatus.NOT_STARTED.name
                                } else if (category.systemKind != SystemCategory.ASSIGNMENTS) {
                                    statusName = convertStatus(statusName?.let(TaskStatus::valueOf) ?: TaskStatus.NOT_STARTED, false, study = category.id == STUDY_ID).name
                                }
                                categoryMenu = false
                            }) }
                        }
                    }
                    if (study) {
                        Box {
                            OutlinedButton(onClick = { statusMenu = true }, enabled = !saving && !imageBusy, shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("study_status")) {
                                Text(tr(R.string.text_study_status_s_349cc1, statusLabel(statusName?.let(TaskStatus::valueOf) ?: TaskStatus.OPEN, STUDY_ID)))
                            }
                            DropdownMenu(statusMenu, onDismissRequest = { statusMenu = false }) {
                                statusOptions(false, true).filter { (expectedExisting && !convertingExisting) || it != TaskStatus.COMPLETED }.forEach { status ->
                                    DropdownMenuItem(text = { Text(statusLabel(status, STUDY_ID)) }, leadingIcon = { AssignmentStatusIcon(status) },
                                        onClick = { statusName = status.name; statusMenu = false })
                                }
                            }
                        }
                        DeadlineTextField(startDateInput, {
                            startDateInput = it; startReference = System.currentTimeMillis(); startZone = ZoneId.systemDefault().id
                            if (it.isBlank()) startTimeInput = ""
                        }, label = tr(R.string.text_start_date_optional_2e6b78), placeholder = "YYYY-MM-DD",
                            supportingText = if (startDatePreview != null) { { Text(startDatePreview) } } else null,
                            trailingIcon = { DatePickerButton(runCatching { resolvedStartDate() }.getOrNull() ?: LocalDate.now(), R.string.choose_date, enabled = !saving) {
                                startDateInput = it.toString(); startReference = System.currentTimeMillis(); startZone = ZoneId.systemDefault().id
                            } }, colors = fieldColors, modifier = Modifier.fillMaxWidth().testTag("task_start_date"), enabled = !saving)
                        DeadlineTextField(startTimeInput, {
                            startTimeInput = it; startReference = System.currentTimeMillis(); startZone = ZoneId.systemDefault().id
                        }, label = tr(R.string.text_start_time_optional_7b7f9f), placeholder = "HH:mm",
                            supportingText = if (startTimePreview != null) { { Text(startTimePreview) } } else null,
                            trailingIcon = { ActionIconButton(R.drawable.ic_clock, tr(R.string.text_choose_time_9f8b0c), enabled = startDateInput.isNotBlank() && !saving,
                                onClick = { showStartTimePicker = true }, modifier = Modifier.testTag("open_start_time")) },
                            modifier = Modifier.fillMaxWidth().testTag("task_start_time"), colors = fieldColors,
                            enabled = !saving && startDateInput.isNotBlank())
                        if (startDateInput.isNotBlank()) {
                            Text(tr(R.string.text_start_time_zone_s_53b2e5, startZone.orEmpty()) + if (startTimeInput.isBlank()) tr(R.string.text_date_only_no_specific_instant_2f9c3c) else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { startDateInput = ""; startTimeInput = ""; startZone = null }, enabled = !saving) { Text(tr(R.string.text_clear_start_date_and_time_e98377)) }
                        }
                    }
                    if (assignment) Box {
                        OutlinedButton(onClick = { statusMenu = true }, enabled = !saving && (expectedExisting || frequency == "NONE"), shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(statusName?.let { tr(R.string.text_assignment_status_s_0da7d4, statusLabel(TaskStatus.valueOf(it))) } ?: tr(R.string.text_choose_an_assignment_status_3fcfdf))
                        }
                        DropdownMenu(statusMenu, onDismissRequest = { statusMenu = false }) {
                            TaskStatus.entries.filter { it != TaskStatus.OPEN && ((expectedExisting && !convertingExisting) || it != TaskStatus.COMPLETED) }.forEach { status ->
                                DropdownMenuItem(text = { Text(statusLabel(status)) },
                                    leadingIcon = { AssignmentStatusIcon(status) },
                                    onClick = { statusName = status.name; statusMenu = false })
                            }
                        }
                    }
                    if (!expectedExisting || recurringContext != null || conversionAllowed) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(tr(R.string.text_repeat_cb59ef), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (recurringContext != null) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(!applyFuture, onClick = { applyFuture = false }, enabled = !saving && !imageBusy,
                                    label = { Text(tr(R.string.text_this_occurrence_e78821)) }, modifier = Modifier.testTag("repeat_scope_single"))
                                FilterChip(applyFuture, onClick = { applyFuture = true },
                                    enabled = !saving && futureAllowed,
                                    label = { Text(tr(R.string.text_this_and_future_806e5f)) }, modifier = Modifier.testTag("repeat_scope_future"))
                            }
                            Text(if (applyFuture) tr(R.string.text_update_this_occurrence_generated_future_incomplete_53a48c) else
                                if (!futureAllowed) tr(R.string.text_only_this_occurrence_can_be_edited_96c862) else tr(R.string.text_edit_only_this_occurrence_keep_the_27e41c),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!expectedExisting || applyFuture || conversionAllowed) RecurrenceFields(frequency, interval, weekdays, endMode, endDate, count,
                            allowNone = recurringContext == null, enabled = !saving && !imageBusy,
                            onFrequency = {
                                frequency = it
                                if (it == "NONE" && date.isBlank()) time = ""
                                if (it != "NONE" && zone == null) zone = ZoneId.systemDefault().id
                                if (!expectedExisting && it != "NONE" && !study) statusName = if (assignment) TaskStatus.NOT_STARTED.name else TaskStatus.OPEN.name
                            },
                            onInterval = { interval = it }, onWeekdays = { weekdays = it }, onEndMode = { endMode = it },
                            onEndDate = { endDate = it; endReference = LocalDate.now().toString() }, onCount = { count = it },
                            endReference = runCatching { LocalDate.parse(endReference) }.getOrElse { LocalDate.now() })
                        if (applyRecurrence) {
                            runCatching { configuredRule()?.dateAt(1) }.getOrNull()?.let {
                                Text(tr(R.string.text_occurrence_date_s_25bd41, it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("repeat_first_date"))
                            }
                            Text(if (convertingExisting) tr(R.string.text_keep_progress_for_this_occurrence_future_8ed16e) else tr(R.string.text_new_occurrences_are_generated_even_when_b5b080), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (expectedExisting && recurringContext == null && existing?.status == TaskStatus.COMPLETED) {
                        Text(tr(R.string.text_reopen_the_completed_task_before_enabling_49a0e1), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("conversion_reopen_hint"))
                    }
                    if (applyRecurrence) {
                        SeriesReminderFields(seriesReminderInputs, { seriesReminderInputs = it }, runCatching { configuredRule()?.dateAt(1) }.getOrNull(),
                            runCatching { parseTimeInput(time) }.getOrNull(), zone, !saving)
                        if (hasSingleReminders) OutlinedCard(Modifier.fillMaxWidth().testTag("reminder_conversion_card")) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (inheritedRemindersReady) tr(R.string.text_single_task_reminders_carried_over_32f58a) else tr(R.string.text_carry_over_existing_reminders_5138e5), style = MaterialTheme.typography.titleSmall)
                                Text(tr(R.string.text_keep_this_form_s_reminders_separately_d7a0ad),
                                    style = MaterialTheme.typography.bodySmall)
                                if (!inheritedRemindersReady && inheritedReminderStamp != null) Text(tr(R.string.text_date_time_or_single_task_reminders_978efd), color = MaterialTheme.colorScheme.error)
                                OutlinedButton(enabled = !saving && !imageBusy, modifier = Modifier.testTag("inherit_single_reminders"), onClick = {
                                    runCatching {
                                        val day = requireNotNull(configuredRule()?.dateAt(1)) { tr(R.string.text_set_the_first_date_first_6f83cd) }
                                        val z = requireNotNull(zone) { tr(R.string.text_set_the_due_time_zone_first_ad95f4) }
                                        val sources = decodeReminderInputs(reminderInputs).map { it.spec(day, parseTimeInput(time), z) }
                                        val inherited = inheritReminderTemplates(sources, day, z)
                                        val extra = decodeSeriesReminderInputs(seriesReminderInputs).filterNot { it.id in inheritedTemplateIds }
                                        require(inherited.size + extra.size <= 32) { tr(R.string.text_each_series_supports_at_most_reminders_8fc6c4) }
                                        seriesReminderInputs = encodeSeriesReminderInputs(inherited.map { it.input() } + extra)
                                        inheritedTemplateIds = inherited.map { it.id }
                                        inheritedReminderStamp = reminderConversionStamp()
                                        error = null
                                    }.onFailure { error = it.message }
                                }) { Text(if (inheritedRemindersReady) tr(R.string.text_carry_over_single_task_reminders_again_1c2aa2) else tr(R.string.text_carry_over_single_task_reminders_9c5ed6)) }
                            }
                        }
                        if (recurringContext != null) Text(tr(R.string.text_apply_the_series_template_to_this_7e59b9), style=MaterialTheme.typography.bodySmall)
                    } else {
                        if (recurringContext != null) {
                            Text(if (resetReminders) tr(R.string.text_reset_to_series_reminders_after_saving_49e339) else if (recurringContext.reminderOverridden) tr(R.string.text_this_occurrence_uses_separate_reminders_kept_60d3fb) else
                                tr(R.string.text_edit_only_this_occurrence_other_occurrences_5d3942),style=MaterialTheme.typography.bodySmall)
                            OutlinedButton(enabled=!saving, modifier=Modifier.testTag("reset_series_reminders"),onClick={
                                runCatching {
                                    val day=requireNotNull(resolvedDate());val z=requireNotNull(zone);val t=parseTimeInput(time)
                                    reminderInputs=encodeReminderInputs(recurringContext.reminders.filter{it.enabled}.map { occurrenceReminder(it.spec(),id,day,t,z).input() })
                                    resetReminders=true
                                }.onFailure { error=it.message }
                            }){Text(tr(R.string.text_reset_to_series_reminders_8bc47e))}
                        }
                        ReminderFields(reminderInputs, { reminderInputs = it; resetReminders = false }, runCatching { resolvedDate() }.getOrNull(),
                            runCatching { parseTimeInput(time) }.getOrNull(), zone, initialReminders, !saving, allowAdd = true)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(tr(R.string.text_tags_9b423a), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (tags.isEmpty()) Text(tr(R.string.text_add_tags_in_categories_tags_fafc2c), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag -> FilterChip(selected = tag.id in selectedTags,
                            onClick = { selectedTags = if (tag.id in selectedTags) selectedTags - tag.id else selectedTags + tag.id },
                            enabled = !saving && !imageBusy, label = { Text(tag.name) }, modifier = Modifier.testTag("editor_tag_${tag.id}")) }
                    }
                    Text(tr(R.string.text_notes_and_images_7252db), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(notes, { notes = it }, label = { Text(tr(R.string.text_notes_85914d)) }, modifier = Modifier.fillMaxWidth().testTag("task_notes"),
                        colors = fieldColors, shape = MaterialTheme.shapes.medium,
                        minLines = 3, enabled = !saving)
                    NoteImageField(imageIds, !saving, { imageIds = it }, { imageBusy = it }, onImagePickerActive)
                }
            }
        }
    }
    if (showStartTimePicker) TodoTimePicker(
        initial = runCatching { parseTimeInput(startTimeInput) }.getOrNull() ?: DEFAULT_REMINDER_TIME,
        onDismiss = { showStartTimePicker = false },
        onTime = { startTimeInput = it.format(DateTimeFormatter.ofPattern("HH:mm")); startReference = System.currentTimeMillis(); startZone = ZoneId.systemDefault().id; showStartTimePicker = false },
    )
    if (showTimePicker) TodoTimePicker(
        initial = runCatching { parseTimeInput(time) }.getOrNull() ?: DEFAULT_REMINDER_TIME,
        onDismiss = { showTimePicker = false },
        onTime = { time = it.format(DateTimeFormatter.ofPattern("HH:mm")); deadlineReference = System.currentTimeMillis(); zone = ZoneId.systemDefault().id; showTimePicker = false },
    )
    if(showDraftExit) AlertDialog(onDismissRequest={if(!saving)showDraftExit=false},
        modifier=Modifier.testTag("draft_exit_dialog"),title={Text(tr(R.string.text_keep_the_new_task_draft_392e0a))},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(tr(R.string.text_continue_editing_later_a_draft_does_275011))
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }},
        confirmButton={TextButton(enabled=!saving,modifier=Modifier.testTag("keep_draft"),onClick={
            saving=true
            scope.launch { try { onKeepDraft(snapshotDraft());onDismiss() }
                catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException)throw e;error=tr(R.string.text_cannot_keep_the_draft_please_try_eb6814) }
                finally { saving=false } }
        }) { Text(tr(R.string.text_keep_draft_f5140c)) }},
        dismissButton={Row {
            TextButton(enabled=!saving,modifier=Modifier.testTag("discard_draft"),onClick={
                saving=true
                scope.launch { try { onDiscardDraft();onDismiss() }
                    catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException)throw e;error=tr(R.string.text_cannot_discard_the_draft_please_try_98042a) }
                    finally { saving=false } }
            }) {Text(tr(R.string.text_discard_4038ba))}
            TextButton(enabled=!saving,onClick={showDraftExit=false},modifier=Modifier.testTag("continue_editing")) {Text(tr(R.string.text_keep_editing_24a05e))}
        }})
    if (showPastWarning) AlertDialog(
        onDismissRequest = { showPastWarning = false },
        modifier = Modifier.testTag("past_deadline_warning"),
        title = { Text(tr(R.string.text_due_time_is_in_the_past_d67628)) },
        text = {
            val warningDate = runCatching { configuredRule()?.dateAt(1) ?: resolvedDate() }.getOrNull()
            val resolvedTime = runCatching { parseTimeInput(time) }.getOrNull()
            Text(if (resolvedTime == null) tr(R.string.text_due_date_s_is_before_today_4c260a, warningDate)
                else tr(R.string.text_due_s_s_s_is_in_c2e64e, warningDate, resolvedTime.format(DateTimeFormatter.ofPattern("HH:mm")), zone))
        },
        confirmButton = { TextButton(onClick = { showPastWarning = false; save(pendingNext, pastConfirmed = true) },
            modifier = Modifier.testTag("confirm_past_deadline")) { Text(tr(R.string.text_save_anyway_4207d3)) } },
        dismissButton = { TextButton(onClick = { showPastWarning = false }, modifier = Modifier.testTag("cancel_past_deadline")) { Text(tr(R.string.text_back_to_edit_3160fc)) } },
    )
    if (showConversionPreview && (convertingExisting || (applyRecurrence && inheritedRemindersReady))) AlertDialog(
        onDismissRequest = { showConversionPreview = false }, modifier = Modifier.testTag("convert_series_preview"),
        title = { Text(tr(R.string.text_convert_to_a_recurring_task_2744f4)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (expectedExisting) tr(R.string.text_this_task_becomes_the_first_occurrence_747041) else tr(R.string.text_create_a_recurring_series_and_keep_2b53b5))
                if (inheritedRemindersReady) {
                    Text(tr(R.string.text_keep_s_separate_reminders_for_this_92aa54, decodeReminderInputs(reminderInputs).size, decodeSeriesReminderInputs(seriesReminderInputs).size))
                    Text(tr(R.string.text_this_occurrence_s_fixed_reminders_keep_5a2aa3, zone))
                    Text(tr(R.string.text_later_series_reminder_edits_do_not_4038fa))
                }
                val previewRule = runCatching { configuredRule() }.getOrNull()
                Text(tr(R.string.text_first_date_s_051546, previewRule?.dateAt(1)))
                Text(tr(R.string.text_due_time_zone_s_aaf1e8, zone))
                previewRule?.let { rule ->
                    val frequencyLabel = if (rule.frequency == RecurrenceFrequency.EVERY_N_DAYS) tr(R.string.text_every_s_days_c56e4e, rule.intervalDays) else recurrenceLabel(rule.frequency)
                    val ending = rule.maxCount?.let { tr(R.string.text_s_occurrences_including_this_one_0f2f00, it) }
                        ?: rule.endDate?.let { tr(R.string.text_until_s_inclusive_4d1d80, it) } ?: tr(R.string.text_never_c329ac)
                    Text(tr(R.string.text_recurrence_s_s_ef4264, frequencyLabel, ending))
                }
                Text(tr(R.string.text_current_progress_s_0d851c, statusName?.let { statusLabel(TaskStatus.valueOf(it)) }.orEmpty()))
                Text(tr(R.string.text_the_total_includes_this_occurrence_when_057095))
                Text(tr(R.string.text_future_assignments_start_as_not_started_7201e2))
            }
        },
        confirmButton = { TextButton(onClick = {
            showConversionPreview = false
            save(pendingNext, pendingPastApproved, conversionConfirmed = true)
        }, modifier = Modifier.testTag("confirm_convert_series")) { Text(tr(R.string.text_confirm_conversion_5080ba)) } },
        dismissButton = { TextButton(onClick = { showConversionPreview = false },
            modifier = Modifier.testTag("cancel_convert_series")) { Text(tr(R.string.text_back_to_edit_3160fc)) } },
    )
    if (showSeriesPreview && recurringContext != null) AlertDialog(
        onDismissRequest = { showSeriesPreview = false }, modifier = Modifier.testTag("series_edit_preview"),
        title = { Text(tr(R.string.text_apply_to_this_and_future_occurrences_a66b47)) },
        text = {
            val preview = runCatching {
                val recurrence = requireNotNull(configuredRule())
                val draft = TaskDraft(id, title, dueDate = recurrence.dateAt(1), recurrence = recurrence)
                planSeriesEdit(draft, SeriesEditRecord(recurringContext.occurrence, requireNotNull(existing)),
                    recurringContext.rule, recurringContext.records, DeviceClock)
            }.getOrNull()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr(R.string.text_this_occurrence_s_8c0b75, runCatching { configuredRule()?.dateAt(1) }.getOrNull()))
                if (preview != null) {
                    Text(tr(R.string.text_also_update_s_reactivate_s_and_f31e51, preview.updatedCount, preview.restoredCount, preview.cancelledCount))
                    Text(tr(R.string.text_keep_progress_on_other_items_ordinary_c3a581))
                    Text(tr(R.string.text_keep_past_completed_and_deleted_records_026a8b))
                    Text(tr(R.string.text_series_reminders_apply_to_this_and_feadd7))
                } else Text(tr(R.string.text_this_occurrence_can_no_longer_edit_dd79ff))
            }
        },
        confirmButton = { TextButton(onClick = { showSeriesPreview = false; save(pendingNext, pendingPastApproved, seriesConfirmed = true) },
            modifier = Modifier.testTag("confirm_series_edit")) { Text(tr(R.string.text_confirm_and_apply_ddf142)) } },
        dismissButton = { TextButton(onClick = { showSeriesPreview = false }, modifier = Modifier.testTag("cancel_series_edit")) { Text(tr(R.string.text_back_to_edit_3160fc)) } },
    )
}
