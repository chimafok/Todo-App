package com.chimafok.todo.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*

@com.chimafok.todo.Regression101
@RunWith(AndroidJUnit4::class)
class ReminderInputTest {
    private val day = LocalDate.of(2026, 10, 28)
    @Test fun blankTimeInheritsDeadlineThenBaseAndDoesNotAddTaskTime() {
        val row = ReminderInput(date = day.toString(), zone = "Asia/Hong_Kong")
        assertEquals(Instant.parse("2026-10-28T11:30:00Z").toEpochMilli(), row.spec(day, LocalTime.of(19,30), "Asia/Hong_Kong").fixedAt)
        assertEquals(Instant.parse("2026-10-28T02:00:00Z").toEpochMilli(), row.spec(day, null, null).fixedAt)
        assertEquals(Instant.parse("2026-10-28T10:15:00Z").toEpochMilli(), row.copy(base = "1815").spec().fixedAt)
        assertEquals(LocalTime.of(18,15), row.copy(base = "1815").spec().baseTime)
        assertEquals(Instant.parse("2026-10-28T09:45:00Z").toEpochMilli(), row.copy(time = "1745", base = "invalid").spec().fixedAt)
        assertEquals(Instant.parse("2026-10-28T11:30:00Z").toEpochMilli(), row.copy(zone = "UTC").spec(day, LocalTime.of(19,30), "Asia/Hong_Kong").fixedAt)
    }
    @Test fun existingFixedPrecisionSurvivesUnrelatedEditsButClearingTimeResolvesAgain() {
        val at = Instant.parse("2026-10-28T11:30:27.123Z").toEpochMilli()
        val row = ReminderInput(date = day.toString(), time = "19:30", zone = "Asia/Hong_Kong", originalFixedAt = at)
        assertEquals(at, row.spec(day.plusDays(4), LocalTime.NOON, "UTC").fixedAt)
        assertEquals(Instant.parse("2026-10-28T10:00:00Z").toEpochMilli(), row.copy(time = "").spec(day, LocalTime.of(18,0), "Asia/Hong_Kong").fixedAt)
        try { row.copy(date = "2026-03-08", time = "02:30", zone = "America/New_York").spec(); fail() }
        catch (_: IllegalArgumentException) {}
    }
    @Test fun jsonPreservesIncompleteQuantityUnitBlankTimeAndIdentityAcrossRecreation() {
        val row = ReminderInput(date = "1028", time = "", offset = "", base = "1815", offsetUnit = ReminderOffsetUnit.DAYS)
        assertEquals(row, decodeReminderInputs(encodeReminderInputs(listOf(row))).single())
        assertEquals(2880, row.copy(kind = "RELATIVE_DUE", offset = "2").spec().offsetMinutes)
    }
    @Test fun oldMinuteDraftWithoutUnitRestoresWithoutChangingMeaningOrFixedInstant() {
        listOf(0, 60, 90, 1440).forEach { minutes ->
            val old = ReminderInput(kind = "RELATIVE_DUE", offset = minutes.toString()).json().also { it.remove("offsetUnit") }
            val restored = decodeReminderInputs(org.json.JSONArray().put(old).toString()).single()
            assertEquals(minutes, restored.spec().offsetMinutes)
        }
    }
}
