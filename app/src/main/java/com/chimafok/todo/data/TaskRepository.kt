package com.chimafok.todo.data

import androidx.room.withTransaction
import com.chimafok.todo.domain.*
import java.time.Clock
import java.time.Duration

class TaskRepository(private val db: TodoDatabase, private val clock: Clock = DeviceClock) {
    val tasks = db.dao().tasks()
    val categories = db.dao().categories()

    suspend fun save(draft: TaskDraft): Unit = db.withTransaction {
        NoteImages(db).requireImages(draft.imageIds)
        if (draft.recurrence != null) {
            val recurring = RecurringRepository(db, clock)
            if (draft.expectedRevision == null && draft.expectedSeriesRevision == null) recurring.create(draft)
            else if (db.dao().occurrence(draft.id) != null) {
                require(draft.expectedSeriesRevision != null) { "任務已屬於重複系列，請重新開啟編輯" }
                recurring.editTemplate(draft)
            } else if (draft.expectedRevision != null) recurring.convert(draft)
            else recurring.create(draft)
            return@withTransaction
        }
        val category = requireNotNull(db.dao().category(draft.categoryId)) { "找不到分類" }
        validateDraft(draft, category.systemKind == SystemCategory.ASSIGNMENTS)
        require(draft.tagIds.all { db.dao().tag(it) != null }) { "標籤已更新，請重新開啟表單" }
        require(draft.seriesReminders == null && !draft.inheritSingleReminders) { "系列提醒須透過系列編輯保存" }
        val old = db.dao().task(draft.id)
        require(old != null || (category.systemKind != SystemCategory.ASSIGNMENTS && category.id != STUDY_ID) || draft.status != TaskStatus.COMPLETED) {
            "新增作業或學習任務不能直接選擇完成，請建立後再更新進度"
        }
        db.dao().occurrence(draft.id)?.let {
            require(!it.excludedByRule) { "這次已由規則取消，請從有效項目延長系列規則" }
            require(draft.dueDate != null) { "重複紀錄須保留本次日期" }
        }
        if (draft.dueDate != null && draft.dueTime != null && (old == null ||
                old.dueDate != draft.dueDate || old.dueTime != draft.dueTime || old.zoneId != draft.zoneId)) {
            deadlineInstant(draft.dueDate, draft.dueTime, requireNotNull(draft.zoneId))
        }
        if (old != null && draft.expectedRevision == null) {
            // A retried create request uses the same ID, never inserts a second task.
            require(old.title == validatedTitle(draft.title) && old.notes == draft.notes && old.imageIds == draft.imageIds &&
                old.categoryId == draft.categoryId && old.dueDate == draft.dueDate &&
                old.dueTime == draft.dueTime && old.startDate == draft.startDate && old.startTime == draft.startTime && old.startZoneId == draft.startZoneId && old.zoneId == (if (draft.dueDate != null) draft.zoneId else null) && old.status == draft.status &&
                db.dao().taskTagIds(old.id).toSet() == draft.tagIds) { "新增識別碼衝突，請重新開啟表單" }
            require(draft.reminders == null || db.reminders().forTask(old.id).filter { it.enabled }.map { it.spec() }.toSet() == draft.reminders.toSet()) { "新增提醒識別碼衝突，請重新開啟表單" }
            return@withTransaction
        }
        require(draft.expectedRevision == null || old?.revision == draft.expectedRevision) { "資料已更新，請重新開啟編輯" }
        require(old?.deletedAt == null) { "此任務已在垃圾桶" }
        if (draft.resetSeriesReminders) {
            val ref = requireNotNull(db.dao().occurrence(draft.id)) { "此任務不是重複實例" }
            require(db.dao().series(ref.seriesId)?.revision == draft.expectedSeriesRevision) { "系列已更新，請重新開啟" }
        }
        val previousReminders = db.reminders().forTask(draft.id).filter { it.enabled }.map { it.spec() }.toSet()
        val now = clock.millis()
        if (old?.status != draft.status) db.history().clearAnchor(draft.id)
        val task = TaskEntity(
            id = draft.id, title = validatedTitle(draft.title), notes = draft.notes,
            categoryId = draft.categoryId, dueDate = draft.dueDate, dueTime = draft.dueTime,
            zoneId = if (draft.dueDate != null) draft.zoneId else null, status = draft.status,
            completedAt = if (draft.status == TaskStatus.COMPLETED) old?.completedAt ?: now else null,
            createdAt = old?.createdAt ?: now, updatedAt = now, revision = (old?.revision ?: 0) + 1,
            startDate = draft.startDate, startTime = draft.startTime, startZoneId = draft.startZoneId, imageIds = draft.imageIds,
        )
        if (old == null) db.dao().insert(task) else db.dao().update(task)
        val ref = db.dao().occurrence(task.id)
        if (draft.resetSeriesReminders && ref != null) applySeriesReminders(db, task,
            db.reminders().templatesFor(ref.seriesId).filter { it.enabled }.map { it.spec() }, now, reset = true)
        else {
            saveReminders(db, task, draft.reminders, now)
            if (ref != null && draft.reminders != null && previousReminders != draft.reminders.toSet())
                db.reminders().override(ReminderOverrideEntity(task.id))
        }
        db.dao().clearTaskTags(task.id)
        db.dao().insertTaskTags(draft.tagIds.map { TaskTagEntity(task.id, it) })
        if (db.dao().occurrence(task.id) != null) {
            val recurring = RecurringRepository(db, clock)
            recurring.snapshot(task.id, replaceContent = true)
            if (task.status == TaskStatus.COMPLETED) recurring.advance(task.id)
        }
    }

