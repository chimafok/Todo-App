package com.chimafok.todo.reminders

import android.app.Application
import android.app.job.*
import android.content.*
import com.chimafok.todo.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** One process-wide owner; a rotation must not close a receiver's database. */
object ReminderRuntime {
    val failed = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var database: TodoDatabase; private set
    private lateinit var gateway: AndroidReminderGateway
    lateinit var engine: ReminderEngine; private set
    @Synchronized fun start(context: Context) {
        if (::database.isInitialized) return
        val app = context.applicationContext
        database = TodoDatabase.create(app)
        gateway = AndroidReminderGateway(app)
        engine = ReminderEngine(database, gateway)
        scope.launch {
            database.invalidationTracker.createFlow("schedule_outbox", "recurring_series", "series_reminders", "retention_policy", "purge_cancellations").collect {
                try {
                    if (database.browser().hasReminders() || database.dao().allSeries().any { it.deletedAt != null || it.lifecycle == SeriesLifecycle.ACTIVE } ||
                        database.browser().hasTrash() || database.history().policy()?.days != null || database.history().cancellations().isNotEmpty()) {
                        gateway.ensureChannel()
                        val scheduler = app.getSystemService(JobScheduler::class.java)
                        if (scheduler.getPendingJob(5001) == null) scheduler.schedule(JobInfo.Builder(5001,
                            ComponentName(app, ReminderMaintenance::class.java)).setPersisted(true).setPeriodic(15 * 60 * 1000L).build())
                    }
                    ReminderMaintenanceWork(database, engine).run(force = false); failed.value = false
                } catch (e: Exception) { if (e is CancellationException) throw e; failed.value = true /* Durable marker remains pending. */ }
            }
        }
    }
    fun refresh(context: Context) { start(context); scope.launch { runCatching { ReminderMaintenanceWork(database, engine).run(recovery = true) }.onSuccess { failed.value = false }.onFailure { failed.value = true } } }
    private var foregroundJob: Job? = null
    private var foregroundRequested = false
    private var recoveredThisProcess = false
    private var foregroundCapabilities: List<Any>? = null
    private var foregroundMaintenanceAt = -30_000L
    /** One cold recovery per process. Warm returns scan state without rescheduling
     * unchanged future alarms; rapid Activity/IME returns coalesce into light work. */
    @Synchronized fun foreground(context: Context) {
        start(context)
        val app = context.applicationContext
        foregroundRequested = true
        if(foregroundJob?.isActive == true) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            while(true) {
                val requested = synchronized(this@ReminderRuntime) {
                    foregroundRequested.also { foregroundRequested = false }
                }
                if(!requested) break
                try {
                    val capabilities = listOf(gateway.notificationsAllowed(),gateway.exactAllowed(),
                        java.time.ZoneId.systemDefault().id,java.time.LocalDate.now().toString())
                    val elapsed = android.os.SystemClock.elapsedRealtime()
                    if(!recoveredThisProcess || capabilities != foregroundCapabilities || elapsed-foregroundMaintenanceAt >= 30_000) {
                        ReminderMaintenanceWork(database,engine).run(recovery=true,force=!recoveredThisProcess,scanAll=true)
                        recoveredThisProcess = true;foregroundCapabilities = capabilities;foregroundMaintenanceAt = elapsed
                    } else engine.reconcile(recovery=true,scanAll=true)
                    failed.value = false
                } catch(e: Exception) {
                    if(e is CancellationException) throw e
                    failed.value = true
                }
            }
        }
        foregroundJob = job
        job.invokeOnCompletion {
            synchronized(this@ReminderRuntime) {
                if(foregroundJob === job) foregroundJob = null
                if(foregroundRequested) foreground(app)
            }
        }
        job.start()
    }
    @Volatile internal var maintenanceCompletions = 0L; private set
    fun maintenance(context: Context, done: (Boolean) -> Unit): Job {
        start(context)
        return scope.launch {
            try { ReminderMaintenanceWork(database, engine).run(); failed.value = false; maintenanceCompletions++; done(false) }
            catch (e: Exception) { if (e is CancellationException) throw e; failed.value = true; done(true) }
        }
    }
    fun requestMaintenance(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(5002) == null) scheduler.schedule(JobInfo.Builder(5002,
            ComponentName(context, ReminderMaintenance::class.java)).setOverrideDeadline(60_000).build())
    }
    fun run(context: Context, done: () -> Unit, block: suspend (ReminderEngine) -> Unit) {
        start(context)
        scope.launch { try { block(engine) } finally { done() } }
    }
}
class TodoApplication : Application() { override fun onCreate() { super.onCreate(); ReminderRuntime.start(this) } }
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        ReminderRuntime.run(context, { pending.finish() }) { engine ->
            try {
                val id = intent.getStringExtra("reminderId")
                val token = intent.getStringExtra("token")
                when (intent.action) {
                    "FIRE" -> if (id != null && token != null) engine.fire(id, token)
                    "COMPLETE" -> if (id != null && token != null) { engine.complete(id, token); engine.reconcile() }
                    else -> { engine.reconcile(force = true, recovery = true); ReminderRuntime.requestMaintenance(context) }
                }
            } catch (_: Exception) { /* No task text in diagnostic logs; retry from durable state. */ }
        }
    }
}
class ReminderMaintenance : JobService() {
    private val running = mutableMapOf<Int, Job>()
    private val parameters = mutableMapOf<Int, JobParameters>()
    override fun onStartJob(params: JobParameters): Boolean {
        parameters[params.jobId] = params
        running[params.jobId] = ReminderRuntime.maintenance(this) { retry ->
            android.os.Handler(mainLooper).post {
                if (parameters[params.jobId] === params) {
                    parameters.remove(params.jobId); running.remove(params.jobId); jobFinished(params, retry)
                }
            }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        parameters.remove(params.jobId); running.remove(params.jobId)?.cancel(); return true
    }
    override fun onDestroy() { parameters.clear(); running.values.forEach { it.cancel() }; running.clear(); super.onDestroy() }
}
