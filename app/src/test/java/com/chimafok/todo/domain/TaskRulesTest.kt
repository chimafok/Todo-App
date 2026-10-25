package com.chimafok.todo.domain

import com.chimafok.todo.data.TaskEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TaskRulesTest {
    private val date = LocalDate.of(2026, 10, 22)
    private val clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneId.of("Asia/Hong_Kong"))
    private fun task(id: String, day: LocalDate?, time: LocalTime? = null) = TaskEntity(
        id, id, "", UNCATEGORIZED_ID, day, time, if (time != null) "Asia/Hong_Kong" else null,
        createdAt = 0, updatedAt = 0,
    )

    @Test fun dateOnlyIsOverdueAfterItsDayAndTimedTaskUsesInstant() {
        assertFalse(isOverdue(date, null, null, clock))
        assertTrue(isOverdue(date.minusDays(1), null, null, clock))
        assertTrue(isOverdue(date, LocalTime.of(17, 59), "Asia/Hong_Kong", clock))
        assertFalse(isOverdue(date, LocalTime.of(18, 0), "Asia/Hong_Kong", clock))
        assertFalse(isOverdue(null, null, null, clock))
    }

    @Test fun agendaKeepsTodayHidesEmptyFutureAndProjectsOverdueWithoutChangingDate() {
        val old = task("old", date.minusDays(2))
        val future = task("future", date.plusDays(2))
        val groups = agendaGroups(listOf(old, future, task("undated", null)), clock)
        assertEquals(listOf(date, date.plusDays(2), null), groups.map { it.date })
        assertEquals("old", groups.first().tasks.single().id)
        assertEquals(date.minusDays(2), groups.first().tasks.single().dueDate)
        assertEquals("undated", groups.last().tasks.single().id)
    }

    @Test fun explicitEmptyDateGetsAnAddAnchor() {
        assertTrue(agendaGroups(emptyList(), clock, date.plusDays(5)).any { it.date == date.plusDays(5) && it.tasks.isEmpty() })
    }

    @Test fun timedItemsSortBeforeDateOnly() {
        val tasks = listOf(task("date", date), task("late", date, LocalTime.of(19, 0)), task("early", date, LocalTime.of(18, 0)))
        assertEquals(listOf("early", "late", "date"), tasks.sortedWith(taskTimeOrder).map { it.id })
    }

    @Test fun completedAndTrashNeverLeakIntoAgenda() {
        val complete = task("complete", date).copy(status = TaskStatus.COMPLETED, completedAt = 1)
        val trash = task("trash", date).copy(deletedAt = 1)
        assertTrue(agendaGroups(listOf(complete, trash), clock).all { it.tasks.isEmpty() })
    }

    @Test fun onlyFinalAssignmentStatusIsCompleted() {
        assertEquals(TaskStatus.DONE_UNSUBMITTED, nextAssignmentStatus(TaskStatus.IN_PROGRESS))
        assertEquals(TaskStatus.COMPLETED, nextAssignmentStatus(TaskStatus.DONE_UNSUBMITTED))
        for (status in TaskStatus.entries.filter { it != TaskStatus.OPEN }) {
            assertEquals(if (status == TaskStatus.COMPLETED) TaskStatus.COMPLETED else TaskStatus.OPEN, convertStatus(status, false))
        }
    }

    @Test fun movingIntoAssignmentRequiresAnExplicitChoice() {
        assertThrows(IllegalArgumentException::class.java) { convertStatus(TaskStatus.OPEN, true) }
        assertEquals(TaskStatus.DONE_UNSUBMITTED, convertStatus(TaskStatus.OPEN, true, TaskStatus.DONE_UNSUBMITTED))
    }

    @Test fun invalidDraftsCannotBeSaved() {
        assertEquals("名稱", validatedTitle("  名稱\n"))
        assertThrows(IllegalArgumentException::class.java) { validatedTitle(" \n ") }
        assertThrows(IllegalArgumentException::class.java) { validateDraft(TaskDraft("id", "test", dueTime = LocalTime.NOON), false) }
        assertThrows(IllegalArgumentException::class.java) { validateDraft(TaskDraft("id", "test", status = TaskStatus.DONE_UNSUBMITTED), false) }
        validateDraft(TaskDraft("id", "test", dueDate = date), false)
    }

    @Test fun completedUndatedItemsUseCompletionTimeAndOriginalDatesStayIntact() {
        val a = task("a", null).copy(status = TaskStatus.COMPLETED, completedAt = 10)
        val b = task("b", null).copy(status = TaskStatus.COMPLETED, completedAt = 20)
        val c = task("c", date.minusDays(2)).copy(status = TaskStatus.COMPLETED, completedAt = 30)
        val groups = completedGroups(listOf(a, b, c))
        assertEquals(date.minusDays(2), groups.first().date)
        assertEquals(listOf("b", "a"), groups.last().tasks.map { it.id })
    }
}
