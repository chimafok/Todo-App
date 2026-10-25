package com.chimafok.todo.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RetentionRulesTest {
    @Test fun trashExpiresAtExactlyThirtyElapsedDays() {
        val at=Instant.parse("2026-10-23T10:15:00Z").toEpochMilli()
        assertFalse(retentionExpired(at,30,at+30*86_400_000L-1))
        assertTrue(retentionExpired(at,30,at+30*86_400_000L))
        assertFalse(retentionExpired(at,30,at-1))
    }
    @Test fun retentionUsesElapsedDaysAcrossDstAndNotCalendarMonths() {
        val zone=ZoneId.of("America/New_York")
        val start=LocalDate.of(2026,10,15).atTime(12,0).atZone(zone)
        assertTrue(retentionExpired(start.toInstant().toEpochMilli(),30,start.plusDays(30).toInstant().toEpochMilli()))
        val spring=LocalDate.of(2026,2,15).atTime(12,0).atZone(zone)
        assertFalse(retentionExpired(spring.toInstant().toEpochMilli(),30,spring.plusDays(30).toInstant().toEpochMilli()))
    }
    @Test fun customDaysArePositiveIntegersWithoutSilentTruncation() {
        assertEquals(1,retentionDays(" 1 "));assertEquals(365,retentionDays("365"));assertEquals(Int.MAX_VALUE,retentionDays(Int.MAX_VALUE.toString()))
        for(text in listOf("","0","-1","1.5","2147483648","1e3")) {
            try {retentionDays(text);fail(text)} catch(_:IllegalArgumentException){}
        }
    }
    @Test fun epochArithmeticDoesNotOverflowOrExpireFutureTimestamps() {
        assertFalse(retentionExpired(Long.MAX_VALUE,30,Long.MAX_VALUE))
        assertTrue(retentionExpired(Long.MIN_VALUE,30,Long.MAX_VALUE))
        assertFalse(retentionExpired(0,Int.MAX_VALUE,1_000))
    }
}
