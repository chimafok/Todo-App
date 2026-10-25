package com.chimafok.todo.reminders

import androidx.room.withTransaction
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

interface ReminderGateway {
    fun notificationsAllowed(): Boolean
    fun exactAllowed(): Boolean
    fun schedule(reminder: ReminderEntity, key: String, triggerAt: Long): String
    fun cancel(reminder: ReminderEntity, key: String)
    fun show(reminder: ReminderEntity, task: TaskEntity, key: String)
    fun cancelRemoved(taskId: String, reminderId: String, key: String)
    fun dismiss(reminderId: String)
}

class ReminderEngine(private val db: TodoDatabase, private val gateway: ReminderGateway,
    private val clock: Clock = DeviceClock) {
    companion object {
        private val guard = Mutex()
    }
    private fun key(task: TaskEntity, reminder: ReminderEntity, at: Long) = "${task.revision}:${reminder.revision}:$at"
    private suspend fun active(task: TaskEntity?) = task != null && task.deletedAt == null &&
        task.status != TaskStatus.COMPLETED && db.dao().occurrence(task.id)?.excludedByRule != true

    /** Platform work can be repeated after a crash; only mark the outbox after it succeeds. */
    suspend fun reconcile(force: Boolean = false, recovery: Boolean = false, scanAll: Boolean = false) = guard.withLock {
        var cancellationFailure: Exception? = null
        db.withTransaction {
            db.history().cancellations().forEach { entry ->
                try {
                    entry.alarmKey?.let { gateway.cancelRemoved(entry.taskId, entry.reminderId, it) }
                    if (db.reminders().get(entry.reminderId) == null) gateway.dismiss(entry.reminderId)
                    db.history().canceled(entry.id)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (cancellationFailure == null) cancellationFailure = e
                }
            }
            val pending = db.reminders().pending()
            if (!force && !scanAll && pending.isEmpty()) return@withTransaction
            val dirty = pending.map { it.taskId }.toSet()
            val permitted = gateway.notificationsAllowed()
            val desiredMode = if (gateway.exactAllowed()) "EXACT" else "INEXACT"
            db.reminders().all().filter { force || scanAll || it.taskId in dirty }.forEach { old ->
                val task = db.dao().task(old.taskId)
                val at = if (task != null && old.enabled) resolveReminder(old.spec(), task.dueDate, task.dueTime, task.zoneId) else old.resolvedAt
                val expectedKey = if (task != null && at != null) key(task, old, at) else null
                val state = when {
                    !old.enabled || !active(task) -> if (old.state == "DELIVERED") "DELIVERED" else "CANCELED"
                    old.state == "DELIVERED" && old.resolvedAt == at -> "DELIVERED"
                    old.state == "MISSED" && old.resolvedAt == at -> "MISSED"
                    at == null -> "CANCELED"
                    at <= clock.millis() -> if (!recovery && old.state == "PENDING" && old.alarmKey == expectedKey) "PENDING" else "MISSED"
                    !permitted -> "BLOCKED"
                    else -> "PENDING"
                }
                val future = state == "PENDING" && requireNotNull(at) > clock.millis()
                val keepWaiting = state == "PENDING" && !future
                val mustSchedule = future && (force || old.alarmKey != expectedKey || old.alarmMode != desiredMode || old.state != "PENDING")
                if (old.alarmKey != null && (!keepWaiting && (!future || mustSchedule))) gateway.cancel(old, old.alarmKey)
                if (!active(task) || !old.enabled || old.alarmKey != expectedKey) gateway.dismiss(old.id)
                val mode = if (mustSchedule) gateway.schedule(old, requireNotNull(expectedKey), requireNotNull(at))
                    else if (future || keepWaiting) old.alarmMode else null
                val next = old.copy(state = state, resolvedAt = at,
                    alarmKey = if (state in listOf("PENDING", "DELIVERED")) expectedKey else null, alarmMode = mode)
                if (next != old) db.reminders().update(next)
            }
            db.acknowledgeSchedules(pending, clock.millis())
        }
        cancellationFailure?.let { throw it }
    }

    /** Re-check identity, revision, current trigger and state under the delivery transaction. */
    suspend fun fire(id: String, token: String) = guard.withLock {
        db.withTransaction {
            val reminder = db.reminders().get(id) ?: return@withTransaction
            val task = db.dao().task(reminder.taskId) ?: return@withTransaction
            if (!active(task) || !reminder.enabled || reminder.state != "PENDING") return@withTransaction
            val at = resolveReminder(reminder.spec(), task.dueDate, task.dueTime, task.zoneId)
            if (
                reminder.alarmKey != token || key(task, reminder, at) != token || at > clock.millis()) return@withTransaction
            val state = when {
                !gateway.notificationsAllowed() -> "BLOCKED"
                else -> "DELIVERED"
            }
            if (state == "DELIVERED") gateway.show(reminder, task, token)
            db.reminders().update(reminder.copy(state = state, alarmMode = null))
        }
    }

    suspend fun complete(id: String, token: String) = guard.withLock {
        db.withTransaction {
            val reminder = db.reminders().get(id) ?: return@withTransaction
            val task = db.dao().task(reminder.taskId) ?: return@withTransaction
            if (!active(task) || !reminder.enabled || reminder.state != "DELIVERED" || reminder.alarmKey != token ||
                key(task, reminder, resolveReminder(reminder.spec(), task.dueDate, task.dueTime, task.zoneId)) != token) {
                gateway.dismiss(id); return@withTransaction
            }
            TaskRepository(db, clock).setStatus(task.id, task.revision, TaskStatus.COMPLETED)
            db.reminders().forTask(task.id).forEach { item ->
                item.alarmKey?.let { gateway.cancel(item, it) }; gateway.dismiss(item.id)
            }
        }
    }
}
