package com.chimafok.todo.data

import androidx.room.withTransaction
import com.chimafok.todo.domain.*
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

data class TrashSelectionPreview(
    val explicitTasks: Map<String,Long>, val series: Map<String,Long>,
    val seriesChildren: Map<String,Map<String,Long>>, val restoreTasks: Map<String,Long>,
    val purge: PurgePreview,
)
data class SeriesSelectionPreview(val items: Map<String,SeriesActionPreview>, val days: Map<String,LocalDate>)

/** Selection previews are immutable, process-local snapshots; all writes are atomic. */
class SelectionRepository(private val db: TodoDatabase, private val clock: Clock = DeviceClock) {
    suspend fun previewTrash(tasks: Map<String,Long>, series: Map<String,Long>): TrashSelectionPreview = db.withTransaction {
        require(tasks.isNotEmpty() || series.isNotEmpty()) { "請先選取任務或系列" }
        val explicit=tasks.map { (id,rev)->requireNotNull(db.dao().task(id)) { "任務已不存在" }.also {
            require(it.deletedAt!=null && it.revision==rev) { "所選任務已更新，請重新選取" }
        } }
        val children=linkedMapOf<String,Map<String,Long>>()
        val restore=explicit.associate { it.id to it.revision }.toMutableMap()
        val purge=restore.toMutableMap()
        for((id,rev) in series) {
            val parent=requireNotNull(db.dao().series(id)) { "系列已不存在" }
            require(parent.deletedAt!=null && parent.revision==rev) { "所選系列已更新，請重新選取" }
            val refs=db.dao().seriesOccurrences(id)
            val rows=refs.associateWith { requireNotNull(db.dao().task(it.taskId)) }
            require(rows.values.all { it.deletedAt!=null }) { "系列仍有未刪除紀錄，請重新檢查" }
            children[id]=rows.values.associate { it.id to it.revision }
            purge.putAll(children.getValue(id))
            rows.filter { (ref,task)->ref.deletedWithSeriesAt==parent.deletedAt && task.deletedAt==parent.deletedAt }
                .values.forEach { restore[it.id]=it.revision }
        }
        TrashSelectionPreview(tasks.toMap(),series.toMap(),children,restore.toMap(),PurgePreview(purge.toMap(),series.toMap()))
    }
    suspend fun restore(preview: TrashSelectionPreview): Unit = db.withTransaction {
        require(preview.explicitTasks.isNotEmpty() || preview.series.isNotEmpty()) { "請先選取項目" }
        for((id,rev) in preview.series) {
            val parent=requireNotNull(db.dao().series(id)) { "系列已不存在" }
            require(parent.deletedAt!=null && parent.revision==rev && !retentionExpired(parent.deletedAt,TRASH_DAYS,clock.millis())) { "系列已更新或超過保留期限" }
            val current=db.dao().seriesOccurrences(id).associate { ref ->
                val task=requireNotNull(db.dao().task(ref.taskId));task.id to task.revision
            }
            require(current==preview.seriesChildren[id]) { "系列紀錄已更新，請重新選取" }
        }
        for((id,rev) in preview.restoreTasks) {
            val task=requireNotNull(db.dao().task(id)) { "任務已不存在" }
            require(task.revision==rev && task.deletedAt!=null && !retentionExpired(task.deletedAt,TRASH_DAYS,clock.millis())) { "任務已更新或超過保留期限" }
            db.dao().occurrence(id)?.let { ref ->
                require(db.dao().series(ref.seriesId)?.deletedAt==null || ref.seriesId in preview.series) { "部分任務屬於已刪系列，請一併選取所屬系列或先復原系列" }
            }
        }
        val recurring=RecurringRepository(db,clock)
        preview.series.forEach { (id,rev)->recurring.restoreSeries(id,rev) }
        val repository=TaskRepository(db,clock)
        preview.explicitTasks.keys.forEach { id ->if(db.dao().task(id)?.deletedAt!=null) repository.restore(id) }
    }
    suspend fun purge(preview: TrashSelectionPreview) = HistoryRepository(db,clock).purge(preview.purge)

    suspend fun previewSeries(revisions: Map<String,Long>): SeriesSelectionPreview = db.withTransaction {
        require(revisions.isNotEmpty()) { "請先選取系列" }
        val browser=HistoryBrowserRepository(db)
        val previewClock=Clock.fixed(clock.instant(),clock.zone)
        val previews=linkedMapOf<String,SeriesActionPreview>();val days=linkedMapOf<String,LocalDate>()
        for((id,revision) in revisions) {
            val item=browser.actionPreview(id,previewClock)
            require(item.revision==revision) { "所選系列已更新，請重新選取" }
            previews[id]=item
            days[id]=LocalDate.now(previewClock.withZone(ZoneId.of(requireNotNull(db.dao().latestRule(id)).zoneId)))
        }
        SeriesSelectionPreview(previews.toMap(),days.toMap())
    }
    suspend fun applySeries(preview: SeriesSelectionPreview, stopOnly: Boolean, includeHistory: Boolean): Unit = db.withTransaction {
        require(preview.items.isNotEmpty()) { "請先選取系列" }
        for((id,item) in preview.items) {
            val parent=requireNotNull(db.dao().series(id)) { "系列已不存在" }
            require(parent.deletedAt==null && parent.revision==item.revision) { "所選系列已更新，請重新選取" }
            require(db.dao().seriesOccurrences(id).associate { ref ->
                val task=requireNotNull(db.dao().task(ref.taskId));task.id to task.revision
            }==item.tasks) { "系列紀錄已更新，請重新選取" }
            if(stopOnly) require(parent.lifecycle==SeriesLifecycle.ACTIVE) { "只能批次停止仍在重複中的系列" }
            else if(!includeHistory) require(LocalDate.now(clock.withZone(ZoneId.of(requireNotNull(db.dao().latestRule(id)).zoneId)))==preview.days[id]) { "日期已變更，請重新預覽刪除範圍" }
        }
        val recurring=RecurringRepository(db,clock)
        preview.items.forEach { (id,item)->if(stopOnly)recurring.stop(id,item.revision) else recurring.delete(id,item.revision,item.tasks,includeHistory) }
    }
}
