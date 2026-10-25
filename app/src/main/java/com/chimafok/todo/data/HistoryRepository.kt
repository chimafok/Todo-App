package com.chimafok.todo.data

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
        require(days > 0) { "天數必須大於 0" }
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
        require(days == null || days > 0) { "天數必須大於 0" }
        RetentionPreview(days, policy().revision, completedCandidates(days).associate { it.id to it.revision })
    }
    suspend fun apply(preview: RetentionPreview) = db.withTransaction {
        val policy = policy()
        require(policy.revision == preview.policyRevision) { "保留設定已更新，請重新預覽" }
        val tasks = completedCandidates(preview.days)
        require(tasks.associate { it.id to it.revision } == preview.tasks) { "完成紀錄已更新，請重新預覽" }
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
        require(preview.tasks.isNotEmpty() || preview.series.isNotEmpty()) { "垃圾桶已是空的" }
        val allTasks = db.dao().allTasks()
        val allSeries = db.dao().allSeries()
        if (preview.allTrash) {
            require(allTasks.filter { it.deletedAt != null }.associate { it.id to it.revision } == preview.tasks &&
                allSeries.filter { it.deletedAt != null }.associate { it.id to it.revision } == preview.series) { "垃圾桶已更新，請重新確認範圍" }
        }
        val tasks = preview.tasks.map { (id, revision) -> requireNotNull(db.dao().task(id)) { "項目已不存在，請重新確認" }.also {
            require(it.deletedAt != null && it.revision == revision) { "項目已復原或更新，請重新確認" }
        } }
        preview.series.forEach { (id, revision) ->
            val series = requireNotNull(db.dao().series(id)) { "系列已不存在" }
            require(series.deletedAt != null && series.revision == revision &&
                db.dao().seriesOccurrences(id).all { it.taskId in preview.tasks }) { "系列已更新，請重新確認範圍" }
        }
        purgeTasks(tasks)
        preview.series.keys.forEach { id ->
            require(db.dao().seriesOccurrences(id).isEmpty()) { "系列仍有保留中的紀錄" }
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
