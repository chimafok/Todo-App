package com.chimafok.todo.domain

import com.chimafok.todo.data.TaskEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class StudyScheduleTest {
    private val day=LocalDate.of(2030,2,3)
    private fun task(id:String,start:LocalDate?=null,time:LocalTime?=null,due:LocalTime?=null)=TaskEntity(id,id,"",STUDY_ID,
        dueDate=due?.let{day},dueTime=due,zoneId=due?.let{"UTC"},createdAt=1,updatedAt=1,
        startDate=start,startTime=time,startZoneId=start?.let{"UTC"})
    private fun rejects(block:()->Unit){var rejected=false;try{block()}catch(_:IllegalArgumentException){rejected=true};assertTrue(rejected)}
    @Test fun optionalStartDateNeedsNoTimeButTimeRequiresDateAndZone() {
        validateDraft(TaskDraft("a","study",startDate=day),false)
        rejects{validateDraft(TaskDraft("a","study",startTime=LocalTime.NOON),false)}
        rejects{validateDraft(TaskDraft("a","study",startDate=day,startTime=LocalTime.NOON),false)}
        validateDraft(TaskDraft("a","study",startDate=day,startTime=LocalTime.NOON,startZoneId="Asia/Hong_Kong"),false)
    }
    @Test fun nonexistentDstStartIsRejectedInsteadOfShifted() {
        rejects{validateDraft(TaskDraft("a","study",startDate=LocalDate.of(2026,3,8),startTime=LocalTime.of(2,30),startZoneId="America/New_York"),false)}
    }
    @Test fun deadlineWinsThenStartDateAndTimeBreakTies() {
        val early=task("early",day,LocalTime.of(9,0),LocalTime.of(17,0))
        val late=task("late",day,LocalTime.of(10,0),LocalTime.of(17,0))
        val prior=task("prior",day.plusDays(1),LocalTime.NOON,LocalTime.of(16,0))
        assertEquals(listOf("prior","early","late"),listOf(late,early,prior).sortedWith(taskTimeOrder).map{it.id})
    }
    @Test fun unscheduledStartsSortBeforeBlankAndDoNotCreateDeadline() {
        val blank=task("blank");val dated=task("date",day);val timed=task("time",day,LocalTime.NOON);val next=task("next",day.plusDays(1))
        assertEquals(listOf("time","date","next","blank"),listOf(blank,next,dated,timed).sortedWith(unscheduledTaskOrder).map{it.id})
        assertTrue(listOf(blank,dated,timed,next).all{it.dueDate==null})
    }
    @Test fun startsUseCapturedTimezoneWhenComparingTimes() {
        val hongKong=task("hk",day,LocalTime.NOON).copy(startZoneId="Asia/Hong_Kong")
        val utc=task("utc",day,LocalTime.of(5,0))
        assertEquals(listOf("hk","utc"),listOf(utc,hongKong).sortedWith(unscheduledTaskOrder).map{it.id})
    }
}
