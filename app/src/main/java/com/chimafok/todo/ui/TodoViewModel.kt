package com.chimafok.todo.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.SeriesEditRecord
import com.chimafok.todo.domain.TaskDraft
import com.chimafok.todo.domain.TaskStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class Destination { AGENDA, UNSCHEDULED, COMPLETED, ORGANIZE, TRASH, SETTINGS }

data class TodoState(
    val tasks: List<TaskEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val taskTags: List<TaskTagEntity> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val series: List<RecurringSeriesEntity> = emptyList(),
    val rules: List<SeriesRuleEntity> = emptyList(),
    val occurrences: List<OccurrenceEntity> = emptyList(),
    val seriesTags: List<SeriesTagEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val seriesReminders: List<SeriesReminderEntity> = emptyList(),
    val reminderOverrides: List<ReminderOverrideEntity> = emptyList(),
    val retentionPolicy: RetentionPolicyEntity = RetentionPolicyEntity(),
    val detailTaskId: String? = null,
    val dataEpoch: Long = 0,
    val contextIds: Set<String> = emptySet(),
    val batchToken: Long = 0,
)

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val database = com.chimafok.todo.reminders.ReminderRuntime.database
    private val draftStore = NewTaskDraftStore(application.filesDir)
    var newTaskDraft by mutableStateOf<NewTaskDraft?>(null)
        private set
    var draftLoadError by mutableStateOf<String?>(null)
        private set
    private val draftLoading = viewModelScope.launch {
        try {
            val saved = withContext(Dispatchers.IO) { draftStore.read() }
            if (saved != null) {
                decodeReminderInputs(saved.reminderInputs)
                decodeSeriesReminderInputs(saved.seriesReminderInputs).forEach {
                    com.chimafok.todo.domain.SeriesReminderKind.valueOf(it.kind)
                    com.chimafok.todo.domain.ReminderOffsetUnit.valueOf(it.unit)
                }
                saved.statusName?.let(TaskStatus::valueOf)
                if(saved.frequency != "NONE") com.chimafok.todo.domain.RecurrenceFrequency.valueOf(saved.frequency)
                require(saved.endMode in listOf("NONE","DATE","COUNT"))
                saved.zoneId?.let(java.time.ZoneId::of)
                // A crash after committing a task but before removing its draft must
                // never offer to create a duplicate task/series on the next launch.
                if (database.dao().task(saved.id) != null || database.dao().series(saved.id) != null)
                    withContext(Dispatchers.IO) { draftStore.clear() }
                else newTaskDraft = saved
            }
        } catch(e: Exception) {
            if(e is kotlinx.coroutines.CancellationException) throw e
            draftLoadError = "草稿無法讀取；原草稿仍保留，可捨棄後重新新增。"
        }
    }
    suspend fun awaitNewTaskDraft(): NewTaskDraft? { draftLoading.join();return newTaskDraft }
    suspend fun keepNewTaskDraft(draft: NewTaskDraft) = viewModelScope.async {
        withContext(Dispatchers.IO) { draftStore.save(draft) }
        if(database.dao().task(draft.id) != null || database.dao().series(draft.id) != null) {
            withContext(Dispatchers.IO) { if(draftStore.read()?.id == draft.id) draftStore.clear() }
            newTaskDraft = null
        } else newTaskDraft = draft
        draftLoadError = null
    }.await()
    suspend fun discardNewTaskDraft(id: String? = null) = viewModelScope.async {
        draftLoading.join()
        withContext(Dispatchers.IO) { if(id == null || draftStore.read()?.id == id) draftStore.clear() }
        if(id == null || newTaskDraft?.id == id) newTaskDraft = null
        draftLoadError = null
    }.await()
    private var homePage: TaskPageWindow? = null
    private var homeDay: String? = null
    private var homeZone: String? = null
    fun cachedHomePage(clock: java.time.Clock): TaskPageWindow? = homePage.takeIf {
        homeDay == java.time.LocalDate.now(clock).toString() && homeZone == clock.zone.id
    }
    fun retainHomePage(page: TaskPageWindow, clock: java.time.Clock) {
        homePage = page;homeDay = java.time.LocalDate.now(clock).toString();homeZone = clock.zone.id
    }
    private val repository = TaskRepository(database)
    private val organization = OrganizationRepository(database)
    private val recurring = RecurringRepository(database)
    private val history = HistoryRepository(database)
    private val _state = MutableStateFlow(TodoState())
    val state = _state.asStateFlow()
    private var currentDestination by mutableStateOf(Destination.AGENDA)
    var destination: Destination
        get() = currentDestination
        set(value) {
            val wasFull = currentDestination in listOf(Destination.ORGANIZE, Destination.TRASH)
            currentDestination = value
            val nowFull = value in listOf(Destination.ORGANIZE, Destination.TRASH)
            if (wasFull != nowFull) observe(showLoading = nowFull)
        }
    private var requestedDetail: String? = null
    private var dataEpoch = 0L
    private val browser = HistoryBrowserRepository(database)
    private val taskPages = TaskPageRepository(database)
    @Volatile private var visibleTaskIds = emptySet<String>()
    val trashSeriesSelection = mutableStateOf(emptyList<String>())
    val historyRecordSelection = mutableStateOf(emptyMap<String,Long>())
    val historySeriesSelection = mutableStateOf(emptyMap<String,Long>())
    private var historySelectionKey=""
    var trashSelectionPreview: TrashSelectionPreview? = null
    var seriesSelectionPreview: SeriesSelectionPreview? = null
    var trashSelectionTicket=""
    var seriesSelectionTicket=""
    private val selections=SelectionRepository(database)
    fun resetHistorySelection(key:String) {if(key!=historySelectionKey) {
        historySelectionKey=key;historyRecordSelection.value=emptyMap();historySeriesSelection.value=emptyMap()
    }}
    suspend fun historySelection(id:String,query:String)=browser.selection(id,query)
    suspend fun prepareTrashSelection(tasks:Map<String,Long>,series:Map<String,Long>)=selections.previewTrash(tasks,series).also {trashSelectionPreview=it}
    suspend fun applyTrashSelection(preview:TrashSelectionPreview,restore:Boolean) {if(restore)selections.restore(preview) else selections.purge(preview)}
    suspend fun prepareSeriesSelection(revisions:Map<String,Long>)=selections.previewSeries(revisions).also {seriesSelectionPreview=it}
    suspend fun applySeriesSelection(preview:SeriesSelectionPreview,stopOnly:Boolean,includeHistory:Boolean)=selections.applySeries(preview,stopOnly,includeHistory)
    val taskSelection = mutableStateOf(emptyList<String>())
    val batchChoices = mutableStateOf(emptyMap<String,String>())
    private var selectionKey = ""
    var purgePreview by mutableStateOf<PurgePreview?>(null)
    var purgeTicket = ""
    var retainedRetention by mutableStateOf<RetentionPreview?>(null)
    var retentionTicket = ""
    private var backupChoiceToken = ""
    private val restoreChoices = mutableStateOf(emptyMap<String,String>())
    fun backupChoicesFor(token: String): androidx.compose.runtime.MutableState<Map<String,String>> {
        if (backupChoiceToken != token) { backupChoiceToken = token; restoreChoices.value = emptyMap() }
        return restoreChoices
    }
    var batchRevisions by mutableStateOf(emptyMap<String,Long>())
        private set
    var batchToken by mutableLongStateOf(0L)
        private set
    fun resetSelectionIfChanged(destination: Destination, epoch: Int) {
        val key = "${destination.name}/$epoch"
        if (key != selectionKey) { selectionKey = key; taskSelection.value = emptyList();trashSeriesSelection.value=emptyList() }
    }
    fun prepareBatch(revisions: Map<String,Long>) { batchRevisions = revisions; batchChoices.value=emptyMap(); batchToken++; observe(false) }
    fun clearBatch() { if (batchRevisions.isNotEmpty()) { batchRevisions = emptyMap(); batchChoices.value=emptyMap(); batchToken++; observe(false) } }
    suspend fun taskWindow(scope: com.chimafok.todo.domain.TaskScope, filter: com.chimafok.todo.domain.TaskFilter,
        clock: java.time.Clock, jumpDate: java.time.LocalDate?, dated: Int, undated: Int) = taskPages.window(scope,filter,clock,jumpDate,dated,undated)

    fun presentPage(rows: TaskPageRows) {
        visibleTaskIds = rows.tasks.map { it.id }.toSet()
        val current = _state.value
        val context = current.contextIds
        _state.value = current.copy(
            tasks = (current.tasks.filter { it.id in context } + rows.tasks.filter { it.id !in context }).sortedWith(compareBy<TaskEntity>{it.createdAt}.thenBy{it.id}),
            taskTags = current.taskTags.filter { it.taskId in context } + rows.links.filter { it.taskId !in context },
            occurrences = current.occurrences.filter { it.taskId in context } + rows.occurrences.filter { it.taskId !in context },
            reminders = current.reminders.filter { it.taskId in context } + rows.reminders.filter { it.taskId !in context },
            reminderOverrides = current.reminderOverrides.filter { it.taskId in context } + rows.overrides.filter { it.taskId !in context })
    }
    fun clearPage() {
        visibleTaskIds = emptySet()
        if (destination !in listOf(Destination.ORGANIZE,Destination.TRASH)) presentPage(TaskPageRows())
    }

    var seriesActionPreview: SeriesActionPreview? = null
        private set
    fun clearSeriesAction() { seriesActionPreview = null }
    suspend fun historyWindow(id: String?, query: String, limit: Int) = browser.window(id, query, limit)
    suspend fun prepareSeriesAction(id: String, clock: java.time.Clock): SeriesActionPreview =
        browser.actionPreview(id, clock).also { seriesActionPreview = it }
    private var editorVersions: EditorVersions? = null
    fun editorVersions(id: String, records: List<SeriesEditRecord>): EditorVersions {
        val old = editorVersions
        return if (old?.taskId == id) old else EditorVersions(id, java.util.UUID.randomUUID().toString(),
            records.associate { it.task.id to it.task.revision }).also { editorVersions = it }
    }
    fun loadDetail(id: String?) {
        if (requestedDetail != id) editorVersions = null
        if (requestedDetail != id) { requestedDetail = id; observe(showLoading = false) }
    }
    var homeEpoch by mutableIntStateOf(0)
        private set
    private var observation: Job? = null
    private var generation: Job? = null
    var recurrenceError by mutableStateOf<String?>(null)
        private set
    var reconciling by mutableStateOf(false)
        private set
    private var lastReconcileAt = -30_000L
    private var lastReconcileDay = ""
    var foregroundEpoch by mutableIntStateOf(0)
        private set
    fun onForeground() {
        foregroundEpoch++
        com.chimafok.todo.reminders.ReminderRuntime.foreground(getApplication())
    }

    init { reload(); reconcile() }

    fun goHome() {
        destination = Destination.AGENDA
        homeEpoch++
        reconcile()
    }

    fun reload() = observe(showLoading = true)

    private fun observe(showLoading: Boolean) {
        observation?.cancel()
        val full = destination in listOf(Destination.ORGANIZE, Destination.TRASH)
        val detail = requestedDetail
        val batchIds = batchRevisions.keys.toSet()
        val token = batchToken
        observation = viewModelScope.launch {
            _state.value = _state.value.copy(loading = showLoading || _state.value.loading, error = null)
            try {
                database.invalidationTracker.createFlow("tasks", "categories", "tags", "task_tags", "recurring_series", "series_rules", "occurrences", "series_tags", "reminders", "series_reminders", "reminder_overrides", "retention_policy").map {
                    database.withTransaction {
                        val dao = database.dao()
                        val detailSeries = detail?.let { dao.occurrence(it)?.seriesId }
                        val context = if (full) database.taskPages().allIds().toSet() else buildSet {
                            addAll(database.taskPages().pendingRecurringIds())
                            detail?.let(::add)
                            detailSeries?.let { addAll(database.taskPages().seriesIds(it)) }
                            addAll(batchIds)
                        }
                        val rows = taskPages.rows(context + visibleTaskIds)
                        TodoState(rows.tasks, dao.allCategories(), dao.allTags(), rows.links, loading = false,
                            series = dao.allSeries(), rules = dao.allRules(), occurrences = rows.occurrences, seriesTags = dao.allSeriesTags(),
                            reminders = rows.reminders, seriesReminders = database.reminders().templates(), reminderOverrides = rows.overrides,
                            retentionPolicy = history.policy(), detailTaskId = detail, dataEpoch = ++dataEpoch, contextIds = context, batchToken = token)
                    }
                }.collect { _state.value = it }
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                _state.value = _state.value.copy(loading = false, error = "無法讀取資料，請重試。")
            }
        }
    }

    fun reconcile(force: Boolean = false) {
        val current = _state.value
        val historyNeeded = current.retentionPolicy.days != null || current.tasks.any { it.deletedAt != null }
        if (!force && !current.loading && current.series.isEmpty() && !historyNeeded) { recurrenceError = null; return }
        if (generation?.isActive == true) return
        val elapsed = android.os.SystemClock.elapsedRealtime()
        val day = "${java.time.ZoneId.systemDefault()}/${java.time.LocalDate.now()}"
        if(!force && elapsed-lastReconcileAt < 30_000 && day == lastReconcileDay) return
        lastReconcileAt = elapsed;lastReconcileDay = day
        generation = viewModelScope.launch {
            // Routine history checks do not need an animated loading bar.
            reconciling = _state.value.series.isNotEmpty(); recurrenceError = null
            try { history.maintain(); recurring.reconcileAll() } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                recurrenceError = "紀錄維護未能完成，請重試。"
            } finally { reconciling = false }
        }
    }

    var externalDocumentFlow by mutableStateOf(false)
    private val backup = BackupRepository(database)
    suspend fun exportBackup():String = backup.export(getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName,0).versionName ?: "0.6.1-dev")
    suspend fun previewBackup(document:BackupDocument,mode:RestoreMode,choices:Map<String,BackupChoice>) = backup.preview(document,mode,choices)
    suspend fun applyBackup(document:BackupDocument,preview:BackupPreview,choices:Map<String,BackupChoice>) = backup.apply(document,preview,choices)

    suspend fun previewRetention(days: Int?) = history.preview(days)
    suspend fun applyRetention(preview: RetentionPreview) = history.apply(preview)
    suspend fun purge(preview: PurgePreview) = history.purge(preview)

    suspend fun save(draft: TaskDraft) {
        repository.save(draft)
        if(draft.expectedRevision == null && newTaskDraft?.id == draft.id) {
            try { discardNewTaskDraft(draft.id) }
            catch(e: Exception) {
                if(e is kotlinx.coroutines.CancellationException) throw e
                // The committed task is authoritative. Startup also detects and
                // removes this leftover draft without offering a duplicate create.
                newTaskDraft = null
            }
        }
        reconcile(force = true)
    }
    suspend fun status(task: TaskEntity, status: TaskStatus) = repository.setStatus(task.id, task.revision, status)
    suspend fun trash(task: TaskEntity) = repository.trash(task.id, task.revision)
    suspend fun restore(task: TaskEntity) = repository.restore(task.id)
    suspend fun moveBatch(revisions: Map<String, Long>, targetId: String, choices: Map<String, TaskStatus>) = repository.moveBatch(revisions, targetId, choices)
    suspend fun addTagsBatch(revisions: Map<String, Long>, tags: Set<String>) = repository.addTagsBatch(revisions, tags)
    suspend fun trashBatch(revisions: Map<String, Long>) = repository.trashBatch(revisions)
    suspend fun saveCategory(id: String, name: String, revision: Long?) = organization.saveCategory(id, name, revision)
    suspend fun saveTag(id: String, name: String, revision: Long?) = organization.saveTag(id, name, revision)
    suspend fun moveOrganization(id: String, tags: Boolean, delta: Int, order: List<String>) = organization.move(id, tags, delta, order)
    suspend fun deleteTag(tag: TagEntity) = organization.deleteTag(tag.id, tag.revision)
    suspend fun deleteCategory(category: CategoryEntity, targetId: String, tasks: Map<String, Long>, choices: Map<String, TaskStatus>) =
        organization.deleteCategory(category.id, category.revision, targetId, tasks, choices)
    suspend fun stopSeries(id: String, revision: Long) = recurring.stop(id, revision)
    suspend fun deleteSeries(id: String, revision: Long, tasks: Map<String, Long>, history: Boolean) = recurring.delete(id, revision, tasks, history)
    suspend fun restoreSeries(id: String, revision: Long) { recurring.restoreSeries(id, revision); reconcile() }

    var notificationTaskId by mutableStateOf<String?>(null)
        private set
    fun openReminder(id: String?, revision: Long) {
        viewModelScope.launch {
            val task = id?.let { database.dao().task(it) }
            if (task != null && task.deletedAt == null && task.revision == revision) notificationTaskId = task.id
        }
    }
    fun dismissReminderDetail() { notificationTaskId = null }
}
