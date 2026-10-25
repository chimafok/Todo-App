package com.chimafok.todo.data

import com.chimafok.todo.domain.*

/** All callers hold the enclosing series/task transaction. */
suspend fun saveSeriesReminders(db: TodoDatabase, seriesId: String, specs: List<SeriesReminderSpec>?, now: Long): List<SeriesReminderSpec> {
    val old = db.reminders().templatesFor(seriesId)
    val selected = specs ?: old.filter { it.enabled }.map { it.spec() }
    require(selected.size <= 32 && selected.map { it.id }.distinct().size == selected.size) { "每個系列最多 32 個不同提醒" }
    selected.forEach { spec ->
        validateSeriesReminder(spec)
        val previous = db.reminders().template(spec.id)
        require(previous == null || previous.seriesId == seriesId) { "提醒識別碼已被其他系列使用" }
        if (previous == null) db.reminders().insertTemplate(SeriesReminderEntity(spec.id, seriesId, spec.kind.name,
            spec.offsetMinutes, spec.dayOffset, spec.time, spec.baseTime, createdAt = now, updatedAt = now))
        else if (previous.spec() != spec || !previous.enabled) db.reminders().updateTemplate(previous.copy(kind = spec.kind.name,
            offsetMinutes = spec.offsetMinutes, dayOffset = spec.dayOffset, time = spec.time, baseTime = spec.baseTime, enabled = true, updatedAt = now))
    }
    old.filter { it.enabled && selected.none { s -> s.id == it.id } }.forEach { db.reminders().updateTemplate(it.copy(enabled = false, updatedAt = now)) }
    return selected
}

suspend fun applySeriesReminders(db: TodoDatabase, task: TaskEntity, templates: List<SeriesReminderSpec>, now: Long,
    previousTemplates: List<SeriesReminderSpec> = emptyList(), reset: Boolean = false) {
    if (!reset && db.reminders().overridden(task.id)) {
        saveReminders(db, task, null, now) // Its own relative offsets still follow its changed deadline.
        return
    }
    if (reset) db.reminders().resetOverride(task.id)
    val previous = db.reminders().forTask(task.id).associateBy { it.id }
    val specs = templates.map { template ->
        val resolved = occurrenceReminder(template, task.id, requireNotNull(task.dueDate), task.dueTime, requireNotNull(task.zoneId))
        val old = previous[resolved.id]
        // A date-only template replan must not move a previously generated fixed instant.
        if (!reset && template.kind == SeriesReminderKind.OCCURRENCE_TIME && old?.enabled == true &&
            previousTemplates.any { it == template }) resolved.copy(fixedAt = old.fixedAt) else resolved
    }
    saveReminders(db, task, specs, now)
}
