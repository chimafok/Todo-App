package com.chimafok.todo.data

import androidx.room.withTransaction
import com.chimafok.todo.domain.*
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.util.UUID

data class OccurrenceSnapshot(val title: String, val notes: String, val category: String,
    val tags: List<String>, val status: String, val late: Boolean?, val imageIds: String = "") {
    fun json(): String = JSONObject().put("title", title).put("notes", notes).put("category", category)
        .put("imageIds", imageIds).put("tags", JSONArray(tags)).put("status", status).put("late", late ?: JSONObject.NULL).toString()
    companion object {
        fun read(json: String?): OccurrenceSnapshot? = json?.let { runCatching {
            val value = JSONObject(it)
            val tags = value.getJSONArray("tags")
            OccurrenceSnapshot(value.getString("title"), value.getString("notes"), value.getString("category"),
                (0 until tags.length()).map(tags::getString), value.getString("status"),
                if (value.isNull("late")) null else value.getBoolean("late"), value.optString("imageIds", ""))
        }.getOrNull() }
    }
}

class RecurringRepository(private val db: TodoDatabase, private val clock: Clock = DeviceClock) {
    private val dao get() = db.dao()

    private fun entityRule(seriesId: String, version: Int, effectiveFrom: Int, draft: TaskDraft): SeriesRuleEntity {
        val rule = requireNotNull(draft.recurrence)
        require(rule.maxCount == null || rule.maxCount >= effectiveFrom) { "總次數不可早於本次序號 $effectiveFrom" }
        return SeriesRuleEntity(UUID.randomUUID().toString(), seriesId, version, effectiveFrom, rule.anchor, rule.frequency,
            rule.intervalDays, rule.weekdays.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) },
            draft.dueTime, rule.zoneId, rule.endDate, rule.maxCount, clock.millis(),
            draft.startDate?.let { Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(requireNotNull(draft.dueDate), it)) },
            draft.startTime, draft.startZoneId)
    }

    private suspend fun validate(draft: TaskDraft) {
        NoteImages(db).requireImages(draft.imageIds)
        if (draft.inheritSingleReminders) {
            require(dao.occurrence(draft.id) == null && !draft.reminders.isNullOrEmpty()) { "只能在首次轉換時沿用單筆提醒" }
            inheritReminderTemplates(draft.reminders, requireNotNull(draft.dueDate), requireNotNull(draft.zoneId))
            require(draft.seriesReminders != null) { "請先檢查沿用後的系列提醒" }
        } else require(draft.reminders.isNullOrEmpty() && (dao.occurrence(draft.id) != null || db.reminders().forTask(draft.id).none { it.enabled })) {
            "請先預覽並沿用單筆提醒，再設定重複；不會自動丟棄現有提醒"
        }
        require(!draft.resetSeriesReminders) { "系列編輯不能同時重設單次提醒" }
        draft.seriesReminders?.let { specs ->
            require(specs.size <= 32 && specs.map { it.id }.distinct().size == specs.size) { "每個系列最多 32 個不同提醒" }
            specs.forEach(::validateSeriesReminder)
        }
        val category = requireNotNull(dao.category(draft.categoryId)) { "找不到分類" }
        validateDraft(draft, category.systemKind == SystemCategory.ASSIGNMENTS)
        val rule = requireNotNull(draft.recurrence)
        require(draft.dueDate == rule.dateAt(1) && draft.zoneId == rule.zoneId) { "重複日期或時區與預覽不一致" }
        require(draft.tagIds.all { dao.tag(it) != null }) { "標籤已更新，請重新開啟表單" }
        val old = dao.task(draft.id)
        if (old == null || old.dueDate != draft.dueDate || old.dueTime != draft.dueTime || old.zoneId != draft.zoneId) {
            draft.dueTime?.let { deadlineInstant(requireNotNull(draft.dueDate), it, rule.zoneId) }
        }
    }

    suspend fun create(draft: TaskDraft): Unit = db.withTransaction {
        require(draft.expectedRevision == null) { "請以新增任務建立重複系列" }
        require(if (draft.categoryId == STUDY_ID) draft.status in listOf(TaskStatus.OPEN, TaskStatus.IN_PROGRESS) else draft.status == if (dao.category(draft.categoryId)?.systemKind == SystemCategory.ASSIGNMENTS) TaskStatus.NOT_STARTED else TaskStatus.OPEN) { "重複任務的新一次須從未完成開始" }
        val old = dao.series(draft.id)
        if (old != null) {
            val stored = requireNotNull(dao.latestRule(old.id))
            require(!draft.resetSeriesReminders && (draft.inheritSingleReminders || draft.reminders.isNullOrEmpty())) { "請先明確沿用單筆提醒" }
            require(draft.dueDate == stored.rule().dateAt(1) && draft.zoneId == stored.zoneId) { "重複日期或時區與預覽不一致" }
            require(old.title == validatedTitle(draft.title) && old.notes == draft.notes && old.imageIds == draft.imageIds && old.categoryId == draft.categoryId &&
                stored.rule() == draft.recurrence && stored.dueTime == draft.dueTime &&
                stored.startDayOffset == draft.startDate?.let { Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(requireNotNull(draft.dueDate), it)) } &&
                stored.startTime == draft.startTime && stored.startZoneId == draft.startZoneId && dao.seriesTagIds(old.id).toSet() == draft.tagIds &&
                (draft.seriesReminders == null || db.reminders().templatesFor(old.id).filter { it.enabled }.map { it.spec() }.toSet() == draft.seriesReminders.toSet())) { "新增識別碼衝突" }
            if (draft.inheritSingleReminders) require(db.reminders().overridden(draft.id) &&
                db.reminders().forTask(draft.id).filter { it.enabled }.map { it.spec() }.toSet() == draft.reminders?.toSet()) { "新增提醒識別碼衝突" }
            return@withTransaction
        }
        require(dao.task(draft.id) == null) { "此任務已存在，不能重複建立系列" }
        validate(draft)
        if (draft.inheritSingleReminders) {
            TaskRepository(db, clock).save(draft.copy(recurrence = null, seriesReminders = null, inheritSingleReminders = false))
            convert(draft.copy(expectedRevision = requireNotNull(dao.task(draft.id)).revision))
            return@withTransaction
        }
        val now = clock.millis()
        dao.insertSeries(RecurringSeriesEntity(draft.id, validatedTitle(draft.title), draft.notes, draft.categoryId, createdAt = now, updatedAt = now, imageIds = draft.imageIds))
        dao.insertRule(entityRule(draft.id, 1, 1, draft))
        dao.insertSeriesTags(draft.tagIds.map { SeriesTagEntity(draft.id, it) })
        saveSeriesReminders(db, draft.id, draft.seriesReminders, now)
        reconcileSeries(draft.id, 0)
        // Only the first occurrence adopts the selected learning progress. Future slots start fresh.
        if (draft.categoryId == STUDY_ID && draft.status == TaskStatus.IN_PROGRESS) {
            val first = requireNotNull(dao.task(occurrenceId(draft.id, 1)))
            TaskRepository(db, clock).setStatus(first.id, first.revision, TaskStatus.IN_PROGRESS)
        }
    }

    /** Adopt the existing task as slot one; later slots keep the normal deterministic IDs. */
    suspend fun convert(draft: TaskDraft): Unit = db.withTransaction {
        val original = requireNotNull(dao.task(draft.id)) { "任務已不存在，請重新開啟編輯" }
        require(draft.expectedRevision != null && original.revision == draft.expectedRevision) { "任務已更新，請重新開啟編輯" }
        require(original.deletedAt == null) { "此任務已在垃圾桶，請先復原" }
        require(original.status != TaskStatus.COMPLETED && draft.status != TaskStatus.COMPLETED) { "已完成任務請先重新開啟，再設定重複" }
        require(dao.occurrence(draft.id) == null && dao.series(draft.id) == null) { "任務已屬於重複系列，請重新開啟編輯" }
        validate(draft)
        TaskRepository(db, clock).save(draft.copy(recurrence = null, seriesReminders = null, inheritSingleReminders = false))
        val now = clock.millis()
        val series = RecurringSeriesEntity(draft.id, validatedTitle(draft.title), draft.notes, draft.categoryId,
            cursor = 1, createdAt = now, updatedAt = now, imageIds = draft.imageIds)
        val rule = entityRule(series.id, 1, 1, draft)
        dao.insertSeries(series)
        dao.insertRule(rule)
        dao.insertSeriesTags(draft.tagIds.map { SeriesTagEntity(series.id, it) })
        dao.insertOccurrence(OccurrenceEntity(original.id, series.id, 1, rule.id))
        // Keep the first reminder IDs, terminal delivery state and absolute instants intact.
        if (draft.inheritSingleReminders) db.reminders().override(ReminderOverrideEntity(original.id))
        val templates = saveSeriesReminders(db, series.id, draft.seriesReminders, now)
        applySeriesReminders(db, requireNotNull(dao.task(original.id)), templates, now)
        reconcileSeries(series.id)
    }

    /** Replans pending future rows atomically; consumed IDs and retired records never change. */
    suspend fun editTemplate(draft: TaskDraft): Unit = db.withTransaction {
        validate(draft)
        val occurrence = requireNotNull(dao.occurrence(draft.id))
        val series = requireNotNull(dao.series(occurrence.seriesId))
        require(series.deletedAt == null && series.lifecycle != SeriesLifecycle.STOPPED) { "系列已停止，請只修改這一次" }
        require(series.revision == draft.expectedSeriesRevision) { "重複系列已更新，請重新開啟編輯" }
        val old = requireNotNull(dao.task(draft.id))
        require(old.deletedAt == null && old.revision == draft.expectedRevision) { "任務已更新，請重新開啟編輯" }
        val latest = requireNotNull(dao.latestRule(series.id))
        val records = dao.seriesOccurrences(series.id).map { SeriesEditRecord(it, requireNotNull(dao.task(it.taskId))) }
        require(records.associate { it.task.id to it.task.revision } == draft.expectedOccurrenceRevisions) { "系列內的紀錄已更新，請重新開啟編輯" }
        val previousTemplates = db.reminders().templatesFor(series.id).filter { it.enabled }.map { it.spec() }
        val plan = planSeriesEdit(draft, SeriesEditRecord(occurrence, old), latest, records, clock)
        snapshotRetired(series.id, latest.zoneId)
        val rule = entityRule(series.id, latest.version + 1, plan.effectiveFrom,
            draft.copy(recurrence = requireNotNull(draft.recurrence).copy(anchor = plan.anchor)))
        dao.insertRule(rule)
        dao.updateSeries(series.copy(title = validatedTitle(draft.title), notes = draft.notes, imageIds = draft.imageIds, categoryId = draft.categoryId,
            lifecycle = SeriesLifecycle.ACTIVE, revision = series.revision + 1, updatedAt = clock.millis()))
        dao.clearSeriesTags(series.id)
        dao.insertSeriesTags(draft.tagIds.map { SeriesTagEntity(series.id, it) })
        val templates = saveSeriesReminders(db, series.id, draft.seriesReminders, clock.millis())
        val assignment = dao.category(draft.categoryId)?.systemKind == SystemCategory.ASSIGNMENTS
        plan.changes.forEach { change ->
            val task = change.record.task
            val ref = change.record.occurrence
            if (change.date == null) {
                snapshot(task.id, retire = true)
                val snapshotted = requireNotNull(dao.occurrence(task.id))
                dao.updateOccurrence(snapshotted.copy(ruleId = rule.id, excludedByRule = true))
                dao.update(task.copy(updatedAt = clock.millis(), revision = task.revision + 1))
            } else {
                val status = if (assignment) {
                    if (task.status == TaskStatus.OPEN) TaskStatus.NOT_STARTED else task.status
                } else convertStatus(task.status, false, study = draft.categoryId == STUDY_ID)
                dao.update(task.copy(title = validatedTitle(draft.title), notes = draft.notes, imageIds = draft.imageIds, categoryId = draft.categoryId,
                    dueDate = change.date, dueTime = draft.dueTime, zoneId = draft.zoneId, status = status,
                    startDate = draft.startDate?.plusDays(java.time.temporal.ChronoUnit.DAYS.between(requireNotNull(draft.dueDate), change.date)),
                    startTime = draft.startTime, startZoneId = draft.startZoneId,
                    updatedAt = clock.millis(), revision = task.revision + 1))
                dao.clearTaskTags(task.id)
                dao.insertTaskTags(draft.tagIds.map { TaskTagEntity(task.id, it) })
                dao.updateOccurrence(ref.copy(ruleId = rule.id, excludedByRule = false, snapshot = null))
                applySeriesReminders(db, requireNotNull(dao.task(task.id)), templates, clock.millis(), previousTemplates)
            }
        }
        dao.updateOccurrence(occurrence.copy(ruleId = rule.id))
        TaskRepository(db, clock).save(draft.copy(recurrence = null, seriesReminders = null))
        applySeriesReminders(db, requireNotNull(dao.task(draft.id)), templates, clock.millis(), previousTemplates)
        reconcileSeries(series.id)
    }

    suspend fun snapshot(taskId: String, replaceContent: Boolean = false, updateStatus: Boolean = false, retire: Boolean = false) {
        val occurrence = dao.occurrence(taskId) ?: return
        if (occurrence.excludedByRule && !retire) return
        val task = dao.task(taskId) ?: return
        val today = LocalDate.now(clock.withZone(ZoneId.of(task.zoneId ?: clock.zone.id)))
        if (!retire && task.status != TaskStatus.COMPLETED && task.deletedAt == null && requireNotNull(task.dueDate) >= today) {
            if (occurrence.snapshot != null) dao.updateOccurrence(occurrence.copy(snapshot = null))
            return
        }
        val old = OccurrenceSnapshot.read(occurrence.snapshot)
        if (old != null && !replaceContent && !updateStatus) return
        val late = if (task.status != TaskStatus.COMPLETED || task.completedAt == null) null else {
            task.dueInstant()?.let { task.completedAt > it.toEpochMilli() }
                ?: (Instant.ofEpochMilli(task.completedAt).atZone(clock.zone).toLocalDate() > requireNotNull(task.dueDate))
        }
        val linkedTags = dao.taskTagIds(task.id).toSet()
        val value = if (old != null && !replaceContent) old.copy(status = task.status.name, late = late) else OccurrenceSnapshot(
            task.title, task.notes, dao.category(task.categoryId)?.name.orEmpty(),
            dao.allTags().filter { it.id in linkedTags }.map { it.name }, task.status.name, late, task.imageIds)
        dao.updateOccurrence(occurrence.copy(snapshot = value.json()))
    }

    private suspend fun snapshotRetired(seriesId: String, zoneId: String) {
        val today = LocalDate.now(clock.withZone(ZoneId.of(zoneId)))
        db.browser().unsnapshotted(seriesId).forEach { occurrence ->
            dao.task(occurrence.taskId)?.takeIf { it.status == TaskStatus.COMPLETED || it.dueDate!! < today }?.let { snapshot(it.id) }
        }
    }

    /** Caller can nest this inside the same completion/deletion transaction. */
    suspend fun reconcileSeries(id: String, advanceAfter: Int? = null): Boolean = db.withTransaction {
        val series = dao.series(id) ?: return@withTransaction false
        if (series.deletedAt != null) return@withTransaction false
        val storedRule = requireNotNull(dao.latestRule(id))
        snapshotRetired(id, storedRule.zoneId)
        if (series.lifecycle != SeriesLifecycle.ACTIVE) return@withTransaction false
        val rule = storedRule.rule()
        val localCursor = series.cursor - storedRule.effectiveFrom + 1
        // A shortened rule may end before already consumed slots. Keep those rows and cursor.
        if (localCursor > 0 && rule.dateAt(localCursor) == null) {
            dao.updateSeries(series.copy(lifecycle = SeriesLifecycle.ENDED, updatedAt = clock.millis(), revision = series.revision + 1))
            return@withTransaction false
        }
        val event = advanceAfter?.minus(storedRule.effectiveFrom - 1)?.takeIf { it >= 0 }
        val templates = db.reminders().templatesFor(id).filter { it.enabled }.map { it.spec() }
        val batch = planOccurrences(id, rule, localCursor, clock.instant(), event,
            throughDate = reminderGenerationDate(templates, clock.instant(), storedRule.zoneId))
        val assignment = dao.category(series.categoryId)?.systemKind == SystemCategory.ASSIGNMENTS
        val tags = dao.seriesTagIds(id)
        val now = clock.millis()
        batch.slots.forEach { slot ->
            val sequence = slot.sequence + storedRule.effectiveFrom - 1
            val taskId = occurrenceId(id, sequence)
            dao.insert(TaskEntity(taskId, series.title, series.notes, series.categoryId, slot.date, storedRule.dueTime, storedRule.zoneId,
                status = if (assignment) TaskStatus.NOT_STARTED else TaskStatus.OPEN, createdAt = now, updatedAt = now,
                startDate = storedRule.startDayOffset?.let { slot.date.plusDays(it.toLong()) },
                startTime = storedRule.startTime, startZoneId = storedRule.startZoneId, imageIds = series.imageIds))
            dao.insertOccurrence(OccurrenceEntity(taskId, id, sequence, storedRule.id))
            dao.insertTaskTags(tags.map { TaskTagEntity(taskId, it) })
            applySeriesReminders(db, requireNotNull(dao.task(taskId)), templates, now)
        }
        if (batch.slots.isNotEmpty() || batch.ended) dao.updateSeries(series.copy(
            cursor = batch.cursor + storedRule.effectiveFrom - 1,
            lifecycle = if (batch.ended) SeriesLifecycle.ENDED else SeriesLifecycle.ACTIVE,
            updatedAt = now, revision = series.revision + 1))
        snapshotRetired(id, storedRule.zoneId)
        batch.hasMoreDue
    }

    suspend fun reconcileAll(continueOnFailure: Boolean = false) {
        var failure: Exception? = null
        dao.allSeries().filter { it.deletedAt == null }.forEach { series ->
            try { do { val more = reconcileSeries(series.id); yield() } while (more) }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException || !continueOnFailure) throw e
                if (failure == null) failure = e
            }
        }
        failure?.let { throw it }
    }

    suspend fun advance(taskId: String) {
        val occurrence = dao.occurrence(taskId) ?: return
        if (occurrence.excludedByRule) return
        reconcileSeries(occurrence.seriesId, occurrence.sequence)
    }

    suspend fun updateTemplateCategory(taskIds: Set<String>, categoryId: String) {
        val seriesIds = taskIds.mapNotNull { dao.occurrence(it)?.seriesId }.toSet()
        seriesIds.forEach { id -> dao.series(id)?.takeIf { it.categoryId != categoryId }?.let { series ->
            dao.updateSeries(series.copy(categoryId = categoryId, updatedAt = clock.millis(), revision = series.revision + 1))
        } }
    }

    suspend fun addTemplateTags(taskIds: Set<String>, tagIds: Set<String>) {
        taskIds.mapNotNull { dao.occurrence(it)?.seriesId }.toSet().forEach { id ->
            val added = tagIds - dao.seriesTagIds(id).toSet()
            if (added.isNotEmpty()) {
                dao.insertSeriesTags(added.map { SeriesTagEntity(id, it) })
                val series = requireNotNull(dao.series(id))
                dao.updateSeries(series.copy(updatedAt = clock.millis(), revision = series.revision + 1))
            }
        }
    }

    suspend fun stop(id: String, revision: Long) = db.withTransaction {
        val series = requireNotNull(dao.series(id))
        require(series.revision == revision && series.deletedAt == null) { "系列已更新，請重試" }
        dao.updateSeries(series.copy(lifecycle = SeriesLifecycle.STOPPED, updatedAt = clock.millis(), revision = series.revision + 1))
    }

    suspend fun delete(id: String, revision: Long, expectedTasks: Map<String, Long>, includeHistory: Boolean) = db.withTransaction {
        val series = requireNotNull(dao.series(id))
        require(series.revision == revision && series.deletedAt == null) { "系列已更新，請重新檢查" }
        val refs = dao.seriesOccurrences(id)
        val tasks = refs.map { requireNotNull(dao.task(it.taskId)) }
        require(tasks.associate { it.id to it.revision } == expectedTasks) { "系列紀錄已更新，請重新檢查" }
        val zone = ZoneId.of(requireNotNull(dao.latestRule(id)).zoneId)
        val today = LocalDate.now(clock.withZone(zone))
        val now = clock.millis()
        dao.updateSeries(series.copy(lifecycle = if (includeHistory) SeriesLifecycle.TRASHED else SeriesLifecycle.STOPPED,
            deletedAt = if (includeHistory) now else null, previousLifecycle = if (includeHistory) series.lifecycle else null,
            updatedAt = now, revision = series.revision + 1))
        tasks.filter { it.deletedAt == null && (includeHistory || (it.status != TaskStatus.COMPLETED && it.dueDate!! > today)) }.forEach { task ->
            snapshot(task.id, retire = true)
            dao.update(task.copy(deletedAt = now, updatedAt = now, revision = task.revision + 1))
            val ref = requireNotNull(dao.occurrence(task.id))
            dao.updateOccurrence(ref.copy(deletedWithSeriesAt = if (includeHistory) now else null))
        }
    }

    suspend fun restoreSeries(id: String, revision: Long) = db.withTransaction {
        val series = requireNotNull(dao.series(id))
        val deleted = requireNotNull(series.deletedAt) { "系列不在垃圾桶" }
        require(series.revision == revision && !retentionExpired(deleted, TRASH_DAYS, clock.millis())) { "系列已更新或已超過保留期限" }
        dao.seriesOccurrences(id).filter { it.deletedWithSeriesAt == deleted }.forEach { ref ->
            val task = requireNotNull(dao.task(ref.taskId))
            if (task.deletedAt == deleted) dao.update(task.copy(deletedAt = null, updatedAt = clock.millis(), revision = task.revision + 1))
            dao.updateOccurrence(ref.copy(deletedWithSeriesAt = null))
        }
        dao.updateSeries(series.copy(deletedAt = null, lifecycle = series.previousLifecycle ?: SeriesLifecycle.STOPPED,
            previousLifecycle = null, updatedAt = clock.millis(), revision = series.revision + 1))
        reconcileSeries(id)
    }
}
