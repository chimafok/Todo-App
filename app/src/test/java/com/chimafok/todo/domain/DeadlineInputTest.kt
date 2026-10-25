package com.chimafok.todo.domain

import com.chimafok.todo.data.TaskEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DeadlineInputTest {
    private val reference = LocalDate.of(2026, 10, 22)
    @Test fun allDateFormatsAreStrictAndHaveExplicitYearRules() {
        for (text in listOf("20261022", "261022", "1022", "2026-10-22", " 1022 "))
            assertEquals(LocalDate.of(2026, 10, 22), parseDateInput(text, reference))
        assertEquals(LocalDate.of(2099, 12, 31), parseDateInput("991231", reference))
        assertEquals(LocalDate.of(2000, 2, 29), parseDateInput("000229", reference))
        assertEquals(LocalDate.of(2024, 2, 29), parseDateInput("0229", LocalDate.of(2024, 1, 1)))
        assertEquals(LocalDate.of(2027, 1, 1), parseDateInput("0101", LocalDate.of(2027, 1, 1)))
        assertNull(parseDateInput("  ", reference))
    }

    @Test fun impossibleDatesAndUnsupportedLengthsNeverRollIntoAnotherDate() {
        for (text in listOf("230229", "20260230", "20261301", "00000101", "0229", "2026/10/22", "101", "12345", "2026102"))
            assertThrows(text, IllegalArgumentException::class.java) { parseDateInput(text, reference) }
    }

    @Test fun both24HourTimeFormsSupportMidnightAndRejectOverflow() {
        assertEquals(LocalTime.of(9, 5), parseTimeInput("0905"))
        assertEquals(LocalTime.of(9, 5), parseTimeInput("09:05"))
        assertEquals(LocalTime.of(9, 5), parseTimeInput("09-05"))
        assertEquals(LocalTime.MIDNIGHT, parseTimeInput("0000"))
        assertEquals(LocalTime.of(23, 59), parseTimeInput("2359"))
        assertNull(parseTimeInput(""))
        for (text in listOf("2400", "24:00", "24-00", "09--30", "1260", "930", "9:30", "12:00:00", "abcd"))
            assertThrows(text, IllegalArgumentException::class.java) { parseTimeInput(text) }
    }

    @Test fun monthDayRollsOnlyEarlierDatesAndNeverChangesAnExplicitYear() {
        assertEquals(LocalDate.of(2027, 1, 5), parseDateInput("0105", reference))
        assertEquals(reference, parseDateInput("1022", reference))
        assertEquals(LocalDate.of(2026, 10, 23), parseDateInput("1023", reference))
        assertEquals(LocalDate.of(2026, 10, 21), parseDateInput("20261021", reference))
        assertEquals(LocalDate.of(2026, 10, 21), parseDateInput("261021", reference))
        assertEquals(LocalDate.of(2026, 10, 21), parseDateInput("2026-10-21", reference))
        assertEquals(LocalDate.of(2027, 1, 1), parseDateInput("0101", LocalDate.of(2026, 12, 31)))
        assertEquals(LocalDate.of(2024, 2, 29), parseDateInput("0229", LocalDate.of(2023, 11, 1)))
        assertThrows(IllegalArgumentException::class.java) { parseDateInput("0229", LocalDate.of(2024, 3, 1)) }
        assertThrows(IllegalArgumentException::class.java) { parseDateInput("0101", LocalDate.of(9999, 12, 31)) }
    }

    @Test fun timedDeadlineKeepsItsInstantAcrossDeviceZones() {
        val date = LocalDate.of(2026, 10, 22)
        val time = LocalTime.of(20, 0)
        val deadline = deadlineInstant(date, time, "Asia/Hong_Kong")
        assertEquals(Instant.parse("2026-10-22T12:00:00Z"), deadline)
        for (zone in listOf("Asia/Hong_Kong", "America/Los_Angeles", "Pacific/Honolulu", "Asia/Tokyo")) {
            assertFalse(isOverdue(date, time, "Asia/Hong_Kong", Clock.fixed(deadline, ZoneId.of(zone))))
            assertTrue(isOverdue(date, time, "Asia/Hong_Kong", Clock.fixed(deadline.plusSeconds(1), ZoneId.of(zone))))
        }
    }

    @Test fun yearlessSameDayPastTimeRollsButExplicitYearsAndDateOnlyNeverDo() {
        val now = Instant.parse("2026-10-22T10:00:00Z")
        val hk = "Asia/Hong_Kong"
        assertEquals(LocalDate.of(2027, 10, 22), resolveDeadlineDate("1022", LocalTime.of(17, 59), hk, now))
        assertEquals(reference, resolveDeadlineDate("1022", LocalTime.of(18, 0), hk, now))
        assertEquals(reference, resolveDeadlineDate("1022", LocalTime.of(18, 1), hk, now))
        assertEquals(reference, resolveDeadlineDate("1022", null, hk, now))
        assertEquals(LocalDate.of(2027, 10, 21), resolveDeadlineDate("1021", LocalTime.of(23, 59), hk, now))
        assertEquals(LocalDate.of(2026, 10, 23), resolveDeadlineDate("1023", LocalTime.MIDNIGHT, hk, now))
        for (text in listOf("2026-10-22", "20261022", "261022"))
            assertEquals(reference, resolveDeadlineDate(text, LocalTime.MIDNIGHT, hk, now))
        assertEquals(LocalDate.of(2027, 10, 22), resolveDeadlineDate("1022", LocalTime.of(18, 0), hk, now.plusSeconds(1)))
    }

    @Test fun sameDayResolutionUsesSavedZoneAndStrictNextYearIncludingDstOverlap() {
        val now = Instant.parse("2026-10-22T10:00:00Z")
        assertEquals(reference, resolveDeadlineDate("1022", LocalTime.of(9, 0), "America/Los_Angeles", now))
        assertEquals(LocalDate.of(2027, 10, 22), resolveDeadlineDate("1022", LocalTime.of(9, 0), "Asia/Hong_Kong", now))
        // First 01:30 has already passed when the fall-back clock reads 01:00 again.
        assertEquals(LocalDate.of(2027, 11, 1), resolveDeadlineDate("1101", LocalTime.of(1, 30), "America/New_York", Instant.parse("2026-11-01T06:00:00Z")))
        assertThrows(IllegalArgumentException::class.java) {
            resolveDeadlineDate("0229", LocalTime.MIDNIGHT, "UTC", Instant.parse("2024-02-29T12:00:00Z"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveDeadlineDate("1231", LocalTime.MIDNIGHT, "UTC", Instant.parse("9999-12-31T12:00:00Z"))
        }
    }

    @Test fun dstGapIsRejectedAndOverlapUsesVisibleEarlierOffset() {
        assertThrows(IllegalArgumentException::class.java) {
            deadlineInstant(LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), "America/New_York")
        }
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"),
            deadlineInstant(LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), "America/New_York"))
        assertTrue(deadlineZoneLabel(LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), "America/New_York").contains("UTC-04:00"))
    }

    private fun task(id: String, date: String, time: String, zone: String) = TaskEntity(id, id, "", UNCATEGORIZED_ID,
        LocalDate.parse(date), LocalTime.parse(time), zone, createdAt = 0, updatedAt = 0)

    @Test fun crossingDateLineNeverHidesAPendingTaskOrDuplicatesAnOverdueTask() {
        val east = Clock.fixed(Instant.parse("2026-10-22T08:00:00Z"), ZoneId.of("Asia/Tokyo"))
        val originalYesterday = task("future-in-tokyo", "2026-10-21", "23:00", "Pacific/Honolulu")
        assertEquals(listOf(originalYesterday), agendaGroups(listOf(originalYesterday), east).first().tasks)
        val west = Clock.fixed(Instant.parse("2026-10-23T01:00:00Z"), ZoneId.of("America/Los_Angeles"))
        val originalTomorrow = task("overdue-in-la", "2026-10-23", "01:00", "Asia/Tokyo")
        val groups = agendaGroups(listOf(originalTomorrow), west)
        assertEquals(1, groups.flatMap { it.tasks }.count { it.id == originalTomorrow.id })
        assertEquals(listOf(originalTomorrow), groups.first().tasks)
        assertEquals(LocalDate.of(2026, 10, 23), originalTomorrow.dueDate)
    }

    @Test fun mixedZonesSortByActualInstantAndDateOnlyDoesNotShift() {
        val first = task("hong-kong", "2026-10-22", "20:00", "Asia/Hong_Kong")
        val second = task("new-york", "2026-10-22", "09:00", "America/New_York")
        val dateOnly = first.copy(id = "date-only", dueTime = null)
        assertEquals(listOf(first, second, dateOnly), listOf(second, dateOnly, first).sortedWith(taskTimeOrder))
        assertEquals(dateOnly.dueDate, dateOnly.agendaDate(ZoneId.of("America/Los_Angeles")))
    }

    @Test fun jumpingToPastDateDoesNotReuseAnItemKeyInTwoGroups() {
        val clock = Clock.fixed(Instant.parse("2026-10-22T08:00:00Z"), ZoneId.of("Asia/Hong_Kong"))
        val past = task("past", "2026-10-21", "20:00", "Asia/Hong_Kong")
        val groups = agendaGroups(listOf(past), clock, past.dueDate)
        assertEquals(1, groups.flatMap { it.tasks }.count { it.id == past.id })
        assertEquals(listOf(past), groups.single { it.date == past.dueDate }.tasks)
    }
}
