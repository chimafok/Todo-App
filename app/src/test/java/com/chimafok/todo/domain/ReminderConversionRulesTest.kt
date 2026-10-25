package com.chimafok.todo.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.TimeZone

class ReminderConversionRulesTest {
    private val day = LocalDate.of(2026, 10, 25)
    private fun fixed(at: String) = ReminderSpec("fixed", ReminderKind.FIXED, "America/New_York", fixedAt = Instant.parse(at).toEpochMilli())
    private fun rejected(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }

    @Test fun relativeOffsetsAndDateOnlyBaselinesAreInheritedWithoutInventingDeadlineTime() {
        val source = ReminderSpec("relative", ReminderKind.RELATIVE_DUE, "UTC", offsetMinutes = 90, baseTime = LocalTime.of(18, 15))
        val template = inheritReminderTemplates(listOf(source), day, "Asia/Hong_Kong").single()
        assertEquals(90, template.offsetMinutes); assertEquals(LocalTime.of(18, 15), template.baseTime)
        val next = occurrenceReminder(template, "next", day.plusWeeks(1), null, "Asia/Hong_Kong")
        assertEquals(day.plusWeeks(1).atTime(16,45).atZone(ZoneId.of("Asia/Hong_Kong")).toInstant().toEpochMilli(), resolveReminder(next, day.plusWeeks(1), null, "Asia/Hong_Kong"))
    }
    @Test fun fixedInstantsBecomeCalendarOffsetsInTheSeriesZoneAndHaveStableIndependentIds() {
        val source = fixed("2026-10-24T16:30:00Z")
        val template = inheritReminderTemplates(listOf(source), day, "Asia/Hong_Kong").single()
        assertEquals(0, template.dayOffset); assertEquals(LocalTime.of(0,30), template.time)
        val first = occurrenceReminder(template,"first",day,null,"Asia/Hong_Kong")
        val next = occurrenceReminder(template,"next",day.plusWeeks(1),null,"Asia/Hong_Kong")
        assertEquals(source.fixedAt,first.fixedAt); assertEquals(source.fixedAt!!+7*86_400_000L,next.fixedAt)
        assertNotEquals(first.id,next.id); assertNotEquals(source.id, first.id)
        assertEquals(listOf(template),inheritReminderTemplates(listOf(source),day,"Asia/Hong_Kong"))
    }
    @Test fun fixedOffsetsCanBeBeforeOrAfterAndUseSavedZoneDuringTravel() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            val before = inheritReminderTemplates(listOf(fixed("2026-10-24T10:30:00Z")),day,"Asia/Hong_Kong").single()
            val after = inheritReminderTemplates(listOf(fixed("2026-10-27T10:30:00Z")),day,"Asia/Hong_Kong").single()
            assertEquals(-1,before.dayOffset);assertEquals(2,after.dayOffset)
            assertEquals(LocalTime.of(18,30),before.time)
        } finally { TimeZone.setDefault(previous) }
    }
    @Test fun inheritedWallClockUsesDstCalendarDaysRatherThanFixed24HourOffsets() {
        val template=inheritReminderTemplates(listOf(fixed("2026-03-07T07:30:00Z")),LocalDate.of(2026,3,7),"America/New_York").single()
        assertEquals(Instant.parse("2026-03-08T07:30:00Z").toEpochMilli(),occurrenceReminder(template,"next",LocalDate.of(2026,3,8),null,"America/New_York").fixedAt)
        val laterFold=fixed("2026-11-01T06:30:00Z")
        val foldTemplate=inheritReminderTemplates(listOf(laterFold),LocalDate.of(2026,11,1),"America/New_York").single()
        assertEquals(LocalTime.of(1,30),foldTemplate.time)
        assertEquals(Instant.parse("2027-11-07T05:30:00Z").toEpochMilli(),occurrenceReminder(foldTemplate,"future",LocalDate.of(2027,11,7),null,"America/New_York").fixedAt)
    }
    @Test fun unsupportedPrecisionRangesAndDuplicateIdsAreRejectedWithoutTruncating() {
        rejected{inheritReminderTemplates(listOf(fixed("2026-10-24T10:30:01Z")),day,"UTC")}
        rejected{inheritReminderTemplates(listOf(fixed("2026-10-24T10:30:00.001Z")),day,"UTC")}
        rejected{inheritReminderTemplates(listOf(fixed("2000-01-01T10:30:00Z")),day,"UTC")}
        val source=fixed("2026-10-24T10:30:00Z")
        rejected{inheritReminderTemplates(listOf(source,source),day,"UTC")}
        rejected{inheritReminderTemplates((1..33).map{source.copy(id=it.toString())},day,"UTC")}
        rejected{inheritReminderTemplates(listOf(source.copy(offsetMinutes=3)),day,"UTC")}
        rejected{inheritReminderTemplates(listOf(ReminderSpec("r",ReminderKind.RELATIVE_DUE,"UTC",offsetMinutes=-1)),day,"UTC")}
    }
}
