package com.chimafok.todo.domain

import com.chimafok.todo.data.*
import java.time.*

data class SeriesEditRecord(val occurrence: OccurrenceEntity, val task: TaskEntity)
data class OccurrenceReplan(val record: SeriesEditRecord, val date: LocalDate?)
data class SeriesEditPlan(val anchor: LocalDate, val effectiveFrom: Int, val changes: List<OccurrenceReplan>) {
    val cancelledCount get() = changes.count { it.date == null }
    val restoredCount get() = changes.count { it.date != null && it.record.occurrence.excludedByRule }
    val updatedCount get() = changes.count { it.date != null && !it.record.occurrence.excludedByRule }
}

fun canEditSeriesFrom(record: SeriesEditRecord, clock: Clock): Boolean =
    !record.occurrence.excludedByRule && record.task.deletedAt == null && record.task.status != TaskStatus.COMPLETED &&
        record.task.dueDate?.let { it >= LocalDate.now(clock.withZone(ZoneId.of(record.task.zoneId ?: clock.zone.id))) } == true

/** Pure preview used by both the editor and the transaction; never removes consumed slots. */
fun planSeriesEdit(draft: TaskDraft, current: SeriesEditRecord, latest: SeriesRuleEntity,
    records: List<SeriesEditRecord>, clock: Clock): SeriesEditPlan {
    require(canEditSeriesFrom(current, clock)) { "過去、已完成或規則取消的紀錄只能修改本次" }
    val input = requireNotNull(draft.recurrence)
    val sequence = current.occurrence.sequence
    require(input.maxCount == null || input.maxCount >= sequence) { "總次數不可早於本次序號 $sequence" }
    val sameCalendar = latest.effectiveFrom <= sequence && draft.dueDate == current.task.dueDate &&
        latest.frequency == input.frequency && latest.intervalDays == input.intervalDays &&
        latest.weekdayMask == input.weekdays.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) } &&
        latest.rule().dateAt(sequence - latest.effectiveFrom + 1) == current.task.dueDate
    val anchor = if (sameCalendar) latest.anchor else input.anchor
    val effectiveFrom = if (sameCalendar) latest.effectiveFrom else sequence
    val schedule = input.copy(anchor = anchor, maxCount = input.maxCount?.let { it - effectiveFrom + 1 })
    val changes = records.filter { record ->
        record.occurrence.sequence > sequence && record.task.deletedAt == null && record.task.status != TaskStatus.COMPLETED &&
            (record.occurrence.excludedByRule || canEditSeriesFrom(record, clock))
    }.sortedBy { it.occurrence.sequence }.mapNotNull { record ->
        val date = schedule.dateAt(record.occurrence.sequence - effectiveFrom + 1)
        if (date == null && record.occurrence.excludedByRule) null else OccurrenceReplan(record, date)
    }
    return SeriesEditPlan(anchor, effectiveFrom, changes)
}
