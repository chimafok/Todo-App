package com.chimafok.todo.data

import androidx.room.*
import com.chimafok.todo.domain.*
import java.time.LocalTime

@Entity(tableName = "reminders", foreignKeys = [ForeignKey(entity = TaskEntity::class,
    parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId"), Index(value = ["state", "resolvedAt"])])
data class ReminderEntity(@PrimaryKey val id: String, val taskId: String, val kind: String,
    val zoneId: String, val fixedAt: Long?, val offsetMinutes: Int?, val baseTime: LocalTime,
    val enabled: Boolean = true, val state: String = "PENDING", val resolvedAt: Long? = null,
    val alarmKey: String? = null, val alarmMode: String? = null,
    val revision: Long = 1, val createdAt: Long, val updatedAt: Long) {
    fun spec() = ReminderSpec(id, ReminderKind.valueOf(kind), zoneId, fixedAt, offsetMinutes, baseTime)
}

// One durable dirty marker per task; its generation is advanced by SQLite triggers
// in the same transaction as every task/definition mutation, including batch edits.
@Entity(tableName = "schedule_outbox")
data class ScheduleOutboxEntity(@PrimaryKey val taskId: String, val generation: Long,
    val processedAt: Long? = null)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY resolvedAt DESC, createdAt, id") suspend fun all(): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE taskId = :id ORDER BY resolvedAt DESC, createdAt, id") suspend fun forTask(id: String): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun get(id: String): ReminderEntity?
    @Insert suspend fun insert(value: ReminderEntity)
    @Update suspend fun update(value: ReminderEntity)
    @Query("SELECT * FROM series_reminders WHERE enabled = 1 ORDER BY createdAt, id") suspend fun templates(): List<SeriesReminderEntity>
    @Query("SELECT * FROM series_reminders WHERE seriesId = :id ORDER BY createdAt, id") suspend fun templatesFor(id: String): List<SeriesReminderEntity>
    @Query("SELECT * FROM series_reminders WHERE id = :id") suspend fun template(id: String): SeriesReminderEntity?
    @Insert suspend fun insertTemplate(value: SeriesReminderEntity)
    @Update suspend fun updateTemplate(value: SeriesReminderEntity)
    @Query("SELECT * FROM reminder_overrides") suspend fun overrides(): List<ReminderOverrideEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM reminder_overrides WHERE taskId = :id)") suspend fun overridden(id: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun override(value: ReminderOverrideEntity)
    @Query("DELETE FROM reminder_overrides WHERE taskId = :id") suspend fun resetOverride(id: String)
    @Query("SELECT * FROM schedule_outbox WHERE processedAt IS NULL") suspend fun pending(): List<ScheduleOutboxEntity>
    @Query("UPDATE schedule_outbox SET processedAt = :now WHERE taskId = :id AND generation = :generation")
    suspend fun processed(id: String, generation: Long, now: Long)
}

/** Legacy rows may not yet have a resolvedAt; derive display order without changing user data. */
fun List<ReminderEntity>.orderedFor(task: TaskEntity): List<ReminderEntity> = sortedWith(
    compareByDescending<ReminderEntity> { runCatching {
        resolveReminder(it.spec(), task.dueDate, task.dueTime, task.zoneId)
    }.getOrNull() ?: Long.MIN_VALUE }.thenBy { it.createdAt }.thenBy { it.id })
