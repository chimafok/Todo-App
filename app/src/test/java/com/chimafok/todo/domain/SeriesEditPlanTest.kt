package com.chimafok.todo.domain

import com.chimafok.todo.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SeriesEditPlanTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC)
    private fun record(sequence: Int, date: String, status: TaskStatus = TaskStatus.OPEN, deleted: Boolean = false, excluded: Boolean = false) =
        SeriesEditRecord(OccurrenceEntity("t$sequence", "s", sequence, "v1", excludedByRule = excluded),
            TaskEntity("t$sequence", "任務", "", UNCATEGORIZED_ID, LocalDate.parse(date), zoneId = "UTC", status = status,
                deletedAt = if (deleted) 1 else null, createdAt = 0, updatedAt = 0))
    private fun rule(anchor: String, frequency: RecurrenceFrequency = RecurrenceFrequency.DAILY, effective: Int = 1) =
        SeriesRuleEntity("v1", "s", 1, effective, LocalDate.parse(anchor), frequency, 1, 0, null, "UTC", null, null, 0)
    private fun draft(date: String, frequency: RecurrenceFrequency = RecurrenceFrequency.DAILY, count: Int? = null) =
        TaskDraft("t1", "名稱", dueDate = LocalDate.parse(date), recurrence = RecurrenceRule(LocalDate.parse(date), frequency, "UTC", maxCount = count))

    @Test fun monthlyClampKeepsOriginalAnchorWhenOnlyContentOrEndChanges() {
        val current = record(2, "2027-02-28")
        val plan = planSeriesEdit(draft("2027-02-28", RecurrenceFrequency.MONTHLY, 6), current,
            rule("2027-01-31", RecurrenceFrequency.MONTHLY), listOf(current, record(3, "2027-03-31")), clock)
        assertEquals(LocalDate.of(2027, 1, 31), plan.anchor)
        assertEquals(1, plan.effectiveFrom)
        assertEquals(LocalDate.of(2027, 3, 31), plan.changes.single().date)
    }

    @Test fun newFrequencyUsesGlobalSequenceWithoutResettingCount() {
        val current = record(3, "2026-10-22")
        val plan = planSeriesEdit(draft("2026-10-22", RecurrenceFrequency.WEEKLY, 4), current, rule("2026-10-20"),
            listOf(current, record(4, "2026-10-23"), record(5, "2026-10-24")), clock)
        assertEquals(3, plan.effectiveFrom)
        assertEquals(LocalDate.of(2026, 10, 29), plan.changes[0].date)
        assertNull(plan.changes[1].date)
        assertEquals(1, plan.cancelledCount)
    }

    @Test fun completedPastAndDeletedRowsAreProtectedEvenWithHigherSequence() {
        val current = record(1, "2026-10-23")
        val plan = planSeriesEdit(draft("2026-10-23"), current, rule("2026-10-23"), listOf(current,
            record(2, "2026-10-24", TaskStatus.COMPLETED), record(3, "2026-10-21"),
            record(4, "2026-10-26", deleted = true), record(5, "2026-10-27")), clock)
        assertEquals(listOf("t5"), plan.changes.map { it.record.task.id })
    }

    @Test fun cancelledRowsCanReactivateButUnchangedCancellationsDoNotChurn() {
        val current = record(1, "2026-10-23")
        val records = listOf(current, record(2, "2026-10-24", excluded = true), record(3, "2026-10-25", excluded = true))
        val plan = planSeriesEdit(draft("2026-10-23", count = 2), current, rule("2026-10-23"), records, clock)
        assertEquals(1, plan.restoredCount)
        assertEquals(listOf("t2"), plan.changes.map { it.record.task.id })
    }

    @Test fun historicalBoundaryUsesEachRecordsSavedZone() {
        val local = Clock.fixed(Instant.parse("2026-10-23T01:00:00Z"), ZoneOffset.UTC)
        val record = record(1, "2026-10-22")
        assertFalse(canEditSeriesFrom(record, local))
        assertTrue(canEditSeriesFrom(record.copy(task = record.task.copy(zoneId = "America/Los_Angeles")), local))
        assertFalse(canEditSeriesFrom(record.copy(task = record.task.copy(zoneId = "Asia/Hong_Kong")), local))
    }

    @Test fun invalidSourceAndCountCannotReplanHistory() {
        val current = record(3, "2026-10-23")
        for (source in listOf(current.copy(task = current.task.copy(status = TaskStatus.COMPLETED)),
            current.copy(occurrence = current.occurrence.copy(excludedByRule = true)), record(3, "2026-10-21"))) {
            assertThrows(IllegalArgumentException::class.java) { planSeriesEdit(draft("2026-10-23"), source, rule("2026-10-20"), listOf(source), clock) }
        }
        assertThrows(IllegalArgumentException::class.java) { planSeriesEdit(draft("2026-10-23", count = 2), current, rule("2026-10-20"), listOf(current), clock) }
    }

    @Test fun weekdayRuleUsesFirstEligibleDateAndAnInclusiveEndDate() {
        val current = record(1, "2026-10-23")
        val schedule = RecurrenceRule(LocalDate.of(2026, 10, 23), RecurrenceFrequency.WEEKDAYS, "UTC",
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), endDate = LocalDate.of(2026, 10, 28))
        val plan = planSeriesEdit(TaskDraft("t1", "指定星期", dueDate = schedule.dateAt(1), recurrence = schedule), current,
            rule("2026-10-23"), listOf(current, record(2, "2026-10-24"), record(3, "2026-10-25")), clock)
        assertEquals(LocalDate.of(2026, 10, 26), schedule.dateAt(1))
        assertEquals(LocalDate.of(2026, 10, 28), plan.changes[0].date)
        assertNull(plan.changes[1].date)
    }

    @Test fun homeProjectionExcludesCancelledRowsWithoutLosingTheirData() {
        val a = record(1, "2026-10-23", excluded = true)
        val b = record(2, "2026-10-24")
        assertEquals(setOf("t2"), recurringHomeTaskIds(listOf(a.task, b.task), listOf(a, b).map {
            RecurringTaskRef(it.task.id, "s", it.occurrence.sequence, it.occurrence.excludedByRule)
        }, clock))
    }
}
