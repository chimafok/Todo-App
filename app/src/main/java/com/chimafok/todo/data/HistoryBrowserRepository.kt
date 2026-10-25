package com.chimafok.todo.data

import androidx.room.*
import com.chimafok.todo.domain.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.*
import kotlin.coroutines.coroutineContext

// Keep the same predicate for tasks and every task-owned relation. Editors can
// request one complete series; organization/trash keep their full operation scope.
private const val SCOPE = "(:includeAll OR o.taskId IS NULL OR t.status <> 'COMPLETED' OR o.seriesId = :detailSeriesId)"
private const val JOINED = " FROM tasks t LEFT JOIN occurrences o ON o.taskId = t.id "
private const val HISTORY = " FROM tasks t JOIN occurrences o ON o.taskId = t.id JOIN recurring_series s ON s.id = o.seriesId WHERE t.deletedAt IS NULL AND s.deletedAt IS NULL AND (:seriesId IS NULL OR o.seriesId = :seriesId) "
// A dedicated non-null series predicate lets SQLite seek the existing
// (seriesId, sequence) index instead of rescanning all history for each series.
private const val SERIES_HISTORY = " FROM occurrences o JOIN tasks t ON t.id = o.taskId JOIN recurring_series s ON s.id = o.seriesId WHERE o.seriesId = :seriesId AND t.deletedAt IS NULL AND s.deletedAt IS NULL "
private const val HISTORY_ORDER = " ORDER BY t.dueDate DESC, o.seriesId, o.sequence, t.id "
private const val ROW = "SELECT t.*, o.taskId AS occ_taskId, o.seriesId AS occ_seriesId, o.sequence AS occ_sequence, o.ruleId AS occ_ruleId, o.snapshot AS occ_snapshot, o.deletedWithSeriesAt AS occ_deletedWithSeriesAt, o.excludedByRule AS occ_excludedByRule"

private const val SEARCH_ROW = "SELECT t.id AS taskId, o.seriesId, o.sequence, t.title, t.notes, t.categoryId, o.snapshot, t.dueDate AS dueDateKey, t.revision AS revision, o.excludedByRule AS excludedByRule"
// Cursor fields use SQLite's own stored date ordering, including equal dates.
// The NULL branch is defensive; valid recurring occurrences always have a date.
private const val AFTER_DATE = " AND (:hasCursor = 0 OR (:afterDate IS NOT NULL AND (t.dueDate < :afterDate OR t.dueDate IS NULL)) OR (t.dueDate IS :afterDate AND o.sequence > :afterSequence)) "

data class HistoryRow(@Embedded val task: TaskEntity, @Embedded(prefix = "occ_") val occurrence: OccurrenceEntity)
data class HistorySearchRow(val taskId: String, val seriesId: String, val sequence: Int, val title: String,
    val notes: String, val categoryId: String, val snapshot: String?, val dueDateKey: String?,
    val revision: Long = 1, val excludedByRule: Boolean = false)
data class HistoryTag(val taskId: String, val name: String)
data class SeriesCounts(val seriesId: String, val completed: Int, val pending: Int, val cancelled: Int)
data class SeriesActionRow(val id: String, val revision: Long, val dueDate: LocalDate?, val status: TaskStatus, val deletedAt: Long?)
data class SeriesActionPreview(val id: String, val revision: Long, val tasks: Map<String, Long>, val activeCount: Int, val futureCount: Int)
data class HistoryWindow(val rows: List<HistoryRow> = emptyList(), val tags: Map<String, List<String>> = emptyMap(),
    val total: Int = 0, val counts: Map<String, SeriesCounts> = emptyMap(), val matchingSeries: Set<String> = emptySet())

@Dao
interface BrowserDao {
    @Query("SELECT t.*" + JOINED + "WHERE " + SCOPE + " ORDER BY t.createdAt, t.id")
    suspend fun tasks(includeAll: Boolean, detailSeriesId: String?): List<TaskEntity>
    @Query("SELECT o.* FROM occurrences o JOIN tasks t ON t.id = o.taskId WHERE " + SCOPE + " ORDER BY o.seriesId, o.sequence")
    suspend fun occurrences(includeAll: Boolean, detailSeriesId: String?): List<OccurrenceEntity>
    @Query("SELECT l.* FROM task_tags l JOIN tasks t ON t.id = l.taskId LEFT JOIN occurrences o ON o.taskId = t.id WHERE " + SCOPE)
    suspend fun taskTags(includeAll: Boolean, detailSeriesId: String?): List<TaskTagEntity>
    @Query("SELECT r.* FROM reminders r JOIN tasks t ON t.id = r.taskId LEFT JOIN occurrences o ON o.taskId = t.id WHERE " + SCOPE + " ORDER BY r.resolvedAt DESC, r.createdAt, r.id")
    suspend fun reminders(includeAll: Boolean, detailSeriesId: String?): List<ReminderEntity>
    @Query("SELECT r.* FROM reminder_overrides r JOIN tasks t ON t.id = r.taskId LEFT JOIN occurrences o ON o.taskId = t.id WHERE " + SCOPE)
    suspend fun overrides(includeAll: Boolean, detailSeriesId: String?): List<ReminderOverrideEntity>