    suspend fun setStatus(id: String, expectedRevision: Long, status: TaskStatus) = db.withTransaction {
        val task = requireNotNull(db.dao().task(id)) { "找不到任務" }
        require(task.deletedAt == null) { "此任務已在垃圾桶" }
        require(db.dao().occurrence(id)?.excludedByRule != true) { "這次已由規則取消" }
        if (task.status == status) return@withTransaction
        require(task.revision == expectedRevision) { "資料已更新，請重試" }
        val assignment = db.dao().category(task.categoryId)?.systemKind == SystemCategory.ASSIGNMENTS
        require(status in statusOptions(assignment, task.categoryId == STUDY_ID))
        val now = clock.millis()
        db.history().clearAnchor(id)
        db.dao().update(task.copy(status = status, completedAt = if (status == TaskStatus.COMPLETED) now else null,
            updatedAt = now, revision = task.revision + 1))
        val recurring = RecurringRepository(db, clock)
        recurring.snapshot(id, replaceContent = status == TaskStatus.COMPLETED, updateStatus = true)
        if (status == TaskStatus.COMPLETED) recurring.advance(id)
    }

    suspend fun trash(id: String, expectedRevision: Long) = db.withTransaction {
        val task = requireNotNull(db.dao().task(id))
        if (task.deletedAt != null) return@withTransaction
        require(task.revision == expectedRevision) { "資料已更新，請重試" }
        RecurringRepository(db, clock).snapshot(id, retire = true)
        val now = clock.millis()
        db.dao().update(task.copy(deletedAt = now, updatedAt = now, revision = task.revision + 1))
        RecurringRepository(db, clock).advance(id)
    }

    suspend fun restore(id: String) = db.withTransaction {
        val task = requireNotNull(db.dao().task(id))
        val deletedAt = task.deletedAt ?: return@withTransaction
        db.dao().occurrence(id)?.let { ref -> require(db.dao().series(ref.seriesId)?.deletedAt == null) { "請先復原所屬的重複系列" } }
        require(!retentionExpired(deletedAt, TRASH_DAYS, clock.millis())) { "已超過垃圾桶保留期限" }
        if (task.status == TaskStatus.COMPLETED && task.completedAt != null && db.dao().occurrence(id) == null)
            db.history().anchor(RetentionAnchorEntity(id, task.completedAt, clock.millis()))
        db.dao().update(task.copy(deletedAt = null, updatedAt = clock.millis(), revision = task.revision + 1))
    }

