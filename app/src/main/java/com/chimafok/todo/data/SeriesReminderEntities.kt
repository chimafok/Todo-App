package com.chimafok.todo.data

import androidx.room.*
import com.chimafok.todo.domain.*
import java.time.LocalTime

@Entity(tableName = "series_reminders", foreignKeys = [ForeignKey(entity = RecurringSeriesEntity::class,
    parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE)], indices = [Index("seriesId")])
data class SeriesReminderEntity(@PrimaryKey val id: String, val seriesId: String, val kind: String,
    val offsetMinutes: Int?, val dayOffset: Int?, val time: LocalTime?, val baseTime: LocalTime,
    val enabled: Boolean = true, val createdAt: Long, val updatedAt: Long) {
    fun spec() = SeriesReminderSpec(id, SeriesReminderKind.valueOf(kind), offsetMinutes, dayOffset, time, baseTime)
}
/** Explicit changes to just one occurrence survive subsequent template edits. */
@Entity(tableName = "reminder_overrides", foreignKeys = [ForeignKey(entity = TaskEntity::class,
    parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)])
data class ReminderOverrideEntity(@PrimaryKey val taskId: String)
