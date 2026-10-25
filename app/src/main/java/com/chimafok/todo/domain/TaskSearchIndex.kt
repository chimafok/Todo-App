package com.chimafok.todo.domain

import com.chimafok.todo.data.*

/** Rebuild when categories, tags or links change; reuse while typing or animating a row. */
class TaskSearchIndex(categories: List<CategoryEntity>, tags: List<TagEntity>, links: List<TaskTagEntity>) {
    val categoryNames = categories.associate { it.id to it.name }
    private val tagsByTask = links.groupBy { it.taskId }.mapValues { (_, values) -> values.map { it.tagId }.toSet() }
    private val namesByTask: Map<String, List<String>>

    init {
        val byId = tags.associateBy { it.id }
        val order = tags.mapIndexed { index, tag -> tag.id to index }.toMap()
        namesByTask = tagsByTask.mapValues { (_, ids) ->
            ids.mapNotNull { byId[it] }.sortedBy { order[it.id] }.map { it.name }
        }
    }

    fun tagNames(taskId: String): List<String> = namesByTask[taskId].orEmpty()

    fun filter(tasks: List<TaskEntity>, filter: TaskFilter): List<TaskEntity> {
        if (!filter.active) return tasks
        val query = filter.query.trim()
        return tasks.filter { task ->
            val taskTags = tagsByTask[task.id].orEmpty()
            (filter.categoryIds.isEmpty() || task.categoryId in filter.categoryIds) &&
                (filter.tagIds.isEmpty() || if (filter.matchAllTags) taskTags.containsAll(filter.tagIds) else taskTags.any { it in filter.tagIds }) &&
                (query.isEmpty() || task.title.contains(query, ignoreCase = true) || task.notes.contains(query, ignoreCase = true) ||
                    categoryNames[task.categoryId].orEmpty().contains(query, ignoreCase = true) ||
                    tagNames(task.id).any { it.contains(query, ignoreCase = true) })
        }
    }
}
