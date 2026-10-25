package com.chimafok.todo.data

import androidx.room.*
import com.chimafok.todo.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.*
import kotlin.coroutines.coroutineContext

data class TaskIndexRow(val id: String, val categoryId: String, val dueDate: LocalDate?, val dueTime: LocalTime?,
    val zoneId: String?, val status: TaskStatus, val completedAt: Long?, val createdAt: Long, val revision: Long,
    val seriesId: String?, val sequence: Int?, val excludedByRule: Boolean?) {
    // A transient ordering projection, never displayed or persisted as a task.
    fun orderingTask() = TaskEntity(id,"","",categoryId,dueDate,dueTime,zoneId,status,completedAt,
        createdAt=createdAt,updatedAt=createdAt,revision=revision)
}
data class TaskTextRow(val id: String, val title: String, val notes: String, val categoryId: String)
data class TaskPageRows(val tasks: List<TaskEntity> = emptyList(), val links: List<TaskTagEntity> = emptyList(),
    val occurrences: List<OccurrenceEntity> = emptyList(), val reminders: List<ReminderEntity> = emptyList(),
    val overrides: List<ReminderOverrideEntity> = emptyList())
data class TaskPageWindow(val rows: TaskPageRows = TaskPageRows(), val revisions: Map<String,Long> = emptyMap(),
    val moreDated: Boolean = false, val moreUndated: Boolean = false,
    val groupCounts: Map<LocalDate?,Int> = emptyMap())

@Dao
interface TaskPageDao {
    @Query("SELECT t.id,t.categoryId,t.dueDate,t.dueTime,t.zoneId,t.status,t.completedAt,t.createdAt,t.revision,o.seriesId,o.sequence,o.excludedByRule FROM tasks t LEFT JOIN occurrences o ON o.taskId=t.id WHERE t.deletedAt IS NULL AND ((:scope=0 AND t.status<>'COMPLETED') OR (:scope=1 AND t.status<>'COMPLETED' AND t.dueDate IS NULL AND o.taskId IS NULL) OR (:scope=2 AND t.status='COMPLETED' AND o.taskId IS NULL)) ORDER BY t.createdAt,t.id")
    suspend fun index(scope: Int): List<TaskIndexRow>
    @Query("SELECT id FROM tasks") suspend fun allIds(): List<String>
    @Query("SELECT t.id,t.title,t.notes,t.categoryId FROM tasks t WHERE t.id IN (:ids)") suspend fun text(ids: List<String>): List<TaskTextRow>
    @Query("SELECT * FROM tasks WHERE id IN (:ids) ORDER BY createdAt,id") suspend fun tasks(ids: List<String>): List<TaskEntity>
    @Query("SELECT * FROM task_tags WHERE taskId IN (:ids)") suspend fun links(ids: List<String>): List<TaskTagEntity>
    @Query("SELECT * FROM occurrences WHERE taskId IN (:ids) ORDER BY seriesId,sequence") suspend fun occurrences(ids: List<String>): List<OccurrenceEntity>
    @Query("SELECT * FROM reminders WHERE taskId IN (:ids) ORDER BY resolvedAt DESC,createdAt,id") suspend fun reminders(ids: List<String>): List<ReminderEntity>
    @Query("SELECT * FROM reminder_overrides WHERE taskId IN (:ids)") suspend fun overrides(ids: List<String>): List<ReminderOverrideEntity>
    @Query("SELECT o.taskId FROM occurrences o JOIN tasks t ON t.id=o.taskId WHERE t.status<>'COMPLETED'") suspend fun pendingRecurringIds(): List<String>
    @Query("SELECT taskId FROM occurrences WHERE seriesId=:id ORDER BY sequence") suspend fun seriesIds(id: String): List<String>
}

