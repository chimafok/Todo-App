package com.chimafok.todo.data

import android.app.job.JobScheduler
import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SeriesReminderJobTest {
    @Test fun persistedAndroidJobRunsSeriesMaintenanceWithoutAnyActivity():Unit=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();assertEquals("com.chimafok.todo.qa",context.packageName)
        val db=ReminderRuntime.database;val scheduler=context.getSystemService(JobScheduler::class.java)
        val id=UUID.randomUUID().toString();val date=LocalDate.now().plusDays(10);val zone=ZoneId.systemDefault().id
        val spec=SeriesReminderSpec("job-$id",SeriesReminderKind.RELATIVE_DUE,offsetMinutes=60)
        try {
            TaskRepository(db).save(TaskDraft(id,"QA 背景系列",dueDate=date,zoneId=zone,
                recurrence=RecurrenceRule(date,RecurrenceFrequency.WEEKLY,zone,maxCount=2),seriesReminders=listOf(spec)))
            val until=System.currentTimeMillis()+15_000
            while(scheduler.getPendingJob(5001)==null&&System.currentTimeMillis()<until)delay(100)
            val job=scheduler.getPendingJob(5001);assertNotNull(job);assertTrue(job!!.isPersisted);assertTrue(job.isPeriodic)
            assertEquals(15*60*1000L,job.intervalMillis)
            val before=ReminderRuntime.maintenanceCompletions
            val output=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cmd jobscheduler run -f ${context.packageName} 5001").use{fd->FileInputStream(fd.fileDescriptor).use{String(it.readBytes())}}
            assertTrue(output,output.contains("Running job"))
            val finished=System.currentTimeMillis()+20_000
            while(ReminderRuntime.maintenanceCompletions<=before&&System.currentTimeMillis()<finished)delay(100)
            assertTrue(ReminderRuntime.maintenanceCompletions>before)
            assertEquals(1,db.dao().seriesOccurrences(id).size)
            assertEquals(1,db.reminders().forTask(occurrenceId(id,1)).size)
            android.util.Log.i("TodoSeriesJob","persisted=true, forcedJobCompleted=true, duplicateOccurrences=0")
        }finally{
            val gateway=AndroidReminderGateway(context)
            db.withTransaction {
                val tasks=db.dao().seriesOccurrences(id).map{it.taskId}
                tasks.forEach{task->db.reminders().forTask(task).forEach{r->r.alarmKey?.let{gateway.cancel(r,it)};gateway.dismiss(r.id)}}
                db.openHelper.writableDatabase.execSQL("DELETE FROM occurrences WHERE seriesId=?",arrayOf(id))
                tasks.forEach{db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id=?",arrayOf(it))}
                db.openHelper.writableDatabase.execSQL("DELETE FROM recurring_series WHERE id=?",arrayOf(id))
            }
        }
    }
}
