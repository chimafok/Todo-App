package com.chimafok.todo.domain

import com.chimafok.todo.data.TaskEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RecurrenceRulesTest {
    private val anchor = LocalDate.of(2026, 1, 31)
    private val now = Instant.parse("2026-02-04T12:00:00Z")
    private fun rule(frequency: RecurrenceFrequency, start: LocalDate = anchor) = RecurrenceRule(start, frequency, "UTC")

    @Test fun dailyWeeklyAndEveryNDaysKeepTheirOriginalAnchor() {
        assertEquals(LocalDate.of(2026, 2, 1), rule(RecurrenceFrequency.DAILY).dateAt(2))
        assertEquals(LocalDate.of(2026, 2, 14), rule(RecurrenceFrequency.WEEKLY).dateAt(3))
        val every = rule(RecurrenceFrequency.EVERY_N_DAYS).copy(intervalDays = 3)
        assertEquals(LocalDate.of(2026, 2, 6), every.dateAt(3))
        assertEquals(anchor, every.dateAt(1))
    }

    @Test fun monthlyFallsBackToMonthEndThenRestoresTheOriginalDay() {
        val monthly = rule(RecurrenceFrequency.MONTHLY)
        assertEquals(LocalDate.of(2026, 2, 28), monthly.dateAt(2))
        assertEquals(LocalDate.of(2026, 3, 31), monthly.dateAt(3))
        assertEquals(LocalDate.of(2026, 4, 30), monthly.dateAt(4))
        val leap = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2024, 1, 31))
        assertEquals(LocalDate.of(2024, 2, 29), leap.dateAt(2))
        assertEquals(LocalDate.of(2024, 3, 31), leap.dateAt(3))
        assertEquals(LocalDate.of(2025, 2, 28), leap.dateAt(14))
    }

    @Test fun selectedWeekdaysStartAtFirstEligibleDateAndRepeatInChronologicalOrder() {
        val weekdays = RecurrenceRule(LocalDate.of(2026, 10, 22), RecurrenceFrequency.WEEKDAYS, "UTC",
            weekdays = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY))
        assertEquals(listOf("2026-10-25", "2026-10-26", "2026-11-01", "2026-11-02"), (1..4).map { weekdays.dateAt(it).toString() })
        val sameDay = weekdays.copy(weekdays = setOf(DayOfWeek.THURSDAY))
        assertEquals(LocalDate.of(2026, 10, 22), sameDay.dateAt(1))
        val everyDay = weekdays.copy(weekdays = DayOfWeek.entries.toSet())
        assertEquals(LocalDate.of(2026, 10, 29), everyDay.dateAt(8))
    }

    @Test fun endDateIsInclusiveAndCountIncludesFirstConsumedSlot() {
        val ending = rule(RecurrenceFrequency.DAILY).copy(endDate = LocalDate.of(2026, 2, 2))
        assertEquals(LocalDate.of(2026, 2, 2), ending.dateAt(3))
        assertNull(ending.dateAt(4))
        val counted = rule(RecurrenceFrequency.DAILY).copy(maxCount = 1)
        assertEquals(anchor, counted.dateAt(1))
        assertNull(counted.dateAt(2))
        val complete = planOccurrences("series", ending, 0, now)
        assertEquals(3, complete.cursor)
        assertTrue(complete.ended)
        assertFalse(complete.hasMoreDue)
    }

    @Test fun malformedRulesAndImpossibleEndConditionsAreRejected() {
        val daily = rule(RecurrenceFrequency.DAILY)
        assertThrows(IllegalArgumentException::class.java) { daily.copy(maxCount = 0) }
        assertThrows(IllegalArgumentException::class.java) { daily.copy(endDate = anchor.minusDays(1)) }
        assertThrows(IllegalArgumentException::class.java) { daily.copy(endDate = anchor, maxCount = 2) }
        assertThrows(IllegalArgumentException::class.java) { daily.copy(intervalDays = 2) }
        assertThrows(IllegalArgumentException::class.java) { daily.copy(frequency = RecurrenceFrequency.EVERY_N_DAYS, intervalDays = 3651) }
        assertThrows(IllegalArgumentException::class.java) { daily.copy(frequency = RecurrenceFrequency.WEEKDAYS) }
        assertThrows(IllegalArgumentException::class.java) { daily.copy(weekdays = setOf(DayOfWeek.MONDAY)) }
        assertThrows(IllegalArgumentException::class.java) { daily.copy(frequency = RecurrenceFrequency.WEEKDAYS, weekdays = setOf(DayOfWeek.MONDAY), endDate = anchor) }
        assertThrows(DateTimeException::class.java) { daily.copy(zoneId = "unknown/zone") }
        assertThrows(IllegalArgumentException::class.java) { daily.dateAt(0) }
    }

    @Test fun offlineCatchUpIsBoundedAndCursorProducesExactlyTheSameLedgerAsOneBatch() {
        val daily = rule(RecurrenceFrequency.DAILY)
        val full = planOccurrences("series", daily, 0, now)
        assertEquals(5, full.slots.size)
        val first = planOccurrences("series", daily, 0, now, limit = 2)
        assertTrue(first.hasMoreDue)
        val second = planOccurrences("series", daily, first.cursor, now, limit = 2)
        val third = planOccurrences("series", daily, second.cursor, now, limit = 2)
        assertEquals(full.slots, first.slots + second.slots + third.slots)
        assertTrue(planOccurrences("series", daily, third.cursor, now).slots.isEmpty())
        assertFalse(third.hasMoreDue)
        assertFalse(third.ended)
    }

    @Test fun futurePreparationIsOneRowAndRetryingCompletionOrCreationIsIdempotent() {
        val future = rule(RecurrenceFrequency.DAILY, LocalDate.of(2026, 2, 10))
        assertTrue(planOccurrences("series", future, 0, now).slots.isEmpty())
        val created = planOccurrences("series", future, 0, now, advanceAfterSequence = 0)
        assertEquals(listOf(1), created.slots.map { it.sequence })
        assertTrue(planOccurrences("series", future, 1, now, advanceAfterSequence = 0).slots.isEmpty())
        val completed = planOccurrences("series", future, 1, now, advanceAfterSequence = 1)
        assertEquals(listOf(2), completed.slots.map { it.sequence })
        assertTrue(planOccurrences("series", future, 2, now, advanceAfterSequence = 1).slots.isEmpty())
        assertEquals(occurrenceId("series", 2), completed.slots.single().taskId)
        assertNotEquals(occurrenceId("series", 2), occurrenceId("another-series", 2))
        assertThrows(IllegalArgumentException::class.java) { planOccurrences("series", future, 0, now, advanceAfterSequence = 1) }
        assertThrows(IllegalArgumentException::class.java) { planOccurrences("series", future, 0, now, limit = 0) }
    }

    @Test fun generationUsesSeriesLocalMidnightIncludingDstAndDateLine() {
        val instant = Instant.parse("2026-10-22T16:00:00Z")
        val hk = rule(RecurrenceFrequency.DAILY, LocalDate.of(2026, 10, 23)).copy(zoneId = "Asia/Hong_Kong")
        assertEquals(1, planOccurrences("s", hk, 0, instant).slots.size)
        assertTrue(planOccurrences("s", hk.copy(zoneId = "America/Los_Angeles"), 0, instant).slots.isEmpty())
        val dst = rule(RecurrenceFrequency.DAILY, LocalDate.of(2026, 3, 7)).copy(zoneId = "America/New_York")
        val batch = planOccurrences("s", dst, 0, Instant.parse("2026-03-09T04:00:00Z"))
        assertEquals(listOf("2026-03-07", "2026-03-08", "2026-03-09"), batch.slots.map { it.date.toString() })
    }

    @Test fun extremeDatesTerminateWithoutWrappingOrReusingSequenceNumbers() {
        val last = rule(RecurrenceFrequency.DAILY, LocalDate.of(9999, 12, 31))
        assertNull(last.dateAt(2))
        val batch = planOccurrences("s", last, 0, Instant.parse("9999-12-31T12:00:00Z"))
        assertEquals(1, batch.cursor)
        assertTrue(batch.ended)
        assertNull(rule(RecurrenceFrequency.MONTHLY).dateAt(Int.MAX_VALUE))
    }

    @Test fun homeProjectionSelectsLatestOverdueThenEarliestPendingWithoutChangingHistory() {
        val clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC)
        fun task(id: String, day: Int) = TaskEntity(id, id, "", UNCATEGORIZED_ID, LocalDate.of(2026, 10, day),
            createdAt = day.toLong(), updatedAt = day.toLong())
        val tasks = listOf(task("old", 20), task("recent", 21), task("today", 22), task("future", 23),
            task("done", 22).copy(status = TaskStatus.COMPLETED, completedAt = 1), task("trash", 21).copy(deletedAt = 1))
        val refs = tasks.mapIndexed { index, task -> RecurringTaskRef(task.id, "s", index + 1) }
        assertEquals(setOf("recent"), recurringHomeTaskIds(tasks, refs, clock))
        assertEquals(6, tasks.size)
        assertEquals(TaskStatus.OPEN, tasks.first().status)
        val noOverdue = tasks.filter { it.id !in setOf("old", "recent") }
        assertEquals(setOf("today"), recurringHomeTaskIds(noOverdue, refs, clock))
        val moreSeries = refs + RecurringTaskRef("other", "other-series", 1)
        assertEquals(setOf("recent", "other"), recurringHomeTaskIds(tasks + task("other", 23), moreSeries, clock))
        assertTrue(recurringHomeTaskIds(tasks.filter { it.status == TaskStatus.COMPLETED || it.deletedAt != null }, refs, clock).isEmpty())
    }
}
