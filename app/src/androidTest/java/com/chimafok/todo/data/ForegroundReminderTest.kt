package com.chimafok.todo.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@com.chimafok.todo.Regression101
@RunWith(AndroidJUnit4::class)
class ForegroundReminderTest {
    private class MutableClock(var now:Instant):Clock() {
        override fun instant()=now
        override fun getZone()=ZoneOffset.UTC
        override fun withZone(zone:ZoneId):Clock=Clock.fixed(now,zone)
    }
    private class Fake:ReminderGateway {
        var allowed=true;var exact=true;var schedules=0;var cancellations=0
        val alarms=mutableMapOf<String,Long>();val shown=mutableListOf<String>()
        override fun notificationsAllowed()=allowed
        override fun exactAllowed()=exact
        override fun schedule(reminder:ReminderEntity,key:String,triggerAt:Long):String {
            schedules++;alarms[reminder.id]=triggerAt;return if(exact)"EXACT" else "INEXACT"
        }
        override fun cancel(reminder:ReminderEntity,key:String) {cancellations++;alarms.remove(reminder.id)}
        override fun cancelRemoved(taskId:String,reminderId:String,key:String) {alarms.remove(reminderId)}
        override fun dismiss(reminderId:String) {}
        override fun show(reminder:ReminderEntity,task:TaskEntity,key:String) {shown+=reminder.id}
    }
    private fun withDb(block:suspend(TodoDatabase,MutableClock,Fake)->Unit)=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.chimafok.todo.qa",context.packageName)
        val name="qa101-foreground-${UUID.randomUUID()}.db"
        val db=TodoDatabase.create(context,name)
        try {block(db,MutableClock(Instant.parse("2026-10-25T00:00:00Z")),Fake())}
        finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun warmScanLeavesFutureAlarmsUntouchedButRechecksChangedCapabilities()=withDb {db,clock,fake ->
        TaskRepository(db,clock).save(TaskDraft("task","owned fixture",dueDate=LocalDate.of(2026,10,27),zoneId="UTC",reminders=listOf(
            ReminderSpec("legacy",ReminderKind.RELATIVE_DUE,"UTC",offsetMinutes=60,baseTime=LocalTime.of(20,0)),
            ReminderSpec("new",ReminderKind.RELATIVE_DUE,"UTC",offsetMinutes=1440))))
        val engine=ReminderEngine(db,fake,clock)
        engine.reconcile(force=true,recovery=true)
        assertEquals(2,fake.schedules)
        val original=fake.alarms.toMap()
        engine.reconcile(recovery=true,scanAll=true)
        assertEquals(2,fake.schedules);assertEquals(0,fake.cancellations);assertEquals(original,fake.alarms)
        assertEquals(LocalTime.of(20,0),db.reminders().get("legacy")!!.baseTime)
        assertEquals(LocalTime.of(10,0),db.reminders().get("new")!!.baseTime)
        fake.allowed=false
        engine.reconcile(recovery=true,scanAll=true)
        assertTrue(fake.alarms.isEmpty());assertTrue(db.reminders().all().all {it.state=="BLOCKED"})
        fake.allowed=true;fake.exact=false
        engine.reconcile(recovery=true,scanAll=true)
        assertEquals(4,fake.schedules);assertTrue(db.reminders().all().all {it.alarmMode=="INEXACT"})
        fake.alarms.clear()
        engine.reconcile(force=true,recovery=true)
        assertEquals(6,fake.schedules);assertEquals(2,fake.alarms.size)
    }
    @Test fun warmRecoveryMarksOverduePendingMissedWithoutLateNotificationBurst()=withDb {db,clock,fake ->
        TaskRepository(db,clock).save(TaskDraft("task","owned fixture",reminders=listOf(
            ReminderSpec("one",ReminderKind.FIXED,"UTC",fixedAt=clock.millis()+3_600_000),
            ReminderSpec("two",ReminderKind.FIXED,"UTC",fixedAt=clock.millis()+7_200_000))))
        val engine=ReminderEngine(db,fake,clock)
        engine.reconcile(force=true)
        clock.now=clock.now.plusSeconds(10_800)
        engine.reconcile(recovery=true,scanAll=true)
        assertTrue(db.reminders().all().all {it.state=="MISSED"})
        assertTrue(fake.shown.isEmpty());assertTrue(fake.alarms.isEmpty());assertEquals(2,fake.schedules)
    }
}
