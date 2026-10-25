package com.chimafok.todo.data

import android.os.SystemClock
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*
import java.util.UUID

/** Diagnostic timings on the same QA device, not a real-device frame/startup benchmark. */
@RunWith(AndroidJUnit4::class)
class ListPerformanceTest {
    @Test fun sixtyThousandRowsPreserveSearchProjectionGroupingAndStatus() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        check(context.packageName.endsWith(".qa"))
        val name = "performance-${UUID.randomUUID()}.db"
        val db = TodoDatabase.create(context, name)
        val date = LocalDate.of(2026, 10, 24)
        val clock = Clock.fixed(date.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val measurements = JSONObject()
        fun <T> measure(label: String, count: Int = 3, block: () -> T): T {
            val times = mutableListOf<Double>()
            var result: T? = null
            repeat(count) {
                val start = SystemClock.elapsedRealtimeNanos()
                result = block()
                times += (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
            }
            measurements.put(label, JSONObject().put("samplesMs", JSONArray(times)).put("medianMs", times.sorted()[times.size / 2]))
            @Suppress("UNCHECKED_CAST") return result as T
        }
        try {
            // Prepared statements keep fixture creation out of the measured read/update paths.
            db.withTransaction {
                val sql = db.openHelper.writableDatabase
                val task = sql.compileStatement("INSERT INTO tasks(id,title,notes,categoryId,dueDate,dueTime,zoneId,status,completedAt,deletedAt,createdAt,updatedAt,revision) VALUES(?,?,?,? ,?,NULL,'UTC',?,?,NULL,1,1,1)")
                fun insert(id: String, due: LocalDate, complete: Boolean) {
                    task.clearBindings(); task.bindString(1, id); task.bindString(2, id)
                    task.bindString(3, if (id == "ordinary-5000") "唯一搜尋目標" else "一般備註")
                    task.bindString(4, UNCATEGORIZED_ID); task.bindString(5, due.toString())
                    task.bindString(6, if (complete) "COMPLETED" else "OPEN")
                    if (complete) task.bindLong(7, 2) else task.bindNull(7)
                    task.executeInsert()
                }
                repeat(10_000) { insert("ordinary-$it", date.plusDays(it.toLong()), false) }
                repeat(100) { s ->
                    sql.execSQL("INSERT INTO recurring_series(id,title,notes,categoryId,lifecycle,cursor,createdAt,updatedAt,revision) VALUES(?,?,'',?,'ENDED',500,1,1,1)", arrayOf("series-$s", "系列 $s", UNCATEGORIZED_ID))
                    sql.execSQL("INSERT INTO series_rules(id,seriesId,version,effectiveFrom,anchor,frequency,intervalDays,weekdayMask,zoneId,maxCount,createdAt) VALUES(?,?,1,1,?,'DAILY',1,0,'UTC',500,1)", arrayOf("rule-$s", "series-$s", date.minusDays(500).toString()))
                }
                val occurrence = sql.compileStatement("INSERT INTO occurrences(taskId,seriesId,sequence,ruleId,excludedByRule) VALUES(?,?,?,?,0)")
                repeat(50_000) { n ->
                    val series = n / 500; val sequence = n % 500 + 1; val id = "history-$n"
                    insert(id, date.minusDays((501 - sequence).toLong()), sequence != 500)
                    occurrence.clearBindings(); occurrence.bindString(1, id); occurrence.bindString(2, "series-$series")
                    occurrence.bindLong(3, sequence.toLong()); occurrence.bindString(4, "rule-$series"); occurrence.executeInsert()
                }
                repeat(100) { series ->
                    val snapshot = OccurrenceSnapshot("原系列 $series", "原紀錄說明", "原分類", listOf("封存標籤"), "COMPLETED", false).json()
                    sql.execSQL("UPDATE occurrences SET snapshot=? WHERE seriesId=?", arrayOf(snapshot,"series-$series"))
                }
                db.dao().insertTag(TagEntity("performance-tag", "當前標籤", "當前標籤", 0, 1, 1))
                sql.execSQL("INSERT INTO task_tags(taskId,tagId) SELECT taskId,'performance-tag' FROM occurrences WHERE sequence % 10 = 0")
                task.close(); occurrence.close()
            }
            val fullTimes = mutableListOf<Double>(); val scopedTimes = mutableListOf<Double>()
            repeat(3) {
                var start = SystemClock.elapsedRealtimeNanos()
                db.withTransaction {
                    assertEquals(60_000, db.dao().allTasks().size)
                    assertEquals(50_000, db.dao().allOccurrences().size)
                }
                fullTimes += (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                start = SystemClock.elapsedRealtimeNanos()
                db.withTransaction {
                    assertEquals(10_100, db.browser().tasks(false, null).size)
                    assertEquals(100, db.browser().occurrences(false, null).size)
                }
                scopedTimes += (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
            }
            measurements.put("fullReadSamplesMs", JSONArray(fullTimes)).put("scopedReadSamplesMs", JSONArray(scopedTimes))
            val pageStart = SystemClock.elapsedRealtimeNanos()
            val page = HistoryBrowserRepository(db).window("series-0", "", 50)
            measurements.put("historyFirstPageMs", (SystemClock.elapsedRealtimeNanos() - pageStart) / 1_000_000.0)
            assertEquals(50, page.rows.size); assertEquals(500, page.total)
            val searchStart = SystemClock.elapsedRealtimeNanos()
            val searched = HistoryBrowserRepository(db).window(null, "history-0", 50)
            measurements.put("historyOverviewFullSearchMs", (SystemClock.elapsedRealtimeNanos() - searchStart) / 1_000_000.0)
            assertEquals(setOf("series-0"), searched.matchingSeries)
            suspend fun sampleSearch(label: String, seriesId: String?, query: String): HistoryWindow {
                val times = mutableListOf<Double>()
                var result = HistoryWindow()
                repeat(3) {
                    val start = SystemClock.elapsedRealtimeNanos()
                    result = HistoryBrowserRepository(db).window(seriesId, query, 50)
                    times += (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                }
                measurements.put(label, JSONObject().put("samplesMs", JSONArray(times)).put("medianMs", times.sorted()[1]))
                return result
            }
            assertEquals(setOf("series-0"),sampleSearch("historyUniqueMatch",null,"history-0").matchingSeries)
            assertTrue(sampleSearch("historyNoMatch",null,"不存在XYZ").matchingSeries.isEmpty())
            assertEquals(100,sampleSearch("historySnapshotMatch",null,"封存標籤").matchingSeries.size)
            assertEquals(100,sampleSearch("historyLiveTagMatch",null,"當前標籤").matchingSeries.size)
            val selectedSearch=sampleSearch("selectedSeriesSnapshotMatch","series-0","封存標籤")
            assertEquals(500,selectedSearch.total);assertEquals(50,selectedSearch.rows.size)
            val taskPageTimes=mutableListOf<Double>()
            repeat(3) {
                val start=SystemClock.elapsedRealtimeNanos()
                val page=TaskPageRepository(db).window(TaskScope.AGENDA,TaskFilter(),clock,null,50,50)
                taskPageTimes+=(SystemClock.elapsedRealtimeNanos()-start)/1_000_000.0
                assertEquals(10_100,page.revisions.size);assertEquals(50,page.rows.tasks.size)
                assertEquals(50,page.rows.tasks.map{it.id}.toSet().size)
                assertTrue(page.rows.tasks.all{it.title.isNotEmpty()&&it.notes.isNotEmpty()})
            }
            measurements.put("ordinaryPageHydratedRows",50).put("ordinaryPageResultCount",10_100)
                .put("ordinaryPageSamplesMs",JSONArray(taskPageTimes))
            val hidden=TaskPageRepository(db).window(TaskScope.AGENDA,TaskFilter("唯一搜尋目標"),clock,null,50,50)
            assertEquals(setOf("ordinary-5000"),hidden.revisions.keys)
            assertEquals("唯一搜尋目標",hidden.rows.tasks.single().notes)
            val readStart = SystemClock.elapsedRealtimeNanos()
            val tasks = db.dao().allTasks(); val occurrences = db.dao().allOccurrences()
            measurements.put("roomReadTasksAndOccurrencesMs", (SystemClock.elapsedRealtimeNanos() - readStart) / 1_000_000.0)
            assertEquals(60_000, tasks.size); assertEquals(50_000, occurrences.size)
            val refs = occurrences.map { RecurringTaskRef(it.taskId, it.seriesId, it.sequence, it.excludedByRule) }
            val ids = measure("homeProjection") { recurringHomeTaskIds(tasks, refs, clock) }
            assertEquals((0 until 100).map { "history-${it * 500 + 499}" }.toSet(), ids)
            val occurrenceIds = refs.map { it.taskId }.toSet()
            val agenda = tasks.filter { it.id !in occurrenceIds || it.id in ids }
            val groups = measure("agendaTenThousandDates") { agendaGroups(agenda, clock) }
            assertEquals(10_100, groups.sumOf { it.tasks.size })
            assertEquals(10_001, groups.size)
            assertEquals(101, groups.first().tasks.size)
            assertEquals(10_100, groups.flatMap { it.tasks }.map { it.id }.toSet().size)
            val completed = measure("completedFiftyThousandHistory") { completedGroups(tasks) }
            assertEquals(49_900, completed.sumOf { it.tasks.size })
            val categories = db.dao().allCategories()
            val found = measure("searchSixtyThousandTasks") { filterTasks(tasks, categories, emptyList(), emptyList(), TaskFilter("唯一搜尋目標")) }
            assertEquals(listOf("ordinary-5000"), found.map { it.id })
            val startUpdate = SystemClock.elapsedRealtimeNanos()
            TaskRepository(db, clock).setStatus("ordinary-5000", 1, TaskStatus.COMPLETED)
            measurements.put("statusTransactionMs", (SystemClock.elapsedRealtimeNanos() - startUpdate) / 1_000_000.0)
            assertEquals(TaskStatus.COMPLETED, db.dao().task("ordinary-5000")!!.status)
            assertEquals(2L, db.dao().task("ordinary-5000")!!.revision)
            assertEquals(60_000, db.dao().allTasks().size)
            assertEquals(50_000, db.dao().allOccurrences().size)
            val report = JSONObject().put("ordinaryTasks", 10_000).put("historyRows", 50_000)
                .put("snapshotRows",50_000).put("distinctSnapshots",100).put("taskTagLinks",5_000)
                .put("api", android.os.Build.VERSION.SDK_INT).put("model", android.os.Build.MODEL)
                .put("measurements", measurements).put("scope", "QA file database and list functions; includes scoped reads/history windows; excludes Compose frame, cold start and real device")
            File(context.filesDir, "list-performance.json").writeText(report.toString(2))
            android.util.Log.i("TodoListPerformance", report.toString())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