class TaskPageRepository(private val db: TodoDatabase) {
    companion object { const val PAGE_SIZE=50; private const val CHUNK=400 }
    /** Call inside a consistent transaction. All IN lists stay below API26's limit. */
    suspend fun rows(ids: Collection<String>): TaskPageRows {
        val dao=db.taskPages()
        val tasks=mutableListOf<TaskEntity>();val links=mutableListOf<TaskTagEntity>()
        val refs=mutableListOf<OccurrenceEntity>();val reminders=mutableListOf<ReminderEntity>();val overrides=mutableListOf<ReminderOverrideEntity>()
        for (chunk in ids.distinct().chunked(CHUNK)) {
            coroutineContext.ensureActive()
            tasks+=dao.tasks(chunk);links+=dao.links(chunk);refs+=dao.occurrences(chunk);reminders+=dao.reminders(chunk);overrides+=dao.overrides(chunk)
        }
        return TaskPageRows(tasks.sortedWith(compareBy<TaskEntity>{it.createdAt}.thenBy{it.id}),links,
            refs.sortedWith(compareBy<OccurrenceEntity>{it.seriesId}.thenBy{it.sequence}),reminders,overrides)
    }
    suspend fun window(scope: TaskScope, filter: TaskFilter, clock: Clock, jumpDate: LocalDate?, datedLimit: Int, undatedLimit: Int): TaskPageWindow = withContext(Dispatchers.IO) {
        require(scope!=TaskScope.TRASH && datedLimit>0 && undatedLimit>0)
        db.withTransaction {
            val dao=db.taskPages()
            val index=dao.index(when(scope){TaskScope.AGENDA->0;TaskScope.UNSCHEDULED->1;else->2})
            val ordering=index.map { it.orderingTask() }
            val refs=index.mapNotNull { row->row.seriesId?.let { RecurringTaskRef(row.id,it,requireNotNull(row.sequence),row.excludedByRule==true) } }
            // Choose the home occurrence before search/filters, matching the original projection.
            val home=if(scope==TaskScope.AGENDA) recurringHomeTaskIds(ordering,refs,clock) else emptySet()
            val recurringIds=refs.map { it.taskId }.toSet()
            var candidates=ordering.filter { (it.id !in recurringIds || it.id in home) && (filter.categoryIds.isEmpty() || it.categoryId in filter.categoryIds) }
            if (filter.query.isNotBlank() || filter.tagIds.isNotEmpty()) {
                val categories=db.dao().allCategories();val tags=db.dao().allTags()
                val found=mutableSetOf<String>()
                for (chunk in candidates.chunked(CHUNK)) {
                    coroutineContext.ensureActive()
                    val ids=chunk.map { it.id }
                    val texts=if(filter.query.isNotBlank()) dao.text(ids).associateBy { it.id } else emptyMap()
                    val searchable=chunk.map { task->texts[task.id]?.let { task.copy(title=it.title,notes=it.notes) } ?: task }
                    found+=TaskSearchIndex(categories,tags,dao.links(ids)).filter(searchable,filter).map { it.id }
                }
                candidates=candidates.filter { it.id in found }
            }
            val groups=when(scope) {
                TaskScope.AGENDA->agendaGroups(candidates,clock,jumpDate)
                TaskScope.COMPLETED->completedGroups(candidates)
                else->listOf(TaskGroup(null,candidates.sortedByDescending { it.createdAt }))
            }
            val dated=groups.filter { it.date!=null }.flatMap { it.tasks }
            val undated=groups.filter { it.date==null }.flatMap { it.tasks }
            val requested=linkedSetOf<String>()
            requested+=dated.take(datedLimit).map { it.id }
            // A date jump loads its own prefix even when it lies beyond the current page.
            if(scope==TaskScope.AGENDA && jumpDate!=null) requested+=groups.filter { it.date==jumpDate }.flatMap { it.tasks }.take(datedLimit).map { it.id }
            val loadedDated=requested.size
            requested+=undated.take(undatedLimit).map { it.id }
            TaskPageWindow(rows(requested),candidates.associate { it.id to it.revision },dated.size>loadedDated,undated.size>undatedLimit,groups.associate { it.date to it.tasks.size })
        }
    }
}
