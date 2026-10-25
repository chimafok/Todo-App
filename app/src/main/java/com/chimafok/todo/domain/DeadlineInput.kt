package com.chimafok.todo.domain

import java.time.*

fun parseDateInput(input: String, referenceDate: LocalDate): LocalDate? {
    val value = input.trim()
    if (value.isEmpty()) return null
    return try {
        when {
            value.matches(Regex("[0-9]{8}")) -> LocalDate.of(value.take(4).toInt(), value.substring(4, 6).toInt(), value.takeLast(2).toInt())
            value.matches(Regex("[0-9]{6}")) -> LocalDate.of(2000 + value.take(2).toInt(), value.substring(2, 4).toInt(), value.takeLast(2).toInt())
            value.matches(Regex("[0-9]{4}")) -> {
                val monthDay = MonthDay.of(value.take(2).toInt(), value.takeLast(2).toInt())
                val year = referenceDate.year + if (monthDay < MonthDay.from(referenceDate)) 1 else 0
                LocalDate.of(year, monthDay.monthValue, monthDay.dayOfMonth)
            }
            value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) -> LocalDate.parse(value)
            else -> throw DateTimeException("format")
        }.also { require(it.year in 1..9999) }
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("日期無效，請用 YYYY-MM-DD、YYYYMMDD、YYMMDD 或 MMDD")
    } catch (_: DateTimeException) {
        throw IllegalArgumentException("日期無效，請用 YYYY-MM-DD、YYYYMMDD、YYMMDD 或 MMDD")
    }
}

fun parseTimeInput(input: String): LocalTime? {
    val value = input.trim()
    if (value.isEmpty()) return null
    require(value.matches(Regex("(?:[01][0-9]|2[0-3])[:-]?[0-5][0-9]"))) {
        "時間無效，請用 HH:mm 或 HHmm（24 小時制）"
    }
    return LocalTime.of(value.take(2).toInt(), value.takeLast(2).toInt())
}

/** A yearless deadline is resolved once against the latest deadline input, not recomposition. */
fun resolveDeadlineDate(input: String, time: LocalTime?, zoneId: String?, reference: Instant): LocalDate? {
    val zone = ZoneId.of(zoneId ?: ZoneId.systemDefault().id)
    val today = reference.atZone(zone).toLocalDate()
    val date = parseDateInput(input, today) ?: return null
    if (input.trim().matches(Regex("[0-9]{4}")) && date == today && time != null &&
        deadlineInstant(date, time, zone.id) < reference) {
        return try {
            LocalDate.of(date.year + 1, date.month, date.dayOfMonth).also { require(it.year <= 9999) }
        } catch (_: DateTimeException) {
            throw IllegalArgumentException("下一年沒有這個日期，請輸入完整日期")
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("下一年沒有這個日期，請輸入完整日期")
        }
    }
    return date
}

/** Gaps are rejected for new input; overlaps use the earlier occurrence, shown in the preview. */
fun deadlineInstant(date: LocalDate, time: LocalTime, zoneId: String): Instant {
    val local = date.atTime(time)
    val offsets = ZoneId.of(zoneId).rules.getValidOffsets(local)
    require(offsets.isNotEmpty()) { "此時區的這個時間因夏令時間不存在，請改選其他時間" }
    return local.toInstant(offsets.first())
}

fun deadlineZoneLabel(date: LocalDate, time: LocalTime, zoneId: String): String {
    val instant = deadlineInstant(date, time, zoneId)
    val offset = ZoneId.of(zoneId).rules.getOffset(instant)
    return "$zoneId（UTC${if (offset == ZoneOffset.UTC) "+00:00" else offset.id}）"
}