    @Query(ROW + SERIES_HISTORY + HISTORY_ORDER + " LIMIT :limit OFFSET :offset")
    suspend fun rows(seriesId: String, limit: Int, offset: Int = 0): List<HistoryRow>
    @Query(SEARCH_ROW + SERIES_HISTORY + " AND o.sequence > :afterSequence ORDER BY o.sequence LIMIT :limit")
    suspend fun searchSequence(seriesId: String, afterSequence: Int, limit: Int): List<HistorySearchRow>
    @Query(SEARCH_ROW + SERIES_HISTORY + AFTER_DATE + HISTORY_ORDER + " LIMIT :limit")
    suspend fun searchDate(seriesId: String, hasCursor: Boolean, afterDate: String?, afterSequence: Int, limit: Int): List<HistorySearchRow>
    @Query(ROW + SERIES_HISTORY + " AND t.id IN (:ids)" + HISTORY_ORDER)
    suspend fun rowsForIds(seriesId: String, ids: List<String>): List<HistoryRow>
    @Query("SELECT COUNT(*)" + HISTORY)
    suspend fun count(seriesId: String?): Int
    @Query("SELECT o.seriesId, SUM(CASE WHEN o.excludedByRule = 0 AND t.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed, SUM(CASE WHEN o.excludedByRule = 0 AND t.status <> 'COMPLETED' THEN 1 ELSE 0 END) AS pending, SUM(CASE WHEN o.excludedByRule = 1 THEN 1 ELSE 0 END) AS cancelled" + HISTORY + " GROUP BY o.seriesId")
    suspend fun counts(seriesId: String? = null): List<SeriesCounts>
    @Query("SELECT l.taskId, g.name FROM task_tags l JOIN tags g ON g.id = l.tagId WHERE l.taskId IN (:ids) ORDER BY g.sortOrder, g.id")
    suspend fun tagNames(ids: List<String>): List<HistoryTag>
    @Query("SELECT t.id, t.revision, t.dueDate, t.status, t.deletedAt FROM tasks t JOIN occurrences o ON o.taskId = t.id WHERE o.seriesId = :id ORDER BY o.sequence")
    suspend fun actionRows(id: String): List<SeriesActionRow>
    @Query("SELECT * FROM tasks WHERE deletedAt IS NOT NULL") suspend fun trashTasks(): List<TaskEntity>
    @Query("SELECT t.* FROM tasks t LEFT JOIN occurrences o ON o.taskId = t.id WHERE t.deletedAt IS NULL AND t.status = 'COMPLETED' AND o.taskId IS NULL")
    suspend fun retentionTasks(): List<TaskEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM tasks WHERE deletedAt IS NOT NULL)") suspend fun hasTrash(): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM reminders)") suspend fun hasReminders(): Boolean
    @Query("SELECT * FROM occurrences WHERE seriesId = :id AND snapshot IS NULL AND excludedByRule = 0 ORDER BY sequence")
    suspend fun unsnapshotted(id: String): List<OccurrenceEntity>
}

private class HistorySearchMatcher(private val needle: String, private val categories: Map<String, String>) {
    // Only the immediately preceding decoded snapshot is retained. This is
    // scoped to one search, so edits/renames cannot reuse a stale cached result.
    private var lastJson: String? = null
    private var lastSnapshot: OccurrenceSnapshot? = null

    fun matches(row: HistorySearchRow, tags: List<String>): Boolean {
        fun String.containsNeedle() = contains(needle, ignoreCase = true)
        if (row.title.containsNeedle() || row.notes.containsNeedle() ||
            categories[row.categoryId].orEmpty().containsNeedle() || tags.any { it.containsNeedle() }) return true
        val json = row.snapshot ?: return false
        if (json != lastJson) { lastJson = json; lastSnapshot = OccurrenceSnapshot.read(json) }
        val snapshot = lastSnapshot ?: return false
        return snapshot.title.containsNeedle() || snapshot.notes.containsNeedle() || snapshot.category.containsNeedle() ||
            snapshot.tags.any { it.containsNeedle() }
    }
}

class HistoryBrowserRepository(private val db: TodoDatabase) {
    companion object { const val PAGE_SIZE = 50; private const val CHUNK = 512 }

