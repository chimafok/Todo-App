package com.chimafok.todo.domain

import com.chimafok.todo.data.TaskEntity
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

data class TaskGroup(val date: LocalDate?, val tasks: List<TaskEntity>)

val taskTimeOrder: Comparator<TaskEntity> = compareBy<TaskEntity> { it.dueTime == null }
    .thenBy { it.dueInstant() }.thenBy { it.createdAt }.thenBy { it.id }

// atZone also preserves the previous interpretation of any legacy DST-gap value.
fun TaskEntity.dueInstant() = dueTime?.let { requireNotNull(dueDate).atTime(it).atZone(ZoneId.of(requireNotNull(zoneId))).toInstant() }
fun TaskEntity.agendaDate(zone: ZoneId): LocalDate? = dueInstant()?.atZone(zone)?.toLocalDate() ?: dueDate

private data class AgendaItem(val task: TaskEntity, val date: LocalDate?, val overdue: Boolean)

fun agendaGroups(tasks: List<TaskEntity>, clock: Clock, jumpDate: LocalDate? = null): List<TaskGroup> {
    val today = LocalDate.now(clock)
    val todayItems = mutableListOf<AgendaItem>()
    val dated = linkedMapOf<LocalDate, MutableList<TaskEntity>>()
    val undated = mutableListOf<TaskEntity>()
    // Partition once. Previously each date rescanned all tasks (10,000 dates × 10,000 tasks).
    for (task in tasks) {
        if (task.deletedAt != null || task.status == TaskStatus.COMPLETED) continue
        val date = task.agendaDate(clock.zone)
        val overdue = isOverdue(task.dueDate, task.dueTime, task.zoneId, clock)
        when {
            (date == today || overdue) && !(jumpDate != null && jumpDate < today && date == jumpDate) ->
                todayItems += AgendaItem(task, date, overdue)
            date != null -> dated.getOrPut(date) { mutableListOf() }.add(task)
            else -> undated += task
        }
    }
    val todayTasks = todayItems.sortedWith(compareBy<AgendaItem> { !it.overdue }.thenBy { it.date }
        .thenComparator { a, b -> taskTimeOrder.compare(a.task, b.task) }).map { it.task }
    val dates = dated.keys.filter { it > today }.toMutableSet()
    jumpDate?.takeIf { it != today }?.let(dates::add)
    return listOf(TaskGroup(today, todayTasks)) + dates.sorted().map { date ->
        TaskGroup(date, dated[date].orEmpty().sortedWith(taskTimeOrder))
    } + TaskGroup(null, undated.sortedByDescending { it.createdAt })
}

fun completedGroups(tasks: List<TaskEntity>): List<TaskGroup> {
    val byDate = tasks.asSequence().filter { it.deletedAt == null && it.status == TaskStatus.COMPLETED }.groupBy { it.dueDate }
    return byDate.keys.filterNotNull().sortedDescending().map { date ->
        TaskGroup(date, byDate.getValue(date).sortedWith(taskTimeOrder))
    } + TaskGroup(null, byDate[null].orEmpty().sortedByDescending { it.completedAt })
}
