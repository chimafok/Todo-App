package com.chimafok.todo.domain

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
import java.time.*

enum class ReminderKind { FIXED, RELATIVE_DUE }
val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(10, 0)
data class ReminderSpec(val id: String, val kind: ReminderKind, val zoneId: String,
    val fixedAt: Long? = null, val offsetMinutes: Int? = null, val baseTime: LocalTime = DEFAULT_REMINDER_TIME)

fun resolveReminder(spec: ReminderSpec, date: LocalDate?, time: LocalTime?, deadlineZone: String?): Long {
    require(spec.id.isNotBlank()) { tr(R.string.text_reminder_id_cannot_be_blank_a51f4f) }
    ZoneId.of(spec.zoneId)
    return when (spec.kind) {
        ReminderKind.FIXED -> {
            require(spec.offsetMinutes == null) { tr(R.string.text_a_fixed_reminder_cannot_also_have_7841a2) }
            requireNotNull(spec.fixedAt) { tr(R.string.text_enter_a_reminder_date_and_time_b57618) }.also { Instant.ofEpochMilli(it) }
        }
        ReminderKind.RELATIVE_DUE -> {
            require(spec.fixedAt == null) { tr(R.string.text_a_relative_reminder_cannot_also_have_aae6a8) }
            val offset = requireNotNull(spec.offsetMinutes) { tr(R.string.text_enter_minutes_before_due_ed5517) }
            require(offset in 0..5_256_000) { tr(R.string.text_minutes_before_due_must_be_2b34c7) }
            val day = requireNotNull(date) { tr(R.string.text_a_relative_reminder_requires_a_due_4725a1) }
            // Legacy date-only tasks may have no saved deadline zone. The reminder
            // captures its own zone without fabricating the task's historical zone.
            val zone = ZoneId.of(deadlineZone ?: spec.zoneId)
            if (time != null) require(deadlineZone != null) { tr(R.string.text_the_due_time_requires_a_time_6c3eea) }
            val base = day.atTime(time ?: spec.baseTime).atZone(zone).toInstant()
            base.minus(Duration.ofMinutes(offset.toLong())).toEpochMilli()
        }
    }
}

/** Store one canonical minute offset; UI quantities never truncate or overflow. */
enum class ReminderOffsetUnit(private val labelId: Int, val minutes: Long) {
    MINUTES(R.string.text_minutes_c26fdd, 1), HOURS(R.string.text_hours_507ac2, 60), DAYS(R.string.text_days_49da61, 1440);
    val label: String get() = tr(labelId)
}
fun reminderOffsetMinutes(quantity: String, unit: ReminderOffsetUnit): Int {
    val text = quantity.trim()
    require(text.matches(Regex("[0-9]+"))) { tr(R.string.text_enter_a_valid_whole_number_offset_6a5e1b) }
    val amount = requireNotNull(text.toLongOrNull()) { tr(R.string.text_reminder_offset_out_of_range_f21630) }
    require(amount <= 5_256_000L / unit.minutes) { tr(R.string.text_reminder_offset_cannot_exceed_minutes_days_4da56b) }
    return (amount * unit.minutes).toInt()
}
fun reminderOffsetDisplay(minutes: Int): Pair<String, ReminderOffsetUnit> {
    val unit = if (minutes > 0) ReminderOffsetUnit.entries.reversed().first { minutes.toLong() % it.minutes == 0L }
        else ReminderOffsetUnit.MINUTES
    return (minutes / unit.minutes).toString() to unit
}

/** Inherit the deadline's clock in the fixed reminder's captured zone, then freeze on save. */
fun fixedReminderDefaultTime(date: LocalDate?, time: LocalTime?, deadlineZone: String?,
    reminderZone: String, base: LocalTime = DEFAULT_REMINDER_TIME): LocalTime {
    val target = ZoneId.of(reminderZone)
    if (time == null) return base
    requireNotNull(date) { tr(R.string.text_a_due_time_requires_a_due_40fa33) }
    val source = requireNotNull(deadlineZone) { tr(R.string.text_the_due_time_requires_a_time_6c3eea) }
    return deadlineInstant(date, time, source).atZone(target).toLocalTime()
}
