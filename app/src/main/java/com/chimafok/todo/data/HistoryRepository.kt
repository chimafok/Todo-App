package com.chimafok.todo.data

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
import androidx.room.withTransaction
import com.chimafok.todo.domain.*
import java.time.Clock
import java.util.UUID

data class RetentionPreview(val days: Int?, val policyRevision: Long, val tasks: Map<String, Long>)
data class PurgePreview(val tasks: Map<String, Long>, val series: Map<String, Long> = emptyMap(), val allTrash: Boolean = false)

class HistoryRepository(private val db: TodoDatabase, private val clock: Clock = DeviceClock) {
    suspend fun policy() = db.history().policy() ?: RetentionPolicyEntity()
    private suspend fun completedCandidates(days: Int?): List<TaskEntity> {
        if (days == null) return emptyList()
        require(days > 0) { tr(R.string.text_days_must_be_greater_than_029793) }
        val anchors = db.history().anchors().associateBy { it.taskId }
        val now = clock.millis()
        return db.browser().retentionTasks().filter { task ->
            task.deletedAt == null && task.status == TaskStatus.COMPLETED &&
                task.completedAt?.let { completed ->
                    val restored = anchors[task.id]?.takeIf { it.completedAt == completed }?.restoredAt
                    retentionExpired(maxOf(completed, restored ?: completed), days, now)
                } == true
        }
    }
    suspend fun preview(days: Int?): RetentionPreview = db.withTransaction {
        require(days == null || days > 0) { tr(R.string.text_days_must_be_greater_than_029793) }
        RetentionPreview(days, policy().revision, completedCandidates(days).associate { it.id to it.revision })
    }
    suspend fun apply(preview: RetentionPreview) = db.withTransaction {
        val policy = policy()
        require(policy.revision == preview.policyRevision) { tr(R.string.text_retention_settings_changed_please_preview_again_793d73) }
        val tasks = completedCandidates(preview.days)
        require(tasks.associate { it.id to it.revision } == preview.tasks) { tr(R.string.text_completed_records_changed_please_preview_again_a36198) }
        if (policy.days != preview.days) db.history().policy(policy.copy(days = preview.days, revision = policy.revision + 1, updatedAt = clock.millis()))
        archive(tasks)
    }
    private suspend fun archive(tasks: List<TaskEntity>) {
        val now = clock.millis()
        tasks.forEach { db.dao().update(it.copy(deletedAt = now, updatedAt = now, revision = it.revision + 1)) }
    }
    suspend fun maintain(): Unit = db.withTransaction {
        archive(completedCandidates(policy().days))
        val expired = db.browser().trashTasks().filter { it.deletedAt?.let { at -> retentionExpired(at, TRASH_DAYS, clock.millis()) } == true }
        purgeTasks(expired)
        db.dao().allSeries().filter { it.deletedAt?.let { at -> retentionExpired(at, TRASH_DAYS, clock.millis()) } == true }.forEach {
            // Never cascade an unexpected surviving occurrence out of a different retention window.
            if (db.dao().seriesOccurrences(it.id).isEmpty()) db.history().purgeSeries(it.id)
        }
    }
    suspend fun purge(preview: PurgePreview): Unit = db.withTransaction {
        require(preview.tasks.isNotEmpty() || preview.series.isNotEmpty()) { tr(R.string.text_trash_is_already_empty_4fd1cf) }
        val allTasks = db.dao().allTasks()
        val allSeries = db.dao().allSeries()
        if (preview.allTrash) {
            require(allTasks.filter { it.deletedAt != null }.associate { it.id to it.revision } == preview.tasks &&
                allSeries.filter { it.deletedAt != null }.associate { it.id to it.revision } == preview.series) { tr(R.string.text_trash_changed_please_confirm_the_scope_bda725) }
        }
        val tasks = preview.tasks.map { (id, revision) -> requireNotNull(db.dao().task(id)) { tr(R.string.text_the_item_no_longer_exists_please_fdb0bf) }.also {
            require(it.deletedAt != null && it.revision == revision) { tr(R.string.text_the_item_was_restored_or_updated_d08624) }
        } }
        preview.series.forEach { (id, revision) ->
            val series = requireNotNull(db.dao().series(id)) { tr(R.string.text_the_series_no_longer_exists_ae5a78) }
            require(series.deletedAt != null && series.revision == revision &&
                db.dao().seriesOccurrences(id).all { it.taskId in preview.tasks }) { tr(R.string.text_the_series_changed_please_confirm_the_eef3be) }
        }
        purgeTasks(tasks)
        preview.series.keys.forEach { id ->
            require(db.dao().seriesOccurrences(id).isEmpty()) { tr(R.string.text_the_series_still_contains_retained_records_45d533) }
            db.history().purgeSeries(id)
        }
    }
    private suspend fun purgeTasks(tasks: List<TaskEntity>) {
        val affected = tasks.mapNotNull { db.dao().occurrence(it.id)?.seriesId }.toSet()
        val now = clock.millis()
        tasks.forEach { task ->
            db.reminders().forTask(task.id).forEach { reminder ->
                val key = UUID.nameUUIDFromBytes("todo/purge/v1/${task.id}/${reminder.id}/${reminder.alarmKey}".toByteArray(Charsets.UTF_8)).toString()
                db.history().cancel(PurgeCancellationEntity(key, task.id, reminder.id, reminder.alarmKey, now))
            }
            db.history().purgeTask(task.id)
            db.history().clearOutbox(task.id)
        }
        affected.forEach { id -> db.dao().series(id)?.let {
            db.dao().updateSeries(it.copy(revision = it.revision + 1, updatedAt = now))
        } }
    }
}
