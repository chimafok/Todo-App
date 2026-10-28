package com.chimafok.todo.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.chimafok.todo.domain.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "categories", indices = [Index(value = ["systemKind"], unique = true)])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val systemKind: SystemCategory? = null,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long = 1,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("categoryId"), Index(value = ["deletedAt", "status", "dueDate", "dueTime"]), Index("completedAt")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val categoryId: String,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val zoneId: String? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val completedAt: Long? = null,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long = 1,
    val startDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val startZoneId: String? = null,
    @ColumnInfo(defaultValue = "''") val imageIds: String = "",
)

@Entity(tableName = "tags", indices = [Index("nameKey")])
data class TagEntity(
    @PrimaryKey val id: String, val name: String, val nameKey: String, val sortOrder: Int,
    val createdAt: Long, val updatedAt: Long, val revision: Long = 1,
)

@Entity(tableName = "task_tags", primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ], indices = [Index("tagId")])
data class TaskTagEntity(val taskId: String, val tagId: String)

class TodoConverters {
    @TypeConverter fun date(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun date(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun time(value: String?): LocalTime? = value?.let(LocalTime::parse)
    @TypeConverter fun time(value: LocalTime?): String? = value?.toString()
    @TypeConverter fun status(value: String): TaskStatus = TaskStatus.valueOf(value)
    @TypeConverter fun status(value: TaskStatus): String = value.name
    @TypeConverter fun category(value: String?): SystemCategory? = value?.let(SystemCategory::valueOf)
    @TypeConverter fun category(value: SystemCategory?): String? = value?.name
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt, id") suspend fun allTasks(): List<TaskEntity>
    @Query("SELECT * FROM task_tags") suspend fun allTaskTags(): List<TaskTagEntity>
    @Query("SELECT * FROM recurring_series ORDER BY createdAt, id") suspend fun allSeries(): List<RecurringSeriesEntity>
    @Query("SELECT * FROM series_rules ORDER BY version") suspend fun allRules(): List<SeriesRuleEntity>
    @Query("SELECT * FROM occurrences ORDER BY seriesId, sequence") suspend fun allOccurrences(): List<OccurrenceEntity>
    @Query("SELECT * FROM series_tags") suspend fun allSeriesTags(): List<SeriesTagEntity>
    @Query("SELECT * FROM recurring_series WHERE id = :id") suspend fun series(id: String): RecurringSeriesEntity?
    @Query("SELECT * FROM recurring_series WHERE categoryId = :id") suspend fun categorySeries(id: String): List<RecurringSeriesEntity>
    @Query("SELECT * FROM series_rules WHERE seriesId = :id ORDER BY version DESC LIMIT 1") suspend fun latestRule(id: String): SeriesRuleEntity?
    @Query("SELECT * FROM occurrences WHERE taskId = :id") suspend fun occurrence(id: String): OccurrenceEntity?
    @Query("SELECT * FROM occurrences WHERE seriesId = :id ORDER BY sequence") suspend fun seriesOccurrences(id: String): List<OccurrenceEntity>
    @Query("SELECT tagId FROM series_tags WHERE seriesId = :id") suspend fun seriesTagIds(id: String): List<String>
    @Query("SELECT seriesId FROM series_tags WHERE tagId = :id") suspend fun taggedSeries(id: String): List<String>
    @Query("DELETE FROM series_tags WHERE seriesId = :id") suspend fun clearSeriesTags(id: String)
    @Insert suspend fun insertSeries(series: RecurringSeriesEntity)
    @Update suspend fun updateSeries(series: RecurringSeriesEntity)
    @Insert suspend fun insertRule(rule: SeriesRuleEntity)
    @Insert suspend fun insertOccurrence(occurrence: OccurrenceEntity)
    @Update suspend fun updateOccurrence(occurrence: OccurrenceEntity)
    @Insert suspend fun insertSeriesTags(tags: List<SeriesTagEntity>)
    @Query("SELECT * FROM categories ORDER BY sortOrder, id") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM tasks ORDER BY createdAt, id") fun tasks(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun category(id: String): CategoryEntity?
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: String): TaskEntity?
    @Insert suspend fun insert(task: TaskEntity)
    @Update suspend fun update(task: TaskEntity)
    @Query("SELECT * FROM categories ORDER BY sortOrder, id") suspend fun allCategories(): List<CategoryEntity>
    @Insert suspend fun insertCategory(category: CategoryEntity)
    @Update suspend fun updateCategory(category: CategoryEntity)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: String)
    @Query("SELECT * FROM tasks WHERE categoryId = :id") suspend fun categoryTasks(id: String): List<TaskEntity>
    @Query("SELECT * FROM tags ORDER BY sortOrder, id") fun tags(): Flow<List<TagEntity>>
    @Query("SELECT * FROM task_tags") fun taskTags(): Flow<List<TaskTagEntity>>
    @Query("SELECT * FROM tags ORDER BY sortOrder, id") suspend fun allTags(): List<TagEntity>
    @Query("SELECT * FROM tags WHERE id = :id") suspend fun tag(id: String): TagEntity?
    @Insert suspend fun insertTag(tag: TagEntity)
    @Update suspend fun updateTag(tag: TagEntity)
    @Query("DELETE FROM tags WHERE id = :id") suspend fun deleteTag(id: String)
    @Query("SELECT tagId FROM task_tags WHERE taskId = :id") suspend fun taskTagIds(id: String): List<String>
    @Query("SELECT tasks.* FROM tasks INNER JOIN task_tags ON tasks.id = task_tags.taskId WHERE task_tags.tagId = :id")
    suspend fun taggedTasks(id: String): List<TaskEntity>
    @Query("DELETE FROM task_tags WHERE taskId = :id") suspend fun clearTaskTags(id: String)
    @Insert suspend fun insertTaskTags(links: List<TaskTagEntity>)
}

