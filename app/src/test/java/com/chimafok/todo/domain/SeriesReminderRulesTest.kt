package com.chimafok.todo.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SeriesReminderRulesTest {
    private val day=LocalDate.of(2026,10,28)
    private fun fixed(days:Int=0,time:LocalTime?=LocalTime.of(18,30)) = SeriesReminderSpec("f",SeriesReminderKind.OCCURRENCE_TIME,dayOffset=days,time=time)
    @Test fun fixedRulesGenerateIndependentInstantsAndStableOccurrenceIdentities() {
        val first=occurrenceReminder(fixed(-1),"one",day,null,"UTC")
        val second=occurrenceReminder(fixed(-1),"two",day.plusDays(1),null,"UTC")
        assertEquals(Instant.parse("2026-10-27T18:30:00Z").toEpochMilli(),first.fixedAt)
        assertEquals(86_400_000L,second.fixedAt!!-first.fixedAt!!)
        assertNotEquals(first.id,second.id);assertEquals(first,occurrenceReminder(fixed(-1),"one",day,null,"UTC"))
        assertEquals(Instant.parse("2026-10-30T18:30:00Z").toEpochMilli(),occurrenceReminder(fixed(2),"one",day,null,"UTC").fixedAt)
    }
    @Test fun blankTemplateTimeInheritsEachDeadlineOrEditableBaseWithoutChangingDeadline() {
        assertEquals(Instant.parse("2026-10-28T04:00:00Z").toEpochMilli(),occurrenceReminder(fixed(time=null),"one",day,LocalTime.NOON,"Asia/Hong_Kong").fixedAt)
        assertEquals(Instant.parse("2026-10-28T02:00:00Z").toEpochMilli(),occurrenceReminder(fixed(time=null),"one",day,null,"Asia/Hong_Kong").fixedAt)
        assertEquals(Instant.parse("2026-10-28T10:15:00Z").toEpochMilli(),occurrenceReminder(fixed(time=null).copy(baseTime=LocalTime.of(18,15)),"one",day,null,"Asia/Hong_Kong").fixedAt)
    }
    @Test fun automaticDstGapsAdvanceAndOverlapsChooseEarlierOffset() {
        assertEquals(Instant.parse("2026-03-08T07:30:00Z").toEpochMilli(),occurrenceReminder(fixed(time=LocalTime.of(2,30)),"one",LocalDate.of(2026,3,8),null,"America/New_York").fixedAt)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z").toEpochMilli(),occurrenceReminder(fixed(time=LocalTime.of(1,30)),"one",LocalDate.of(2026,11,1),null,"America/New_York").fixedAt)
    }
    @Test fun generationLooksAheadByLargestLeadWithTwoDayBufferInSeriesZone() {
        val now=Instant.parse("2026-10-23T23:30:00Z")
        val rel=SeriesReminderSpec("r",SeriesReminderKind.RELATIVE_DUE,offsetMinutes=3*1440+1)
        assertNull(reminderGenerationDate(emptyList(),now,"UTC"))
        assertEquals(LocalDate.of(2026,10,29),reminderGenerationDate(listOf(rel,fixed(-2)),now,"UTC"))
        assertEquals(LocalDate.of(2026,10,30),reminderGenerationDate(listOf(rel),now,"Asia/Hong_Kong"))
    }
    @Test fun futurePlanningRespectsHorizonBatchLimitAndDoesNotConsumeUnneededSlots() {
        val rule=RecurrenceRule(day,RecurrenceFrequency.DAILY,"UTC",maxCount=10)
        val now=Instant.parse("2026-10-23T10:00:00Z")
        val first=planOccurrences("s",rule,0,now,0,limit=2,throughDate=day.plusDays(4))
        assertEquals(listOf(day,day.plusDays(1)),first.slots.map{it.date});assertTrue(first.hasMoreDue)
        val next=planOccurrences("s",rule,first.cursor,now,throughDate=day.plusDays(4))
        assertEquals(listOf(3,4,5),next.slots.map{it.sequence});assertFalse(next.hasMoreDue);assertFalse(next.ended)
    }
    @Test fun invalidTemplatesAndOutOfRangeDatesAreRejected() {
        val invalid=listOf(fixed().copy(dayOffset=3651),fixed().copy(offsetMinutes=60),fixed().copy(id=""),
            SeriesReminderSpec("r",SeriesReminderKind.RELATIVE_DUE,offsetMinutes=-1),
            SeriesReminderSpec("r",SeriesReminderKind.RELATIVE_DUE,offsetMinutes=1,time=LocalTime.NOON))
        invalid.forEach { try{validateSeriesReminder(it);fail()}catch(_:IllegalArgumentException){} }
        try{occurrenceReminder(fixed(-1),"one",LocalDate.of(1,1,1),null,"UTC");fail()}catch(_:IllegalArgumentException){}
    }
}
