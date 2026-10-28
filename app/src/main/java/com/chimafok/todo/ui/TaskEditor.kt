package com.chimafok.todo.ui

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
    var endReference by rememberSaveable { mutableStateOf(restoredDraft?.endReference ?: LocalDate.now().toString()) }
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
        val start = requireNotNull(resolvedDate()) { "重複任務請填第一次截止日期" }
        return RecurrenceRule(start, RecurrenceFrequency.valueOf(frequency), requireNotNull(zone),
            if (frequency == "EVERY_N_DAYS") requireNotNull(interval.toIntOrNull()) { "間隔須為 1–3650 天" } else 1,
            if (frequency == "WEEKDAYS") weekdays.map { java.time.DayOfWeek.of(it) }.toSet() else emptySet(),
            if (endMode == "DATE") requireNotNull(parseDateInput(endDate, LocalDate.parse(endReference))) { "請填結束日期" } else null,
            if (endMode == "COUNT") requireNotNull(count.toIntOrNull()) { "請填有效的總次數" } else null)
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
            require(!versionsLost) { "編輯狀態已重建，請關閉後重新開啟任務，再確認系列修改。" }
            require(!expectedExisting || existing != null) { "任務已不存在，請關閉表單後重試" }
            val dueTime = parseTimeInput(time)
            val recurrence = configuredRule()
            val dueDate = recurrence?.dateAt(1) ?: resolvedDate()
            require(recurringContext == null || dueDate != null) { "重複紀錄須保留本次日期" }
            require(!applyRecurrence || !hasSingleReminders || inheritedRemindersReady) { "請先按「沿用單筆提醒」，檢查系列提醒後再儲存" }
            val inherit = applyRecurrence && inheritedRemindersReady
            require(expectedExisting || (!assignment && !study) || statusName != TaskStatus.COMPLETED.name) { "新增作業或學習任務不能直接選擇完成" }
            val beginning = resolvedStartDate()
            val draft = TaskDraft(id, title, notes, categoryId, dueDate, dueTime,
                if (dueDate != null) zone else null,
                requireNotNull(statusName?.let(TaskStatus::valueOf)) { "請先選擇作業狀態" }, revision.takeIf { it >= 0 }, selectedTags.toSet(),
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
                "已完成任務請先重新開啟，再設定重複"
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
                    error = exception.message ?: "儲存失敗，輸入已保留，請重試。"
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
                    TopAppBar(title = { Text(if (expectedExisting) "編輯任務" else "新增任務") },
                        navigationIcon = { TextButton(onClick = ::requestExit, enabled = !saving) { Text("取消") } })
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!expectedExisting) OutlinedButton(onClick = { save(true) }, enabled = !saving && !imageBusy,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("save_next")) { Text("新增下一項") }
                            Button(onClick = { save(false) }, enabled = !saving && !imageBusy, shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("save_task")) {
                                Text(if (saving) "儲存中…" else if (expectedExisting) "儲存" else "建立")
                            }
                        }
                    }
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("任務名稱") },
                        modifier = Modifier.fillMaxWidth().focusRequester(titleFocus).testTag("task_title"), enabled = !saving && !imageBusy,
                        colors = fieldColors, shape = MaterialTheme.shapes.medium,
                        singleLine = true, isError = error != null && title.isBlank())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("editor_error")) }
                    if(draftReferencesChanged) Text("草稿中的部分分類或標籤已不存在，已移除失效標籤並使用可用分類，請重新確認。",
                        color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                    Text("日期與時間", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    DeadlineTextField(date, {
                        date = it; deadlineReference = System.currentTimeMillis(); zone = ZoneId.systemDefault().id
                        if (it.isBlank() && !applyRecurrence) time = ""
                    },
                        label = if (applyRecurrence && recurringContext == null) "第一次截止日期（留空為今天）" else if (applyRecurrence) "截止日期" else "截止日期（選填）", placeholder = "YYYY-MM-DD",
                        supportingText = if (datePreview != null) { { Text(datePreview) } } else null,
                        trailingIcon = { DatePickerButton(runCatching { resolvedDate() }.getOrNull() ?: LocalDate.now(), R.string.choose_date, enabled = !saving) {
                            date = it.toString(); deadlineReference = System.currentTimeMillis(); zone = ZoneId.systemDefault().id
                        } },
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().testTag("task_date"), enabled = !saving)
                    DeadlineTextField(time, { time = it; deadlineReference = System.currentTimeMillis(); zone = ZoneId.systemDefault().id }, label = "截止時間（選填）", placeholder = "HH:mm",
                        supportingText = if (timePreview != null) { { Text(timePreview) } } else null,
                        trailingIcon = {
                            TextButton(enabled = hasDeadlineDate && !saving, onClick = { showTimePicker = true },
                                modifier = Modifier.testTag("open_time")) { Text("選擇") }
                        }, modifier = Modifier.fillMaxWidth().testTag("task_time"),
                        colors = fieldColors,
                        enabled = !saving && hasDeadlineDate)
                    if (hasDeadlineDate) Text(
                        "截止時區：" + runCatching {
                            val parsedDate = resolvedDate()
                            val parsedTime = parseTimeInput(time)
                            if (parsedDate != null && parsedTime != null && zone != null) deadlineZoneLabel(parsedDate, parsedTime, requireNotNull(zone)) else zone
                        }.getOrNull().orEmpty().ifBlank { zone.orEmpty().ifBlank { "未記錄；修改日期或時間後記錄" } } + if (time.isBlank()) "（只有日期，不指定時刻）" else "",
                        Modifier.testTag("deadline_zone"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (date.isNotBlank()) TextButton(onClick = { date = ""; time = "" }, enabled = !saving) { Text("清除日期與時間") }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("分類與狀態", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Box {
                        OutlinedButton(onClick = { categoryMenu = true }, enabled = !saving && !imageBusy, shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text("分類：${categories.find { it.id == categoryId }?.name ?: "未分類"}")
                        }
                        DropdownMenu(categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            categories.forEach { category -> DropdownMenuItem(text = { Text(category.name) }, onClick = {
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
                                Text("學習狀態：${statusLabel(statusName?.let(TaskStatus::valueOf) ?: TaskStatus.OPEN, STUDY_ID)}")
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
                        }, label = "開始日期（選填）", placeholder = "YYYY-MM-DD",
                            supportingText = if (startDatePreview != null) { { Text(startDatePreview) } } else null,
                            trailingIcon = { DatePickerButton(runCatching { resolvedStartDate() }.getOrNull() ?: LocalDate.now(), R.string.choose_date, enabled = !saving) {
                                startDateInput = it.toString(); startReference = System.currentTimeMillis(); startZone = ZoneId.systemDefault().id
                            } }, colors = fieldColors, modifier = Modifier.fillMaxWidth().testTag("task_start_date"), enabled = !saving)
                        DeadlineTextField(startTimeInput, {
                            startTimeInput = it; startReference = System.currentTimeMillis(); startZone = ZoneId.systemDefault().id
                        }, label = "開始時間（選填）", placeholder = "HH:mm",
                            supportingText = if (startTimePreview != null) { { Text(startTimePreview) } } else null,
                            trailingIcon = { TextButton(enabled = startDateInput.isNotBlank() && !saving, onClick = { showStartTimePicker = true },
                                modifier = Modifier.testTag("open_start_time")) { Text("選擇") } },
                            modifier = Modifier.fillMaxWidth().testTag("task_start_time"), colors = fieldColors,
                            enabled = !saving && startDateInput.isNotBlank())
                        if (startDateInput.isNotBlank()) {
                            Text("開始時區：${startZone.orEmpty()}" + if (startTimeInput.isBlank()) "（只有日期，不指定時刻）" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { startDateInput = ""; startTimeInput = ""; startZone = null }, enabled = !saving) { Text("清除開始日期與時間") }
                        }
                    }
                    if (assignment) Box {
                        OutlinedButton(onClick = { statusMenu = true }, enabled = !saving && (expectedExisting || frequency == "NONE"), shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(statusName?.let { "作業狀態：${statusLabel(TaskStatus.valueOf(it))}" } ?: "請選擇作業狀態")
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
                        Text("重複", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (recurringContext != null) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(!applyFuture, onClick = { applyFuture = false }, enabled = !saving && !imageBusy,
                                    label = { Text("僅本次") }, modifier = Modifier.testTag("repeat_scope_single"))
                                FilterChip(applyFuture, onClick = { applyFuture = true },
                                    enabled = !saving && futureAllowed,
                                    label = { Text("本次及未來") }, modifier = Modifier.testTag("repeat_scope_future"))
                            }
                            Text(if (applyFuture) "更新本次、已產生的未來未完成項目及系列規則；過去、已完成及已刪除紀錄保留。" else
                                if (!futureAllowed) "這次只可修改本次；過去紀錄與已停止系列不重寫未來規則。" else "只修改這一次，保留系列規則。",
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
                            onEndDate = { endDate = it; endReference = LocalDate.now().toString() }, onCount = { count = it })
                        if (applyRecurrence) {
                            runCatching { configuredRule()?.dateAt(1) }.getOrNull()?.let {
                                Text("本次日期：$it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("repeat_first_date"))
                            }
                            Text(if (convertingExisting) "這一次保留目前進度；之後的新作業從「未開始」開始。" else "未完成也會繼續產生；每次作業從「未開始」開始。", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (expectedExisting && recurringContext == null && existing?.status == TaskStatus.COMPLETED) {
                        Text("已完成任務請先重新開啟，再設定重複。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("conversion_reopen_hint"))
                    }
                    if (applyRecurrence) {
                        SeriesReminderFields(seriesReminderInputs, { seriesReminderInputs = it }, runCatching { configuredRule()?.dateAt(1) }.getOrNull(),
                            runCatching { parseTimeInput(time) }.getOrNull(), zone, !saving)
                        if (hasSingleReminders) OutlinedCard(Modifier.fillMaxWidth().testTag("reminder_conversion_card")) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (inheritedRemindersReady) "已沿用單筆提醒" else "沿用現有提醒", style = MaterialTheme.typography.titleSmall)
                                Text("這一次保留目前表單的提醒，設為獨立提醒；之後各次使用上方系列提醒。截止前沿用提前量；固定時間按截止時區換算成日期偏移與時刻。",
                                    style = MaterialTheme.typography.bodySmall)
                                if (!inheritedRemindersReady && inheritedReminderStamp != null) Text("日期、時間或單筆提醒已改動，請重新沿用並檢查。", color = MaterialTheme.colorScheme.error)
                                OutlinedButton(enabled = !saving && !imageBusy, modifier = Modifier.testTag("inherit_single_reminders"), onClick = {
                                    runCatching {
                                        val day = requireNotNull(configuredRule()?.dateAt(1)) { "請先設定首次日期" }
                                        val z = requireNotNull(zone) { "請先設定截止時區" }
                                        val sources = decodeReminderInputs(reminderInputs).map { it.spec(day, parseTimeInput(time), z) }
                                        val inherited = inheritReminderTemplates(sources, day, z)
                                        val extra = decodeSeriesReminderInputs(seriesReminderInputs).filterNot { it.id in inheritedTemplateIds }
                                        require(inherited.size + extra.size <= 32) { "每個系列最多 32 個提醒，請先減少提醒數量" }
                                        seriesReminderInputs = encodeSeriesReminderInputs(inherited.map { it.input() } + extra)
                                        inheritedTemplateIds = inherited.map { it.id }
                                        inheritedReminderStamp = reminderConversionStamp()
                                        error = null
                                    }.onFailure { error = it.message }
                                }) { Text(if (inheritedRemindersReady) "重新沿用單筆提醒" else "沿用單筆提醒") }
                            }
                        }
                        if (recurringContext != null) Text("本次及未來套用系列模板；其他次數單獨自訂的提醒會保留。", style=MaterialTheme.typography.bodySmall)
                    } else {
                        if (recurringContext != null) {
                            Text(if (resetReminders) "儲存後重設為系列提醒。" else if (recurringContext.reminderOverridden) "這次有獨立提醒，後續系列修改會保留。" else
                                "此處只改這一次；其他次數及系列模板不變。",style=MaterialTheme.typography.bodySmall)
                            OutlinedButton(enabled=!saving, modifier=Modifier.testTag("reset_series_reminders"),onClick={
                                runCatching {
                                    val day=requireNotNull(resolvedDate());val z=requireNotNull(zone);val t=parseTimeInput(time)
                                    reminderInputs=encodeReminderInputs(recurringContext.reminders.filter{it.enabled}.map { occurrenceReminder(it.spec(),id,day,t,z).input() })
                                    resetReminders=true
                                }.onFailure { error=it.message }
                            }){Text("重設為系列提醒")}
                        }
                        ReminderFields(reminderInputs, { reminderInputs = it; resetReminders = false }, runCatching { resolvedDate() }.getOrNull(),
                            runCatching { parseTimeInput(time) }.getOrNull(), zone, initialReminders, !saving, allowAdd = true)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("標籤", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (tags.isEmpty()) Text("可到「分類與標籤」新增標籤。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag -> FilterChip(selected = tag.id in selectedTags,
                            onClick = { selectedTags = if (tag.id in selectedTags) selectedTags - tag.id else selectedTags + tag.id },
                            enabled = !saving && !imageBusy, label = { Text(tag.name) }, modifier = Modifier.testTag("editor_tag_${tag.id}")) }
                    }
                    Text("備註與圖片", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    NoteImageField(imageIds, !saving, { imageIds = it }, { imageBusy = it }, onImagePickerActive)
                    OutlinedTextField(notes, { notes = it }, label = { Text("備註") }, modifier = Modifier.fillMaxWidth().testTag("task_notes"),
                        colors = fieldColors, shape = MaterialTheme.shapes.medium,
                        minLines = 3, enabled = !saving)
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
        modifier=Modifier.testTag("draft_exit_dialog"),title={Text("保留新增任務草稿？")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("保留後可稍後繼續編輯；草稿尚未建立任務，也不會排提醒。")
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }},
        confirmButton={TextButton(enabled=!saving,modifier=Modifier.testTag("keep_draft"),onClick={
            saving=true
            scope.launch { try { onKeepDraft(snapshotDraft());onDismiss() }
                catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException)throw e;error="草稿未能保留，請重試。" }
                finally { saving=false } }
        }) { Text("保留草稿") }},
        dismissButton={Row {
            TextButton(enabled=!saving,modifier=Modifier.testTag("discard_draft"),onClick={
                saving=true
                scope.launch { try { onDiscardDraft();onDismiss() }
                    catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException)throw e;error="草稿未能捨棄，請重試。" }
                    finally { saving=false } }
            }) {Text("捨棄")}
            TextButton(enabled=!saving,onClick={showDraftExit=false},modifier=Modifier.testTag("continue_editing")) {Text("繼續編輯")}
        }})
    if (showPastWarning) AlertDialog(
        onDismissRequest = { showPastWarning = false },
        modifier = Modifier.testTag("past_deadline_warning"),
        title = { Text("截止時間早於現在") },
        text = {
            val warningDate = runCatching { configuredRule()?.dateAt(1) ?: resolvedDate() }.getOrNull()
            val resolvedTime = runCatching { parseTimeInput(time) }.getOrNull()
            Text(if (resolvedTime == null) "截止日期 $warningDate 已早於今天，仍要儲存嗎？"
                else "截止為 $warningDate ${resolvedTime.format(DateTimeFormatter.ofPattern("HH:mm"))}（$zone），已早於現在。仍要儲存嗎？")
        },
        confirmButton = { TextButton(onClick = { showPastWarning = false; save(pendingNext, pastConfirmed = true) },
            modifier = Modifier.testTag("confirm_past_deadline")) { Text("仍要儲存") } },
        dismissButton = { TextButton(onClick = { showPastWarning = false }, modifier = Modifier.testTag("cancel_past_deadline")) { Text("返回修改") } },
    )
    if (showConversionPreview && (convertingExisting || (applyRecurrence && inheritedRemindersReady))) AlertDialog(
        onDismissRequest = { showConversionPreview = false }, modifier = Modifier.testTag("convert_series_preview"),
        title = { Text("改為重複任務？") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (expectedExisting) "這項任務會成為系列的第 1 次，保留原本的建立時間與目前進度。" else "建立重複系列，並保留這一次已輸入的提醒。")
                if (inheritedRemindersReady) {
                    Text("這一次保留 ${decodeReminderInputs(reminderInputs).size} 個獨立提醒；之後每次套用 ${decodeSeriesReminderInputs(seriesReminderInputs).size} 個系列提醒。")
                    Text("本次固定提醒保留原時刻與時區。之後固定提醒按截止時區 $zone 的曆日及時間計算，不會重用第一筆的絕對日期。")
                    Text("這一次不隨後續系列提醒修改；需要時可在「僅本次」重設為系列提醒。")
                }
                val previewRule = runCatching { configuredRule() }.getOrNull()
                Text("首次日期：${previewRule?.dateAt(1)}")
                Text("截止時區：$zone")
                previewRule?.let { rule ->
                    val frequencyLabel = if (rule.frequency == RecurrenceFrequency.EVERY_N_DAYS) "每隔 ${rule.intervalDays} 天" else recurrenceLabel(rule.frequency)
                    val ending = rule.maxCount?.let { "共 $it 次（含這一次）" }
                        ?: rule.endDate?.let { "至 $it（含當天）" } ?: "一直重複"
                    Text("重複方式：$frequencyLabel · $ending")
                }
                Text("目前進度：${statusName?.let { statusLabel(TaskStatus.valueOf(it)) }.orEmpty()}")
                Text("總次數包含這一次；開始日期在過去時，會補齊已到日期的紀錄。之後可在「重複紀錄」查閱各次進度。")
                Text("之後的新作業從「未開始」開始；名稱、備註、分類、標籤與截止採表單值。")
            }
        },
        confirmButton = { TextButton(onClick = {
            showConversionPreview = false
            save(pendingNext, pendingPastApproved, conversionConfirmed = true)
        }, modifier = Modifier.testTag("confirm_convert_series")) { Text("確認轉換") } },
        dismissButton = { TextButton(onClick = { showConversionPreview = false },
            modifier = Modifier.testTag("cancel_convert_series")) { Text("返回修改") } },
    )
    if (showSeriesPreview && recurringContext != null) AlertDialog(
        onDismissRequest = { showSeriesPreview = false }, modifier = Modifier.testTag("series_edit_preview"),
        title = { Text("套用到本次及未來？") },
        text = {
            val preview = runCatching {
                val recurrence = requireNotNull(configuredRule())
                val draft = TaskDraft(id, title, dueDate = recurrence.dateAt(1), recurrence = recurrence)
                planSeriesEdit(draft, SeriesEditRecord(recurringContext.occurrence, requireNotNull(existing)),
                    recurringContext.rule, recurringContext.records, DeviceClock)
            }.getOrNull()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("本次：${runCatching { configuredRule()?.dateAt(1) }.getOrNull()}")
                if (preview != null) {
                    Text("另外更新 ${preview.updatedCount} 項、重新啟用 ${preview.restoredCount} 項、取消 ${preview.cancelledCount} 項未來紀錄。")
                    Text("其他項目的進度會保留。一般待辦轉作業設為「未開始」，作業轉一般設為「未完成」。名稱、備註、標籤與截止採表單值。")
                    Text("過去、已完成及已刪除紀錄保留。規則取消的項目可在歷史查閱，延長規則時可重新啟用。")
                    Text("系列提醒套用到本次及未來未自訂的項目；單獨自訂的提醒保留。既有固定時刻只有明確改該模板或重設時才重新計算。")
                } else Text("這次已不能修改系列，請返回後重新開啟。")
            }
        },
        confirmButton = { TextButton(onClick = { showSeriesPreview = false; save(pendingNext, pendingPastApproved, seriesConfirmed = true) },
            modifier = Modifier.testTag("confirm_series_edit")) { Text("確認套用") } },
        dismissButton = { TextButton(onClick = { showSeriesPreview = false }, modifier = Modifier.testTag("cancel_series_edit")) { Text("返回修改") } },
    )
}
