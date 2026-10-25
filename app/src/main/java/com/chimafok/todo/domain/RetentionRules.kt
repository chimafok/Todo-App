package com.chimafok.todo.domain

import java.time.Instant
import java.time.temporal.ChronoUnit

const val TRASH_DAYS = 30
fun retentionDays(text: String): Int {
    require(text.trim().matches(Regex("[0-9]+"))) { "請填正整數天數" }
    return requireNotNull(text.trim().toIntOrNull()) { "天數超出範圍" }.also { require(it > 0) { "天數必須大於 0" } }
}
fun retentionExpired(start: Long, days: Int, now: Long): Boolean {
    require(days > 0)
    return Instant.ofEpochMilli(now) >= Instant.ofEpochMilli(start).plus(days.toLong(), ChronoUnit.DAYS)
}
