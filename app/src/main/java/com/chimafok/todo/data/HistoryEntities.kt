package com.chimafok.todo.data

import androidx.room.*

@Entity(tableName = "retention_policy")
data class RetentionPolicyEntity(@PrimaryKey val id: Int = 1, val days: Int? = null, val revision: Long = 0, val updatedAt: Long = 0)

@Entity(tableName = "retention_anchors", foreignKeys = [ForeignKey(entity = TaskEntity::class,
    parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)])
data class RetentionAnchorEntity(@PrimaryKey val taskId: String, val completedAt: Long, val restoredAt: Long)

// No foreign key: cancellation must survive the physical deletion of tasks and reminders.
@Entity(tableName = "purge_cancellations")
data class PurgeCancellationEntity(@PrimaryKey val id: String, val taskId: String, val reminderId: String,
    val alarmKey: String?, val createdAt: Long)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM retention_policy WHERE id = 1") suspend fun policy(): RetentionPolicyEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun policy(value: RetentionPolicyEntity)
    @Query("SELECT * FROM retention_anchors") suspend fun anchors(): List<RetentionAnchorEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun anchor(value: RetentionAnchorEntity)
    @Query("DELETE FROM retention_anchors WHERE taskId = :id") suspend fun clearAnchor(id: String)
    @Query("SELECT * FROM purge_cancellations ORDER BY createdAt, id") suspend fun cancellations(): List<PurgeCancellationEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun cancel(value: PurgeCancellationEntity)
    @Query("DELETE FROM purge_cancellations WHERE id = :id") suspend fun canceled(id: String)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun purgeTask(id: String)
    @Query("DELETE FROM recurring_series WHERE id = :id") suspend fun purgeSeries(id: String)
    @Query("DELETE FROM schedule_outbox WHERE taskId = :id") suspend fun clearOutbox(id: String)
}
