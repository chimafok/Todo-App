package com.chimafok.todo.domain

import com.chimafok.todo.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ListScalingTest {
    private val date = LocalDate.of(2026, 10, 24)
    private val clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC)
    private fun task(id: String, day: LocalDate? = date) = TaskEntity(id, id, "", UNCATEGORIZED_ID, day, createdAt = 1, updatedAt = 1)

    @Test fun jumpToPastRemovesOnlyThatLocalDateFromTodayWithoutDuplicates() {
        val old = task("old", date.minusDays(2))
        val yesterday = task("yesterday", date.minusDays(1))
        val future = task("future", date.plusDays(1))
        val undated = task("undated", null)
        val groups = agendaGroups(listOf(old, yesterday, future, undated), clock, date.minusDays(2))
        assertEquals(listOf(date, date.minusDays(2), date.plusDays(1), null), groups.map { it.date })
        assertEquals(listOf(yesterday), groups.first().tasks)
        assertEquals(listOf(old), groups[1].tasks)
        assertEquals(4, groups.flatMap { it.tasks }.map { it.id }.toSet().size)
        assertEquals(date.minusDays(2), old.dueDate)
    }

    @Test fun localDisplayDateAndDeadlineInstantDetermineGroupingAcrossZonesAndMidnight() {
        val tokyo = task("tokyo", date.plusDays(1)).copy(dueTime = LocalTime.of(1, 0), zoneId = "Asia/Tokyo")
        val newYork = task("ny", date).copy(dueTime = LocalTime.of(23, 0), zoneId = "America/New_York")
        val onlyDate = task("date", date)
        val tasks = listOf(newYork, onlyDate, tokyo)
        val before = agendaGroups(tasks, clock)
        assertEquals(listOf("tokyo", "date"), before.first().tasks.map { it.id })
        assertEquals(listOf(newYork), before[1].tasks)
        val after = agendaGroups(tasks, Clock.offset(clock, Duration.ofHours(13)))
        assertEquals(date.plusDays(1), after.first().date)
        assertEquals(listOf("tokyo", "date", "ny"), after.first().tasks.map { it.id })
        assertEquals(date.plusDays(1), tokyo.dueDate)
    }

    @Test fun equalTimesKeepCreationThenIdOrderAndUnscheduledKeepsNewestFirst() {
        val a = task("a").copy(dueTime = LocalTime.of(13, 0), zoneId = "UTC")
        val b = a.copy(id = "b")
        val earlier = a.copy(id = "earlier", createdAt = 0)
        val undated = task("undated", null)
        val newUndated = undated.copy(id = "new", createdAt = 2)
        val groups = agendaGroups(listOf(b, undated, a, newUndated, earlier, task("onlyDate")), clock)
        assertEquals(listOf("earlier", "a", "b", "onlyDate"), groups.first().tasks.map { it.id })
        assertEquals(listOf("new", "undated"), groups.last().tasks.map { it.id })
    }

    @Test fun completedKeepsOriginalDateStableTiesAndExcludesTrash() {
        val a = task("a").copy(status = TaskStatus.COMPLETED, completedAt = 5)
        val b = a.copy(id = "b", dueDate = null)
        val c = b.copy(id = "c")
        val groups = completedGroups(listOf(c, a.copy(id = "trash", deletedAt = 1), a, b, task("open")))
        assertEquals(listOf(date, null), groups.map { it.date })
        assertEquals(listOf("c", "b"), groups.last().tasks.map { it.id })
        assertEquals(listOf(a), groups.first().tasks)
    }

    @Test fun inputTraversalIsBoundedWhenEveryTaskHasItsOwnDate() {
        val source = List(2_000) { task("$it", date.plusDays(it.toLong())) }
        val counted = CountingList(source)
        var zoneReads = 0
        val countedClock = object : Clock() {
            override fun instant() = clock.instant()
            override fun getZone(): ZoneId { zoneReads++; return clock.zone }
            override fun withZone(zone: ZoneId): Clock = clock.withZone(zone)
        }
        assertEquals(2_001, agendaGroups(counted, countedClock).size)
        assertTrue("Agenda repeatedly traversed its input: ${counted.reads}", counted.reads <= source.size * 2)
        assertTrue("Agenda recalculated every date per group: $zoneReads", zoneReads <= source.size * 3)
        val done = CountingList(source.map { it.copy(status = TaskStatus.COMPLETED, completedAt = 1) })
        assertEquals(2_001, completedGroups(done).size)
        assertTrue("Completed repeatedly traversed its input: ${done.reads}", done.reads <= source.size * 2)
    }

    @Test fun indexedTagsKeepConfiguredOrderWithoutRescanningLinksPerRow() {
        val tags = listOf(TagEntity("b", "第二", "second", 0, 1, 1), TagEntity("a", "第一", "first", 1, 1, 1))
        val links = CountingList(listOf(TaskTagEntity("one", "a"), TaskTagEntity("one", "b"), TaskTagEntity("other", "a")))
        val index = TaskSearchIndex(emptyList(), tags, links)
        val reads = links.reads
        repeat(1_000) { assertEquals(listOf("第二", "第一"), index.tagNames("one")) }
        assertEquals(reads, links.reads)
        assertTrue(index.tagNames("missing").isEmpty())
        val reordered = TaskSearchIndex(emptyList(), tags.reversed(), links)
        assertEquals(listOf("第一", "第二"), reordered.tagNames("one"))
    }

    @Test fun rebuiltIndexReflectsRenamesRemovalsAndLinkChanges() {
        val tasks = listOf(task("one"), task("two"))
        val tag = TagEntity("tag", "舊標籤", "tag", 0, 1, 1)
        val category = CategoryEntity(UNCATEGORIZED_ID, "舊分類", sortOrder = 0, createdAt = 1, updatedAt = 1)
        val old = TaskSearchIndex(listOf(category), listOf(tag), listOf(TaskTagEntity("one", "tag")))
        val fresh = TaskSearchIndex(listOf(category.copy(name = "新分類")), listOf(tag.copy(name = "新標籤")), listOf(TaskTagEntity("two", "tag")))
        assertEquals(listOf("one"), old.filter(tasks, TaskFilter("舊標籤")).map { it.id })
        assertTrue(fresh.filter(tasks, TaskFilter("舊標籤")).isEmpty())
        assertEquals(listOf("two"), fresh.filter(tasks, TaskFilter("新標籤")).map { it.id })
        assertEquals(tasks, fresh.filter(tasks, TaskFilter("新分類")))
        assertTrue(TaskSearchIndex(emptyList(), emptyList(), emptyList()).filter(tasks, TaskFilter("新分類")).isEmpty())
    }

    private class CountingList<T>(private val items: List<T>) : AbstractList<T>() {
        var reads = 0
        override val size get() = items.size
        override fun get(index: Int): T { reads++; return items[index] }
    }
}
