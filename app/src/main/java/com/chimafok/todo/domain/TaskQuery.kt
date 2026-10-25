package com.chimafok.todo.domain

import com.chimafok.todo.data.*
import java.time.Clock
import java.time.Duration

enum class TaskScope { AGENDA, UNSCHEDULED, COMPLETED, TRASH }

data class TaskFilter(
    val query: String = "", val categoryIds: Set<String> = emptySet(),
    val tagIds: Set<String> = emptySet(), val matchAllTags: Boolean = true,
) {
    val active get() = query.isNotBlank() || categoryIds.isNotEmpty() || tagIds.isNotEmpty()
}

fun scopedTasks(tasks: List<TaskEntity>, scope: TaskScope, clock: Clock): List<TaskEntity> = tasks.filter {
    when (scope) {
        TaskScope.AGENDA -> it.deletedAt == null && it.status != TaskStatus.COMPLETED
        TaskScope.UNSCHEDULED -> it.deletedAt == null && it.status != TaskStatus.COMPLETED && it.dueDate == null
        TaskScope.COMPLETED -> it.deletedAt == null && it.status == TaskStatus.COMPLETED
        TaskScope.TRASH -> it.deletedAt != null && !retentionExpired(it.deletedAt, TRASH_DAYS, clock.millis())
    }
}

/** Scope is applied separately so a retained completion row can finish its feedback. */
fun filterTasks(tasks: List<TaskEntity>, categories: List<CategoryEntity>, tags: List<TagEntity>,
    links: List<TaskTagEntity>, filter: TaskFilter): List<TaskEntity> {
    return TaskSearchIndex(categories, tags, links).filter(tasks, filter)
}
