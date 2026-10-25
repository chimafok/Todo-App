package com.chimafok.todo.data

import android.Manifest
import android.app.NotificationManager
import android.content.Context
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
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidReminderTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private fun shell(command:String):String=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { fd -> FileInputStream(fd.fileDescriptor).use{String(it.readBytes())} }
    @Test fun actualSystemAlarmDeliversNotificationAndItsActionCompletesAssignment(): Unit=runBlocking {
        assertEquals("com.chimafok.todo.qa",context.packageName)
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        val id=UUID.randomUUID().toString();val reminderId="real-$id";val gateway=AndroidReminderGateway(context);gateway.ensureChannel()
        val db=ReminderRuntime.database;val manager=context.getSystemService(NotificationManager::class.java)
        try {
            val at=System.currentTimeMillis()+12_000
            TaskRepository(db).save(TaskDraft(id,"QA 系統提醒",categoryId=ASSIGNMENTS_ID,status=TaskStatus.DONE_UNSUBMITTED,
                reminders=listOf(ReminderSpec(reminderId,ReminderKind.FIXED,"UTC",fixedAt=at))))
            ReminderRuntime.engine.reconcile(force=true)
            val row=db.reminders().get(reminderId)!!
            assertNotNull(row.alarmKey);assertTrue(row.alarmMode in listOf("EXACT","INEXACT"))
            val deadline=System.currentTimeMillis()+90_000
            while(manager.activeNotifications.none{it.tag==reminderId}&&System.currentTimeMillis()<deadline) delay(250)
            val notification=manager.activeNotifications.firstOrNull{it.tag==reminderId}
            assertNotNull("A real system alarm must post a real notification",notification)
            val deliveredAt=System.currentTimeMillis()
            assertEquals("DELIVERED",db.reminders().get(reminderId)!!.state)
            assertEquals(com.chimafok.todo.R.drawable.ic_notification, notification!!.notification.smallIcon.resId)
            notification!!.notification.actions.first().actionIntent.send()
            val completionDeadline=System.currentTimeMillis()+10_000
            while(db.dao().task(id)!!.status!=TaskStatus.COMPLETED&&System.currentTimeMillis()<completionDeadline) delay(100)
            assertEquals(TaskStatus.COMPLETED,db.dao().task(id)!!.status)
            assertTrue(manager.activeNotifications.none{it.tag==reminderId})
            // Store timing only, never task names/notes or database contents.
            val evidence=org.json.JSONObject().put("mode",row.alarmMode).put("plannedAt",at).put("receivedAt",deliveredAt)
                .put("delayMs",deliveredAt-at).put("notificationCompletion","passed")
            android.util.Log.i("TodoReminderTiming", evidence.toString())
        } finally {
            db.reminders().get(reminderId)?.let{row->row.alarmKey?.let{gateway.cancel(row,it)}};gateway.dismiss(reminderId)
            db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id=?",arrayOf(id))
            // QA only; leave the user's normal app and its permissions untouched.
            // Revoking a runtime permission kills instrumentation; QA keeps its test grants.
        }
    }
}
