package com.chimafok.todo.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeriesReminderInputTest {
    @Test fun incompleteQuantityDirectionAndBlankTimeRoundTripWithoutLosingIds() {
        val row=SeriesReminderInput(quantity="",unit="DAYS",kind="OCCURRENCE_TIME",direction="BEFORE",dayCount="",time="")
        assertEquals(row,decodeSeriesReminderInputs(encodeSeriesReminderInputs(listOf(row))).single())
    }
    @Test fun canonicalTemplateInputRoundTripsWithoutTurningBlankIntoTwentyOClock() {
        val specs=listOf(SeriesReminderSpec("r",SeriesReminderKind.RELATIVE_DUE,offsetMinutes=90),
            SeriesReminderSpec("f",SeriesReminderKind.OCCURRENCE_TIME,dayOffset=-2,time=null))
        assertEquals(specs,specs.map{it.input().spec()})
        try{SeriesReminderInput(kind="OCCURRENCE_TIME",direction="BEFORE",dayCount="9999999999999999").spec();fail()}catch(_:IllegalArgumentException){}
    }
}