    private suspend fun batchTasks(revisions: Map<String, Long>): List<TaskEntity> {
        require(revisions.isNotEmpty()) { "請先選取任務" }
        return revisions.map { (id, revision) ->
            requireNotNull(db.dao().task(id)) { "所選任務已不存在，請重新選取" }.also {
                require(it.deletedAt == null && it.revision == revision) { "所選任務已更新，請重新選取" }
                require(db.dao().occurrence(id)?.excludedByRule != true) { "所選項目已由規則取消" }
            }
        }
    }

    suspend fun moveBatch(revisions: Map<String, Long>, targetId: String, choices: Map<String, TaskStatus>) = db.withTransaction {
        val tasks = batchTasks(revisions)
        val category = requireNotNull(db.dao().category(targetId)) { "目標分類已不存在" }
        val assignment = category.systemKind == SystemCategory.ASSIGNMENTS
        val entering = tasks.filter { assignment && it.categoryId != ASSIGNMENTS_ID }.map { it.id }.toSet()
        require(choices.keys == entering && choices.values.none { it == TaskStatus.OPEN }) { "請為每項移入作業的任務選擇狀態" }
        val now = clock.millis()
        val recurring = RecurringRepository(db, clock)
        tasks.forEach { task ->
            recurring.snapshot(task.id)
            val status = when {
                task.id in entering -> choices.getValue(task.id)
                assignment -> task.status
                else -> convertStatus(task.status, false, study = targetId == STUDY_ID)
            }
            if (task.categoryId != targetId || task.status != status) db.dao().update(task.copy(
                categoryId = targetId, status = status,
                completedAt = if (status == TaskStatus.COMPLETED) task.completedAt ?: now else null,
                updatedAt = now, revision = task.revision + 1))
        }
        recurring.updateTemplateCategory(tasks.map { it.id }.toSet(), targetId)
        tasks.forEach { if (db.dao().task(it.id)?.status == TaskStatus.COMPLETED) {
            // Already completed history keeps its original names and completion-zone result.
            recurring.snapshot(it.id, replaceContent = it.status != TaskStatus.COMPLETED)
            recurring.advance(it.id)
        } }
    }

    suspend fun addTagsBatch(revisions: Map<String, Long>, tagIds: Set<String>) = db.withTransaction {
        val tasks = batchTasks(revisions)
        require(tagIds.isNotEmpty()) { "請選擇要加入的標籤" }
        require(tagIds.all { db.dao().tag(it) != null }) { "標籤已更新，請重新選擇" }
        val now = clock.millis()
        val recurring = RecurringRepository(db, clock)
        tasks.forEach { task ->
            val added = tagIds - db.dao().taskTagIds(task.id).toSet()
            if (added.isNotEmpty()) {
                recurring.snapshot(task.id)
                db.dao().insertTaskTags(added.map { TaskTagEntity(task.id, it) })
                db.dao().update(task.copy(updatedAt = now, revision = task.revision + 1))
            }
        }
        recurring.addTemplateTags(tasks.map { it.id }.toSet(), tagIds)
    }

    suspend fun trashBatch(revisions: Map<String, Long>) = db.withTransaction {
        val tasks = batchTasks(revisions)
        val now = clock.millis()
        val recurring = RecurringRepository(db, clock)
        tasks.forEach { recurring.snapshot(it.id, retire = true) }
        tasks.forEach { db.dao().update(it.copy(deletedAt = now, updatedAt = now, revision = it.revision + 1)) }
        tasks.forEach { recurring.advance(it.id) }
    }
}
