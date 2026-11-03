package com.chimafok.todo.domain

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
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
        throw IllegalArgumentException(tr(R.string.text_invalid_date_use_yyyy_mm_dd_a5ba4d))
    } catch (_: DateTimeException) {
        throw IllegalArgumentException(tr(R.string.text_invalid_date_use_yyyy_mm_dd_a5ba4d))
    }
}

fun parseTimeInput(input: String): LocalTime? {
    val value = input.trim()
    if (value.isEmpty()) return null
    require(value.matches(Regex("(?:[01][0-9]|2[0-3])[:-]?[0-5][0-9]"))) {
        tr(R.string.text_invalid_time_use_hh_mm_or_407181)
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
            throw IllegalArgumentException(tr(R.string.text_this_date_does_not_exist_next_1b3fc4))
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException(tr(R.string.text_this_date_does_not_exist_next_1b3fc4))
        }
    }
    return date
}

/** Gaps are rejected for new input; overlaps use the earlier occurrence, shown in the preview. */
fun deadlineInstant(date: LocalDate, time: LocalTime, zoneId: String): Instant {
    val local = date.atTime(time)
    val offsets = ZoneId.of(zoneId).rules.getValidOffsets(local)
    require(offsets.isNotEmpty()) { tr(R.string.text_this_local_time_does_not_exist_21a08f) }
    return local.toInstant(offsets.first())
}

fun deadlineZoneLabel(date: LocalDate, time: LocalTime, zoneId: String): String {
    val instant = deadlineInstant(date, time, zoneId)
    val offset = ZoneId.of(zoneId).rules.getOffset(instant)
    return "$zoneId（UTC${if (offset == ZoneOffset.UTC) "+00:00" else offset.id}）"
}
