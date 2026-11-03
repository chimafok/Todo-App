package com.chimafok.todo.domain

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
import java.time.*
import java.util.UUID

enum class SeriesReminderKind { RELATIVE_DUE, OCCURRENCE_TIME }
data class SeriesReminderSpec(val id: String, val kind: SeriesReminderKind, val offsetMinutes: Int? = null,
    val dayOffset: Int? = null, val time: LocalTime? = null, val baseTime: LocalTime = DEFAULT_REMINDER_TIME)

fun validateSeriesReminder(spec: SeriesReminderSpec) {
    require(spec.id.isNotBlank()) { tr(R.string.text_reminder_id_cannot_be_blank_a51f4f) }
    when (spec.kind) {
        SeriesReminderKind.RELATIVE_DUE -> require(spec.offsetMinutes != null && spec.offsetMinutes in 0..5_256_000 && spec.dayOffset == null && spec.time == null) { tr(R.string.text_a_before_due_reminder_requires_a_0ffc5d) }
        SeriesReminderKind.OCCURRENCE_TIME -> require(spec.offsetMinutes == null && spec.dayOffset != null && spec.dayOffset in -3650..3650) { tr(R.string.text_each_reminder_date_offset_must_be_01df03) }
    }
    require(spec.time == null || (spec.time.second == 0 && spec.time.nano == 0)) { tr(R.string.text_set_series_reminders_in_minutes_b9cc65) }
}
fun occurrenceReminderId(taskId: String, templateId: String): String =
    UUID.nameUUIDFromBytes("todo/series-reminder/v1/$taskId/$templateId".toByteArray(Charsets.UTF_8)).toString()

/** New occurrences get new absolute instants. Automatic DST gaps advance, overlaps use the first offset. */
fun occurrenceReminder(spec: SeriesReminderSpec, taskId: String, date: LocalDate, dueTime: LocalTime?, zone: String): ReminderSpec {
    validateSeriesReminder(spec)
    val id = occurrenceReminderId(taskId, spec.id)
    return if (spec.kind == SeriesReminderKind.RELATIVE_DUE) ReminderSpec(id, ReminderKind.RELATIVE_DUE, zone,
        offsetMinutes = spec.offsetMinutes, baseTime = spec.baseTime)
    else {
        val day = date.plusDays(requireNotNull(spec.dayOffset).toLong())
        require(day.year in 1..9999) { tr(R.string.text_reminder_date_out_of_range_9e9909) }
        ReminderSpec(id, ReminderKind.FIXED, zone,
            fixedAt = day.atTime(spec.time ?: dueTime ?: spec.baseTime).atZone(ZoneId.of(zone)).toInstant().toEpochMilli(), baseTime = spec.baseTime)
    }
}
/** Include a two-day buffer so maintenance needn't run at the exact reminder minute. */
fun reminderGenerationDate(specs: List<SeriesReminderSpec>, now: Instant, zone: String): LocalDate? {
    if (specs.isEmpty()) return null
    specs.forEach(::validateSeriesReminder)
    val days = specs.maxOf { if (it.kind == SeriesReminderKind.RELATIVE_DUE) (requireNotNull(it.offsetMinutes).toLong() + 1439) / 1440
        else maxOf(0, -requireNotNull(it.dayOffset)).toLong() }
    val today = now.atZone(ZoneId.of(zone)).toLocalDate()
    return runCatching { today.plusDays(days + 2).coerceAtMost(LocalDate.of(9999,12,31)) }.getOrElse { LocalDate.of(9999,12,31) }
}

/** Explicit editor conversion: calendar offsets use the saved series zone, never the device zone. */
fun inheritReminderTemplates(sources: List<ReminderSpec>, firstDate: LocalDate, zone: String): List<SeriesReminderSpec> {
    require(sources.size <= 32 && sources.map { it.id }.distinct().size == sources.size) { tr(R.string.text_each_task_supports_at_most_distinct_fc1d97) }
    val seriesZone = ZoneId.of(zone)
    return sources.map { source ->
        val id = UUID.nameUUIDFromBytes("todo/reminder-conversion/v1/${source.id}".toByteArray(Charsets.UTF_8)).toString()
        ZoneId.of(source.zoneId)
        require(source.id.isNotBlank()) { tr(R.string.text_reminder_id_cannot_be_blank_a51f4f) }
        val template = when (source.kind) {
            ReminderKind.RELATIVE_DUE -> {
                require(source.fixedAt == null) { tr(R.string.text_a_relative_reminder_cannot_also_have_aae6a8) }
                SeriesReminderSpec(id, SeriesReminderKind.RELATIVE_DUE, offsetMinutes = source.offsetMinutes, baseTime = source.baseTime)
            }
            ReminderKind.FIXED -> {
                require(source.offsetMinutes == null) { tr(R.string.text_a_fixed_reminder_cannot_also_have_7841a2) }
                val local = Instant.ofEpochMilli(requireNotNull(source.fixedAt) { tr(R.string.text_enter_a_fixed_reminder_date_and_fd69fc) }).atZone(seriesZone)
                require(local.second == 0 && local.nano == 0) { tr(R.string.text_the_fixed_reminder_includes_seconds_change_7d8090) }
                val days = java.time.temporal.ChronoUnit.DAYS.between(firstDate, local.toLocalDate())
                require(days in -3650L..3650L) { tr(R.string.text_the_fixed_reminder_is_more_than_086d8f) }
                SeriesReminderSpec(id, SeriesReminderKind.OCCURRENCE_TIME, dayOffset = days.toInt(), time = local.toLocalTime(), baseTime = source.baseTime)
            }
        }
        validateSeriesReminder(template)
        template
    }
}
