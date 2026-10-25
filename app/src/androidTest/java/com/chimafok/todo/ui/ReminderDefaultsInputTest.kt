package com.chimafok.todo.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*

@com.chimafok.todo.Regression101
@RunWith(AndroidJUnit4::class)
class ReminderDefaultsInputTest {
    @Test fun newSingleAndSeriesInputsUseOneDayTenClockButExistingInputsRoundTripExactly() {
        val fresh=ReminderInput(kind="RELATIVE_DUE",zone="UTC")
        assertEquals(ReminderOffsetUnit.DAYS,fresh.offsetUnit);assertEquals("1",fresh.offset);assertEquals("10:00",fresh.base)
        assertEquals(1440,fresh.spec(LocalDate.of(2026,10,27),null,"UTC").offsetMinutes)
        val series=SeriesReminderInput()
        assertEquals("DAYS",series.unit);assertEquals("10:00",series.base);assertEquals(1440,series.spec().offsetMinutes)
        val legacy=ReminderSpec("old",ReminderKind.RELATIVE_DUE,"UTC",offsetMinutes=60,baseTime=LocalTime.of(20,0))
        val row=legacy.input()
        assertEquals(ReminderOffsetUnit.HOURS,row.offsetUnit);assertEquals("20:00",row.base)
        assertEquals(legacy,decodeReminderInputs(encodeReminderInputs(listOf(row))).single().spec(LocalDate.of(2026,10,27),null,"UTC"))
        val oldSeries=SeriesReminderSpec("old-s",SeriesReminderKind.RELATIVE_DUE,offsetMinutes=60,baseTime=LocalTime.of(20,0))
        assertEquals(oldSeries,decodeSeriesReminderInputs(encodeSeriesReminderInputs(listOf(oldSeries.input()))).single().spec())
        assertEquals(LocalTime.of(20,0),ReminderInput(zone="UTC").effectiveTime(LocalDate.of(2026,10,27),LocalTime.of(20,0),"UTC"))
    }
}
