package com.chimafok.todo.domain

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
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
        require(anchor.year in 1..9999) { tr(R.string.text_start_date_out_of_range_f5cecb) }
        ZoneId.of(zoneId)
        require(intervalDays in 1..3650 && (frequency == RecurrenceFrequency.EVERY_N_DAYS || intervalDays == 1)) { tr(R.string.text_the_interval_must_be_days_878fbc) }
        require(if (frequency == RecurrenceFrequency.WEEKDAYS) weekdays.isNotEmpty() else weekdays.isEmpty()) { tr(R.string.text_select_weekdays_779c9d) }
        require(endDate == null || (endDate.year in 1..9999 && endDate >= anchor)) { tr(R.string.text_the_end_date_cannot_precede_the_b3df90) }
        require(maxCount == null || maxCount >= 1) { tr(R.string.text_occurrence_count_must_be_greater_than_b6b7e8) }
        require(endDate == null || maxCount == null) { tr(R.string.text_choose_only_one_end_condition_daa511) }
        require(endDate == null || rawDate(1)?.let { it <= endDate } == true) { tr(R.string.text_no_matching_dates_before_the_end_e1f7e3) }
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
        require(sequence >= 1) { tr(R.string.text_sequence_numbers_must_start_at_048d18) }
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
    require(cursor == 0 || rule.dateAt(cursor) != null) { tr(R.string.text_the_rule_does_not_match_the_e7f05b) }
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
