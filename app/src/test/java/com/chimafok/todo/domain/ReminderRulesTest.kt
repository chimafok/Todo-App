package com.chimafok.todo.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderRulesTest {
    private val date = LocalDate.of(2026, 10, 28)
    private fun relative(minutes: Int, base: LocalTime = LocalTime.of(20, 0)) =
        ReminderSpec("r", ReminderKind.RELATIVE_DUE, "Asia/Hong_Kong", offsetMinutes = minutes, baseTime = base)
    @Test fun dateOnlyBaseIsSeparateAndEditable() {
        assertEquals(Instant.parse("2026-10-27T12:00:00Z").toEpochMilli(), resolveReminder(relative(1440), date, null, "Asia/Hong_Kong"))
        assertEquals(Instant.parse("2026-10-28T11:00:00Z").toEpochMilli(), resolveReminder(relative(60), date, null, "Asia/Hong_Kong"))
        assertEquals(Instant.parse("2026-10-28T09:30:00Z").toEpochMilli(), resolveReminder(relative(60, LocalTime.of(18, 30)), date, null, "Asia/Hong_Kong"))
    }
    @Test fun timedRelativeUsesDeadlineZoneAndElapsedMinutesAcrossDst() {
        val spec = relative(60).copy(zoneId = "Asia/Tokyo")
        assertEquals(Instant.parse("2026-03-08T06:30:00Z").toEpochMilli(),
            resolveReminder(spec, LocalDate.of(2026,3,8), LocalTime.of(3,30), "America/New_York"))
    }
    @Test fun legacyDateOnlyCapturesReminderZoneWithoutChangingDeadline() {
        assertEquals(resolveReminder(relative(0), date, null, null), resolveReminder(relative(0), date, null, "Asia/Hong_Kong"))
    }
    @Test fun fixedInstantDoesNotFollowDeadlineOrDeviceZone() {
        val at = Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
        val spec = ReminderSpec("r", ReminderKind.FIXED, "Asia/Hong_Kong", fixedAt = at)
        assertEquals(at, resolveReminder(spec, null, null, null))
        assertEquals(at, resolveReminder(spec, date.plusYears(2), LocalTime.NOON, "America/Los_Angeles"))
    }
    @Test fun changingDeadlineMovesRelativeButFixedStays() {
        assertEquals(Duration.ofDays(3).toMillis(), resolveReminder(relative(60),date.plusDays(3),null,null)-resolveReminder(relative(60),date,null,null))
    }
    @Test fun rejectsInvalidDependenciesAndMutuallyExclusiveFields() {
        val cases = listOf<() -> Unit>(
            { resolveReminder(relative(60),null,null,null) },
            { resolveReminder(relative(-1),date,null,null) },
            { resolveReminder(relative(5_256_001),date,null,null) },
            { resolveReminder(relative(60).copy(fixedAt=1),date,null,null) },
            { resolveReminder(relative(0),date,LocalTime.NOON,null) },
            { resolveReminder(relative(0).copy(zoneId="unknown-zone"),date,null,null) },
            { resolveReminder(ReminderSpec("r",ReminderKind.FIXED,"UTC"),date,null,null) })
        cases.forEach { block -> try { block(); fail("Invalid input accepted") } catch (_: RuntimeException) {} }
    }
    @Test fun quantityUnitsConvertExactlyAndLegacyMinutesRoundTrip() {
        assertEquals(90, reminderOffsetMinutes("90", ReminderOffsetUnit.MINUTES))
        assertEquals(120, reminderOffsetMinutes("2", ReminderOffsetUnit.HOURS))
        assertEquals(4320, reminderOffsetMinutes("3", ReminderOffsetUnit.DAYS))
        assertEquals(0, reminderOffsetMinutes("0", ReminderOffsetUnit.DAYS))
        assertEquals(5_256_000, reminderOffsetMinutes("3650", ReminderOffsetUnit.DAYS))
        listOf(0, 1, 59, 60, 90, 1440, 1500, 5_256_000).forEach { minutes ->
            val (quantity, unit) = reminderOffsetDisplay(minutes)
            assertEquals(minutes, reminderOffsetMinutes(quantity, unit))
        }
        assertEquals("1" to ReminderOffsetUnit.HOURS, reminderOffsetDisplay(60))
        assertEquals("1" to ReminderOffsetUnit.DAYS, reminderOffsetDisplay(1440))
    }
    @Test fun quantityNeverTruncatesClampsOrOverflows() {
        listOf("", "-1", "1.5", "+2", "1e3", "一", "9999999999999999999999999").forEach { value ->
            try { reminderOffsetMinutes(value, ReminderOffsetUnit.DAYS); fail(value) } catch (_: IllegalArgumentException) {}
        }
        ReminderOffsetUnit.entries.forEach { unit ->
            try { reminderOffsetMinutes((5_256_000L / unit.minutes + 1).toString(), unit); fail(unit.name) }
            catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun fixedTimeFallbackPrefersDeadlineAndConvertsCapturedZoneOtherwiseUsesBase() {
        assertEquals(LocalTime.of(19, 30), fixedReminderDefaultTime(date, LocalTime.of(19,30), "Asia/Hong_Kong", "Asia/Hong_Kong"))
        assertEquals(LocalTime.of(11, 30), fixedReminderDefaultTime(date, LocalTime.of(19,30), "Asia/Hong_Kong", "UTC"))
        assertEquals(LocalTime.of(10, 0), fixedReminderDefaultTime(date, null, null, "UTC"))
        assertEquals(LocalTime.of(18, 15), fixedReminderDefaultTime(null, null, null, "UTC", LocalTime.of(18,15)))
        try { fixedReminderDefaultTime(date, LocalTime.NOON, null, "UTC"); fail() } catch (_: IllegalArgumentException) {}
    }

}
