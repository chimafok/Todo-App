package com.chimafok.todo.data

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
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
        require(tasks.isNotEmpty() || series.isNotEmpty()) { tr(R.string.text_select_tasks_or_series_first_8084e2) }
        val explicit=tasks.map { (id,rev)->requireNotNull(db.dao().task(id)) { tr(R.string.text_the_task_no_longer_exists_c94435) }.also {
            require(it.deletedAt!=null && it.revision==rev) { tr(R.string.text_selected_tasks_changed_please_select_again_db1692) }
        } }
        val children=linkedMapOf<String,Map<String,Long>>()
        val restore=explicit.associate { it.id to it.revision }.toMutableMap()
        val purge=restore.toMutableMap()
        for((id,rev) in series) {
            val parent=requireNotNull(db.dao().series(id)) { tr(R.string.text_the_series_no_longer_exists_ae5a78) }
            require(parent.deletedAt!=null && parent.revision==rev) { tr(R.string.text_selected_series_changed_please_select_again_f30690) }
            val refs=db.dao().seriesOccurrences(id)
            val rows=refs.associateWith { requireNotNull(db.dao().task(it.taskId)) }
            require(rows.values.all { it.deletedAt!=null }) { tr(R.string.text_the_series_still_has_undeleted_records_4b2091) }
            children[id]=rows.values.associate { it.id to it.revision }
            purge.putAll(children.getValue(id))
            rows.filter { (ref,task)->ref.deletedWithSeriesAt==parent.deletedAt && task.deletedAt==parent.deletedAt }
                .values.forEach { restore[it.id]=it.revision }
        }
        TrashSelectionPreview(tasks.toMap(),series.toMap(),children,restore.toMap(),PurgePreview(purge.toMap(),series.toMap()))
    }
    suspend fun restore(preview: TrashSelectionPreview): Unit = db.withTransaction {
        require(preview.explicitTasks.isNotEmpty() || preview.series.isNotEmpty()) { tr(R.string.text_select_items_first_e250ad) }
        for((id,rev) in preview.series) {
            val parent=requireNotNull(db.dao().series(id)) { tr(R.string.text_the_series_no_longer_exists_ae5a78) }
            require(parent.deletedAt!=null && parent.revision==rev && !retentionExpired(parent.deletedAt,TRASH_DAYS,clock.millis())) { tr(R.string.text_the_series_changed_or_its_retention_11b666) }
            val current=db.dao().seriesOccurrences(id).associate { ref ->
                val task=requireNotNull(db.dao().task(ref.taskId));task.id to task.revision
            }
            require(current==preview.seriesChildren[id]) { tr(R.string.text_series_records_changed_please_select_again_9cc2ce) }
        }
        for((id,rev) in preview.restoreTasks) {
            val task=requireNotNull(db.dao().task(id)) { tr(R.string.text_the_task_no_longer_exists_c94435) }
            require(task.revision==rev && task.deletedAt!=null && !retentionExpired(task.deletedAt,TRASH_DAYS,clock.millis())) { tr(R.string.text_the_task_changed_or_its_retention_3eb29a) }
            db.dao().occurrence(id)?.let { ref ->
                require(db.dao().series(ref.seriesId)?.deletedAt==null || ref.seriesId in preview.series) { tr(R.string.text_some_tasks_belong_to_deleted_series_237058) }
            }
        }
        val recurring=RecurringRepository(db,clock)
        preview.series.forEach { (id,rev)->recurring.restoreSeries(id,rev) }
        val repository=TaskRepository(db,clock)
        preview.explicitTasks.keys.forEach { id ->if(db.dao().task(id)?.deletedAt!=null) repository.restore(id) }
    }
    suspend fun purge(preview: TrashSelectionPreview) = HistoryRepository(db,clock).purge(preview.purge)

    suspend fun previewSeries(revisions: Map<String,Long>): SeriesSelectionPreview = db.withTransaction {
        require(revisions.isNotEmpty()) { tr(R.string.text_select_series_first_85f338) }
        val browser=HistoryBrowserRepository(db)
        val previewClock=Clock.fixed(clock.instant(),clock.zone)
        val previews=linkedMapOf<String,SeriesActionPreview>();val days=linkedMapOf<String,LocalDate>()
        for((id,revision) in revisions) {
            val item=browser.actionPreview(id,previewClock)
            require(item.revision==revision) { tr(R.string.text_selected_series_changed_please_select_again_f30690) }
            previews[id]=item
            days[id]=LocalDate.now(previewClock.withZone(ZoneId.of(requireNotNull(db.dao().latestRule(id)).zoneId)))
        }
        SeriesSelectionPreview(previews.toMap(),days.toMap())
    }
    suspend fun applySeries(preview: SeriesSelectionPreview, stopOnly: Boolean, includeHistory: Boolean): Unit = db.withTransaction {
        require(preview.items.isNotEmpty()) { tr(R.string.text_select_series_first_85f338) }
        for((id,item) in preview.items) {
            val parent=requireNotNull(db.dao().series(id)) { tr(R.string.text_the_series_no_longer_exists_ae5a78) }
            require(parent.deletedAt==null && parent.revision==item.revision) { tr(R.string.text_selected_series_changed_please_select_again_f30690) }
            require(db.dao().seriesOccurrences(id).associate { ref ->
                val task=requireNotNull(db.dao().task(ref.taskId));task.id to task.revision
            }==item.tasks) { tr(R.string.text_series_records_changed_please_select_again_9cc2ce) }
            if(stopOnly) require(parent.lifecycle==SeriesLifecycle.ACTIVE) { tr(R.string.text_only_active_recurring_series_can_be_f5b6a5) }
            else if(!includeHistory) require(LocalDate.now(clock.withZone(ZoneId.of(requireNotNull(db.dao().latestRule(id)).zoneId)))==preview.days[id]) { tr(R.string.text_the_date_changed_preview_the_deletion_346e92) }
        }
        val recurring=RecurringRepository(db,clock)
        preview.items.forEach { (id,item)->if(stopOnly)recurring.stop(id,item.revision) else recurring.delete(id,item.revision,item.tasks,includeHistory) }
    }
}
