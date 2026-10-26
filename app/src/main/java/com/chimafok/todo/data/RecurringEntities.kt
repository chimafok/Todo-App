package com.chimafok.todo.data

import androidx.room.*
import com.chimafok.todo.domain.*
import java.time.LocalDate
import java.time.LocalTime

enum class SeriesLifecycle { ACTIVE, STOPPED, ENDED, TRASHED }

@Entity(tableName = "recurring_series", foreignKeys = [ForeignKey(entity = CategoryEntity::class,
    parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("categoryId")])
data class RecurringSeriesEntity(
    @PrimaryKey val id: String, val title: String, val notes: String, val categoryId: String,
    val lifecycle: SeriesLifecycle = SeriesLifecycle.ACTIVE, val cursor: Int = 0,
    val deletedAt: Long? = null, val previousLifecycle: SeriesLifecycle? = null,
    val createdAt: Long, val updatedAt: Long, val revision: Long = 1,
)

@Entity(tableName = "series_rules", foreignKeys = [ForeignKey(entity = RecurringSeriesEntity::class,
    parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["seriesId", "version"], unique = true)])
data class SeriesRuleEntity(
    @PrimaryKey val id: String, val seriesId: String, val version: Int, val effectiveFrom: Int,
    val anchor: LocalDate, val frequency: RecurrenceFrequency, val intervalDays: Int, val weekdayMask: Int,
    val dueTime: LocalTime?, val zoneId: String, val endDate: LocalDate?, val maxCount: Int?, val createdAt: Long,
    val startDayOffset: Int? = null, val startTime: LocalTime? = null, val startZoneId: String? = null,
) {
    fun rule() = RecurrenceRule(anchor, frequency, zoneId, intervalDays,
        java.time.DayOfWeek.entries.filter { weekdayMask and (1 shl (it.value - 1)) != 0 }.toSet(),
        endDate, maxCount?.let { it - effectiveFrom + 1 })
}

@Entity(tableName = "series_tags", primaryKeys = ["seriesId", "tagId"], foreignKeys = [
    ForeignKey(entity = RecurringSeriesEntity::class, parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("tagId")])
data class SeriesTagEntity(val seriesId: String, val tagId: String)

@Entity(tableName = "occurrences", foreignKeys = [
    ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = RecurringSeriesEntity::class, parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = SeriesRuleEntity::class, parentColumns = ["id"], childColumns = ["ruleId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["seriesId", "sequence"], unique = true), Index("ruleId")])
data class OccurrenceEntity(@PrimaryKey val taskId: String, val seriesId: String, val sequence: Int, val ruleId: String,
    val snapshot: String? = null, val deletedWithSeriesAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val excludedByRule: Boolean = false)
