package com.chimafok.todo.domain

import java.time.*
import java.util.UUID

enum class SeriesReminderKind { RELATIVE_DUE, OCCURRENCE_TIME }
data class SeriesReminderSpec(val id: String, val kind: SeriesReminderKind, val offsetMinutes: Int? = null,
    val dayOffset: Int? = null, val time: LocalTime? = null, val baseTime: LocalTime = DEFAULT_REMINDER_TIME)

fun validateSeriesReminder(spec: SeriesReminderSpec) {
    require(spec.id.isNotBlank()) { "提醒識別碼不可空白" }
    when (spec.kind) {
        SeriesReminderKind.RELATIVE_DUE -> require(spec.offsetMinutes != null && spec.offsetMinutes in 0..5_256_000 && spec.dayOffset == null && spec.time == null) { "截止前提醒須設定有效的提前量" }
        SeriesReminderKind.OCCURRENCE_TIME -> require(spec.offsetMinutes == null && spec.dayOffset != null && spec.dayOffset in -3650..3650) { "每次提醒的日期偏移須在前後 3650 天內" }
    }
    require(spec.time == null || (spec.time.second == 0 && spec.time.nano == 0)) { "系列提醒請以分鐘設定" }
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
        require(day.year in 1..9999) { "提醒日期超出範圍" }
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
    require(sources.size <= 32 && sources.map { it.id }.distinct().size == sources.size) { "每項任務最多 32 個不同提醒" }
    val seriesZone = ZoneId.of(zone)
    return sources.map { source ->
        val id = UUID.nameUUIDFromBytes("todo/reminder-conversion/v1/${source.id}".toByteArray(Charsets.UTF_8)).toString()
        ZoneId.of(source.zoneId)
        require(source.id.isNotBlank()) { "提醒識別碼不可空白" }
        val template = when (source.kind) {
            ReminderKind.RELATIVE_DUE -> {
                require(source.fixedAt == null) { "相對提醒不能同時設定固定時間" }
                SeriesReminderSpec(id, SeriesReminderKind.RELATIVE_DUE, offsetMinutes = source.offsetMinutes, baseTime = source.baseTime)
            }
            ReminderKind.FIXED -> {
                require(source.offsetMinutes == null) { "固定提醒不能同時設定相對時間" }
                val local = Instant.ofEpochMilli(requireNotNull(source.fixedAt) { "請填固定提醒日期與時間" }).atZone(seriesZone)
                require(local.second == 0 && local.nano == 0) { "固定提醒包含秒數，請先改成另一個分鐘並儲存，再沿用" }
                val days = java.time.temporal.ChronoUnit.DAYS.between(firstDate, local.toLocalDate())
                require(days in -3650L..3650L) { "固定提醒距首次日期超過 3650 天，請先調整提醒再沿用" }
                SeriesReminderSpec(id, SeriesReminderKind.OCCURRENCE_TIME, dayOffset = days.toInt(), time = local.toLocalTime(), baseTime = source.baseTime)
            }
        }
        validateSeriesReminder(template)
        template
    }
}
