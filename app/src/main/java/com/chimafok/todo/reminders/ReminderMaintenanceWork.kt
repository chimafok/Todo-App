package com.chimafok.todo.reminders

import com.chimafok.todo.data.*
import com.chimafok.todo.domain.DeviceClock
import java.time.Clock

/** Foreground and persisted jobs share this Activity-independent, idempotent catch-up. */
class ReminderMaintenanceWork(private val db: TodoDatabase, private val engine: ReminderEngine, private val clock: Clock = DeviceClock) {
    suspend fun run(recovery: Boolean = false, force: Boolean = true, scanAll: Boolean = false) {
        var failure: Exception? = null
        try { HistoryRepository(db, clock).maintain() }
        catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; failure = e }
        try { RecurringRepository(db, clock).reconcileAll(continueOnFailure = true) }
        catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; failure = e }
        // A failed series must not prevent other tasks' persisted alarms from being restored.
        engine.reconcile(force = force, recovery = recovery, scanAll = scanAll)
        failure?.let { throw it }
    }
}
