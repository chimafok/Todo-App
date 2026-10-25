package com.chimafok.todo.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderDefaultsTest {
    @Test fun newDefinitionsUseTenClockWhileTypedDeadlineStillWins() {
        assertEquals(LocalTime.of(10,0),ReminderSpec("n",ReminderKind.RELATIVE_DUE,"UTC",offsetMinutes=1440).baseTime)
        assertEquals(LocalTime.of(10,0),SeriesReminderSpec("s",SeriesReminderKind.RELATIVE_DUE,offsetMinutes=1440).baseTime)
        assertEquals(LocalTime.of(10,0),fixedReminderDefaultTime(null,null,null,"UTC"))
        assertEquals(LocalTime.of(20,0),fixedReminderDefaultTime(LocalDate.of(2026,10,26),LocalTime.of(20,0),"UTC","UTC"))
    }
    @Test fun explicitLegacyTwentyClockDefinitionsKeepTheirOriginalInstants() {
        val day=LocalDate.of(2026,10,27)
        val old=ReminderSpec("old",ReminderKind.RELATIVE_DUE,"UTC",offsetMinutes=1440,baseTime=LocalTime.of(20,0))
        assertEquals(Instant.parse("2026-10-26T20:00:00Z").toEpochMilli(),resolveReminder(old,day,null,null))
        val template=SeriesReminderSpec("legacy",SeriesReminderKind.OCCURRENCE_TIME,dayOffset=0,time=null,baseTime=LocalTime.of(20,0))
        assertEquals(Instant.parse("2026-10-27T20:00:00Z").toEpochMilli(),occurrenceReminder(template,"t",day,null,"UTC").fixedAt)
    }
}
