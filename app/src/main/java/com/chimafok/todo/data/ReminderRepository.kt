package com.chimafok.todo.data

import com.chimafok.todo.domain.*

/** Called inside the task save transaction; never saves an independent editor draft. */
suspend fun saveReminders(db: TodoDatabase, task: TaskEntity, specs: List<ReminderSpec>?, now: Long) {
    val previous = db.reminders().forTask(task.id)
    val selected = specs ?: previous.filter { it.enabled }.map { it.spec() }
    require(selected.map { it.id }.distinct().size == selected.size) { "提醒識別碼重複" }
    require(selected.size <= 32) { "每項任務最多 32 個提醒" }
    selected.forEach { resolveReminder(it, task.dueDate, task.dueTime, task.zoneId) }
    selected.forEach { spec ->
        val at = resolveReminder(spec, task.dueDate, task.dueTime, task.zoneId)
        val old = db.reminders().get(spec.id)
        require(old == null || old.taskId == task.id) { "提醒識別碼已被其他任務使用" }
        val inactive = task.status == TaskStatus.COMPLETED || task.deletedAt != null
        if (old == null) db.reminders().insert(ReminderEntity(spec.id, task.id, spec.kind.name,
            spec.zoneId, spec.fixedAt, spec.offsetMinutes, spec.baseTime, state = if (inactive) "CANCELED" else "PENDING",
            resolvedAt = at, createdAt = now, updatedAt = now))
        else {
            val changed = old.spec() != spec || !old.enabled
            // Persist ordering immediately, but do not hide a moved delivered/missed trigger
            // from the scheduler. Keep its old alarmKey until durable cancellation succeeds.
            val state = if (inactive) { if (old.state == "DELIVERED") "DELIVERED" else "CANCELED" }
                else if (changed || old.resolvedAt != at) "PENDING" else old.state
            if (changed || old.resolvedAt != at || old.state != state) db.reminders().update(old.copy(kind = spec.kind.name,
                zoneId = spec.zoneId, fixedAt = spec.fixedAt, offsetMinutes = spec.offsetMinutes,
                baseTime = spec.baseTime, enabled = true, state = state, resolvedAt = at,
                revision = old.revision + if (changed) 1 else 0, updatedAt = now))
        }
    }
    previous.filter { it.enabled && selected.none { spec -> spec.id == it.id } }.forEach {
        // Keep a tombstone until its old platform alarm can be cancelled durably.
        db.reminders().update(it.copy(enabled = false, state = "CANCELED", revision = it.revision + 1, updatedAt = now))
    }
}
