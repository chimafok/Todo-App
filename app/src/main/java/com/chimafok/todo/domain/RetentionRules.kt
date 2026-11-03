package com.chimafok.todo.domain

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
import java.time.Instant
import java.time.temporal.ChronoUnit

const val TRASH_DAYS = 30
fun retentionDays(text: String): Int {
    require(text.trim().matches(Regex("[0-9]+"))) { tr(R.string.text_enter_a_positive_whole_number_of_dc0d81) }
    return requireNotNull(text.trim().toIntOrNull()) { tr(R.string.text_days_out_of_range_46e8dc) }.also { require(it > 0) { tr(R.string.text_days_must_be_greater_than_029793) } }
}
fun retentionExpired(start: Long, days: Int, now: Long): Boolean {
    require(days > 0)
    return Instant.ofEpochMilli(now) >= Instant.ofEpochMilli(start).plus(days.toLong(), ChronoUnit.DAYS)
}