@Database(entities = [TaskEntity::class, CategoryEntity::class, TagEntity::class, TaskTagEntity::class,
    RecurringSeriesEntity::class, SeriesRuleEntity::class, SeriesTagEntity::class, OccurrenceEntity::class, ReminderEntity::class, ScheduleOutboxEntity::class, SeriesReminderEntity::class, ReminderOverrideEntity::class, RetentionPolicyEntity::class, RetentionAnchorEntity::class, PurgeCancellationEntity::class, NoteImageEntity::class], version = 9, exportSchema = true)
@TypeConverters(TodoConverters::class)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun noteImages(): NoteImageDao
    abstract fun dao(): TodoDao
    abstract fun reminders(): ReminderDao
    abstract fun history(): HistoryDao
    abstract fun browser(): BrowserDao
    abstract fun taskPages(): TaskPageDao

    companion object {
        fun create(context: Context, name: String = "todo.db"): TodoDatabase =
            Room.databaseBuilder(context.applicationContext, TodoDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .addCallback(seedCallback())
                // No destructive migration fallback: future versions must provide a migration.
                .build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS tags (id TEXT NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL, sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tags_nameKey ON tags (nameKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS task_tags (taskId TEXT NOT NULL, tagId TEXT NOT NULL, PRIMARY KEY(taskId, tagId), FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_tags_tagId ON task_tags (tagId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS recurring_series (id TEXT NOT NULL, title TEXT NOT NULL, notes TEXT NOT NULL, categoryId TEXT NOT NULL, lifecycle TEXT NOT NULL, cursor INTEGER NOT NULL, deletedAt INTEGER, previousLifecycle TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_series_categoryId ON recurring_series(categoryId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS series_rules (id TEXT NOT NULL, seriesId TEXT NOT NULL, version INTEGER NOT NULL, effectiveFrom INTEGER NOT NULL, anchor TEXT NOT NULL, frequency TEXT NOT NULL, intervalDays INTEGER NOT NULL, weekdayMask INTEGER NOT NULL, dueTime TEXT, zoneId TEXT NOT NULL, endDate TEXT, maxCount INTEGER, createdAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(seriesId) REFERENCES recurring_series(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_series_rules_seriesId_version ON series_rules(seriesId,version)")
                db.execSQL("CREATE TABLE IF NOT EXISTS series_tags (seriesId TEXT NOT NULL, tagId TEXT NOT NULL, PRIMARY KEY(seriesId,tagId), FOREIGN KEY(seriesId) REFERENCES recurring_series(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_series_tags_tagId ON series_tags(tagId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS occurrences (taskId TEXT NOT NULL, seriesId TEXT NOT NULL, sequence INTEGER NOT NULL, ruleId TEXT NOT NULL, snapshot TEXT, deletedWithSeriesAt INTEGER, PRIMARY KEY(taskId), FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(seriesId) REFERENCES recurring_series(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(ruleId) REFERENCES series_rules(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_occurrences_seriesId_sequence ON occurrences(seriesId,sequence)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_occurrences_ruleId ON occurrences(ruleId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE occurrences ADD COLUMN excludedByRule INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reminders (id TEXT NOT NULL, taskId TEXT NOT NULL, kind TEXT NOT NULL, zoneId TEXT NOT NULL, fixedAt INTEGER, offsetMinutes INTEGER, baseTime TEXT NOT NULL, enabled INTEGER NOT NULL, state TEXT NOT NULL, resolvedAt INTEGER, alarmKey TEXT, alarmMode TEXT, revision INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_taskId ON reminders(taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_state_resolvedAt ON reminders(state,resolvedAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS schedule_outbox (taskId TEXT NOT NULL, generation INTEGER NOT NULL, processedAt INTEGER, PRIMARY KEY(taskId))")
                installReminderTriggers(db)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS series_reminders (id TEXT NOT NULL, seriesId TEXT NOT NULL, kind TEXT NOT NULL, offsetMinutes INTEGER, dayOffset INTEGER, time TEXT, baseTime TEXT NOT NULL, enabled INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(seriesId) REFERENCES recurring_series(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_series_reminders_seriesId ON series_reminders(seriesId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS reminder_overrides (taskId TEXT NOT NULL, PRIMARY KEY(taskId), FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS retention_policy (id INTEGER NOT NULL, days INTEGER, revision INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS retention_anchors (taskId TEXT NOT NULL, completedAt INTEGER NOT NULL, restoredAt INTEGER NOT NULL, PRIMARY KEY(taskId), FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS purge_cancellations (id TEXT NOT NULL, taskId TEXT NOT NULL, reminderId TEXT NOT NULL, alarmKey TEXT, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }

        private fun installReminderTriggers(db: SupportSQLiteDatabase) {
            // WHERE NOT EXISTS avoids the outer Room INSERT OR ABORT overriding
            // a trigger-local OR IGNORE conflict policy on older SQLite versions.
            fun dirty(task: String) = "INSERT INTO schedule_outbox(taskId,generation,processedAt) SELECT $task,0,NULL WHERE NOT EXISTS(SELECT 1 FROM schedule_outbox WHERE taskId=$task); UPDATE schedule_outbox SET generation=generation+1,processedAt=NULL WHERE taskId=$task;"
            listOf("INSERT" to "NEW", "UPDATE" to "NEW", "DELETE" to "OLD").forEach { (event, row) ->
                val cancel = if (event == "UPDATE") "UPDATE reminders SET state='CANCELED' WHERE taskId=NEW.id AND state IN ('PENDING','BLOCKED') AND (NEW.status='COMPLETED' OR NEW.deletedAt IS NOT NULL);" else ""
                db.execSQL("CREATE TRIGGER IF NOT EXISTS reminder_task_${event.lowercase()} AFTER $event ON tasks BEGIN ${dirty("$row.id")}$cancel END")
            }
            // Room @Update mentions every column even when only delivery state changes.
            // Compare values so scheduler bookkeeping never creates a fresh dirty marker.
            listOf("INSERT" to "NEW", "UPDATE OF kind,zoneId,fixedAt,offsetMinutes,baseTime,enabled,revision" to "NEW", "DELETE" to "OLD").forEachIndexed { index, (event, row) ->
                val changed = if (index == 1) "WHEN OLD.kind IS NOT NEW.kind OR OLD.zoneId IS NOT NEW.zoneId OR OLD.fixedAt IS NOT NEW.fixedAt OR OLD.offsetMinutes IS NOT NEW.offsetMinutes OR OLD.baseTime IS NOT NEW.baseTime OR OLD.enabled IS NOT NEW.enabled OR OLD.revision IS NOT NEW.revision" else ""
                db.execSQL("CREATE TRIGGER IF NOT EXISTS reminder_definition_$index AFTER $event ON reminders $changed BEGIN ${dirty("$row.taskId")} END")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN startDate TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN startTime TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN startZoneId TEXT")
                db.execSQL("ALTER TABLE series_rules ADD COLUMN startDayOffset INTEGER")
                db.execSQL("ALTER TABLE series_rules ADD COLUMN startTime TEXT")
                db.execSQL("ALTER TABLE series_rules ADD COLUMN startZoneId TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN imageIds TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recurring_series ADD COLUMN imageIds TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS note_images (id TEXT NOT NULL, data TEXT NOT NULL, PRIMARY KEY(id))")
            }
        }

        fun seedCallback(): Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                installReminderTriggers(db)
                val now = System.currentTimeMillis()
                listOf(
                    CategoryEntity(STUDY_ID, "學習", sortOrder = 0, createdAt = now, updatedAt = now),
                    CategoryEntity("00000000-0000-4000-8000-000000000004", "生活", sortOrder = 1, createdAt = now, updatedAt = now),
                    CategoryEntity(ASSIGNMENTS_ID, "作業", SystemCategory.ASSIGNMENTS, 2, now, now),
                    CategoryEntity(UNCATEGORIZED_ID, "未分類", SystemCategory.UNCATEGORIZED, 3, now, now),
                ).forEach { category ->
                    db.execSQL(
                        "INSERT INTO categories (id,name,systemKind,sortOrder,createdAt,updatedAt,revision) VALUES (?,?,?,?,?,?,?)",
                        arrayOf<Any?>(category.id, category.name, category.systemKind?.name, category.sortOrder, now, now, 1),
                    )
                }
            }
        }
    }
}
