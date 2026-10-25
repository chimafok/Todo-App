package com.chimafok.todo.domain

import com.chimafok.todo.data.TaskEntity
import java.time.*
import java.util.UUID

enum class RecurrenceFrequency { DAILY, WEEKLY, WEEKDAYS, MONTHLY, EVERY_N_DAYS }

/** Immutable scheduling rule. Count includes every consumed slot, including deleted occurrences. */
data class RecurrenceRule(
    val anchor: LocalDate,
    val frequency: RecurrenceFrequency,
    val zoneId: String,
    val intervalDays: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val endDate: LocalDate? = null,
    val maxCount: Int? = null,
) {
    init {
        require(anchor.year in 1..9999) { "開始日期超出範圍" }
        ZoneId.of(zoneId)
        require(intervalDays in 1..3650 && (frequency == RecurrenceFrequency.EVERY_N_DAYS || intervalDays == 1)) { "間隔須為 1–3650 天" }
        require(if (frequency == RecurrenceFrequency.WEEKDAYS) weekdays.isNotEmpty() else weekdays.isEmpty()) { "請選擇指定星期" }
        require(endDate == null || (endDate.year in 1..9999 && endDate >= anchor)) { "結束日期不可早於開始日期" }
        require(maxCount == null || maxCount >= 1) { "重複次數須大於零" }
        require(endDate == null || maxCount == null) { "請只選一種結束條件" }
        require(endDate == null || rawDate(1)?.let { it <= endDate } == true) { "結束前沒有符合的日期" }
    }

    private fun rawDate(sequence: Int): LocalDate? = try {
        val offset = sequence.toLong() - 1
        when (frequency) {
            RecurrenceFrequency.DAILY -> anchor.plusDays(offset)
            RecurrenceFrequency.WEEKLY -> anchor.plusWeeks(offset)
            RecurrenceFrequency.EVERY_N_DAYS -> anchor.plusDays(Math.multiplyExact(offset, intervalDays.toLong()))
            // Always calculate from the original anchor, so February never changes March's day.
            RecurrenceFrequency.MONTHLY -> anchor.plusMonths(offset)
            RecurrenceFrequency.WEEKDAYS -> {
                val days = weekdays.map { (it.value - anchor.dayOfWeek.value + 7) % 7 }.sorted()
                anchor.plusDays(offset / days.size * 7 + days[(offset % days.size).toInt()])
            }
        }.takeIf { it.year in 1..9999 }
    } catch (_: DateTimeException) { null } catch (_: ArithmeticException) { null }

    fun dateAt(sequence: Int): LocalDate? {
        require(sequence >= 1) { "序號須由 1 開始" }
        if (maxCount != null && sequence > maxCount) return null
        return rawDate(sequence)?.takeIf { endDate == null || it <= endDate }
    }
}

data class RecurrenceSlot(val sequence: Int, val date: LocalDate, val taskId: String)
data class RecurrenceBatch(val slots: List<RecurrenceSlot>, val cursor: Int, val hasMoreDue: Boolean, val ended: Boolean)

fun occurrenceId(seriesId: String, sequence: Int): String {
    require(seriesId.isNotBlank() && sequence >= 1)
    return UUID.nameUUIDFromBytes("todo/occurrence/v1/$seriesId/$sequence".toByteArray(Charsets.UTF_8)).toString()
}

/**
 * Plan at most [limit] rows for one future DB transaction. Never infer progress from completion.
 * [advanceAfterSequence] is an explicit event's sequence (0 for a new series), not a persistent flag;
 * retrying that event after its next occurrence exists cannot create another future row.
 */
fun planOccurrences(seriesId: String, rule: RecurrenceRule, cursor: Int, now: Instant,
    advanceAfterSequence: Int? = null, limit: Int = 256, throughDate: LocalDate? = null): RecurrenceBatch {
    require(seriesId.isNotBlank() && cursor >= 0 && limit in 1..1000)
    require(advanceAfterSequence == null || advanceAfterSequence in 0..cursor)
    require(cursor == 0 || rule.dateAt(cursor) != null) { "規則與已保存游標不一致" }
    val today = throughDate ?: now.atZone(ZoneId.of(rule.zoneId)).toLocalDate()
    var position = cursor
    val slots = mutableListOf<RecurrenceSlot>()
    while (position < Int.MAX_VALUE && slots.size < limit) {
        val next = rule.dateAt(position + 1) ?: break
        if (next > today && (advanceAfterSequence == null || position > advanceAfterSequence)) break
        position++
        slots += RecurrenceSlot(position, next, occurrenceId(seriesId, position))
        if (next > today) break
    }
    val next = if (position < Int.MAX_VALUE) rule.dateAt(position + 1) else null
    return RecurrenceBatch(slots, position, next != null && next <= today, next == null)
}

data class RecurringTaskRef(val taskId: String, val seriesId: String, val sequence: Int, val excludedByRule: Boolean = false)

/** Returns a projection only. It must never delete, complete or hide history in persistent storage. */
fun recurringHomeTaskIds(tasks: List<TaskEntity>, occurrences: List<RecurringTaskRef>, clock: Clock): Set<String> {
    val byId = tasks.associateBy { it.id }
    return occurrences.filterNot { it.excludedByRule }.groupBy { it.seriesId }.values.mapNotNull { refs ->
        val candidates = refs.mapNotNull { ref -> byId[ref.taskId]?.let { ref to it } }
            .filter { (_, task) -> task.deletedAt == null && task.status != TaskStatus.COMPLETED }
        val order = compareBy<Pair<RecurringTaskRef, TaskEntity>> { it.second.agendaDate(clock.zone) }
            .thenComparator { a, b -> taskTimeOrder.compare(a.second, b.second) }.thenBy { it.first.sequence }
        val overdue = candidates.filter { (_, task) -> isOverdue(task.dueDate, task.dueTime, task.zoneId, clock) }
        (overdue.maxWithOrNull(order) ?: candidates.minWithOrNull(order))?.first?.taskId
    }.toSet()
}
