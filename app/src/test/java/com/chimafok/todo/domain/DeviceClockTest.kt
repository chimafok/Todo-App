package com.chimafok.todo.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.util.TimeZone

class DeviceClockTest {
    @Test fun longLivedClockFollowsTimezoneChangesButExplicitZoneStaysFixed() {
        val previous = TimeZone.getDefault()
        val clock = DeviceClock
        val fixedZone = clock.withZone(ZoneId.of("Asia/Hong_Kong"))
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(ZoneId.of("America/Los_Angeles"), clock.zone)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(ZoneId.of("Asia/Tokyo"), clock.zone)
            assertEquals(ZoneId.of("Asia/Hong_Kong"), fixedZone.zone)
        } finally { TimeZone.setDefault(previous) }
    }
}
