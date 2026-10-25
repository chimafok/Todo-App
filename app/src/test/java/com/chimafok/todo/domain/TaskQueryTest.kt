package com.chimafok.todo.domain

import com.chimafok.todo.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TaskQueryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC)
    private fun task(id: String, category: String = "c1") = TaskEntity(id, id, "", category, createdAt = 1, updatedAt = 1)
    private val categories = listOf(CategoryEntity("c1", "School", sortOrder = 0, createdAt = 1, updatedAt = 1),
        CategoryEntity("c2", "生活", sortOrder = 1, createdAt = 1, updatedAt = 1))
    private val tags = listOf(TagEntity("a", "Priority", "priority", 0, 1, 1), TagEntity("b", "閱讀", "閱讀", 1, 1, 1))

    @Test fun scopesNeverMixCompletedTrashAndPendingAndExpireAtThirtyDays() {
        val pending = task("pending")
        val dated = task("dated").copy(dueDate = LocalDate.of(2026, 10, 23))
        val done = task("done").copy(status = TaskStatus.COMPLETED, completedAt = 1)
        val trash = task("trash").copy(deletedAt = clock.millis() - 1)
        val expired = task("expired").copy(deletedAt = clock.millis() - Duration.ofDays(30).toMillis())
        val all = listOf(pending, dated, done, trash, expired)
        assertEquals(listOf(pending, dated), scopedTasks(all, TaskScope.AGENDA, clock))
        assertEquals(listOf(pending), scopedTasks(all, TaskScope.UNSCHEDULED, clock))
        assertEquals(listOf(done), scopedTasks(all, TaskScope.COMPLETED, clock))
        assertEquals(listOf(trash), scopedTasks(all, TaskScope.TRASH, clock))
    }

    @Test fun textSearchUsesTitleNotesCategoryAndTagsCaseInsensitively() {
        val tasks = listOf(task("one").copy(title = "Report"), task("two", "c2").copy(notes = "Read REPORT"), task("three", "c2"))
        val links = listOf(TaskTagEntity("three", "a"))
        fun ids(query: String) = filterTasks(tasks, categories, tags, links, TaskFilter(query)).map { it.id }
        assertEquals(listOf("one", "two"), ids(" report "))
        assertEquals(listOf("one"), ids("school"))
        assertEquals(listOf("three"), ids("PRIORITY"))
        assertEquals(listOf("one", "two", "three"), ids("  "))
        assertTrue(ids("missing").isEmpty())
    }

    @Test fun tagAndOrAndCategoryOrCombineWithTextUsingAnd() {
        val tasks = listOf(task("a"), task("b", "c2"), task("c").copy(notes = "target"))
        val links = listOf(TaskTagEntity("a", "a"), TaskTagEntity("a", "b"), TaskTagEntity("b", "a"), TaskTagEntity("c", "b"))
        fun ids(filter: TaskFilter) = filterTasks(tasks, categories, tags, links, filter).map { it.id }
        val both = TaskFilter(tagIds = setOf("a", "b"))
        assertEquals(listOf("a"), ids(both))
        assertEquals(listOf("a", "b", "c"), ids(both.copy(matchAllTags = false)))
        assertEquals(listOf("a", "c"), ids(both.copy(categoryIds = setOf("c1"), matchAllTags = false)))
        assertEquals(listOf("a", "b", "c"), ids(TaskFilter(categoryIds = setOf("c1", "c2"))))
        assertEquals(listOf("c"), ids(both.copy(query = "target", categoryIds = setOf("c1"), matchAllTags = false)))
        assertTrue(ids(both.copy(tagIds = setOf("missing"))).isEmpty())
    }
}
