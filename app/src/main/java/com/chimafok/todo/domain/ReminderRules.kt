package com.chimafok.todo.domain

import java.time.*

enum class ReminderKind { FIXED, RELATIVE_DUE }
val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(10, 0)
data class ReminderSpec(val id: String, val kind: ReminderKind, val zoneId: String,
    val fixedAt: Long? = null, val offsetMinutes: Int? = null, val baseTime: LocalTime = DEFAULT_REMINDER_TIME)

fun resolveReminder(spec: ReminderSpec, date: LocalDate?, time: LocalTime?, deadlineZone: String?): Long {
    require(spec.id.isNotBlank()) { "提醒識別碼不可空白" }
    ZoneId.of(spec.zoneId)
    return when (spec.kind) {
        ReminderKind.FIXED -> {
            require(spec.offsetMinutes == null) { "固定提醒不能同時設定相對時間" }
            requireNotNull(spec.fixedAt) { "請填提醒日期與時間" }.also { Instant.ofEpochMilli(it) }
        }
        ReminderKind.RELATIVE_DUE -> {
            require(spec.fixedAt == null) { "相對提醒不能同時設定固定時間" }
            val offset = requireNotNull(spec.offsetMinutes) { "請填提前分鐘數" }
            require(offset in 0..5_256_000) { "提前分鐘數須為 0–5256000" }
            val day = requireNotNull(date) { "相對提醒需要截止日期；請改成固定提醒或刪除此提醒" }
            // Legacy date-only tasks may have no saved deadline zone. The reminder
            // captures its own zone without fabricating the task's historical zone.
            val zone = ZoneId.of(deadlineZone ?: spec.zoneId)
            if (time != null) require(deadlineZone != null) { "截止時間缺少時區" }
            val base = day.atTime(time ?: spec.baseTime).atZone(zone).toInstant()
            base.minus(Duration.ofMinutes(offset.toLong())).toEpochMilli()
        }
    }
}

/** Store one canonical minute offset; UI quantities never truncate or overflow. */
enum class ReminderOffsetUnit(val label: String, val minutes: Long) {
    MINUTES("分鐘", 1), HOURS("小時", 60), DAYS("天", 1440)
}
fun reminderOffsetMinutes(quantity: String, unit: ReminderOffsetUnit): Int {
    val text = quantity.trim()
    require(text.matches(Regex("[0-9]+"))) { "請填有效的提前數字（整數，0 表示截止時）" }
    val amount = requireNotNull(text.toLongOrNull()) { "提醒提前量超出範圍" }
    require(amount <= 5_256_000L / unit.minutes) { "提醒提前量不可超過 5256000 分鐘（3650 天）" }
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
    requireNotNull(date) { "截止時間需要截止日期" }
    val source = requireNotNull(deadlineZone) { "截止時間缺少時區" }
    return deadlineInstant(date, time, source).atZone(target).toLocalTime()
}
