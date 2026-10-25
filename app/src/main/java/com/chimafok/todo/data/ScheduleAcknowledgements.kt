package com.chimafok.todo.data

/** Acknowledge only the captured generations, after all platform work succeeds, in the caller's transaction. */
internal fun TodoDatabase.acknowledgeSchedules(entries: List<ScheduleOutboxEntity>, now: Long) {
    check(inTransaction())
    // 1 timestamp + 2 arguments per row <= 801; Android 8 SQLite allows 999.
    // Avoid one suspending Room statement per dirty task after a large restore/batch.
    for (chunk in entries.chunked(400)) {
        val conditions = List(chunk.size) { "(taskId=? AND generation=?)" }.joinToString(" OR ")
        val args = ArrayList<Any>(1 + chunk.size * 2)
        args += now
        chunk.forEach { args += it.taskId; args += it.generation }
        openHelper.writableDatabase.execSQL(
            "UPDATE schedule_outbox SET processedAt=? WHERE processedAt IS NULL AND ($conditions)", args.toTypedArray())
    }
}