    /** A consistent read transaction covers totals, matches and the requested
     * prefix. Only displayed matches are hydrated as full task/occurrence rows. */
    suspend fun window(seriesId: String?, query: String, limit: Int): HistoryWindow = withContext(Dispatchers.IO) {
        require(limit > 0)
        db.withTransaction {
            val dao = db.browser()
            val counts = dao.counts().associateBy { it.seriesId }
            val needle = query.trim()
            if (needle.isEmpty()) {
                if (seriesId == null) return@withTransaction HistoryWindow(counts = counts)
                val rows = dao.rows(seriesId, limit)
                val tags = rows.map { it.task.id }.chunked(CHUNK).flatMap { dao.tagNames(it) }.groupBy({ it.taskId }, { it.name })
                return@withTransaction HistoryWindow(rows, tags, dao.count(seriesId), counts)
            }
            val matcher = HistorySearchMatcher(needle, db.dao().allCategories().associate { it.id to it.name })
            if (seriesId == null) {
                // The overview displays complete series statistics, not a global
                // matching-row total. One match is sufficient to include a series.
                val matching = mutableSetOf<String>()
                for (series in db.dao().allSeries().filter { it.deletedAt == null }) {
                    coroutineContext.ensureActive()
                    if (series.title.contains(needle, ignoreCase = true)) { matching += series.id; continue }
                    var afterSequence = 0
                    var scanLimit = 32
                    while (true) {
                        coroutineContext.ensureActive()
                        val chunk = dao.searchSequence(series.id, afterSequence, scanLimit)
                        if (chunk.isEmpty()) break
                        val tags = dao.tagNames(chunk.map { it.taskId }).groupBy({ it.taskId }, { it.name })
                        if (chunk.any { matcher.matches(it, tags[it.taskId].orEmpty()) }) { matching += series.id; break }
                        afterSequence = chunk.last().sequence
                        if (chunk.size < scanLimit) break
                        scanLimit = CHUNK
                    }
                }
                return@withTransaction HistoryWindow(counts = counts, matchingSeries = matching)
            }
            val ids = mutableListOf<String>()
            val foundTags = mutableMapOf<String, List<String>>()
            var total = 0
            var hasCursor = false
            var afterDate: String? = null
            var afterSequence = 0
            while (true) {
                coroutineContext.ensureActive()
                val chunk = dao.searchDate(seriesId, hasCursor, afterDate, afterSequence, CHUNK)
                if (chunk.isEmpty()) break
                val tags = dao.tagNames(chunk.map { it.taskId }).groupBy({ it.taskId }, { it.name })
                for (row in chunk) {
                    if (matcher.matches(row, tags[row.taskId].orEmpty())) {
                        total++
                        if (ids.size < limit) { ids += row.taskId; foundTags[row.taskId] = tags[row.taskId].orEmpty() }
                    }
                }
                hasCursor = true
                afterDate = chunk.last().dueDateKey
                afterSequence = chunk.last().sequence
                if (chunk.size < CHUNK) break
            }
            val byId = ids.chunked(CHUNK).flatMap { dao.rowsForIds(seriesId, it) }.associateBy { it.task.id }
            HistoryWindow(ids.map { byId.getValue(it) }, foundTags, total, counts)
        }
    }

    suspend fun selection(id: String, query: String): Map<String,Long> = withContext(Dispatchers.IO) {
        db.withTransaction {
            val dao=db.browser();val needle=query.trim()
            val matcher=HistorySearchMatcher(needle,db.dao().allCategories().associate {it.id to it.name})
            val result=linkedMapOf<String,Long>();var after=0
            while(true) {
                coroutineContext.ensureActive()
                val rows=dao.searchSequence(id,after,CHUNK)
                if(rows.isEmpty())break
                val tags=if(needle.isEmpty()) emptyMap() else dao.tagNames(rows.map {it.taskId}).groupBy({it.taskId},{it.name})
                rows.filter { !it.excludedByRule && (needle.isEmpty() || matcher.matches(it,tags[it.taskId].orEmpty())) }
                    .forEach {result[it.taskId]=it.revision}
                after=rows.last().sequence
                if(rows.size<CHUNK)break
            }
            result.toMap()
        }
    }

    suspend fun actionPreview(id: String, clock: Clock): SeriesActionPreview = withContext(Dispatchers.IO) {
        db.withTransaction {
            val series = requireNotNull(db.dao().series(id)) { "系列已不存在" }
            require(series.deletedAt == null) { "系列已刪除，請重新確認" }
            val rule = requireNotNull(db.dao().latestRule(id))
            val today = LocalDate.now(clock.withZone(ZoneId.of(rule.zoneId)))
            val rows = db.browser().actionRows(id)
            val active = rows.filter { it.deletedAt == null }
            SeriesActionPreview(id, series.revision, rows.associate { it.id to it.revision }, active.size,
                active.count { it.status != TaskStatus.COMPLETED && it.dueDate?.isAfter(today) == true })
        }
    }
}
